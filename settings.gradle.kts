pluginManagement {
    repositories {
        maven { url = uri("/root/work/aapt2-maven") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("/root/work/aapt2-maven") }
        google()
        mavenCentral()
    }
}
rootProject.name = "RootMyVivoNeo"
include(":app")
