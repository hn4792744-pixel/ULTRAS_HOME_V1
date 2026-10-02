plugins {
    // Lets Gradle automatically download a matching JDK for the toolchain above if one isn't
    // already installed locally (safe to remove if you always build with the right JDK on PATH).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "ULTRAS_HOMES"
