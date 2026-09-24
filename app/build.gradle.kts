import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.canelle.compagnon"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.canelle.compagnon"
        // Le cerveau local (LiteRT-LM) demande Android 12 ou plus récent.
        minSdk = 31
        targetSdk = 35
        versionCode = 510
        versionName = "5.1.0"
        ndk {
            // Les téléphones capables de faire tourner le modèle sont tous en 64 bits.
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Toujours la même signature : les mises à jour s'installent par-dessus l'ancienne version.
    signingConfigs {
        create("canelle") {
            storeFile = file("canelle.keystore")
            storePassword = "canelle-poche"
            keyAlias = "canelle"
            keyPassword = "canelle-poche"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("canelle")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("canelle")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Moteur d'IA local de Google (fait tourner Gemma directement sur le téléphone).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
}
