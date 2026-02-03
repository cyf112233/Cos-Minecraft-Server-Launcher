import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.iconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.jackson.databind)
            implementation(libs.jackson.dataformat.yaml)
            implementation(libs.jackson.module.kotlin)
        }
    }
}


compose.desktop {
    application {
        mainClass = "byd.cxkcxkckx.mcserver.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Cos Minecraft Server Launcher"
            packageVersion = "1.0.0"
            
            // Application description
            description = "A modern Minecraft server launcher built with Compose Multiplatform"
            copyright = "© 2026 Cos Minecraft Server Launcher"
            vendor = "CMSL"
            
            // Windows specific configuration
            windows {
                // Create desktop shortcut
                shortcut = true
                // Create menu shortcut
                menu = true
                // Menu group name
                menuGroup = "Cos Minecraft Server Launcher"
                // Allow user to choose installation directory
                dirChooser = true
                // Install for all users
                perUserInstall = false
                // Unique identifier for upgrades
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
            
            // macOS specific configuration
            macOS {
                // Bundle identifier
                bundleID = "byd.cxkcxkckx.mcserver"
            }
            
            // Linux specific configuration
            linux {
                // Create desktop shortcut
                shortcut = true
            }
        }
    }
}
