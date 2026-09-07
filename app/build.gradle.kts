plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.healthy.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.healthy.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-step1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

// MigrationTestHelper reads the exported schema JSON from the test APK assets.
android.sourceSets.getByName("androidTest").assets.srcDirs("$projectDir/schemas")

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
