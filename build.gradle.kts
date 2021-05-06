plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.0"
    `java-library`
    `maven-publish`
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = uri("https://oss.sonatype.org/content/repositories/snapshots"))
}

val roboVMVersion = "2.3.13-SNAPSHOT"

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.dkimitsa.robovm"
            artifactId = "kotlinx-coroutines-robovm"
            version = "0.1-SNAPSHOT"

            from(components["kotlin"])
        }
    }
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0-RC")

    // RoboVM libs to be provided
    api("com.mobidevelop.robovm:robovm-rt:$roboVMVersion")
    api("com.mobidevelop.robovm:robovm-cocoatouch:$roboVMVersion")
}
