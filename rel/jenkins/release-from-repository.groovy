library changelog: false, identifier: 'lib@master', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
]) _

pipeline {
    agent {
        label 'jenkins'
    }
    //agent any
    parameters {
        string(
            defaultValue: '',
            description: 'PATH_TO_BUILD must be in form $DESTINATION/**release**/$revision',
            name: 'PATH_TO_BUILD')
        string(
            defaultValue: 'INNOVATION',
            description: 'separate repository to push to. Please use CAPS letters.',
            name: 'REPOSITORY')
        booleanParam(name: 'REMOVE_BEFORE_PUSH', defaultValue: false, description: 'check to remove sources and binary version if equals pushing')
        booleanParam(name: 'REMOVE_LOCKFILE', defaultValue: false, description: 'remove lockfile after unsuccessful push')
        choice(
            choices: 'TESTING\nRELEASE\nEXPERIMENTAL\nLABORATORY',
            description: 'repo component to push to',
            name: 'COMPONENT')
        choice(
            choices: 'NO\nYES',
            description: 'PRO build',
            name: 'PROBUILD')
    }
    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps ()
    }
    stages {
        stage('Push to RPM repository') {
            steps {
                withCredentials([string(credentialsId: 'SIGN_PASSWORD', variable: 'SIGN_PASSWORD')]) {
                    withCredentials([sshUserPrivateKey(credentialsId: 'repo.ci.percona.com', keyFileVariable: 'KEY_PATH', usernameVariable: 'USER')]) {
                        sh """ 
                            REPOCOMP=\$(echo "${COMPONENT}" | tr '[:upper:]' '[:lower:]')
                            LCREPOSITORY=\$(echo "${REPOSITORY}" | tr '[:upper:]' '[:lower:]')
                            if [ x"${PATH_TO_BUILD}" = x ]; then
                                echo "Empty path!"
                                exit 1
                            fi
                            ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${KEY_PATH} ${USER}@repo.ci.percona.com << 'ENDSSH'
                                set -o errexit
                                set -o xtrace
                                echo /srv/UPLOAD/${PATH_TO_BUILD}
                                cd /srv/UPLOAD/${PATH_TO_BUILD}
                                ALGO=""
                                NoDBRepos=("PSMDB" "PDMDB")
                                for repo in \${NoDBRepos[*]}; do
                                    if [[ "${REPOSITORY}" =~ "\${repo}".* ]]; then
                                        export ALGO="--no-database"
                                    fi
                                done
                                if [[ ! "${REPOSITORY}" == "PERCONA" ]]; then
                                    export PATH="/usr/local/reprepro5/bin:\${PATH}"
                                fi
ENDSSH
                        """ 
                    }
                }
            }
        }
        stage('Push to DEB repository') {
            steps {
                withCredentials([string(credentialsId: 'SIGN_PASSWORD', variable: 'SIGN_PASSWORD')]) {
                    withCredentials([sshUserPrivateKey(credentialsId: 'repo.ci.percona.com', keyFileVariable: 'KEY_PATH', usernameVariable: 'USER')]) {
                        sh """
                            REPOCOMP=\$(echo "${COMPONENT}" | tr '[:upper:]' '[:lower:]')
                            if [ x"${PATH_TO_BUILD}" = x ]; then
                                echo "Empty path!"
                                exit 1
                            fi
                            REPOPATH=repo-copy/${REPOSITORY}/apt
                            ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${KEY_PATH} ${USER}@repo.ci.percona.com << 'ENDSSH'
                                set -o errexit
                                set -o xtrace
                                echo /srv/UPLOAD/${PATH_TO_BUILD}
                                cd /srv/UPLOAD/${PATH_TO_BUILD}
                                REPOPUSH_ARGS=""
                                if [ ${REMOVE_BEFORE_PUSH} = true ]; then
                                     REPOPUSH_ARGS=" --remove-package "
                                fi
                                tree
ENDSSH
                        """
                    }
                }
            }
        }
        stage('Sync downloads to production') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'repo.ci.percona.com', keyFileVariable: 'KEY_PATH', usernameVariable: 'USER')]) {
                    sh '''
                        echo "The step is skipped"
                    '''
                }
            }
        }
        stage('Sync private repos to production') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'repo.ci.percona.com', keyFileVariable: 'KEY_PATH', usernameVariable: 'USER')]) {
                    sh '''
                        echo "The step is skipped"
                    '''
                }
            }
        }
        stage('Refresh downloads area') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'repo.ci.percona.com', keyFileVariable: 'KEY_PATH', usernameVariable: 'USER')]) {
                    sh '''
                        echo "The step is skipped"
                    '''
                }
            }
        }
        stage('Cleanup') {
            steps {
                deleteDir()
            }
        }
    }
}
