pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        maven("https://repo.maven.apache.org/maven2/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        maven("https://repo.maven.apache.org/maven2/")
    }
}
rootProject.name = "BookRoomWeb"
include(":composeApp")
