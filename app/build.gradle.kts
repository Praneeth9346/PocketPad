import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aistudio.pocketpad"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.pocketpad"
        minSdk = 26
        targetSdk = 36
        versionCode = 110
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val propertiesFile = rootProject.file("keystore.properties")
            if (propertiesFile.exists()) {
                val properties = Properties().apply {
                    load(FileInputStream(propertiesFile))
                }

                val storeFilePath = properties.getProperty("storeFile") ?: error("storeFile missing")
                val storePassword = properties.getProperty("storePassword") ?: error("storePassword missing")
                val keyAlias = properties.getProperty("keyAlias") ?: error("keyAlias missing")
                val keyPassword = properties.getProperty("keyPassword") ?: error("keyPassword missing")

                val keystoreFile = rootProject.file(storeFilePath)
                check(keystoreFile.exists()) {
                    """
                    Release keystore does not exist:

                    ${keystoreFile.absolutePath}
                    """.trimIndent()
                }

                this.storeFile = keystoreFile
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    gradle.taskGraph.whenReady {
        if (hasTask(":app:assembleRelease") || hasTask(":app:packageRelease")) {
            val propertiesFile = rootProject.file("keystore.properties")
            check(propertiesFile.exists()) {
                """
                Production signing configuration missing.

                Expected:
                  keystore.properties

                Refusing to create a release APK
                with debug signing.
                """.trimIndent()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true

            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols.add("**/*.so")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.graphics:graphics-path:1.0.1")

    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    debugImplementation(libs.androidx.ui.tooling)
}
