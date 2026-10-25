import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.zip.ZipFile
import java.io.FileInputStream
import java.io.File

val APP_NAME = libs.versions.nzikAppName.get()

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

    // Protobuf (Listen Together wire format — Metrolist port)
    alias(libs.plugins.protobuf)
    kotlin("plugin.lombok")
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

            // Local PC bridge server
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.zxing.android.embedded)

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

    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = libs.versions.nzikApplicationId.get()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = libs.versions.nzikVersionCode.get().toInt()
        versionName = libs.versions.nzikVersionName.get()

        /*
                UNIVERSAL VARIABLES
         */
        buildConfigField("Boolean", "IS_AUTOUPDATE", "true")
        buildConfigField("String", "APP_NAME", "\"$APP_NAME\"")
        // Pinned version of the FFmpeg extension module (downloaded on first use — see FfmpegModule)
        buildConfigField("String", "FFMPEG_MODULE_VERSION", "\"${libs.versions.ffmpegkit.get()}\"")

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        // Committed keys for public (release / F-Droid) builds. F-Droid builds
        // without environment variables, so the values must live in the source
        // to stay reproducible; they are already embedded in release APKs.
        val buildProperties = Properties()
        val buildPropertiesFile = rootProject.file("build.properties")
        if (buildPropertiesFile.exists()) {
            buildProperties.load(FileInputStream(buildPropertiesFile))
        }
        val shazamKey = localProperties.getProperty("shazam_proxy_api_key", "")
        buildConfigField("String", "SHAZAM_PROXY_API_KEY", "\"$shazamKey\"")
        val lastfmApiKey = localProperties.getProperty("lastfm_api_key")
            ?: buildProperties.getProperty("lastfm_api_key")
            ?: System.getenv("LASTFM_API_KEY")
            ?: ""
        val lastfmApiSecret = localProperties.getProperty("lastfm_api_secret")
            ?: buildProperties.getProperty("lastfm_api_secret")
            ?: System.getenv("LASTFM_API_SECRET")
            ?: ""
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"$lastfmApiSecret\"")
        // Canary/decoy fields (same scheme as RiPlay): random unused constants
        // compiled into the APK to pollute automated key harvesters. Names are
        // listed here; values are committed in build.properties.
        val canaryFieldNames = listOf(
            "Qx7Kd2Wm9P", "Vt4Rn8Jb3H", "Zy6Mf5Lc1N", "Wd3Gh9Qs6U", "Xb8Jk2Tv5R",
            "Fp5Yw7Zd4M", "Hn2Cb9Xk3G", "Mr7Qf4Lw8S", "Kz6Vt1Jh5D", "Pw9Xb3Fm7Y",
            "Gd4Rk8Nq2C", "Sx5Wj7Zb1H"
        )
        canaryFieldNames.forEach { name ->
            buildConfigField("String", name, "\"${buildProperties.getProperty(name, "")}\"")
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("**/*.so")
            // FFmpeg's native libraries are NOT bundled in any build
            // (spec-ffmpeg-extension-module): the vendored FFmpegKit jar
            // (libs/ffmpeg-kit-audio-6.0.1.jar) provides the Java API only; the
            // .so files ship as the downloadable extension module and are
            // dlopen'd from filesDir by FfmpegModule. libc++_shared is excluded
            // too — it is an orphan of the FFmpegKit AAR (no app library has a
            // DT_NEEDED on it, verified via readelf on the APK) and the module
            // ships its own, patched with DF_1_GLOBAL.
            // Verification: the built APKs must contain zero FFmpeg .so files -
            // enforced by the per-package check below, which re-scans each APK
            // right after it is packaged (local builds and CI alike).
            excludes += listOf(
                "lib/*/libav*.so",
                "lib/*/libsw*.so",
                "lib/*/libffmpegkit*.so",
                "lib/*/libc++_shared.so"
            )
        }
    }

    // Global ABI split — kept as a documented no-op (isUniversalApk is the
    // AGP default; per-build-type ABI splits are not expressible through the
    // variant API). The per-build-type ABI restriction lives on the
    // merge<Variant>NativeLibs task instead (below,
    // spec-ffmpeg-extension-module).
    splits {
        abi {
            reset()
            isUniversalApk = true
        }
    }

    // Per-build-type ABI restriction (spec-ffmpeg-extension-module): packaging
    // excludes are global to the module, so the per-type strip is applied on
    // the merged-native-libs task (merge<Variant>NativeLibs, output layout
    // <out>/lib/<abi>/<lib>.so) — 64-bit builds drop the 32-bit ABIs, 32-bit
    // builds drop the 64-bit ABIs; foss + debug stay universal (4 ABIs).
    // Composes with the global FFmpegKit .so excludes above (applied by the
    // same task): those strip the FFmpeg files from every build, this drops
    // whole ABI dirs per build.
    val abi64OnlyBuildTypes = setOf("release64", "minified64", "beta64", "dev64")
    val abi32OnlyBuildTypes = setOf("release32", "minified32", "beta32", "dev32")
    tasks.withType(MergeNativeLibsTask::class.java).configureEach {
        val variant = name.removePrefix("merge").removeSuffix("NativeLibs").lowercase()
        val abisToDrop = when (variant) {
            in abi64OnlyBuildTypes -> listOf("armeabi-v7a", "x86")
            in abi32OnlyBuildTypes -> listOf("arm64-v8a", "x86_64")
            else -> null // foss, debug and test variants: universal
        }
        if (abisToDrop != null) {
            // Always re-run so the drop applies even when the inputs did not
            // change (a stale universal merge would otherwise survive an
            // up-to-date check)
            outputs.upToDateWhen { false }
            doLast {
                val lib = File(outputDir.get().asFile, "lib")
                for (abi in abisToDrop) {
                    File(lib, abi).deleteRecursively()
                }
            }
        }
    }

    // Verification (spec-ffmpeg-extension-module): no FFmpeg native library may
    // ever ship in an APK. The packaging excludes above strip them; each
    // package task re-checks its own produced APK right after packaging, so a
    // regression fails the build for any build type, locally and in CI. Stale
    // APKs left in the outputs dir by older builds are not re-flagged.
    tasks.matching { it.name.startsWith("package") }.configureEach {
        val buildType = name.removePrefix("package").lowercase()
        // Captured at configuration time: Task.project is unavailable at
        // execution time under the configuration cache
        val apkFile = project.layout.buildDirectory
            .file("outputs/apk/$buildType/$APP_NAME-$buildType.apk")
        doLast {
            val apk = apkFile.get().asFile
            if (!apk.exists()) return@doLast
            val offenders = mutableListOf<String>()
            val zip = ZipFile(apk)
            try {
                for (entry in zip.entries()) {
                    val slash = entry.name.lastIndexOf('/')
                    val base = if (slash >= 0) entry.name.substring(slash + 1) else entry.name
                    if (entry.name.startsWith("lib/") &&
                        (base.startsWith("libav") || base.startsWith("libsw") ||
                            base.startsWith("libffmpegkit") || base == "libc++_shared.so")
                    ) {
                        offenders.add(entry.name)
                    }
                }
            } finally {
                zip.close()
            }
            check(offenders.isEmpty()) {
                "FFmpeg native libraries leaked into $apk.name: ${offenders.joinToString(", ")}"
            }
            println("FFmpeg native library check: OK, no FFmpeg .so in $apk.name")
        }
    }

    namespace = libs.versions.nzikNamespace.get()

    buildTypes {
        debug {
            manifestPlaceholders += mapOf("appName" to "$APP_NAME${libs.versions.nzikSuffixAppNameDebug.get()}")
            versionNameSuffix = libs.versions.nzikSuffixVersionDebug.get()
            applicationIdSuffix = libs.versions.nzikSuffixAppIdDebug.get()

            buildConfigField("Boolean", "IS_AUTOUPDATE", "false")
            signingConfig = signingConfigs.getByName("debug")
        }

        create("release64") {
            // App's properties
            versionNameSuffix = libs.versions.nzikSuffixVersionRelease64.get()
            // Fallback for modules that don't have a 'release64' build type (like :discordrpc)
            matchingFallbacks += listOf("release")
        }

        create("minified64") {
            // App's properties
            versionNameSuffix = libs.versions.nzikSuffixVersionMinified64.get()
            // Fallback for modules that don't have a 'minified64' build type (like :discordrpc)
            matchingFallbacks += listOf("release")

            // Package optimization
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("release32") {
            // App's properties
            initWith(maybeCreate("release64"))
            versionNameSuffix = libs.versions.nzikSuffixVersionRelease32.get()
            // Fallback for modules that don't have a 'release32' build type
            matchingFallbacks += listOf("release")
        }

        create("minified32") {
            // App's properties
            initWith(maybeCreate("minified64"))
            versionNameSuffix = libs.versions.nzikSuffixVersionMinified32.get()
            // Fallback for modules that don't have a 'minified32' build type
            matchingFallbacks += listOf("release")
        }

        create("beta64") {
            initWith(maybeCreate("release64"))
            versionNameSuffix = libs.versions.nzikSuffixVersionBeta64.get()
            // Fallback for modules that don't have a 'beta64' build type (like :discordrpc)
            matchingFallbacks += listOf("release")
        }

        create("beta32") {
            initWith(maybeCreate("beta64"))
            versionNameSuffix = libs.versions.nzikSuffixVersionBeta32.get()
            // Fallback for modules that don't have a 'beta32' build type
            matchingFallbacks += listOf("release")
        }

        create("foss") {
            // App's properties
            initWith(maybeCreate("release64"))
            manifestPlaceholders += mapOf("appName" to "$APP_NAME${libs.versions.nzikSuffixAppNameFoss.get()}")
            applicationIdSuffix = libs.versions.nzikSuffixAppIdFoss.get()
            buildConfigField("Boolean", "IS_AUTOUPDATE", "false")

            // Fallback for modules that don't have a 'foss' build type
            matchingFallbacks += listOf("release")
        }

        val devDate = project.findProperty("devDate")?.toString()
            ?: SimpleDateFormat("yyyyMMdd").format(Date())

        create("dev64") {
            manifestPlaceholders += mapOf("appName" to "$APP_NAME${libs.versions.nzikSuffixAppNameDev.get()}")
            applicationIdSuffix = libs.versions.nzikSuffixAppIdDev.get()
            versionNameSuffix = "${libs.versions.nzikSuffixVersionDev64.get()}$devDate"
            buildConfigField("Boolean", "IS_AUTOUPDATE", "true")

            // Fallback for modules that don't have a 'dev64' build type (like :discordrpc)
            matchingFallbacks += listOf("release")
        }

        create("dev32") {
            // App's properties
            manifestPlaceholders += mapOf("appName" to "$APP_NAME${libs.versions.nzikSuffixAppNameDev32.get()}")
            applicationIdSuffix = libs.versions.nzikSuffixAppIdDev.get()
            initWith(maybeCreate("dev64"))
            versionNameSuffix = "${libs.versions.nzikSuffixVersionDev32.get()}$devDate"
            buildConfigField("Boolean", "IS_AUTOUPDATE", "true")

            // Fallback for modules that don't have a 'dev32' build type
            matchingFallbacks += listOf("release")
        }

        /**
         * For convenience only.
         * "Forkers" want to change app name across builds
         * just need to change this variable
         */
        // FFmpeg availability is no longer a build-time flag
        // (spec-ffmpeg-extension-module): the module is downloaded on first use
        // and checked at runtime (FfmpegModule.isAvailableOnDevice)
        forEach {
            it.manifestPlaceholders.putIfAbsent("appName", APP_NAME)
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

    testOptions {
        // Unit tests (Robolectric) must be able to resolve app resources such as strings
        // and drawables (e.g. the rewind reminder worker reads its notification strings)
        unitTests {
            isIncludeAndroidResources = true
        }
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
    packageOfResClass = libs.versions.nzikResClassPackage.get()
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Listen Together protocol (Metrolist port): protobuf-lite, generated in the
// `main` source set (src/main/proto). Mirrors the Metrolist app module config.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobufVersion.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") { option("lite") }
            }
        }
    }
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
    // FFmpegKit Java API for every build — the AAR's native .so are stripped
    // from every build (packaging.jniLibs.excludes) and dlopen'd at runtime
    // from the downloaded extension module (spec-ffmpeg-extension-module)
    implementation(libs.ffmpeg.kit.audio)
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
    implementation(projects.musicbrainz)
    implementation(projects.lastfm)
    implementation(projects.betterlyrics)


    coreLibraryDesugaring(libs.desugaring.nio)

    testImplementation(libs.bundles.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.ktor.server.test.host)
    testRuntimeOnly(libs.junit.platform)
    testRuntimeOnly(libs.junit.vintage.engine)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.jetbrains.annotations)
    implementation(libs.okhttp3.okhttp)
    implementation(libs.protobuf.java)

    // Debug only
    debugImplementation(libs.ui.tooling.preview.android)
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Robolectric + Compose dialog tests are heap-heavy (per-SDK environment
    // accumulation in the test worker); the default 512 MB worker ceiling
    // caused OutOfMemoryError in the heaviest classes (DialogOverflowTest).
    maxHeapSize = "1g"
}

tasks.register("assembleFossRelease") {
    dependsOn("assembleFoss")
}
