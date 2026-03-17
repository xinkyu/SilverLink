pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.columbus.heytapmobi.com/repository/heytap-health-releases/")
            isAllowInsecureProtocol = true
            credentials {
                username = "healthUser"
                password = "8174a9eac1264495b593a9d5ab221491"
            }
        }
        maven {
            url = uri("https://maven.columbus.heytapmobi.com/repository/heytap-health-snapshots/")
            isAllowInsecureProtocol = true
            credentials {
                username = "healthUser"
                password = "8174a9eac1264495b593a9d5ab221491"
            }
        }
    }
}

rootProject.name = "SilverLink"
include(":app")
include(":shared")
include(":wear")
include(":oppo-sdk-bridge")
