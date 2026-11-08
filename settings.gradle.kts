pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TeaPlayer"

include(":app", ":tv", ":feature")
include(":core:model", ":core:domain", ":core:data", ":core:net")
include(":core:player", ":core:plugin", ":core:plugin-runtime", ":core:designsystem")
