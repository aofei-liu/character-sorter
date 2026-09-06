plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// Pure JVM on purpose. Nothing here may depend on an Android or AndroidX
// artifact -- that is what keeps the module buildable and testable without
// the SDK. See ROADMAP.md, "Module split".
dependencies {
    // `api`, not `implementation`: OkHttpClient, HttpUrl and CookieJar all
    // appear in this module's public API, so a consumer cannot compile
    // against it unless okhttp is on their compile classpath too.
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
