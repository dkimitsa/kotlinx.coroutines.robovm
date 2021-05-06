# Module kotlinx-coroutines-robovm

Provides `Dispatchers.Main` context for RoboVM applications.

Read [Guide to UI programming with coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/ui/coroutines-guide-ui.md)
for tutorial on this module.

# How to use
Artifact was published to [sonatype](https://oss.sonatype.org/content/repositories/snapshots/io/github/dkimitsa/robovm/kotlinx-coroutines-robovm/) maven repository and can be referenced as dependency in `build.gradle` as bellow:
```groovy
repositories {
    mavenCentral()
    maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }
}
dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlin_version"
    implementation "io.github.dkimitsa.robovm:kotlinx-coroutines-robovm:0.1-SNAPSHOT"
}
```