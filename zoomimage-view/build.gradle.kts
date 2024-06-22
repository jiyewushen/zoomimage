plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.github.panpf.zoomimage.view"
    compileSdk = property("compileSdk").toString().toInt()

    defaultConfig {
        minSdk = property("minSdk").toString().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    api(projects.zoomimageCore)
    api(libs.kotlin.stdlib)
    api(libs.androidx.appcompat)
    api(libs.androidx.core.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)

    androidTestImplementation(projects.internal.testUtils)
}