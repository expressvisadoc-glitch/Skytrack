// Firebase Cloud Messaging settings (docs/NOTIFICATIONS.md in skytrack-admin). Set them in
// ~/.gradle/gradle.properties; when they are missing the app builds and runs without push notifications.
fun firebaseProperty(name: String): String = providers.gradleProperty(name).getOrElse("")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.skypass.skytrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.skypass.skytrack"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"

        // Replace this with your Apps Script /exec Web App URL.
        buildConfigField(
            type = "String",
            name = "SKYTRACK_API_URL",
            value = "\"https://cwtrrtbodqctntkpnjlv.supabase.co/functions/v1/skytrack-api\""
        )
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseProperty("firebaseApiKey")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseProperty("firebaseAppId")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseProperty("firebaseProjectId")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${firebaseProperty("firebaseSenderId")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        // Debug builds (Android Studio Run / Build APK) are staging builds too, so a development build can never
        // replace the live app or use production data. Only release builds talk to production.
        debug {
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField(
                type = "String",
                name = "SKYTRACK_API_URL",
                value = "\"${providers.gradleProperty("skytrackStagingApiUrl").getOrElse("")}\""
            )
            buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseProperty("firebaseStagingAppId")}\"")
        }
        // Test build against a staging Supabase project. Installs alongside the production app
        // (separate application id). Set skytrackStagingApiUrl in ~/.gradle/gradle.properties.
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += listOf("debug")
            buildConfigField(
                type = "String",
                name = "SKYTRACK_API_URL",
                value = "\"${providers.gradleProperty("skytrackStagingApiUrl").getOrElse("")}\""
            )
            // The staging app (.staging package) is registered as its own app in the Firebase project
            buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseProperty("firebaseStagingAppId")}\"")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.data.store.preferences)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Offline-first (Room)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Foreground location for office geofence checks
    implementation(libs.play.services.location)
    implementation(libs.osmdroid.android)
    implementation(libs.androidx.biometric)

    // Push notifications
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
