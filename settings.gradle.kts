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

        val propertiesFile: File = rootProject.projectDir.resolve("local.properties")
        val properties = if (propertiesFile.exists()) propertiesFile.loadProperties() else mapOf()
        val githubPersonalToken: String? by properties
        val myPassword = githubPersonalToken ?: System.getProperty("GITHUB_TOKEN")

        maven {
            url = uri("https://maven.pkg.github.com/zhelenskiy/KotlinSourceTextField")
            credentials {
                username = "zhelenskiy"
                password = myPassword
            }
        }

        maven {
            url = uri("https://maven.pkg.github.com/zhelenskiy/AnimateContentSingleDimension")
            credentials {
                username = "zhelenskiy"
                password = myPassword
            }
        }

        maven {
            url = uri("https://raw.githubusercontent.com/terjedahl/junique/master/maven2")
            name = "github-terjedahl-junique"
        }
    }
}

include(":composeApp")