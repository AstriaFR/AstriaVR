import java.util.Properties

plugins { id("com.android.application") }

val releaseSigningFile = rootProject.file("signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}

android {
    namespace = "dev.astriavr.player"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.astriavr.player"
        minSdk = 28
        targetSdk = 36
        versionCode = 27
        versionName = "4.3"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (releaseSigningFile.isFile) {
            create("distribution") {
                storeFile = rootProject.file(requireNotNull(releaseSigning.getProperty("storeFile")) { "Missing storeFile" })
                storePassword = requireNotNull(releaseSigning.getProperty("storePassword")) { "Missing storePassword" }
                keyAlias = requireNotNull(releaseSigning.getProperty("keyAlias")) { "Missing keyAlias" }
                keyPassword = requireNotNull(releaseSigning.getProperty("keyPassword")) { "Missing keyPassword" }
            }
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".validation"
            versionNameSuffix = "-validation"
        }
        getByName("release") {
            if (releaseSigningFile.isFile) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    androidTestCompileOnly(files(androidComponents.sdkComponents.sdkDirectory.map { sdk ->
        listOf("android.test.runner.jar", "android.test.base.jar").map { sdk.file("platforms/android-36/optional/$it") }
    }))
}



