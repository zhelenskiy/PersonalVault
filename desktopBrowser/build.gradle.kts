import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    id("org.openjfx.javafxplugin") version "0.0.13"
}

group = "com.example.webview"
version = "1.0.0"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
            withJava()
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                val osName = System.getProperty("os.name")
                val targetOs = when {
                    osName == "Mac OS X" -> "macos"
                    osName.startsWith("Win") -> "windows"
                    osName.startsWith("Linux") -> "linux"
                    else -> error("Unsupported OS: $osName")
                }

                val targetArch = when (val osArch = System.getProperty("os.arch")) {
                    "x86_64", "amd64" -> "x64"
                    "aarch64" -> "arm64"
                    else -> error("Unsupported arch: $osArch")
                }

                val version = "0.7.70" // or any more recent version
                val target = "${targetOs}-${targetArch}"
                api("org.jetbrains.skiko:skiko-awt-runtime-$target:$version")
                //Optional other deps
                implementation(compose.uiTooling)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.animation)
                implementation(compose.animationGraphics)
            }
        }
        val jvmTest by getting {
            dependencies {
            // testing deps
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "Mainkt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "your.package.name"
            packageVersion = "1.0.0"
        }
    }
}

javafx {
    version = "17"
    modules = listOf("javafx.controls", "javafx.swing", "javafx.web", "javafx.graphics")
}