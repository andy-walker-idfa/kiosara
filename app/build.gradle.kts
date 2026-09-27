import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.licensee)
}

// Release signing secrets live outside git (see the build-and-deploy skill).
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

android {
    namespace = "io.github.andy_walker_idfa.smarthome_dashboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.andy_walker_idfa.smarthome_dashboard"
        minSdk = 29
        // Must be raised to the current stable API level every year (Play and F-Droid requirements).
        targetSdk = 37
        // Versioning: versionCode +1 per release (never reused, shared by both flavors of a release);
        // versionName is semver, 0.x until v1.0. Keep CHANGELOG.md and fastlane changelogs in sync.
        versionCode = 15
        versionName = "0.9.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // "github": sideloaded builds from GitHub releases (self-updater comes in Phase 7).
    // "store": F-Droid / IzzyOnDroid / Google Play builds without updater or install permission.
    // Flavor-specific code and manifest entries live only in src/github and src/store.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
        }
        create("store") {
            dimension = "distribution"
        }
    }

    // Reproducible builds for F-Droid / IzzyOnDroid: no Google-encrypted dependency metadata block
    // in the APK signing block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Reproducible builds: never strip prebuilt native libraries (e.g. DataStore's). Stripping depends on
    // whether an NDK is installed on the build machine, which made Linux CI and Windows builds differ.
    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
        // Jar metadata duplicated across the Netty artifacts (via HiveMQ); not used at runtime.
        resources {
            excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds install next to the release build (different signature), so tests and
            // experiments never wipe the release install or its HA login. Identifiers for HA come from
            // Settings.device.id, never from the applicationId.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned when keystore.properties is absent (e.g. CI and F-Droid builds).
            signingConfig = signingConfigs.findByName("release")
            // Reproducibility: don't embed the git commit (F-Droid builds from source without .git).
            vcsInfo { include = false }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // android.util.Log (used by AppLog) becomes a no-op in JVM unit tests.
        unitTests.isReturnDefaultValues = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // Dependency and SDK version freshness is reviewed manually against the official release pages.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}

// Fails the build if a runtime dependency has a license outside this list. Only permissive licenses
// (Apache-2.0, MIT, BSD, EPL) may ever be added here.
licensee {
    allow("Apache-2.0")
    // MIT No Attribution (org.reactivestreams:reactive-streams, via the HiveMQ MQTT client): permissive,
    // even less restrictive than MIT.
    allow("MIT-0")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hivemq.mqtt.client)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
