import java.util.regex.Pattern

def call(Closure closure) {
    // Project Config
    def buildEnvironmentImage = "docker.dragon.zone:10080/dragonzone/maven-build:master"
    def buildableBranchRegex = ".*" // ( PRs are in the form 'PR-\d+' )
    def deployableBranchRegex = "master"

    // Maven Config
    def mavenArgs = "-B -U -Dci=true"
    def mavenNonDeployArgs = "-P sign"
    def mavenNonDeployGoals = "clean verify"
    def mavenDeployArgs = "-P sign,maven-central -DdeployAtEnd=true"
    def mavenDeployGoals = "clean deploy nexus-staging:deploy"
    def requireTests = false
    def globalMavenSettingsConfig = "maven-dragonZone"

    // Exit if we shouldn't be building
    if (!env.BRANCH_NAME.matches(buildableBranchRegex)) {
        echo "Branch ${env.BRANCH_NAME} is not buildable, aborting."
        return
    }

    // Pipeline Definition
    node("docker") {
        // Prepare the docker image to be used as a build environment
        def buildEnv = docker.image(buildEnvironmentImage)
        def isDeployableBranch = env.BRANCH_NAME.matches(deployableBranchRegex)

        stage("Prepare Build Environment") {
            docker.withRegistry('https://docker.dragon.zone:10080', 'jenkins-nexus') {
                buildEnv.pull()
            }
        }

        buildEnv.inside {
            configFileProvider([configFile(fileId: globalMavenSettingsConfig, variable: "MAVEN_SETTINGS")]) {
                /*
                 * Clone the repository and make sure that the pom.xml file is structurally valid and has a GAV
                 */
                stage("Checkout & Initialize Project") {
                    checkout scm
                }

                // Get Git Information
                def gitSha1 = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                def gitAuthor = env.CHANGE_AUTHOR ? env.CHANGE_AUTHOR : sh(returnStdout: true, script: 'git log -1 --format="%aN" HEAD').trim()
                def gitAuthorEmail = env.CHANGE_AUTHOR_EMAIL ? env.CHANGE_AUTHOR_EMAIL : sh(returnStdout: true, script: 'git log -1 --format="%aE" HEAD').trim()
                sh "git config user.name '${gitAuthor}'"
                sh "git config user.email '${gitAuthorEmail}'"
                def lastTag = "Unknown"
                try {
                    lastTag = sh(returnStdout: true, script: "git describe --abbrev=0 --tags").trim()
                } catch (Exception e) {
                    echo "Could not query the last tag."
                }
                echo "Last Tag: ${lastTag}"

                // Set Build Information
                def pom = readMavenPom(file: "pom.xml")
                def artifactId = pom.artifactId
                def versionTemplate = pom.version
                def revision = "0"
                if (lastTag.startsWith(artifactId+"-")) {
                    echo "Artifact ID matches"
                    def lastVersion = lastTag.substring(artifactId.length()+1)
                    echo "Last Version: ${lastVersion}"
                    def versionPattern = Pattern.compile(Pattern.quote(versionTemplate).replaceAll('\\Q${revision}\\E', '\\\\E(?<revision>\\\\d+)\\\\Q').replaceAll('\\Q${sha1}\\E', '\\\\E[0-9a-fA-F]+\\\\Q'))
                    echo "Version Pattern: ${versionPattern}"
                    def matcher = lastVersion =~ versionPattern
                    if (matcher.matches()) {
                        def lastRevision = matcher.group('revision') as Integer
                        echo "Version pattern matches; Last Revision: ${lastRevision}"
                        revision = (lastRevision+1).toString()
                    }
                }

                def version = versionTemplate.replaceAll(Pattern.quote('${revision}'), revision).replaceAll(Pattern.quote('${sha1}'), gitSha1)


                def tag = "${artifactId}-${version}"
                currentBuild.displayName = tag
                currentBuild.description = gitAuthor
                mavenArgs = "${mavenArgs} -Dsha1=${gitSha1} -Drevision=${revision}"

                // Build the project
                stage("Build Project") {
                    try {
                        withCredentials([string(credentialsId: 'gpg-signing-key-id', variable: 'GPG_KEYID'), file(credentialsId: 'gpg-signing-key', variable: 'GPG_SIGNING_KEY')]) {
                            sh 'gpg --allow-secret-key-import --import $GPG_SIGNING_KEY && echo "$GPG_KEYID:6:" | gpg --import-ownertrust'

                            sh "mvn -s \\\"$MAVEN_SETTINGS\\\" \\\"-Dmaven.repo.local=$WORKSPACE/.m2\\\" ${mavenArgs} ${isDeployableBranch ? mavenDeployGoals : mavenNonDeployGoals} ${isDeployableBranch ? mavenDeployArgs : mavenNonDeployArgs} \"-Dgpg.keyname=$GPG_KEYID\""
                        }

                        if (isDeployableBranch) {
                            sshagent([scm.userRemoteConfigs[0].credentialsId]) {
                                sh 'mkdir ~/.ssh && echo StrictHostKeyChecking no > ~/.ssh/config'
                                sh "git tag -a ${tag} -m Release"
                                sh "git push origin ${tag}"
                            }
                        }

                        archiveArtifacts artifacts: '**/*pom.xml', excludes: '.m2/**'
                    } finally {
                        junit allowEmptyResults: !requireTests, testResults: "target/checkout/**/target/surefire-reports/TEST-*.xml"
                    }
                }
                if (isDeployableBranch) {
                    stage("Stage to Maven Central") {
                        try {
                            sh "mvn -s \\\"$MAVEN_SETTINGS\\\" \\\"-Dmaven.repo.local=$WORKSPACE/.m2\\\" ${mavenArgs} -P maven-central nexus-staging:deploy-staged"

                            input message: 'Publish to Central?', ok: 'Publish'

                            sh "mvn -s \\\"$MAVEN_SETTINGS\\\" \\\"-Dmaven.repo.local=$WORKSPACE/.m2\\\" ${mavenArgs} -P maven-central nexus-staging:release"
                        } catch (err) {
                            sh "mvn -s \\\"$MAVEN_SETTINGS\\\" \\\"-Dmaven.repo.local=$WORKSPACE/.m2\\\" ${mavenArgs} -P maven-central nexus-staging:drop"
                            throw err
                        }
                    }
                }
            }
        }
    }
}
