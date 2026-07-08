plugins {
    id("com.android.library") version "8.1.1"
    id("org.jetbrains.kotlin.android") version "1.9.0"
}

android {
    compileSdk = 34
    namespace = "com.example"
    defaultConfig {
        minSdk = 21
    }
}

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.recloudstream:cloudstream:3.0.0") // مكتبة كلاود ستريم الأساسية
}
