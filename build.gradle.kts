// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "7.2.2" apply false
    kotlin("android") version "1.7.10" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
