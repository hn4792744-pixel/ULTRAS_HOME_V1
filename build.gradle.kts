
plugins {
    java
}

group = "me.uc.hussein"
version = "1.1.0"

// NOTE: this sandbox only has JDK 21 installed, so the source was syntax/type-checked here with
// --release 21 (no Java-25-only language features are used, so nothing behaves differently).
// The toolchain below targets 25 as requested; if your machine only has JDK 21 installed, change
// the two "25" values below to "21" - everything else stays the same.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Latest Paper API that actually exists at the time of writing. api-version in plugin.yml is
    // kept at "1.21" (not a specific patch) so the plugin loads on every current and future 1.21.x
    // Paper build without changes.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
    jar {
        archiveBaseName.set("ULTRAS_HOMES")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
    }
    build {
        dependsOn(jar)
    }
}
