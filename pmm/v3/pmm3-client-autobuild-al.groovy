library changelog: false, identifier: 'lib@master', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
]) _

pipeline {
    agent none
    parameters {
        string(
            defaultValue: 'v3',
            description: 'Tag/Branch for pmm-submodules repository',
            name: 'GIT_BRANCH'
        )
        choice(
            choices: ['experimental', 'testing', 'laboratory'],
            description: 'Publish packages to repositories: testing for RC, experimental for 3-dev-latest, laboratory for FBs',
            name: 'DESTINATION'
        )
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        skipDefaultCheckout()
        disableConcurrentBuilds()
        parallelsAlwaysFailFast()
    }
    environment {
        PATH_TO_SCRIPTS = 'sources/pmm/src/github.com/percona/pmm/build/scripts'
    }
    stages {
        stage('Build PMM Client') {
            agent {
                label 'agent-amd64-ol9'
            }
            stages {
                stage('Prepare') {
                    steps {
                        git poll: true, branch: GIT_BRANCH, url: 'http://github.com/Percona-Lab/pmm-submodules'
                        sh '''
                            git reset --hard
                            git clean -xdf
                            git submodule update --init --jobs 10
                            git submodule status

                            git rev-parse --short HEAD > shortCommit
                            echo "UPLOAD/${DESTINATION}/${JOB_NAME}/pmm3/\$(cat VERSION)/${GIT_BRANCH}/\$(cat shortCommit)/${BUILD_NUMBER}" > uploadPath
                        '''
                        archiveArtifacts 'uploadPath'
                        stash includes: 'uploadPath', name: 'uploadPath'
                        archiveArtifacts 'shortCommit'
                    }
                }
                stage('Build client source') {
                    steps {
                        sh "${PATH_TO_SCRIPTS}/build-client-source"
                        stash includes: 'results/source_tarball/*.tar.*', name: 'source.tarball'
                        uploadTarball('source')
                    }
                }
                stage('Build client binary') {
                    steps {
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'pmm-staging-slave', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh """
                                ${PATH_TO_SCRIPTS}/build-client-binary
                                ls -la "results/tarball" || :
                                aws s3 cp --only-show-errors --acl public-read results/tarball/pmm-client-*.tar.gz \
                                    s3://pmm-build-cache/PR-BUILDS/pmm-client/pmm-client-latest-${BUILD_ID}.tar.gz
                                aws s3 cp --only-show-errors --acl public-read --copy-props none \
                                  s3://pmm-build-cache/PR-BUILDS/pmm-client/pmm-client-latest-${BUILD_ID}.tar.gz \
                                  s3://pmm-build-cache/PR-BUILDS/pmm-client/pmm-client-latest.tar.gz                                    
                            """
                        }
                        stash includes: 'results/tarball/*.tar.*', name: 'binary.tarball'
                        uploadTarball('binary')
                    }
                }
                stage('Build client source rpm') {
                    steps {
                        sh """
                            ${PATH_TO_SCRIPTS}/build-client-srpm public.ecr.aws/amazonlinux/amazonlinux:2023
                        """
                    }
                    post {
                        success {
                            stash includes: 'results/srpm/pmm*-client-*.src.rpm', name: 'rpms'
                            // uploadRPM()
                        }
                    }
                }
                stage('Build client binary rpms') {
                    parallel {
                        stage('Build client binary rpm AZ') {
                            steps {
                                sh """
                                    ${PATH_TO_SCRIPTS}/build-client-rpm public.ecr.aws/amazonlinux/amazonlinux:2023
                                """
                            }
                        }
                    }
                    post {
                        success {
                            stash includes: 'results/rpm/pmm*-client-*.rpm', name: 'rpms'
                            // uploadRPM()
                        }
                    }
                }
            }
        }
        // stage('Push to public repository') {
        //     agent {
        //         label 'master'
        //     }
        //     steps {
        //         unstash 'uploadPath'
        //         script {
        //           env.UPLOAD_PATH = sh(returnStdout: true, script: "cat uploadPath").trim()
        //         }
        //         // Upload packages to the repo defined in `DESTINATION`
        //         // sync2ProdPMMClient(DESTINATION, 'yes')
        //         sync2ProdPMMClientRepo(DESTINATION, env.UPLOAD_PATH, 'pmm3-client')
        //         withCredentials([sshUserPrivateKey(credentialsId: 'repo.ci.percona.com', keyFileVariable: 'KEY_PATH', usernameVariable: 'USER')]) {
        //             script {
        //                 sh '''
        //                     ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${KEY_PATH} ${USER}@repo.ci.percona.com "
        //                         scp -P 2222 -o ConnectTimeout=1 -o StrictHostKeyChecking=no ${UPLOAD_PATH}/binary/tarball/*.tar.gz jenkins@jenkins-deploy.jenkins-deploy.web.r.int.percona.com:/data/downloads/TESTING/pmm/
        //                     "
        //                 '''
        //             }  
        //         }
        //     }
        // }
    }
    post {
        success {
            script {
                // slackSend botUser: true, channel: '#pmm-notifications', color: '#00FF00', message: "[${JOB_NAME}]: build finished, pushed to ${DESTINATION} repo - ${BUILD_URL}"
                if (params.DESTINATION == "testing") {
                    env.TARBALL_URL = "https://s3.us-east-2.amazonaws.com/pmm-build-cache/PR-BUILDS/pmm-client/pmm-client-latest-${BUILD_ID}.tar.gz"
                    currentBuild.description = "RC Build, tarball: " + env.TARBALL_URL
                    // slackSend botUser: true,
                    //           channel: '#pmm-qa',
                    //           color: '#00FF00',
                    //           message: "[${JOB_NAME}]: ${BUILD_URL} RC Client build finished\nClient Tarball: ${env.TARBALL_URL}"
                }
            }
        }
        failure {
            script {
                echo "Pipeline failed"
                // slackSend botUser: true, channel: '#pmm-notifications', color: '#FF0000', message: "[${JOB_NAME}]: build ${currentBuild.result} - ${BUILD_URL}"
                // slackSend botUser: true, channel: '#pmm-qa', color: '#FF0000', message: "[${JOB_NAME}]: build ${currentBuild.result} - ${BUILD_URL}"
            }
        }
    }
}
