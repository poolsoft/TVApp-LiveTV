plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.tvapp.livetv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvapp.livetv"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        viewBinding = true
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
        val source = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val destination = layout.buildDirectory.file("TVApp.apk").get().asFile
        source.copyTo(destination, overwrite = true)
    }
}

tasks.configureEach {
    if (name == "assembleDebug") {
        finalizedBy(exportNamedDebugApk)
    }
}
