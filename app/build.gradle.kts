plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "2.3.3"
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "jp.gr.java_conf.SenseMusicClock"
    compileSdk = 36

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
            isMinifyEnabled = false
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

    buildFeatures{
        viewBinding = true
    }

}



dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.ads.mobile.sdk)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.legacy.support.v4)
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("androidx.paging:paging-runtime:3.4.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.palette:palette:1.0.0")
    implementation(libs.androidx.preference)
    val media3_version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
    implementation("androidx.media3:media3-cast:$media3_version")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.media3:media3-container:$media3_version")

    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.11.1")

    implementation("com.google.code.gson:gson:2.13.2")
    // Use Coil 2.x (stable) for ImageView `load` extensions
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    ksp("com.google.dagger:dagger-compiler:2.59.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.ksp.api)
    implementation(libs.room.runtime)
    // If this project only uses Java source, use the Java annotationProcessor
    // No additional plugins are necessary
    ksp(libs.room.compiler)

    // optional - Kotlin Extensions and Coroutines support for Room
    implementation(libs.room.ktx)

    // optional - RxJava2 support for Room
    implementation(libs.androidx.room.rxjava2)

    // optional - RxJava3 support for Room
    implementation(libs.androidx.room.rxjava3)

    // optional - Guava support for Room, including Optional and ListenableFuture
    implementation(libs.androidx.room.guava)

    // optional - Test helpers
    testImplementation(libs.androidx.room.testing)

    // optional - Paging 3 Integration
    implementation(libs.androidx.room.paging)
}