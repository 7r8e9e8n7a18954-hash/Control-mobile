package com.universalaiagent.automation

data class UiElement(
    val index: Int,
    val text: String,
    val description: String,
    val viewId: String?,
    val className: String,
    val bounds: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
)

data class UiSnapshot(
    val packageName: String,
    val appLabel: String,
    val elements: List<UiElement>,
) {
    fun toPrompt(maxElements: Int = 80): String {
        val header = "Current app: $appLabel ($packageName)"
        if (elements.isEmpty()) return "$header\nNo readable UI elements were exposed."
        val rows = elements.take(maxElements).joinToString("\n") { element ->
            buildString {
                append("#${element.index} ")
                if (element.text.isNotBlank()) append("text=\"${element.text}\" ")
                if (element.description.isNotBlank()) append("desc=\"${element.description}\" ")
                element.viewId?.let { append("id=\"${it.substringAfterLast('/')}\" ") }
                append("class=\"${element.className.substringAfterLast('.')}\" ")
                append("bounds=${element.bounds} ")
                if (element.clickable) append("clickable ")
                if (element.editable) append("editable ")
                if (element.scrollable) append("scrollable ")
                if (!element.enabled) append("disabled")
            }.trim()
        }
        return "$header\n$rows"
    }
}
