plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.kapt)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.minshoki.image_editor"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        dataBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.google.gson)
    implementation(libs.glide)
    implementation(libs.hilt)
    implementation(libs.androidx.activity)
    implementation(libs.mlkit.face.detection)
    kapt(libs.hilt.compiler)
    kapt(libs.hilt.kapt.compiler)
    implementation(libs.opencv) {
        exclude(group = "androidx.databinding")
    }
    implementation(libs.lottie)
    implementation(project(":image-compress"))
    implementation(project(":core:design"))
    implementation(project(":core:util"))
    implementation(libs.androidx.databinding.runtime)
    testImplementation(libs.junit)
    implementation(libs.androidx.viewmodel.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}