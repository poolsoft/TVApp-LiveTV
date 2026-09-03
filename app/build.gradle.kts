import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val buildNumber = rootProject.file(".build").readText().trim().toInt()
val localSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("../.signing/TVApp-release.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}
val localSigningDirectory = rootProject.file("../.signing")
val releaseKeystorePath = System.getenv("TVAPP_KEYSTORE_PATH")
    ?: localSigningProperties.getProperty("keystore")?.let {
        localSigningDirectory.resolve(it).absolutePath
    }
val releaseKeyAlias = System.getenv("TVAPP_KEY_ALIAS")
    ?: localSigningProperties.getProperty("alias")
val releaseKeyPassword = System.getenv("TVAPP_KEY_PASSWORD")
    ?: localSigningProperties.getProperty("keyPassword")
val releaseStorePassword = System.getenv("TVAPP_STORE_PASSWORD")
    ?: localSigningProperties.getProperty("storePassword")
val updateManifestUrl = System.getenv("TVAPP_UPDATE_MANIFEST_URL")
    ?: "https://github.com/poolsoft/TVApp-LiveTV/releases/latest/download/version.json"

android {
    namespace = "com.tvapp.livetv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvapp.livetv"
        minSdk = 30
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("local") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
            buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
            buildConfigField("boolean", "STORE_BILLING_ENABLED", "false")
            buildConfigField("boolean", "IPTV_PRO_REQUIRED", "false")
        }
        create("paid") {
            dimension = "distribution"
            applicationIdSuffix = ".play"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
            buildConfigField("String", "UPDATE_MANIFEST_URL", "\"\"")
            buildConfigField("boolean", "STORE_BILLING_ENABLED", "true")
            buildConfigField("boolean", "IPTV_PRO_REQUIRED", "false")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    signingConfigs {
        if (
            releaseKeystorePath != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null &&
            releaseStorePassword != null
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.leanback:leanback-preference:1.2.0")
    implementation("androidx.tvprovider:tvprovider:1.0.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("io.coil-kt:coil:2.7.0")
    val media3Version = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val exportNamedDebugApk by tasks.registering {
    doLast {
        val source = layout.buildDirectory.file(
            "outputs/apk/local/debug/app-local-debug.apk",
        ).get().asFile
        val destination = layout.buildDirectory.file("TVApp.apk").get().asFile
        source.copyTo(destination, overwrite = true)
    }
}

tasks.configureEach {
    if (name == "assembleLocalDebug") {
        finalizedBy(exportNamedDebugApk)
    }
}
