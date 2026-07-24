plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)


    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "jp.gr.java_conf.SenseMusicClock"
    compileSdk = 37

    defaultConfig {
        applicationId = "jp.gr.java_conf.SenseMusicClock"
        minSdk = 32
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

}

ksp{
    arg("room.schemaLocation", "$projectDir/schemas")

}


dependencies {

    // --------------------------------------------------
    // Firebase
    // --------------------------------------------------

    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-crashlytics")


    // --------------------------------------------------
    // Jetpack Compose
    // --------------------------------------------------

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose")
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // --------------------------------------------------
    // Glance / Compose
    // --------------------------------------------------

    val glanceVersion = "1.1.1"
    implementation("androidx.glance:glance-appwidget:$glanceVersion")
    implementation("androidx.glance:glance-material3:$glanceVersion")
    implementation("androidx.glance:glance-material:$glanceVersion")

    // --------------------------------------------------
    // AndroidX UI
    // --------------------------------------------------

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.legacy.support.v4)

    implementation(libs.material)
    implementation("androidx.palette:palette:1.0.0")


    // --------------------------------------------------
    // Glance / App Widget
    // --------------------------------------------------

    implementation(libs.androidx.glance.appwidget)


    // --------------------------------------------------
    // Lifecycle
    // --------------------------------------------------

    implementation("androidx.lifecycle:lifecycle-service:2.11.0")


    // --------------------------------------------------
    // Media3
    // --------------------------------------------------

    val media3Version = "1.10.1"

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-cast:$media3Version")
    implementation("androidx.media3:media3-container:$media3Version")
    implementation(libs.androidx.media3.ui)


    // --------------------------------------------------
    // Room
    // --------------------------------------------------

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.room.rxjava2)
    implementation(libs.androidx.room.rxjava3)
    implementation(libs.androidx.room.guava)
    implementation(libs.androidx.room.paging)

    ksp(libs.room.compiler)

    testImplementation(libs.androidx.room.testing)


    // --------------------------------------------------
    // Paging
    // --------------------------------------------------

    implementation("androidx.paging:paging-runtime:3.5.0")


    // --------------------------------------------------
    // DataStore
    // --------------------------------------------------

    implementation("androidx.datastore:datastore-preferences:1.2.1")


    // --------------------------------------------------
    // WorkManager
    // --------------------------------------------------

    implementation("androidx.work:work-runtime-ktx:2.11.2")


    // --------------------------------------------------
    // Coil
    // --------------------------------------------------

    // Coil 2.x for ImageView `load` extensions
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")


    // --------------------------------------------------
    // JSON
    // --------------------------------------------------

    implementation("com.google.code.gson:gson:2.13.2")


    // --------------------------------------------------
    // Google Mobile Ads
    // --------------------------------------------------

    implementation(libs.ads.mobile.sdk)


    // --------------------------------------------------
    // Dagger / KSP
    // --------------------------------------------------


    ksp("com.google.dagger:dagger-compiler:2.60.1")


    // --------------------------------------------------
    // Tests
    // --------------------------------------------------

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}