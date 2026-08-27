import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date

val APP_NAME = "N-Zik"

plugins {
    // Multiplatform
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)

    // Android
    alias(libs.plugins.android.application)
    alias(libs.plugins.room)


    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }

        androidMain.dependencies {
            implementation(libs.media3.session)
            implementation(libs.kotlinx.coroutines.guava)
            implementation(libs.extractor)
            implementation(libs.nanojson)
            implementation(libs.androidx.webkit)
            implementation(libs.androidx.graphics.shapes)
            implementation(libs.ktor.okhttp)
            implementation(projects.discordrpc)
            implementation(projects.nextvisualizer)

            // Related to built-in game, maybe removed in future?
            implementation(libs.compose.runtime.livedata)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)


            implementation(projects.oldtube)
            implementation(projects.invidious)


            implementation(libs.room)
            implementation(libs.room.runtime)
            implementation(libs.room.sqlite.bundled)

            implementation(libs.mediaplayer.kmp)

            implementation(libs.navigation.kmp)

            //coil3 mp
            implementation(libs.coil.compose.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.mp)
            implementation(libs.coil.network.okhttp)

            implementation(libs.translator)

        }
    }
}

android {
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    androidComponents {
        beforeVariants(selector().withBuildType("release")) {
            it.enable = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileSdk = 37

    defaultConfig {
        applicationId = "com.nevar.nzik"
        minSdk = 24
        targetSdk = 37
        versionCode = 86
        versionName = "7.5.0"

        /*
                UNIVERSAL VARIABLES
         */
        buildConfigField( "Boolean", "IS_AUTOUPDATE", "true" )
        buildConfigField( "String", "APP_NAME", "\"$APP_NAME\"" )
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("**/*.so")
        }
    }

    splits {
        abi {
            reset()
            isUniversalApk = true
        }
    }

    namespace = "app.n_zik.android"

    buildTypes {
        debug {
            manifestPlaceholders += mapOf("appName" to "$APP_NAME-debug")
            versionNameSuffix = "-debug"
            applicationIdSuffix = ".debug"

            buildConfigField( "Boolean", "IS_AUTOUPDATE", "false" )
            signingConfig = signingConfigs.getByName("debug")
        }

        create( "full" ) {
            // App's properties
            versionNameSuffix = "-f"
            // Fallback for modules that don't have a 'full' build type (like :discordrpc)
            matchingFallbacks += listOf("release")
        }

        create( "minified" ) {
            // App's properties
            versionNameSuffix = "-m"
            // Fallback for modules that don't have a 'minified' build type (like :discordrpc)
            matchingFallbacks += listOf("release")

            // Package optimization
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create( "full32" ) {
            // App's properties
            initWith( maybeCreate("full") )
            versionNameSuffix = "-f32"
            // Fallback for modules that don't have a 'full32' build type
            matchingFallbacks += listOf("release")
        }

        create( "minified32" ) {
            // App's properties
            initWith( maybeCreate("minified") )
            versionNameSuffix = "-m32"
            // Fallback for modules that don't have a 'minified32' build type
            matchingFallbacks += listOf("release")
        }

        create( "beta" ) {
            initWith( maybeCreate("full") )
            versionNameSuffix = "-beta"
            // Fallback for modules that don't have a 'beta' build type (like :discordrpc)
            matchingFallbacks += listOf("release")
        }

        create( "beta32" ) {
            initWith( maybeCreate("beta") )
            versionNameSuffix = "-beta32"
            // Fallback for modules that don't have a 'beta32' build type
            matchingFallbacks += listOf("release")
        }

        create( "foss" ) {
            // App's properties
            initWith( maybeCreate("full") )
            manifestPlaceholders += mapOf("appName" to "$APP_NAME-Foss")
            applicationIdSuffix = ".foss"
            buildConfigField( "Boolean", "IS_AUTOUPDATE", "false" )

            // Fallback for modules that don't have a 'foss' build type
            matchingFallbacks += listOf("release")
        }

        val devDate = project.findProperty("devDate")?.toString()
            ?: SimpleDateFormat("yyyyMMdd").format(Date())

        create( "dev" ) {
            manifestPlaceholders += mapOf("appName" to "$APP_NAME-dev")
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev-$devDate"
            buildConfigField( "Boolean", "IS_AUTOUPDATE", "true" )

            // Fallback for modules that don't have a 'dev' build type (like :discordrpc)
            matchingFallbacks += listOf("release")
        }

        create( "dev32" ) {
            // App's properties
            manifestPlaceholders += mapOf("appName" to "$APP_NAME-dev32")
            applicationIdSuffix = ".dev"
            initWith( maybeCreate("dev") )
            versionNameSuffix = "-dev32-$devDate"
            buildConfigField( "Boolean", "IS_AUTOUPDATE", "true" )

            // Fallback for modules that don't have a 'dev32' build type
            matchingFallbacks += listOf("release")
        }

        /**
         * For convenience only.
         * "Forkers" want to change app name across builds
         * just need to change this variable
         */
        forEach {
            it.manifestPlaceholders.putIfAbsent( "appName", APP_NAME )
            if (it.name == "full32" || it.name == "minified32" || it.name == "beta32" || it.name == "dev32") {
                it.buildConfigField("Boolean", "ENABLE_FFMPEG", "false")
            } else {
                it.buildConfigField("Boolean", "ENABLE_FFMPEG", "true")
            }
        }
    }

    applicationVariants.all {
        outputs.map { it as BaseVariantOutputImpl }
               .forEach { output ->
                   val typeName = buildType.name
                   output.outputFileName = "$APP_NAME-$typeName.apk"
               }
    }

    sourceSets.all {
        kotlin.srcDirs("src/$name/kotlin")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    androidResources {
        generateLocaleConfig = true
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "rimusic.composeapp.generated.resources"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.shimmer)
    implementation(libs.androidx.palette)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.material3)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.compose.animation)
    implementation(libs.kotlin.csv)
    compileOnly(libs.ffmpeg.kit.audio)
    "debugImplementation"(libs.ffmpeg.kit.audio)
    "fullImplementation"(libs.ffmpeg.kit.audio)
    "minifiedImplementation"(libs.ffmpeg.kit.audio)
    "betaImplementation"(libs.ffmpeg.kit.audio)
    "devImplementation"(libs.ffmpeg.kit.audio)
    "fossImplementation"(libs.ffmpeg.kit.audio)
    implementation(libs.monetcompat)
    implementation(libs.androidmaterial)
    implementation(libs.timber)
    implementation(libs.androidx.crypto)
    implementation(libs.math3)
    implementation(libs.toasty)
    implementation(libs.androidyoutubeplayer)
    implementation(libs.androidx.glance.widgets)
    implementation(libs.gson)
    implementation(libs.hypnoticcanvas)
    implementation(libs.hypnoticcanvas.shaders)
    implementation(libs.github.jeziellago.compose.markdown)
    implementation(libs.compose.reorderable)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.car.app)

    implementation(libs.room)
    add("kspAndroid", libs.room.compiler)


    implementation(projects.oldtube)
    implementation(projects.kugou)
    implementation(projects.lrclib)
    implementation(projects.betterlyrics)
    implementation(libs.freedroidwarn)


    coreLibraryDesugaring(libs.desugaring.nio)

    testImplementation(libs.bundles.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testRuntimeOnly(libs.junit.platform)
    testRuntimeOnly(libs.junit.vintage.engine)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.jetbrains.annotations)
    implementation(libs.okhttp3.okhttp)

    // Debug only
    debugImplementation(libs.ui.tooling.preview.android)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("assembleFossRelease") {
    dependsOn("assembleFoss")
}
