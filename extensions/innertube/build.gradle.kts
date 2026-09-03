
plugins {
    kotlin("jvm")
    @Suppress("DSL_SCOPE_VIOLATION")
    alias(libs.plugins.kotlin.serialization)
}

sourceSets.all {
    java.srcDir("src/$name/kotlin")
}

dependencies {
    implementation(projects.ktorClientBrotli)
    implementation(projects.invidious)

    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.encoding)
    implementation(libs.ktor.serialization)
    implementation(libs.okhttp3.okhttp)
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.serialization.kotlinx.xml)
    implementation(libs.ktor.serialization.kotlinx.protobuf)
    implementation(libs.extractor)
    api(libs.innertubex)
    implementation(libs.ksoup.html)
    implementation(libs.ksoup.entities)
}
