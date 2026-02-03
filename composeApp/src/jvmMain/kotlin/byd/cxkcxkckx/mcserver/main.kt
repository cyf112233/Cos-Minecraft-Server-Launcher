package byd.cxkcxkckx.mcserver

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cos Minecraft Server Launcher",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp
        )
    ) {
        App()
    }
}
