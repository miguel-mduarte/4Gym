plugins {
    id("com.android.application") version "9.1.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("com.google.devtools.ksp") version "2.3.6"
    id("androidx.room") version "2.8.4"
}

android {
    namespace = "br.com.fourgym"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "br.com.fourgym"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("junit:junit:4.13.2")
}
