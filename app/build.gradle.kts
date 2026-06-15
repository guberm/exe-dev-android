plugins {
    id("com.android.application")
}

android {
    namespace = "dev.guber.exedev"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.guber.exedev"
        minSdk = 23
        targetSdk = 34
        versionCode = 11
        versionName = "1.2.0"
    }
}

dependencies {
    implementation("com.github.mwiede:jsch:0.2.21")
}
