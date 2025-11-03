import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    `java-library`
    `maven-publish`
    id("org.jreleaser") version "1.20.0"
    id("org.jetbrains.dokka") version "1.9.0"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = uri("https://oss.sonatype.org/content/repositories/snapshots"))
}

val roboVMVersion = "2.3.23"

// target jvm8 in kotlin module file
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

// common for jreleaser and maven
group = "io.github.dkimitsa.robovm"
version = "1.9.0.1"
val pomArtifactId = "kotlinx-coroutines-robovm"

/// java docs and sources
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource) // includes all Kotlin/Java source
}
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.javadoc) // uses the Javadoc task
}
val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc.get().outputDirectory)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = pomArtifactId
            artifact(sourcesJar.get())
            artifact(javadocJar.get())

            pom {
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "dkimitsa"
                        name = "Demyan Kimitsa"
                        email = "demyan.kimitsa@gmail.com"
                    }
                }
                scm {
                    url = "https://github.com/dkimitsa/kotlinx.coroutines.robovm"
                    connection = "scm:git:https://github.com/dkimitsa/kotlinx.coroutines.robovm.git"
                    developerConnection = "scm:git:git@https://github.com/dkimitsa/kotlinx.coroutines.robovm.git"
                    tag = "HEAD"
                }
                name = "Dispatchers.Main.RoboVM"
                description = "Provides Dispatchers.Main context for RoboVM applications."
                url = "https://dkimitsa.github.io"
            }
            repositories {
                if (project.version.toString().endsWith("-SNAPSHOT")) {
                    // snapshots are going to sonatype
                    maven {
                        name = "ossrh"
                        credentials {
                            username = System.getenv("MAVEN_USERNAME")
                            password = System.getenv("MAVEN_PASSWORD")
                        }
                        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                    }
                } else {
                    // local staging repository for JReleaser
                    maven {
                        url = uri(layout.buildDirectory.dir("staging-deploy"))
                    }
                }
            }
            from(components["kotlin"])
        }
    }
}

// JReleaser for staging to Sonatype
// https://central.sonatype.org/publish/publish-portal-gradle/
jreleaser {
    signing {
        setActive("ALWAYS")
        armored = true
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    setActive("RELEASE")
                    username = System.getenv("MAVEN_USERNAME")
                    password = System.getenv("MAVEN_PASSWORD")
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                    setStage("UPLOAD") // OTHERWISE it will do "publish"
                }
            }
        }
    }
    release {
        github {
            skipRelease = true
            token = "-" // must be not blank
        }
    }
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // RoboVM libs to be provided
    compileOnly("com.mobidevelop.robovm:robovm-rt:$roboVMVersion")
    compileOnly("com.mobidevelop.robovm:robovm-cocoatouch:$roboVMVersion")
}
