plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.indianservers.circuitssimulator"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.indianservers.circuitssimulator"
        minSdk = 31
        targetSdk = 37
        versionCode = 4
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("test").kotlin.directories.add("src/domainValidation/kotlin")
        getByName("androidTest").kotlin.directories.add("src/domainValidation/kotlin")
        getByName("androidTest").assets.srcDir("../testing/engineering-validation/projects")
        getByName("androidTest").assets.srcDir("../testing/engineering-validation/domain-assets")
        getByName("androidTest").assets.srcDir("../testing/phase2/assets")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test>().configureEach {
    // Opt-in process isolation for independent validation suites; normal local runs stay serial.
    maxParallelForks = providers.environmentVariable("VALIDATION_PARALLEL_FORKS")
        .map { it.toInt().coerceIn(1, 4) }.orElse(1).get()
    inputs.property("validationBoard", providers.environmentVariable("VALIDATION_BOARD").orElse("ALL"))
    inputs.property("validationProject", providers.environmentVariable("VALIDATION_PROJECT").orElse("ALL"))
}
