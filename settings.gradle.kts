import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "qlty-jetbrains"

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.13.1"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
