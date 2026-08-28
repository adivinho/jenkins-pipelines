pipeline {
    agent {
        label params.USE_ONDEMAND ? 'agent-amd64-ondemand' : 'agent-amd64'
    }
    parameters {
        string(
            defaultValue: 'main',
            description: 'Tag/Branch for pmm repository',
            name: 'PMM_BRANCH')
        string(
            defaultValue: 'docker.io/perconalab/pmm-server:3-dev-latest',
            description: 'Docker image for PMM Server running in the AMI',
            name: 'PMM_SERVER_IMAGE')
        choice(
            choices: ['amd64', 'arm64'],
            description: 'CPU architecture of the AMI to build',
            name: 'AMI_ARCH')
        choice(
            choices: ['no', 'yes'],
            description: "Build a Release Candidate?",
            name: 'RELEASE_CANDIDATE')
        booleanParam(
            defaultValue: false,
            description: 'Use on-demand instances instead of spot (for RC/Release builds)',
            name: 'USE_ONDEMAND'
        )
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        disableConcurrentBuilds()
        parallelsAlwaysFailFast()
    }
    triggers {
        cron('0 3 * * 1-5')
    }
    stages {
        stage('Prepare') {
            steps {
                checkout([$class: 'GitSCM', 
                          branches: [[name: "*/${PMM_BRANCH}"]],
                          extensions: [[$class: 'CloneOption',
                          noTags: true,
                          reference: '',
                          shallow: true]],
                          userRemoteConfigs: [[url: 'https://github.com/percona/pmm.git']]
                ])
            }
        }
        stage('Build PMM AMI Image') {
            steps {
                dir("build") {
                    script {
                        def makeTarget = params.AMI_ARCH == 'arm64' ? 'pmm-ami-arm64' : 'pmm-ami'
                        sh "PMM_SERVER_IMAGE=${PMM_SERVER_IMAGE} make ${makeTarget}"
                    }
                }
                script {
                    env.AMI_ID = sh(script: "jq -r '.builds[-1].artifact_id' build/manifest.json | cut -d ':' -f2", returnStdout: true)
                }
            }
        }
        stage('Run PMM AMI UI tests') {
            steps{
                script {
                    build job: 'pmm3-ami-test', parameters: [
                        string(name: 'AMI_ID', value: env.AMI_ID.trim()),
                        string(name: 'AMI_ARCH', value: params.AMI_ARCH)
                    ]
                }
            }
        }
    }
    post {
        success {
            script {
                if (params.RELEASE_CANDIDATE == "yes") {
                    currentBuild.description = "Release Candidate Build (${params.AMI_ARCH}): ${env.AMI_ID}"
                    slackSend botUser: true, channel: '#pmm-qa', color: '#00FF00', message: "[${JOB_NAME}]: ${BUILD_URL} Release Candidate build finished: ${env.AMI_ID}"
                } else {
                    currentBuild.description = "AMI Instance ID (${params.AMI_ARCH}): ${env.AMI_ID}"
                    slackSend botUser: true, channel: '#pmm-notifications', color: '#00FF00', message: "[${JOB_NAME}]: build ${BUILD_URL} finished: ${env.AMI_ID}"
                }
            }
        }
        failure {
            echo "Pipeline failed"
            slackSend botUser: true, channel: '#pmm-notifications', color: '#FF0000', message: "[${JOB_NAME}]: build ${BUILD_URL} failed"
        }
    }
}
