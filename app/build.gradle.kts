import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseStorePath = providers.gradleProperty("INVENTORY_POS_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("INVENTORY_POS_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("INVENTORY_POS_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("INVENTORY_POS_RELEASE_KEY_PASSWORD").orNull

android {
    namespace = "com.kline.inventorypos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kline.inventorypos"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "API_BASE_URL", "\"https://api.invalid/\"")
    }

    val releaseSigning = if (listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }) {
        signingConfigs.create("release") {
            storeFile = file(requireNotNull(releaseStorePath))
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    } else null

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            val apiBaseUrl = providers.gradleProperty("INVENTORY_POS_API_URL")
                .orElse("http://127.0.0.1:5000/api/")
                .get()
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
        create("staging") {
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "Inventory POS (Staging)")
            val apiBaseUrl = providers.gradleProperty("INVENTORY_POS_STAGING_API_URL")
                .orElse("https://staging-api.invalid/")
                .get()
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release", "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            val apiBaseUrl = providers.gradleProperty("INVENTORY_POS_API_URL")
                .orElse("https://api.invalid/")
                .get()
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            signingConfig = releaseSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val verifyReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Rejects release builds without an HTTPS API endpoint and complete signing credentials."
    inputs.property("apiUrl", providers.gradleProperty("INVENTORY_POS_API_URL").orElse(""))
    inputs.property("storePath", providers.gradleProperty("INVENTORY_POS_RELEASE_STORE_FILE").orElse(""))
    inputs.property("storePassword", providers.gradleProperty("INVENTORY_POS_RELEASE_STORE_PASSWORD").orElse(""))
    inputs.property("keyAlias", providers.gradleProperty("INVENTORY_POS_RELEASE_KEY_ALIAS").orElse(""))
    inputs.property("keyPassword", providers.gradleProperty("INVENTORY_POS_RELEASE_KEY_PASSWORD").orElse(""))
    doLast {
        val values = inputs.properties.mapValues { it.value.toString() }
        val apiUrl = values.getValue("apiUrl")
        require(apiUrl.isNotBlank() && apiUrl.startsWith("https://") && !apiUrl.contains(".invalid")) {
            "Set INVENTORY_POS_API_URL to the approved HTTPS production API endpoint."
        }
        val requiredSigningValues = mapOf(
            "INVENTORY_POS_RELEASE_STORE_FILE" to values.getValue("storePath"),
            "INVENTORY_POS_RELEASE_STORE_PASSWORD" to values.getValue("storePassword"),
            "INVENTORY_POS_RELEASE_KEY_ALIAS" to values.getValue("keyAlias"),
            "INVENTORY_POS_RELEASE_KEY_PASSWORD" to values.getValue("keyPassword"),
        )
        val missing = requiredSigningValues.filterValues { it.isNullOrBlank() }.keys
        require(missing.isEmpty()) { "Missing release signing properties: ${missing.joinToString()}" }
        val storePath = values.getValue("storePath")
        require(File(storePath).isFile) { "Release keystore does not exist: $storePath" }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseConfiguration)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.google.code.scanner)

    testImplementation(libs.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
