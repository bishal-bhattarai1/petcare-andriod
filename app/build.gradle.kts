import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
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
    implementation(libs.material)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
