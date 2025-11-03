# Module kotlinx-coroutines-robovm

Provides `Dispatchers.Main` context for RoboVM applications.

Read [Guide to UI programming with coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/ui/coroutines-guide-ui.md)
for tutorial on this module.

# How to use
Artifact is published to `MavenCentral` repository and can be referenced as dependency in `build.gradle` as bellow:
```groovy
repositories {
    mavenCentral()
}
dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlin_version"
    implementation "io.github.dkimitsa.robovm:kotlinx-coroutines-robovm:1.9.0.1"
}
```