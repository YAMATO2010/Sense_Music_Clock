plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)


    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.mikepenz.aboutlibraries.plugin.android")
}

android {
    namespace = "jp.gr.java_conf.SenseMusicClock"
    compileSdk = 37

    defaultConfig {
        applicationId = "jp.gr.java_conf.SenseMusicClock"
        minSdk = 32
        targetSdk = 36
        versionCode = 6
        versionName = "1.0_beta6"

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
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // パッケージ名を分ける
            applicationIdSuffix = ".test"

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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")

}


dependencies {

    // --------------------------------------------------
    // AboutLibraries
    // --------------------------------------------------

    implementation(libs.aboutlibraries.compose.m3)

    // --------------------------------------------------
    // Firebase
    // --------------------------------------------------

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)


    // --------------------------------------------------
    // Jetpack Compose
    // --------------------------------------------------

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui.tooling.preview)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)

    // --------------------------------------------------
    // Glance / Compose
    // --------------------------------------------------


    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.glance.material)

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
    implementation(libs.androidx.palette)


    // --------------------------------------------------
    // Glance / App Widget
    // --------------------------------------------------

    implementation(libs.androidx.glance.appwidget)


    // --------------------------------------------------
    // Lifecycle
    // --------------------------------------------------

    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.guava)


    // --------------------------------------------------
    // Media3
    // --------------------------------------------------



    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.container)
    implementation(libs.androidx.media3.ui)


    // --------------------------------------------------
    // Room
    // --------------------------------------------------

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.room.rxjava3)
    implementation(libs.androidx.room.guava)
    implementation(libs.androidx.room.paging)

    ksp(libs.room.compiler)

    testImplementation(libs.androidx.room.testing)


    // --------------------------------------------------
    // Paging
    // --------------------------------------------------

    implementation(libs.androidx.paging.runtime)


    // --------------------------------------------------
    // DataStore
    // --------------------------------------------------

    implementation(libs.androidx.datastore.preferences)


    // --------------------------------------------------
    // WorkManager
    // --------------------------------------------------

    implementation(libs.androidx.work.runtime.ktx)


    // --------------------------------------------------
    // Coil
    // --------------------------------------------------

    // Coil 2.x for ImageView `load` extensions
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")


    // --------------------------------------------------
    // JSON
    // --------------------------------------------------

    implementation(libs.gson)


    // --------------------------------------------------
    // Dagger / KSP
    // --------------------------------------------------


    ksp(libs.dagger.compiler)


    // --------------------------------------------------
    // Tests
    // --------------------------------------------------

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
