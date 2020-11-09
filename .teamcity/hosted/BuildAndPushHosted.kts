package hosted

import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.ReuseBuilds
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.freeDiskSpace
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.swabra
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import common.TeamCityDockerImagesRepo.TeamCityDockerImagesRepo

object BuildAndPushHosted : BuildType({
    name = "Build and push for teamcity.jetbrains.com"
    buildNumberPattern = "%dockerImage.teamcity.buildNumber%-%build.counter%"
    vcs { root(TeamCityDockerImagesRepo) }
    steps {
        dockerCommand {
            name = "pull mcr.microsoft.com/powershell:nanoserver"
            commandType = other {
                subCommand = "pull"
                commandArgs = "mcr.microsoft.com/powershell:nanoserver-%windowsBuild%"
            }
        }

        dockerCommand {
            name = "pull mcr.microsoft.com/windows/nanoserver"
            commandType = other {
                subCommand = "pull"
                commandArgs = "mcr.microsoft.com/windows/nanoserver:%windowsBuild%"
            }
        }

        script {
            name = "context"
            scriptContent = """
echo 2> context/.dockerignore
echo TeamCity/buildAgent >> context/.dockerignore
echo TeamCity/temp >> context/.dockerignore
""".trimIndent()
        }

        dockerCommand {
            name = "build teamcity-server"
            commandType = build {
                source = file {
                    path = """context/generated/windows/Server/nanoserver/%windowsBuild%/Dockerfile"""
                }
                contextDir = "context"
                namesAndTags = "teamcity-server:%dep.TC_Trunk_BuildDistDocker.build.number%"
            }
            param("dockerImage.platform", "windows")
        }

        dockerCommand {
            name = "tag teamcity-server"
            commandType = other {
                subCommand = "tag"
                commandArgs = "teamcity-server:%dep.TC_Trunk_BuildDistDocker.build.number% %docker.buildRepository%teamcity-server:%dep.TC_Trunk_BuildDistDocker.build.number%"
            }
        }

        dockerCommand {
            name = "push teamcity-server"
            commandType = push {
                namesAndTags = "%docker.buildRepository%teamcity-server:%dep.TC_Trunk_BuildDistDocker.build.number%"
            }
        }
    }
    features {
        freeDiskSpace {
            requiredSpace = "33gb"
            failBuild = true
        }
        dockerSupport {
            cleanupPushedImages = true
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_315,PROJECT_EXT_4003,PROJECT_EXT_4022"
            }
        }
        swabra {
            forceCleanCheckout = true
        }
    }
    dependencies {
        dependency(AbsoluteId("TC_Trunk_BuildDistDocker")) {
            snapshot {
                onDependencyFailure = FailureAction.IGNORE
                reuseBuilds = ReuseBuilds.ANY
            }
            artifacts {
                artifactRules = "TeamCity.zip!/**=>context/TeamCity"
            }
        }
    }
    params {
        param("system.teamcity.agent.ensure.free.space", "33gb")
    }
})