pipeline {
    agent {
        label 'linux && java21-docker'
    }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage('Verify Agent') {
            steps {
                sh '''
                    set -eu
                    java -version
                    docker version
                    date --version
                    ./gradlew --version
                '''
            }
        }

        stage('Test Pipeline Helpers') {
            steps {
                sh '''
                    set -eu

                    resolved_date="$(
                        REQUESTED_AGGREGATION_END_DATE= \
                        BUILD_SCHEDULED_TIME_MILLIS=1784826000000 \
                        sh jenkins/resolve-ranking-parameters.sh \
                            aggregation-end-date
                    )"
                    test "$resolved_date" = '20260723'

                    resolved_date="$(
                        REQUESTED_AGGREGATION_END_DATE=20260228 \
                        sh jenkins/resolve-ranking-parameters.sh \
                            aggregation-end-date
                    )"
                    test "$resolved_date" = '20260228'

                    if REQUESTED_AGGREGATION_END_DATE=20260230 \
                        sh jenkins/resolve-ranking-parameters.sh \
                            aggregation-end-date >/dev/null 2>&1
                    then
                        exit 1
                    fi

                    resolved_revision="$(
                        REQUESTED_REVISION=2147483647 \
                        sh jenkins/resolve-ranking-parameters.sh revision
                    )"
                    test "$resolved_revision" = '2147483647'

                    if REQUESTED_REVISION=2147483648 \
                        sh jenkins/resolve-ranking-parameters.sh revision \
                            >/dev/null 2>&1
                    then
                        exit 1
                    fi
                '''
            }
        }

        stage('Test Ranking Batch') {
            steps {
                sh '''
                    ./gradlew --no-daemon \
                        :modules:ranking:test \
                        :apps:commerce-batch:test
                '''
            }
        }

        stage('Build Ranking Batch') {
            steps {
                sh './gradlew --no-daemon :apps:commerce-batch:bootJar'
            }
        }

        stage('Archive Batch Jar') {
            steps {
                archiveArtifacts(
                    artifacts: 'apps/commerce-batch/build/libs/commerce-batch-*.jar',
                    fingerprint: true,
                    onlyIfSuccessful: true
                )
            }
        }
    }

    post {
        always {
            junit(
                allowEmptyResults: true,
                testResults: '**/build/test-results/test/*.xml'
            )
        }
    }
}
