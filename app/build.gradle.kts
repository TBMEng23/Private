plugins { id("com.android.application") }

android {
    namespace = "com.tbm.spinner"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tbm.spinner"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    val ksPass: String? = System.getenv("TBM_KS_PASS")?.trim()?.ifEmpty { null }
    signingConfigs {
        if (ksPass != null && file("tbm-release.jks").exists()) {
            create("tbm") {
                storeFile = file("tbm-release.jks")
                storePassword = ksPass
                keyAlias = "tbm"
                keyPassword = ksPass
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("tbm") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
