plugins {
    kotlin("jvm")
    @Suppress("DSL_SCOPE_VIOLATION")
    alias(libs.plugins.kotlin.serialization)
}

sourceSets.all {
    java.srcDir("src/$name/kotlin")
}

dependencies {
    implementation(libs.kotlinx.coroutines)
    implementation(projects.ktorClientBrotli)
    implementation(libs.kotlinx.datetime)
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.encoding)
    implementation(libs.ktor.serialization)
    implementation(libs.ktor.serialization.json)
    implementation(libs.okhttp3.logging.interceptor)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.platform)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
