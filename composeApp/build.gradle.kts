import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.serialization)
}

val jdkVersion = "17"
kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = jdkVersion
            }
        }
    }
    
    jvm("desktop") {
        jvmToolchain {
            languageVersion = JavaLanguageVersion.of(jdkVersion)
        }
    }
    
    sourceSets {
        val desktopMain by getting
        
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.bouncycastle)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            @OptIn(ExperimentalComposeLibrary::class)
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
        }
        desktopMain.dependencies {
            implementation(project(":desktopBrowser"))
            val os = org.gradle.internal.os.OperatingSystem.current()

            val platform = when {
                os.isWindows -> "win"
                os.isMacOsX -> "mac"
                else -> "linux"
            }
            val fxVersion = "$jdkVersion.0.2"
            val osName = System.getProperty("os.name")
            val targetOs = when {
                osName == "Mac OS X" -> "macos"
                osName.startsWith("Win") -> "windows"
                osName.startsWith("Linux") -> "linux"
                else -> error("Unsupported OS: $osName")
            }

            val osArch = System.getProperty("os.arch")
            val targetArch = when (osArch) {
                "x86_64", "amd64" -> "x64"
                "aarch64" -> "arm64"
                else -> error("Unsupported arch: $osArch")
            }

            val version = "0.7.70" // or any more recent version
            val target = "${targetOs}-${targetArch}"
            api("org.jetbrains.skiko:skiko-awt-runtime-$target:$version")

            implementation("org.openjfx:javafx-base:$fxVersion:${platform}-$osArch")
            implementation("org.openjfx:javafx-graphics:$fxVersion:${platform}-$osArch")
            implementation("org.openjfx:javafx-controls:$fxVersion:${platform}-$osArch")
            implementation("org.openjfx:javafx-media:$fxVersion:${platform}-$osArch")
            implementation("org.openjfx:javafx-web:$fxVersion:${platform}-$osArch")
            implementation("org.openjfx:javafx-swing:$fxVersion:${platform}-$osArch")
            implementation(compose.desktop.currentOs)
            implementation(libs.bouncycastle)
        }
    }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
            packageName = "com.zhelenskiy.vault"
            packageVersion = "1.0.0"
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
