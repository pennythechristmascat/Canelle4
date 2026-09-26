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
        // Google Play exige Android 16 (API 36) pour les nouvelles applis et les mises à jour depuis le 31 août 2026.
        targetSdk = 36
        versionCode = 590
        versionName = "5.9.0"
        ndk {
            // Les téléphones capables de faire tourner le modèle sont tous en 64 bits.
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Toujours la même signature : les mises à jour s'installent par-dessus l'ancienne version.
    // Pour Google Play, la clé d'envoi peut venir des secrets GitHub (variables CANELLE_*) ;
    // sinon, on utilise la clé du projet (app/canelle.keystore).
    signingConfigs {
        create("canelle") {
            val ks = System.getenv("CANELLE_KEYSTORE")
            storeFile = if (!ks.isNullOrBlank()) file(ks) else file("canelle.keystore")
            storePassword = System.getenv("CANELLE_STORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "canelle-poche"
            keyAlias = System.getenv("CANELLE_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "canelle"
            keyPassword = System.getenv("CANELLE_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "canelle-poche"
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
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Moteur d'IA local de Google (fait tourner Gemma directement sur le téléphone).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
}
