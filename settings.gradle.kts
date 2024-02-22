import java.util.*

rootProject.name = "PersonalVault"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

fun File.loadProperties(): Properties {
    val properties = Properties()
    this.bufferedReader().use { reader ->
        properties.load(reader)
    }
    return properties
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven {
            url = uri("https://maven.pkg.github.com/zhelenskiy/AnimateContentSingleDimension")
            credentials {
                username = "zhelenskiy"

                val propertiesFile: File = rootProject.projectDir.resolve("local.properties")
                val properties = if (propertiesFile.exists()) propertiesFile.loadProperties() else mapOf()
                val githubPersonalToken: String? by properties
                password = githubPersonalToken ?: System.getProperty("GITHUB_TOKEN")
            }
        }
    }
}

include(":composeApp")