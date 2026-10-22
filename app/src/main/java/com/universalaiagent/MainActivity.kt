package com.universalaiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.universalaiagent.data.local.AppSettingsData
import com.universalaiagent.ui.AddProviderViewModel
import com.universalaiagent.ui.AutomationViewModel
import com.universalaiagent.ui.ChatViewModel
import com.universalaiagent.ui.DiagnosticsViewModel
import com.universalaiagent.ui.KeysViewModel
import com.universalaiagent.ui.RoutingViewModel
import com.universalaiagent.ui.ScreenViewModel
import com.universalaiagent.ui.SettingsViewModel
import com.universalaiagent.ui.VoiceViewModel
import com.universalaiagent.ui.screens.AddProviderScreen
import com.universalaiagent.ui.screens.ApiKeysScreen
import com.universalaiagent.ui.screens.ChatScreen
import com.universalaiagent.ui.screens.DiagnosticsScreen
import com.universalaiagent.ui.screens.HistoryScreen
import com.universalaiagent.ui.screens.ModelBrowserScreen
import com.universalaiagent.ui.screens.PrivacyScreen
import com.universalaiagent.ui.screens.RoutingScreen
import com.universalaiagent.ui.screens.ScreenUnderstandingScreen
import com.universalaiagent.ui.screens.SettingsScreen
import com.universalaiagent.ui.screens.TaskProgressScreen
import com.universalaiagent.ui.screens.VoiceScreen
import com.universalaiagent.ui.screens.WelcomeScreen
import com.universalaiagent.ui.theme.UniversalAIAgentTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container: AppContainer by lazy {
        (application as UniversalAiAgentApp).container
    }
    private val factory by lazy { AppViewModelFactory(container) }

    private val chatViewModel: ChatViewModel by viewModels { factory }
    private val keysViewModel: KeysViewModel by viewModels { factory }
    private val routingViewModel: RoutingViewModel by viewModels { factory }
    private val settingsViewModel: SettingsViewModel by viewModels { factory }
    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels { factory }
    private val screenViewModel: ScreenViewModel by viewModels { factory }
    private val voiceViewModel: VoiceViewModel by viewModels { factory }
    private val automationViewModel: AutomationViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings: AppSettingsData? by container.settings.data.collectAsStateWithLifecycle(
                initialValue = null,
            )
            val current = settings
            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                UniversalAIAgentTheme(
                    themeMode = current.themeMode,
                    dynamicColor = current.dynamicColor,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AgentNavHost(settings = current)
                    }
                }
            }
        }
    }

    @Composable
    private fun AgentNavHost(settings: AppSettingsData) {
        val nav = rememberNavController()
        val scope = rememberCoroutineScope()
        val startDestination = if (settings.onboardingComplete) "chat" else "welcome"

        NavHost(navController = nav, startDestination = startDestination) {
            composable("welcome") {
                WelcomeScreen(
                    onChat = {
                        scope.launch { container.settings.setOnboardingComplete(true) }
                        nav.navigate("chat") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    },
                    onKeys = {
                        scope.launch { container.settings.setOnboardingComplete(true) }
                        nav.navigate("keys")
                    },
                    onPrivacy = { nav.navigate("privacy") },
                    onSettings = {
                        scope.launch { container.settings.setOnboardingComplete(true) }
                        nav.navigate("settings")
                    },
                )
            }

            composable("chat") {
                ChatScreen(
                    viewModel = chatViewModel,
                    automationViewModel = automationViewModel,
                    onHistory = { nav.navigate("history") },
                    onKeys = { nav.navigate("keys") },
                    onSettings = { nav.navigate("settings") },
                    onVoice = { nav.navigate("voice") },
                    onRouting = { nav.navigate("routing") },
                    onDiagnostics = { nav.navigate("diagnostics") },
                    onScreen = { nav.navigate("screen") },
                    onTasks = { nav.navigate("tasks") },
                )
            }

            composable("keys") {
                ApiKeysScreen(
                    viewModel = keysViewModel,
                    onBack = { nav.popBackStack() },
                    onAdd = { nav.navigate("addProvider") },
                    onEdit = { id -> nav.navigate("addProvider?configId=$id") },
                    onModels = { nav.navigate("models") },
                    onDiagnostics = { nav.navigate("diagnostics") },
                )
            }

            composable(
                route = "addProvider?configId={configId}",
                arguments = listOf(
                    navArgument("configId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val configId = entry.arguments?.getString("configId").orEmpty().ifBlank { null }
                val addProviderViewModel: AddProviderViewModel = viewModel(
                    viewModelStoreOwner = entry,
                    key = "addProvider-${configId ?: "new"}",
                    factory = AppViewModelFactory(container, configId),
                )
                AddProviderScreen(
                    viewModel = addProviderViewModel,
                    onBack = { nav.popBackStack() },
                )
            }

            composable("models") {
                ModelBrowserScreen(
                    viewModel = keysViewModel,
                    onBack = { nav.popBackStack() },
                    onAddProvider = { nav.navigate("addProvider") },
                )
            }

            composable("routing") {
                RoutingScreen(
                    viewModel = routingViewModel,
                    onBack = { nav.popBackStack() },
                    onAddProvider = { nav.navigate("addProvider") },
                )
            }

            composable("voice") {
                VoiceScreen(
                    viewModel = voiceViewModel,
                    onBack = { nav.popBackStack() },
                    onSendToAgent = { transcript ->
                        chatViewModel.updateInput(transcript)
                        chatViewModel.setAgentMode(true)
                        nav.navigate("chat") {
                            popUpTo("chat") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable("screen") {
                ScreenUnderstandingScreen(
                    viewModel = screenViewModel,
                    onBack = { nav.popBackStack() },
                )
            }

            composable("tasks") {
                TaskProgressScreen(
                    viewModel = automationViewModel,
                    onBack = { nav.popBackStack() },
                )
            }

            composable("history") {
                HistoryScreen(
                    chatViewModel = chatViewModel,
                    diagnosticsViewModel = diagnosticsViewModel,
                    onBack = { nav.popBackStack() },
                    onOpenConversation = {
                        nav.navigate("chat") {
                            popUpTo("chat") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { nav.popBackStack() },
                    onKeys = { nav.navigate("keys") },
                    onRouting = { nav.navigate("routing") },
                    onPrivacy = { nav.navigate("privacy") },
                    onDiagnostics = { nav.navigate("diagnostics") },
                    onHistory = { nav.navigate("history") },
                )
            }

            composable("privacy") {
                PrivacyScreen(onBack = { nav.popBackStack() })
            }

            composable("diagnostics") {
                DiagnosticsScreen(
                    viewModel = diagnosticsViewModel,
                    onBack = { nav.popBackStack() },
                    onKeys = { nav.navigate("keys") },
                )
            }
        }
    }
}

private class AppViewModelFactory(
    private val container: AppContainer,
    private val configId: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(container)
            modelClass.isAssignableFrom(KeysViewModel::class.java) -> KeysViewModel(container)
            modelClass.isAssignableFrom(AddProviderViewModel::class.java) ->
                AddProviderViewModel(container, configId)
            modelClass.isAssignableFrom(RoutingViewModel::class.java) -> RoutingViewModel(container)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container)
            modelClass.isAssignableFrom(DiagnosticsViewModel::class.java) -> DiagnosticsViewModel(container)
            modelClass.isAssignableFrom(ScreenViewModel::class.java) -> ScreenViewModel(container)
            modelClass.isAssignableFrom(VoiceViewModel::class.java) -> VoiceViewModel(container)
            modelClass.isAssignableFrom(AutomationViewModel::class.java) -> AutomationViewModel(container)
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
        return viewModel as T
    }
}
