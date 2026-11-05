import java.security.MessageDigest
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.0"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = "com.github.khopland.symbols"
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Platform 261+ interfaces use real JVM default methods, so no DefaultImpls bridges are needed.
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
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
        bundledPlugin("com.intellij.java")
        bundledPlugin("Git4Idea")
        // 263 moved VCS out of the core into its own plugin, so -PplatformVersion=2026.3 needs it on the classpath.
        if (providers.gradleProperty("platformVersion").get() >= "2026.3") {
            bundledPlugin("com.intellij.platform.vcs")
        }
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // 1.2.0-beta.1 publishes to the "beta" channel; plain versions go to "default".
        channels = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            listOf(pluginVersion.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = "https://github.com/khopland/idea-emberkai-icons"
}

val hasCertificate = providers.environmentVariable("CERTIFICATE_CHAIN").orNull?.isNotBlank() == true
val hasPrivateKey = providers.environmentVariable("PRIVATE_KEY").orNull?.isNotBlank() == true
val releaseDir = layout.buildDirectory.dir("release")

// The release workflow tests the plugin in one job, then signs and publishes that same ZIP in a job with the
// secrets. There, -PtestedArchive=<zip> takes the place of buildPlugin's output, so nothing is rebuilt.
val testedArchive = providers.gradleProperty("testedArchive").orNull?.let { layout.projectDirectory.file(it) }
if (testedArchive != null) {
    tasks.signPlugin {
        archiveFile = testedArchive
    }
    // Publishes the ZIP that prepareRelease staged, without building or signing again.
    tasks.publishPlugin {
        setDependsOn(emptyList<Any>())
        archiveFiles.setFrom(fileTree(releaseDir) { include("*.zip") })
    }
}
val unsignedArchive = testedArchive?.let { provider { it } } ?: tasks.buildPlugin.flatMap { it.archiveFile }
val releaseArchive = if (hasCertificate && hasPrivateKey) {
    tasks.signPlugin.flatMap { it.signedArchiveFile }
} else {
    unsignedArchive
}

tasks.verifyPluginSignature {
    inputArchiveFile = tasks.signPlugin.flatMap { it.signedArchiveFile }
}

tasks.register<Sync>("prepareRelease") {
    group = "distribution"
    description = "Signs the plugin when configured and stages the release ZIP with a SHA-256 checksum."
    inputs.property("signingConfigurationComplete", hasCertificate == hasPrivateKey)
    if (hasCertificate && hasPrivateKey) dependsOn(tasks.verifyPluginSignature)
    from(releaseArchive)
    into(releaseDir)
    doFirst {
        require(hasCertificate == hasPrivateKey) {
            "Provide both CERTIFICATE_CHAIN and PRIVATE_KEY, or leave both unset."
        }
    }
    doLast {
        val archive = destinationDir.resolve(releaseArchive.get().asFile.name)
        val digest = MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
        val checksum = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        destinationDir.resolve("SHA256SUMS").writeText("$checksum  ${archive.name}\n")
    }
}

tasks.processResources {
    from(listOf("LICENSE", "NOTICE")) { into("META-INF") }
    from("licenses") { into("META-INF/licenses") }
}

// ./gradlew test --tests '*MarketplaceScreenshots' -Pscreenshots renders design/screenshots/*.png.
tasks.test {
    if (providers.gradleProperty("screenshots").isPresent) {
        systemProperty("emberkai.screenshots.dir", layout.projectDirectory.dir("design/screenshots").asFile.absolutePath)
        outputs.upToDateWhen { false }
    }
}
