import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing, read from a file that is not in git.
 *
 * Obtainium refuses an update whose signature differs from what is installed,
 * so this key has to outlive every release: lose it and the only way to ship
 * an update is to uninstall, which takes the database with it. The keystore
 * sits outside the repository and is listed in .gitignore twice over.
 *
 * Absent the file the release build falls back to the debug key, so a fresh
 * clone still builds. Such a build is fine to test and must not be published.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { stream -> load(stream) }
}

android {
    namespace = "com.healthy.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.healthy.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        create("release") {
            val store = keystoreProperties.getProperty("storeFile")
            if (store != null) {
                storeFile = file(store)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Left off deliberately. R8 strips what it cannot see used, and
            // this app reaches Room entities and Compose internals in ways it
            // cannot always see. Turning it on is a change worth making on
            // its own, against a device, not bundled into a first release.
            isMinifyEnabled = false
            signingConfig = if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        // java.time is available natively from API 26, which is minSdk here,
        // so the 04:00 boundary maths needs no library desugaring.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room writes the schema as JSON on every build. The files are committed.
// A schema change with no migration then shows up as a diff in review,
// and MigrationTestHelper reads these files to test the upgrade path.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// room-testing parses the exported schema JSON with kotlinx-serialization.
// The Kotlin plugin pins serialization-core to "strictly 1.7.3", while
// room-migration brings serialization-json 1.8.1; the mismatched halves throw
// AbstractMethodError at runtime. Forcing both halves to 1.8.1 fixes it.
//
// REVISIT AT STEP 17. This force is currently test-only, because nothing in
// the app uses serialization. Step 17 adds the JSON export. If that export is
// written with kotlinx-serialization, this force starts governing production
// code and must be re-checked against the runtime the app ships with, rather
// than left as a test-path workaround nobody reads.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
    }
}

// MigrationTestHelper reads the exported schema JSON from the test APK assets.
android.sourceSets.getByName("androidTest").assets.srcDirs("$projectDir/schemas")

dependencies {
    implementation(libs.androidx.core.ktx)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    // ZXing Android Embedded, Apache 2.0. Never ML Kit: it is proprietary and
    // would stop an F-Droid release (spec 11.1).
    implementation(libs.zxing.embedded)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    // android.jar stubs org.json for unit tests, so the real implementation is
    // needed to test the export and import round trip off-device.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
