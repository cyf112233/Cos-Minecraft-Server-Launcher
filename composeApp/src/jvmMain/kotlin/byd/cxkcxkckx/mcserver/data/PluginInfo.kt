package byd.cxkcxkckx.mcserver.data

import java.io.File

data class PluginInfo(
    val fileName: String,
    val file: File,
    val isEnabled: Boolean
) {
    val displayName: String
        get() = if (isEnabled) {
            fileName.removeSuffix(".jar")
        } else {
            fileName.removeSuffix(".jar.disabled")
        }
}
