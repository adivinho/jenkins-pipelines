library changelog: false, identifier: 'lib@master', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
]) _
void runPackageTestingJob(String GIT_BRANCH, DOCKER_VERSION, PMM_VERSION, TESTS, METRICS_MODE, INSTALL_REPO) {
    upgradeJob = build job: 'pmm3-package-testing-arm', parameters: [
        string(name: 'GIT_BRANCH', value: GIT_BRANCH),
        string(name: 'DOCKER_VERSION', value: DOCKER_VERSION),
        string(name: 'PMM_VERSION', value: PMM_VERSION),
        string(name: 'TESTS', value: TESTS),
        string(name: 'INSTALL_REPO', value: INSTALL_REPO),
        string(name: 'METRICS_MODE', value: METRICS_MODE)
    ]
}

def latestVersion = pmmVersion()

pipeline {
    agent {
        label 'cli'
    }
    parameters {
        string(
            defaultValue: 'v3',
            description: 'Tag/Branch for package-testing repository',
            name: 'GIT_BRANCH',
            trim: true)
        string(
            defaultValue: '',
            description: 'Commit hash for the branch',
            name: 'GIT_COMMIT_HASH',
            trim: true)
        string(
            defaultValue: 'perconalab/pmm-server:3-dev-latest',
            description: 'PMM Server docker container version (image-name:version-tag)',
            name: 'DOCKER_VERSION',
            trim: true)
        string(
            defaultValue: latestVersion,
            description: 'PMM Version for testing',
            name: 'PMM_VERSION',
            trim: true)
        choice(
            choices: ['experimental', 'testing', 'main', 'pmm-client-main'],
            description: 'Enable Repo for Client Nodes',
            name: 'INSTALL_REPO')
        choice(
            choices: ['auto', 'push', 'pull'],
            description: 'Select the Metrics Mode for Client',
            name: 'METRICS_MODE')
    }
    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
    }
    triggers {
        cron('0 4 * * *')
    }
    stages{
        stage('UI tests Upgrade Matrix') {
            parallel {
                stage('Integration Playbook'){
                    steps {
                        script {
                            runPackageTestingJob(GIT_BRANCH, DOCKER_VERSION, PMM_VERSION, 'pmm3-client_integration', METRICS_MODE, INSTALL_REPO);
                        }
                    }
                }
                stage('Integration Playbook with custom path'){
                    steps {
                        script {
                            runPackageTestingJob(GIT_BRANCH, DOCKER_VERSION, PMM_VERSION, 'pmm3-client_integration_custom_path', METRICS_MODE, INSTALL_REPO);
                        }
                    }
                }
                stage('Integration Playbook with custom port'){
                    steps {
                        script {
                            runPackageTestingJob(GIT_BRANCH, DOCKER_VERSION, PMM_VERSION, 'pmm3-client_integration_custom_port', METRICS_MODE, INSTALL_REPO);
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
                    slackSend channel: '#pmm-ci', color: '#00FF00', message: "[${JOB_NAME}]: build finished - ${BUILD_URL} "
                } else {
                    slackSend channel: '#pmm-ci', color: '#FF0000', message: "[${JOB_NAME}]: build ${currentBuild.result} - ${BUILD_URL}"
                }
            }
            deleteDir()
        }
    }
}
