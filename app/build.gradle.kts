import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.androidx.room)
}

val releaseSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? =
    System.getenv(environmentName) ?: releaseSigningProperties.getProperty(propertyName)

val releaseStoreFile = releaseSigningValue("storeFile", "RELEASE_STORE_FILE")
val appVersionName = providers.gradleProperty("releaseVersionName").orElse("1.0.0").get()
val appVersionCode = providers.gradleProperty("releaseVersionCode").orElse("1").get().toIntOrNull()
    ?: error("releaseVersionCode must be an integer")

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "ru.landilf.memeorganizer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ru.landilf.memeorganizer"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile?.let(rootProject::file)
            storePassword = releaseSigningValue("storePassword", "RELEASE_STORE_PASSWORD")
            keyAlias = releaseSigningValue("keyAlias", "RELEASE_KEY_ALIAS")
            keyPassword = releaseSigningValue("keyPassword", "RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.onnxruntime.android)
    implementation(libs.opencv)
    implementation(libs.commons.compress)
    implementation(libs.djl.tokenizers)
    runtimeOnly(libs.djl.tokenizer.native)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
