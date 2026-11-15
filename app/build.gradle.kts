plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ryuseilabs.pleostest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ryuseilabs.pleostest"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 코드 축소(R8)는 끈다: 켜면 Kotlin 표준 라이브러리 클래스가 재배치되어 실행 시 IllegalAccessError가 난다.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Pleos SDK (nexus-playground.pleos.ai, anonymous)
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    // runtime-scope in the SDK POMs but needed at compile time for callback/data types
    implementation("ai.pleos.playground:vehicle-data:1.1.11")
    implementation("ai.pleos.playground:fused-location-data:0.0.3")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}