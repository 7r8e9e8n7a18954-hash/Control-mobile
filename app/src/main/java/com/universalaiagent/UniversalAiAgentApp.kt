package com.universalaiagent

import android.app.Application

class UniversalAiAgentApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
