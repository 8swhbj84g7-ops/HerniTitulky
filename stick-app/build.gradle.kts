plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cz.titulky.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "cz.siller.hernititulky.stick"
        minSdk = 26
        targetSdk = 34
        versionCode = 36
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
    buildTypes {
        debug { applicationIdSuffix = ".testbed" }
        release { isDebuggable = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:language-id:17.0.6")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

// Explicit allowlist keeps the legacy authentication, HTTP/UDP receivers and their assets
// outside this APK while sharing the tested game, input and OCR implementation.
val original = rootProject.layout.projectDirectory.dir("app/src/main")
val sharedStickSources = tasks.register<Sync>("syncStickSources") {
    from(original.dir("java")) {
        include("com/metallic/chiaki/lib/Chiaki.kt")
        include("cz/titulky/tv/stick/*.kt")
        exclude("cz/titulky/tv/stick/StickPlatform.kt")
        for (file in listOf("Ps5Registration", "Ctecka", "VyberTitulku", "Prekladac",
            "PrekladPribehu", "CteciFronta", "CasovaniPribehu")) {
            include("cz/titulky/tv/$file.kt")
        }
    }
    into(layout.buildDirectory.dir("generated/stickSources"))
}

val sharedStickNative = tasks.register<Sync>("syncStickNative") {
    from(original.dir("jniLibs")) { include("*/libchiaki-hra.so") }
    into(layout.buildDirectory.dir("generated/stickNative"))
}

val sharedStickResources = tasks.register<Sync>("syncStickResources") {
    from(original.dir("res")) { include("drawable-nodpi/gamelingo_*.png") }
    into(layout.buildDirectory.dir("generated/stickResources"))
}

android.sourceSets.getByName("main") {
    java.srcDir(sharedStickSources)
    jniLibs.srcDir(sharedStickNative)
    res.srcDir(sharedStickResources)
}

tasks.named("preBuild") {
    dependsOn(sharedStickSources, sharedStickNative, sharedStickResources)
}

val fixtures = layout.buildDirectory.dir("generated/stickTestFixtures")
val sharedTests = tasks.register<Sync>("syncStickTests") {
    from(rootProject.layout.projectDirectory.dir("app/src/androidTest")) {
        include("java/cz/titulky/tv/StickPlaybackTest.kt", "java/cz/titulky/tv/StickProbe.kt")
        include("assets/stick/*.mp4", "jniLibs/*/libstickprobe.so")
    }
    into(fixtures)
}

android.sourceSets.getByName("androidTest") {
    java.srcDir(fixtures.map { it.dir("java") })
    assets.srcDir(fixtures.map { it.dir("assets") })
    jniLibs.srcDir(fixtures.map { it.dir("jniLibs") })
}
tasks.matching { it.name == "preDebugAndroidTestBuild" }.configureEach { dependsOn(sharedTests) }
