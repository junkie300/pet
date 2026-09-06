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
        // 카카오맵 SDK 는 Maven Central 에 없다. 카카오가 직접 여는 저장소에서만 받는다 (D-70).
        maven("https://devrepo.kakao.com/nexus/content/groups/public/")
    }
}

rootProject.name = "pet-app"
include(":app")
