package com.universalaiagent.data.routing

import com.universalaiagent.data.local.AppSettings
import com.universalaiagent.data.model.AiRequest
import com.universalaiagent.data.model.AiResponse
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ErrorCategory
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.ProviderType
import com.universalaiagent.data.model.RoutingNotice
import com.universalaiagent.data.model.TaskType
import com.universalaiagent.data.provider.ErrorClassifier
import com.universalaiagent.data.provider.ProviderRegistry
import com.universalaiagent.data.repository.ProviderRepository
import com.universalaiagent.data.repository.UsageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.Call
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class AiRouter(
    private val providers: ProviderRepository,
    private val registry: ProviderRegistry,
    private val settings: AppSettings,
    private val usage: UsageStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val activeCalls: MutableSet<Call> = ConcurrentHashMap.newKeySet()

    fun cancelActive() {
        activeCalls.toList().forEach { runCatching { it.cancel() } }
        activeCalls.clear()
    }

    suspend fun execute(
        task: TaskType,
        request: AiRequest,
        onDelta: (String) -> Unit,
        onNotice: (RoutingNotice) -> Unit = {},
    ): AiResponse {
        val snapshot = settings.snapshot()
        val configs = providers.all()
        val routeIds = snapshot.routeFor(task)
        val candidates = RoutePlanner.candidates(
            configs = configs,
            routeIds = routeIds,
            task = task,
            manualConfigId = snapshot.manualConfigId,
            manualMode = snapshot.manualMode,
            manualAllowFallback = snapshot.manualAllowFallback,
        )

        if (candidates.isEmpty()) {
            val message = when {
                task.requiresVision && configs.none { RoutePlanner.supportsTask(it, task) } ->
                    "No enabled configuration supports image input. Add a vision-capable model or enable one in Routing."
                snapshot.manualMode ->
                    "The manually selected provider is missing, disabled, or incompatible with ${task.label}."
                else ->
                    "No enabled provider configuration is available for ${task.label}. Add an API key first."
            }
            throw ProviderError(ErrorCategory.UNSUPPORTED_CAPABILITY, message)
        }

        val now = clock()
        val ready = candidates.filter { it.available && !it.isCoolingDown(now) }
        if (ready.isEmpty()) {
            val earliest = candidates.minOfOrNull { it.cooldownUntil } ?: now
            val waitSeconds = ((earliest - now) / 1000).coerceAtLeast(0)
            throw ProviderError(
                ErrorCategory.RATE_LIMIT,
                "All eligible providers are cooling down or disabled after errors. " +
                    "Retry in about $waitSeconds seconds or update the API key.",
            )
        }

        val failures = mutableListOf<String>()
        var lastError: ProviderError? = null

        ready.forEachIndexed { index, config ->
            var finalError: ProviderError? = null
            var succeeded = false
            val key = providers.keyFor(config)
            if (key == null && config.provider != ProviderType.CUSTOM_OPENAI) {
                finalError = ProviderError(
                    ErrorCategory.AUTHENTICATION,
                    "No API key is stored for ${config.label}. Edit the configuration to add it.",
                )
            } else {
                for (attempt in 1..2) {
                    val retryLabel = if (attempt > 1) "Retrying" else if (index > 0) "Falling back to" else "Using"
                    onNotice(
                        RoutingNotice(
                            message = "$retryLabel ${config.label} · ${config.model.ifBlank { "no model" }}",
                            isFallback = index > 0,
                        ),
                    )
                    val attemptCalls = Collections.synchronizedList(mutableListOf<Call>())
                    try {
                        val response = registry.adapterFor(config.provider).chat(
                            config = config,
                            apiKey = key.orEmpty(),
                            request = request,
                            onDelta = onDelta,
                            onCall = { call ->
                                activeCalls.add(call)
                                attemptCalls.add(call)
                            },
                        )
                        providers.recordSuccess(config.id, response)
                        usage.add(
                            provider = config.provider,
                            configLabel = config.label,
                            model = config.model,
                            success = true,
                            inputTokens = response.inputTokens,
                            outputTokens = response.outputTokens,
                            reportedCost = response.reportedCost,
                        )
                        return response
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        val providerError = if (error is ProviderError) {
                            error
                        } else {
                            ErrorClassifier.fromException(error)
                        }
                        if (providerError.category == ErrorCategory.CANCELLED) {
                            throw CancellationException("Request cancelled")
                        }
                        finalError = providerError
                        val retryDelay = ErrorClassifier.retryDelaySeconds(
                            providerError.category,
                            providerError.retryAfterSeconds,
                        )
                        if (attempt == 1 && retryDelay != null) {
                            delay(retryDelay * 1000)
                            continue
                        }
                        break
                    } finally {
                        attemptCalls.forEach { activeCalls.remove(it) }
                    }
                }
            }

            finalError?.let { error ->
                providers.recordError(
                    config.id,
                    error,
                    ErrorClassifier.cooldownSeconds(error.category, error.retryAfterSeconds),
                )
                usage.add(
                    provider = config.provider,
                    configLabel = config.label,
                    model = config.model,
                    success = false,
                    inputTokens = null,
                    outputTokens = null,
                    reportedCost = null,
                    errorCategory = error.category.name,
                    errorMessage = error.message,
                )
                failures += "${config.label}: ${error.category.name} — ${error.message}"
                lastError = error
                onNotice(
                    RoutingNotice(
                        message = "${config.label} failed (${error.category.name}). Trying the next provider.",
                        isFallback = true,
                    ),
                )
            }
        }

        val final = lastError ?: ProviderError(ErrorCategory.UNKNOWN, "No provider produced a response.")
        throw ProviderError(
            category = final.category,
            message = "All eligible providers failed:\n" + failures.joinToString("\n") { "• $it" },
            httpCode = final.httpCode,
            retryAfterSeconds = final.retryAfterSeconds,
            cause = final,
        )
    }
}
