import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // KSP generates the Room DAO/database implementations at compile time
    alias(libs.plugins.ksp)
}

// Secrets live in local.properties (gitignored) so they never reach GitHub.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.petcare"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.petcare"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resValue(
            "string",
            "google_web_client_id",
            localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")
        )
    }

    buildFeatures {
        resValues = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    // Exported Room schemas are packaged into androidTest so MigrationTestHelper can validate migrations.
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    // lifecycleScope: runs password hashing / DB work off the main thread
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ViewModels for the Home, Tasks and Expenses screens
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // Room: type-safe SQLite access (entities, DAOs, migrations)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // room-testing reads exported schemas with kotlinx-serialization 1.8; another library pins 1.7.3,
    // and AGP makes the test classpath match the app, so the newer version is declared here.
    implementation(libs.kotlinx.serialization.core)
    // WorkManager: deferrable background work (daily reminder check, re-scheduling after boot/update)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing) // MigrationTestHelper
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.work.testing) // TestListenableWorkerBuilder, WorkManagerTestInitHelper
}

// Export each Room schema version as JSON (app/schemas/) so migrations can be tested and reviewed.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
