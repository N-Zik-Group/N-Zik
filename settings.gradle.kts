enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "N-Zik"
include(":ComposeN-Zik")
// Projects from extensions
include(":oldtube")
project(":oldtube").projectDir = file("extensions/innertube")
include(":kugou")
project(":kugou").projectDir = file("extensions/kugou")
include(":lrclib")
project(":lrclib").projectDir = file("extensions/lrclib")
include(":musicbrainz")
project(":musicbrainz").projectDir = file("extensions/musicbrainz")
include(":invidious")
project(":invidious").projectDir = file("extensions/invidious")
include(":ktor-client-brotli")
project(":ktor-client-brotli").projectDir = file("extensions/ktor-client-brotli")
include(":lastfm")
project(":lastfm").projectDir = file("extensions/lastfm")
// Submodules
include(":betterlyrics")
project(":betterlyrics").projectDir = file("modules/betterlyrics")
include(":discordrpc")
project(":discordrpc").projectDir = file("modules/discordrpc")
include(":nextvisualizer")
project(":nextvisualizer").projectDir = file("modules/nextvisualizer")

