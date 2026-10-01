import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val releaseRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
val admobAppId = providers.gradleProperty("ADMOB_APP_ID").orNull
val admobBannerId = providers.gradleProperty("ADMOB_BANNER_ID").orNull
val documentsApiUrl = providers.gradleProperty("DOCUMENTS_API_URL").orElse("").get()
val marketplaceApiUrl = providers.gradleProperty("MARKETPLACE_API_URL").orElse("").get()
val keystoreFile = providers.gradleProperty("KEYSTORE_FILE").orNull
val keystorePassword = providers.gradleProperty("KEYSTORE_PASSWORD").orNull
val keyAliasValue = providers.gradleProperty("KEY_ALIAS").orNull
val keyPasswordValue = providers.gradleProperty("KEY_PASSWORD").orNull

fun validAdMobAppId(value: String?): Boolean =
    value != null && Regex("^ca-app-pub-[0-9]+~[0-9]+$").matches(value.trim())

fun validAdMobBannerId(value: String?): Boolean =
    value != null && Regex("^ca-app-pub-[0-9]+/[0-9]+$").matches(value.trim())

if (releaseRequested) {
    if (!validAdMobAppId(admobAppId)) {
        throw GradleException("ADMOB_APP_ID inválido. Debe tener formato ca-app-pub-...~... (con ~, no /).")
    }
    if (!validAdMobBannerId(admobBannerId)) {
        throw GradleException("ADMOB_BANNER_ID inválido. Debe tener formato ca-app-pub-.../... (con /, no ~).")
    }
    if (keystoreFile.isNullOrBlank() || keystorePassword.isNullOrBlank() || keyAliasValue.isNullOrBlank() || keyPasswordValue.isNullOrBlank()) {
        throw GradleException("Para bundleRelease definí KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS y KEY_PASSWORD.")
    }
    if (documentsApiUrl.isBlank()) {
        throw GradleException("Para bundleRelease definí DOCUMENTS_API_URL con la URL del Worker R2.")
    }
    if (marketplaceApiUrl.isBlank()) {
        throw GradleException("Para bundleRelease definí MARKETPLACE_API_URL con la URL del Worker Marketplace.")
    }
}

android {
    namespace = "com.fixhome.soluciona"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fixhome.soluciona"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "0.8.6"
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        buildConfigField("String", "DOCUMENTS_API_URL", "\"${documentsApiUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "MARKETPLACE_API_URL", "\"${marketplaceApiUrl.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        if (!keystoreFile.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = admobAppId?.trim() ?: ""
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${(admobBannerId?.trim() ?: "").replace("\"", "\\\"")}\"")
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
