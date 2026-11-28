// UPR ZX for Android (unofficial) - Gradle build (BUILD_SPEC section 3).
// :core is a plain JVM library built from the existing engine in ../src.
rootProject.name = "uprzx-android"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(":core")
