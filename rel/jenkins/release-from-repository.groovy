library changelog: false, identifier: 'lib@master', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
]) _

pipeline {
    agent {
        label 'jenkins'
    }
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
                                REPOCOMP=\$(echo "${COMPONENT}" | tr '[:upper:]' '[:lower:]')
                                LCREPOSITORY=\$(echo "${REPOSITORY}" | tr '[:upper:]' '[:lower:]')
                                NoDBRepos=("PSMDB" "PDMDB")
                                for repo in \${NoDBRepos[*]}; do
                                    if [[ "${REPOSITORY}" =~ "\${repo}".* ]]; then
                                        export ALGO="--no-database"
                                    fi
                                done
                                if [[ ! "${REPOSITORY}" == "PERCONA" ]]; then
                                    export PATH="/usr/local/reprepro5/bin:\${PATH}"
                                fi
                                if [[ "${REPOSITORY}" == "DEVELOPMENT" ]]; then
                                    export REPOPATH="yum-repo"
                                else
                                    export REPOPATH="repo-copy/"\${LCREPOSITORY}"/yum"
                                fi
                                echo \${REPOPATH}
                                RHVERS=\$(ls -1 binary/redhat | grep -v 6)
                                # -------------------------------------> source processing
                                if [[ -d source/redhat ]]; then
                                    SRCRPM=\$(find source/redhat -name '*.src.rpm')
                                    for rhel in \${RHVERS}; do
                                        mkdir -p /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/SRPMS
                                        cp -v \${SRCRPM} /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/SRPMS
                                        createrepo \${ALGO:-} --update /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/SRPMS
                                        if [[ -f /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/SRPMS/repodata/repomd.xml.asc ]]; then
                                            rm -f /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/SRPMS/repodata/repomd.xml.asc
                                        fi
                                        gpg --detach-sign --armor --passphrase $SIGN_PASSWORD /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/SRPMS/repodata/repomd.xml
                                    done
                                fi
                                # -------------------------------------> binary processing
                                pushd binary
                                for rhel in \${RHVERS}; do
                                    mkdir -p /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/RPMS
                                    for arch in \$(ls -1 redhat/\${rhel}); do
                                        mkdir -p /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/RPMS/\${arch}
                                        cp -av redhat/\${rhel}/\${arch}/*.rpm /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/RPMS/\${arch}/
                                        createrepo  \${ALGO:-} --update /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/RPMS/\${arch}/
                                        if [ -f  /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/RPMS/\${arch}/repodata/repomd.xml.asc ]; then
                                            rm -f  /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/RPMS/\${arch}/repodata/repomd.xml.asc
                                        fi
                                        gpg --detach-sign --armor --passphrase $SIGN_PASSWORD /srv/\${REPOPATH}/\${REPOCOMP}/\${rhel}/RPMS/\${arch}/repodata/repomd.xml
                                    done
                                done
                                date +%s > /srv/repo-copy/version
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
                                REPOCOMP=\$(echo "${COMPONENT}" | tr '[:upper:]' '[:lower:]')
                                LCREPOSITORY=\$(echo "${REPOSITORY}" | tr '[:upper:]' '[:lower:]')
                                if [ ${REMOVE_BEFORE_PUSH} = true ]; then
                                     if [[ ! ${COMPONENT} == RELEASE ]]; then
                                         REPOPUSH_ARGS=" --remove-package "
                                     else
                                         echo "it is not allowed to remove packages from RELEASE repository"
                                         exit 1
                                     fi
                                fi
                                if [[ ! "${REPOSITORY}" == "PERCONA" ]]; then
                                    export PATH="/usr/local/reprepro5/bin:\${PATH}"
                                fi
                                if [[ "${REPOSITORY}" == "DEVELOPMENT" ]]; then
                                    export REPOPATH="apt-repo"
                                else
                                    export REPOPATH="repo-copy/"\${LCREPOSITORY}"/apt"
                                fi
                                set -e
                                echo "<*> path to repo is "\${REPOPATH}
                                echo "<*> reprepro binary is "\$(which reprepro)
                                pushd /srv/UPLOAD/${PATH_TO_BUILD}/binary/debian
                                CODENAMES=\$(ls -1)
                                echo "<*> Distributions are: "\${CODENAMES}
                                tree
                                # -------------------------------------> source pushing, it's a bit specific
                                if [[ ${REMOVE_LOCKFILE} = true ]]; then
                                    echo "<*> Removing lock file as requested..."
                                    rm -vf  /srv/\${REPOPATH}/db/lockfile
                                fi
                                if [[ ${COMPONENT} == RELEASE ]]; then
                                    export REPOCOMP=main
                                    if  [ -d /srv/UPLOAD/${PATH_TO_BUILD}/source/debian ]; then
                                        cd /srv/UPLOAD/${PATH_TO_BUILD}/source/debian
                                        DSC=\$(find . -type f -name '*.dsc')
                                        for DSC_FILE in \${DSC}; do
                                            echo "<*> DSC file is "\${DSC_FILE}
                                            for _codename in \${CODENAMES}; do
                                                echo "<*> CODENAME: "\${_codename}
                                                echo "repopush --gpg-pass=${SIGN_PASSWORD} --package=\${DSC_FILE} --repo-path=\${REPOPATH} --component=\${REPOCOMP}  --codename=\${_codename} --verbose \${REPOPUSH_ARGS} || true"
                                                sleep 5
                                            done
                                        done
                                     fi
                                fi

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
    post {
        always {
            script {
                currentBuild.description = "Repo: ${REPOSITORY}, path to packages: ${PATH_TO_BUILD}"
            }
        }
    }
}
