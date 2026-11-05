import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * 签名信息从 local.properties（不进版本库）或环境变量读取，
 * 避免把 keystore 密码写进公开仓库。缺失时 release 包不做签名。
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(key: String, env: String): String? =
    localProps.getProperty(key) ?: System.getenv(env)

val storePath = secret("RELEASE_STORE_FILE", "RELEASE_STORE_FILE")
val storePass = secret("RELEASE_STORE_PASSWORD", "RELEASE_STORE_PASSWORD")
val aliasName = secret("RELEASE_KEY_ALIAS", "RELEASE_KEY_ALIAS")
val aliasPass = secret("RELEASE_KEY_PASSWORD", "RELEASE_KEY_PASSWORD")
val hasSigning = storePath != null && storePass != null && aliasName != null && aliasPass != null

android {
    namespace = "com.dage.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dage.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "1.9.6"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(storePath!!)
                storePassword = storePass
                keyAlias = aliasName
                keyPassword = aliasPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.slf4j:slf4j-api:1.7.36")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
