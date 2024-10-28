library changelog: false, identifier: 'lib@master', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
]) _

pipeline {
    //agent {
    //    label 'source-builder'
    //}
    agent any
    parameters {
        string(
            defaultValue: '',
            description: 'PATH_TO_BUILD must be in form $DESTINATION/**release**/$revision',
            name: 'PATH_TO_BUILD')
        string(
            defaultValue: 'PERCONA',
            description: 'separate repository to push to',
            name: 'REPOSITORY')
        choice(
            choices: 'TESTING\nRELEASE\nEXPERIMENTAL\nLABORATORY',
            description: 'repo component to push to',
            name: 'COMPONET')
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
                        tree /srv/UPLOAD/${REPOPATH}
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
