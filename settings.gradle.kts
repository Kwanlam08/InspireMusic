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
        // Maven Central can be intermittently unreachable on local networks;
        // keep a compatible mirror ahead of it so tests remain reproducible.
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}
rootProject.name = "InspireMusic"
include(":app")
