package com.universalaiagent

import android.content.Context
import com.universalaiagent.automation.ActionEngine
import com.universalaiagent.automation.TaskRunStore
import com.universalaiagent.data.local.AppSettings
import com.universalaiagent.data.provider.ProviderRegistry
import com.universalaiagent.data.repository.AttachmentStore
import com.universalaiagent.data.repository.ConversationRepository
import com.universalaiagent.data.repository.ProviderRepository
import com.universalaiagent.data.repository.UsageStore
import com.universalaiagent.data.routing.AiRouter
import com.universalaiagent.data.security.SecureKeyStore
import com.universalaiagent.screen.ScreenCaptureController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = AppSettings(appContext)
    val secureStore = SecureKeyStore(appContext)
    val providers = ProviderRepository(appContext, secureStore)
    val conversations = ConversationRepository(appContext, applicationScope, settings)
    val usage = UsageStore(appContext, applicationScope)
    val attachments = AttachmentStore(appContext)
    val registry = ProviderRegistry()
    val router = AiRouter(providers, registry, settings, usage)
    val actionEngine = ActionEngine(appContext)
    val taskStore = TaskRunStore()
    val screenCapture: ScreenCaptureController = ScreenCaptureController
}
