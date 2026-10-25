import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "com.github.khopland.symbols"
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }
}

tasks.processResources {
    from(listOf("LICENSE", "NOTICE")) { into("META-INF") }
    from("licenses") { into("META-INF/licenses") }
}
