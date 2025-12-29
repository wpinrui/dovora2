import java.io.File
import java.util.Properties

fun loadSecrets(rootDir: File): Properties {
    val props = Properties()
    val localFile = File(rootDir, "local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use(props::load)
    }
    val secretsFile = File(rootDir, "secrets.properties")
    if (secretsFile.exists()) {
        secretsFile.inputStream().use(props::load)
    }
    return props
}

val secretProps = loadSecrets(rootDir)

fun String.escapeForBuildConfig(): String = this
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val backendBaseUrl = secretProps.getProperty("backendBaseUrl")
    ?: System.getenv("DOVORA_BACKEND_BASE_URL")
    ?: "http://10.0.2.2:8000"

val backendApiKey = secretProps.getProperty("backendApiKey")
    ?: System.getenv("DOVORA_BACKEND_API_KEY")
    ?: ""

val gptApiKey = secretProps.getProperty("gptApiKey")
    ?: System.getenv("DOVORA_GPT_API_KEY")
    ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wpinrui.dovora"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wpinrui.dovora"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "BACKEND_BASE_URL",
            "\"${backendBaseUrl.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "BACKEND_API_KEY",
            "\"${backendApiKey.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "GPT_API_KEY",
            "\"${gptApiKey.escapeForBuildConfig()}\""
        )
    }

    buildTypes {
        debug {
            // Removed .debug suffix to match OAuth client configuration
            // applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val fileName = when (variant.buildType.name) {
                "debug" -> "dovora-debug.apk"
                "release" -> "dovora-release.apk"
                else -> "dovora-${variant.buildType.name}.apk"
            }
            output.outputFileName = fileName
        }
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
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Image Loading
    implementation(libs.coil.compose)

    // Media / playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.androidx.media)
    implementation(libs.compose.foundation.pager)
    implementation(libs.compose.foundation.pager.indicators)
    
    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")
    
    // Palette for color extraction
    implementation("androidx.palette:palette-ktx:1.0.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}