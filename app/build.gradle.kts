plugins {
    id("com.android.application")
}

android {
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "org.asteroidos.sync"
        minSdk = 24
        targetSdk = 34
        versionCode = 30
        versionName = "0.30"
    }
    buildTypes {
        named("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    sourceSets {
        named("main") {
            java {
                srcDir("src/main/lib/android-ripple-background/library/src/main/java/")
                srcDir("src/main/lib/material-intro-screen/material-intro-screen/src/main/java/")
                srcDir("src/main/lib/powerampapi/poweramp_api_lib/src/")
            }
            res {
                srcDir("src/main/lib/android-ripple-background/library/src/main/res/")
                srcDir("src/main/lib/material-intro-screen/material-intro-screen/src/main/res/")
                srcDir("src/main/lib/powerampapi/poweramp_api_lib/res/")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        checkReleaseBuilds = true
        disable += "MissingTranslation"
    }
    namespace = "org.asteroidos.sync"

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-key.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.google.com")
    maven("https://jitpack.io")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.appcompat:appcompat:1.7.0-alpha03")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.github.MagneFire:EasyWeather:1.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.osmdroid:osmdroid-android:6.1.16")
    implementation("no.nordicsemi.android.support.v18:scanner:1.6.0")
    implementation("no.nordicsemi.android:ble:2.7.2")
}
