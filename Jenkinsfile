// 랭킹 배치(rankingProductMvJob) 파이프라인.
// - 수동 실행(ranking-batch-verify): PERIOD/PERIOD_KEY 를 직접 넣고 SEED_TEST_DATA=true 로 더미 데이터까지 채워 검증.
// - 스케줄 실행(ranking-batch-weekly-schedule / ranking-batch-monthly-schedule): PERIOD_KEY 를 비워두면
//   직전에 완료된 주/월을 자동으로 계산하고, SEED_TEST_DATA=false 로 실제 CDC 누적 데이터만 집계한다.
// cron 트리거는 Jenkinsfile 이 아니라 Job 생성 스크립트에서 Job별로 다르게 부여한다(공용 스크립트 재사용 목적).
// Jenkins 컨테이너에 리포가 /workspace/loopers 로 볼륨 마운트되어 있음을 전제로 한다(git checkout 없음).
pipeline {
    agent any

    parameters {
        choice(name: 'PERIOD', choices: ['WEEKLY', 'MONTHLY'], description: '집계 주기')
        string(name: 'PERIOD_KEY', defaultValue: '', description: '비우면 직전에 완료된 기간을 자동 계산. WEEKLY: yyyyWww (예: 2026W30) / MONTHLY: yyyyMM (예: 202607)')
        booleanParam(name: 'SEED_TEST_DATA', defaultValue: true, description: 'true면 product_daily_metrics 에 검증용 더미 데이터를 채운 뒤 실행 (실데이터를 덮어쓰므로 스케줄 실행에서는 false 권장)')
    }

    environment {
        PERIOD = "${params.PERIOD}"
        REPO_DIR = '/workspace/loopers'
    }

    stages {
        stage('Build commerce-batch') {
            steps {
                dir(env.REPO_DIR) {
                    sh './gradlew :apps:commerce-batch:bootJar -x test --no-daemon'
                }
            }
        }

        stage('Resolve period') {
            steps {
                dir(env.REPO_DIR) {
                    script {
                        // params.PERIOD_KEY 를 environment{} 블록에 그대로 대입하면 빈 문자열이 null 로 붕괴되는
                        // 문제가 있어(Jenkins 선언형 파이프라인 이슈), 여기서 params 를 직접 읽어 env 에 명시적으로 채운다.
                        def periodKey = params.PERIOD_KEY?.trim()
                        if (!periodKey) {
                            // 비어 있으면 "직전에 완료된" 주/월을 자동 계산한다(RankingBatchJobParameters 와 동일한 ISO 주 규칙).
                            periodKey = sh(script: '''#!/usr/bin/env bash
                                set -e
                                if [ "$PERIOD" = "WEEKLY" ]; then
                                    ref_date=$(date -u -d "-7 days" +%F)
                                    echo "$(date -u -d "$ref_date" +%G)W$(date -u -d "$ref_date" +%V)"
                                else
                                    ref_date=$(date -u -d "$(date -u +%Y-%m-01) -1 day" +%F)
                                    date -u -d "$ref_date" +%Y%m
                                fi
                            ''', returnStdout: true).trim()
                        }
                        env.PERIOD_KEY = periodKey
                        echo "Using PERIOD=${env.PERIOD} PERIOD_KEY=${env.PERIOD_KEY}"
                    }
                }
            }
        }

        stage('Seed test data') {
            when { expression { return params.SEED_TEST_DATA } }
            steps {
                dir(env.REPO_DIR) {
                    // product_daily_metrics 는 원래 commerce-streamer 의 CDC 로 채워지는 테이블이라
                    // 로컬 검증용으로 선택한 PERIOD/PERIOD_KEY 기간에 맞춰 더미 데이터를 직접 채운다.
                    // 날짜 범위 계산은 RankingBatchJobParameters 와 동일한 규칙(ISO 주 월요일 시작)을 따른다.
                    sh '''#!/usr/bin/env bash
                        set -e
                        apt-get update -qq && apt-get install -y -qq default-mysql-client

                        if [ "$PERIOD" = "WEEKLY" ]; then
                            year="${PERIOD_KEY:0:4}"
                            week="${PERIOD_KEY:5:2}"
                            jan4_dow=$(date -u -d "${year}-01-04" +%u)
                            monday_of_week1=$(date -u -d "${year}-01-04 -$((jan4_dow-1)) days" +%F)
                            start_date=$(date -u -d "${monday_of_week1} +$((10#$week - 1)) weeks" +%F)
                            end_date=$(date -u -d "${start_date} +6 days" +%F)
                        else
                            year="${PERIOD_KEY:0:4}"
                            month="${PERIOD_KEY:4:2}"
                            start_date=$(date -u -d "${year}-${month}-01" +%F)
                            end_date=$(date -u -d "${start_date} +1 month -1 day" +%F)
                        fi
                        echo "Seeding product_daily_metrics for ${start_date} ~ ${end_date}"

                        SQL_FILE=$(mktemp)
                        {
                            echo "CREATE TABLE IF NOT EXISTS product_daily_metrics ("
                            echo "  metric_date DATE NOT NULL,"
                            echo "  product_id BIGINT NOT NULL,"
                            echo "  order_count BIGINT,"
                            echo "  like_count BIGINT,"
                            echo "  view_count BIGINT,"
                            echo "  updated_at DATETIME(6),"
                            echo "  PRIMARY KEY (metric_date, product_id)"
                            echo ");"

                            d="$start_date"
                            day_index=0
                            end_epoch=$(date -u -d "$end_date" +%s)
                            while [ "$(date -u -d "$d" +%s)" -le "$end_epoch" ]; do
                                for product_id in $(seq 1 30); do
                                    view_count=$(( (product_id * 37 + day_index * 11) % 500 + 10 ))
                                    like_count=$(( (product_id * 13 + day_index * 7) % 100 ))
                                    order_count=$(( (product_id * 5 + day_index * 3) % 50 ))
                                    echo "INSERT INTO product_daily_metrics (metric_date, product_id, order_count, like_count, view_count, updated_at) VALUES ('${d}', ${product_id}, ${order_count}, ${like_count}, ${view_count}, NOW()) ON DUPLICATE KEY UPDATE order_count=VALUES(order_count), like_count=VALUES(like_count), view_count=VALUES(view_count), updated_at=NOW();"
                                done
                                day_index=$((day_index + 1))
                                d=$(date -u -d "$d +1 day" +%F)
                            done
                        } > "$SQL_FILE"

                        mysql --skip-ssl -h mysql -uloopers -p'loopers!!!' loopers < "$SQL_FILE"
                        rm -f "$SQL_FILE"
                    '''
                }
            }
        }

        stage('Run rankingProductMvJob') {
            steps {
                dir(env.REPO_DIR) {
                    // ddl-auto=update: 기존 product_daily_metrics 데이터를 보존하면서 MV 테이블만 없으면 생성
                    // datasource 오버라이드: local 프로파일 기본값(localhost)이 아닌 compose 서비스명으로 접속
                    sh '''
                        set -e
                        JAR=$(ls apps/commerce-batch/build/libs/commerce-batch-*.jar | tail -n1)
                        java -jar "$JAR" \
                          --job.name=rankingProductMvJob \
                          --spring.jpa.hibernate.ddl-auto=update \
                          --spring.batch.jdbc.initialize-schema=always \
                          --datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://mysql:3306/loopers \
                          --datasource.mysql-jpa.main.username=loopers \
                          --datasource.mysql-jpa.main.password=loopers!!! \
                          --datasource.redis.master.host=redis-master \
                          --datasource.redis.master.port=6379 \
                          --datasource.redis.replicas[0].host=redis-readonly \
                          --datasource.redis.replicas[0].port=6379 \
                          "period=$PERIOD" \
                          "periodKey=$PERIOD_KEY"
                    '''
                }
            }
        }
    }
}
