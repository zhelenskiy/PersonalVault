import org.gradle.internal.os.OperatingSystem
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.serialization)
    alias(libs.plugins.compose.compiler)
}

val jdkVersion = libs.versions.jdk.get()
kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = jdkVersion
            }
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting
        
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.bouncycastle)
            implementation(libs.androidx.startup.runtime)
            implementation(libs.kotlin.coroutines.android)
            implementation(libs.android.svg)
            implementation(libs.androidx.documentfile)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.kodein)
            implementation(libs.kodein)
            implementation(libs.kodein.compose)
            implementation(libs.kotlin.collections.immutable)
            implementation(compose.materialIconsExtended)
            implementation(libs.serialization)
            implementation(libs.markdown)
            implementation(libs.color.picker)
            implementation(libs.kstore)
            implementation(libs.kstore.file)
            implementation(libs.kotlin.coroutines.core)
            implementation(libs.compose.animate.single.dimension)
            implementation(libs.file.kit)
            implementation(libs.editor.kotlin)
            implementation(libs.reorderable)
        }
        desktopMain.dependencies {
            nativeJavaFx()
            
            nativeSkiko()
            
            implementation(compose.desktop.currentOs)
            implementation(libs.bouncycastle)
            implementation(libs.appdirs)
            implementation(libs.kotlin.coroutines.swing)
            implementation(libs.junique)
        }
    }
}

fun KotlinDependencyHandler.nativeJavaFx() {
    val os = OperatingSystem.current()

    val platform = when {
        os.isWindows -> "win"
        os.isMacOsX -> "mac"
        else -> "linux"
    }

    val targetArch = when (val osArch = System.getProperty("os.arch")) {
        "x86" -> "x86"
        "x86_64", "amd64" -> null
        "aarch64" -> "aarch64"
        else -> error("Unsupported arch: $osArch")
    }

    val suffix = listOfNotNull(platform, targetArch).joinToString("-")

    implementation("${libs.javafx.base.get()}:$suffix")
    implementation("${libs.javafx.graphics.get()}:$suffix")
    implementation("${libs.javafx.controls.get()}:$suffix")
    implementation("${libs.javafx.media.get()}:$suffix")
    implementation("${libs.javafx.web.get()}:$suffix")
    implementation("${libs.javafx.swing.get()}:$suffix")
}

fun KotlinDependencyHandler.nativeSkiko() {
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
    
    val target = "${targetOs}-${targetArch}"
    implementation("org.jetbrains.skiko:skiko-awt-runtime-$target:${libs.versions.skiko.get()}")
}

android {
    namespace = "com.zhelenskiy.vault"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.zhelenskiy.vault"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(jdkVersion)
        targetCompatibility = JavaVersion.toVersion(jdkVersion)
    }
    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            packageName = "Secure vault"
            description = "Secure Vault Desktop App"
            packageVersion = "1.0.0"
            vendor = "Zhelenskiy"
            includeAllModules = true
            licenseFile = project.file("LICENSE.txt")

            macOS {
                iconFile.set(project.file("icon.icns"))
            }
            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
            }
            linux {
                iconFile.set(project.file("icon.png"))
                menuGroup = "Tools;"
            }
        }
    }
}

kotlin {
    targets.all {
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
}
