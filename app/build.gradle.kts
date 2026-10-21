plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.phuuun.legato"
    compileSdk { version = release(37) { minorApiLevel = 0 } }

    defaultConfig {
        applicationId = "com.phuuun.legato"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
        // TagLib is native; this phone is 64-bit ARM, and every other ABI is dead weight in the APK.
        ndk { abiFilters += "arm64-v8a" }
    }

    // The release key lives outside the repo; its path and password come from ~/.gradle/gradle.properties.
    // Without them (anyone else building this) release builds come out unsigned.
    val storeFile = providers.gradleProperty("LEGATO_STORE_FILE").orNull
    val legatoKey = storeFile?.let {
        signingConfigs.create("legato") {
            this.storeFile = file(it)
            storePassword = providers.gradleProperty("LEGATO_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("LEGATO_KEY_ALIAS").get()
            keyPassword = storePassword
        }
    }

    buildTypes {
        release {
            // R8 drops unused code and resources (the debug APK is ~75 MB); keep rules in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = legatoKey
        }
        // A separate app, "Legato Debug", beside the real one: trying a build never touches the real playlists.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            legatoKey?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

// Each database version's schema goes in app/schemas (committed), so a later version can migrate playlists instead of losing them.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.taglib)
    implementation(libs.newpipe.extractor)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
