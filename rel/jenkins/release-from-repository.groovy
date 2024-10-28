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
            defaultValue: 'PERCONA',
            description: 'separate repository to push to',
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
                withCredentials([sshUserPrivateKey(credentialsId: 'repo.ci.percona.com', keyFileVariable: 'KEY_PATH', usernameVariable: 'USER')]) {
                    sh '''
                        REPOCOMP=\$(echo "${COMPONENT}" | tr '[:upper:]' '[:lower:]')
                        REPOPATH=${REPOSITORY}
                        if [ x"${PATH_TO_BUILD}" = x ]; then
                            echo "Empty path!"
                            exit 1
                        fi
                        pwd
                        ssh -o StrictHostKeyChecking=no -i ${KEY_PATH} ${USER}@repo.ci.percona.com \
                            /usr/bin/tree /srv/UPLOAD/${PATH_TO_BUILD}
                    '''
                }
            }
        }
        stage('Push to DEB repository') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'repo.ci.percona.com', keyFileVariable: 'KEY_PATH', usernameVariable: 'USER')]) {
                    sh '''
                        echo "The step is skipped"
                    '''
                }
/*
                updateRepoIndex(REPO_LINKS.split(','))
                stash allowEmpty: true, includes: "new-index.html", name: "NewIndexHtml"
*/
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
