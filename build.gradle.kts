// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "7.1.3" apply false
    kotlin("android") version "1.6.20" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}