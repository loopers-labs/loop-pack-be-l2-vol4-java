# Worklog Snapshot

이 문서는 현재 작업 상태를 이어가기 위한 최신 스냅샷이다. 누적 로그가 아니라 현재 기준만 유지한다.

## 현재 상태

| 항목 | 내용 |
| --- | --- |
| 날짜 | 2026-07-22 |
| 브랜치 | `volume-10` |
| 현재 단계 | 10주차 구현 검증 완료: 일간 `product_metrics` 집계 기반 주간/월간 Batch MV와 Ranking API 기간 분기 구현 |
| 작업 범위 | `productRankingAggregationJob` Batch, 주간/월간 랭킹 MV 테이블, `period=daily/weekly/monthly` Ranking API 확장 |
| 구현 범위 | `period/baseDate` JobParameter 검증, 주간/월간 기간 계산, `product_metrics` 점수 집계, MV 삭제 후 TOP 100 재적재, API MV 조회 분기 |
| 제외 범위 | Batch 스케줄러, 운영 배포 실행 주기, 실제 HTTP API E2E |
| 기준 문서 | `AGENTS.md`, `.docs/README.md`, `.docs/design-review.md`, `.docs/worklog.md`, `.docs/domain.md`, `.docs/architecture.md`, `.docs/dto-spec.md`, `.codeguide/loopers-10-week.md` |
| 제출 문서 | `.docs/design/*` 4개 파일은 volume-2 설계 이력으로 보존하며 이번 작업에서 수정하지 않음 |

## 최근 결정

- Batch 구현 위치는 기존 `apps/commerce-batch` 모듈의 `com.loopers.batch.job.catalog.ranking` 하위로 확정했다.
- Batch Job 이름은 `productRankingAggregationJob`이다.
- JobParameter는 `period=weekly|monthly`, `baseDate=yyyyMMdd`를 사용한다.
- `weekly`는 `baseDate`가 속한 주의 월요일부터 일요일, `monthly`는 해당 월의 1일부터 말일까지 집계한다.
- 주간/월간 랭킹 점수는 `view_count * 0.1 + like_count * 0.2 + sales_amount * 0.6`로 계산한다.
- MV 테이블은 `mv_product_rank_weekly`, `mv_product_rank_monthly`를 사용한다.
- MV 컬럼은 `period_start_date`, `period_end_date`, `rank`, `product_id`, `score`, 감사 컬럼을 사용한다.
- 재실행 정책은 같은 기간 결과 삭제 후 TOP 100 재적재다.
- Ranking API는 `GET /api/v1/rankings?period=daily|weekly|monthly&date=yyyyMMdd&page=0&size=20`을 사용한다.
- `period` 기본값은 `daily`, `date` 기본값은 Asia/Seoul 기준 오늘이다.

## 수정 파일 요약

| 구분 | 파일 |
| --- | --- |
| 10주차 가이드 | `.codeguide/loopers-10-week.md` |
| 기준 문서 | `.docs/domain.md`, `.docs/architecture.md`, `.docs/dto-spec.md`, `.docs/worklog.md` |
| Batch Job | `apps/commerce-batch/src/main/java/com/loopers/batch/job/catalog/ranking/*` |
| Batch 테이블 매핑 | `apps/commerce-batch/src/main/java/com/loopers/infrastructure/catalog/ranking/*` |
| Batch 테스트 | `apps/commerce-batch/src/test/java/com/loopers/job/catalog/ranking/*` |
| Batch 공통 테스트 | `apps/commerce-batch/src/test/java/com/loopers/CommerceBatchApplicationTest.java`, `apps/commerce-batch/src/test/java/com/loopers/job/demo/DemoJobE2ETest.java` |
| Ranking API/application | `apps/commerce-api/src/main/java/com/loopers/application/catalog/ranking/*`, `apps/commerce-api/src/main/java/com/loopers/interfaces/api/catalog/ranking/RankingController.java` |
| Ranking domain/infrastructure | `apps/commerce-api/src/main/java/com/loopers/domain/catalog/ranking/*`, `apps/commerce-api/src/main/java/com/loopers/infrastructure/catalog/ranking/*` |
| Ranking 테스트 | `apps/commerce-api/src/test/java/com/loopers/application/catalog/ranking/RankingQueryServiceTest.java` |
| Streamer 일간 집계 | `apps/commerce-streamer/src/main/java/com/loopers/**/metrics/*`, `ProductMetricsConsumer.java`, 관련 테스트 |
| DB 초기화 | `docker/mysql/init/02-round7-event-schema.sql` |
| 제출 문서 | `.docs/design/*` 수정 없음 |

## 검증 결과

| 명령 | 결과 | 메모 |
| --- | --- | --- |
| `docker compose -f docker\infra-compose.yml up -d mysql` | 성공 | 로컬 Compose MySQL 기동 |
| `$env:JAVA_HOME='C:\Users\woodo\.jdks\ms-21.0.9'; $env:LOOPERS_TESTCONTAINERS_ENABLED='false'; .\gradlew.bat --no-daemon :apps:commerce-batch:test` | 성공 | Batch 전체 테스트 통과. 이전 실패 원인은 로컬 Spring Batch 메타 테이블 재사용 시 고정 JobParameter가 완료된 JobInstance와 충돌한 것이라 `run.id`를 유니크하게 변경 |
| `$env:JAVA_HOME='C:\Users\woodo\.jdks\ms-21.0.9'; $env:LOOPERS_TESTCONTAINERS_ENABLED='false'; .\gradlew.bat --no-daemon :apps:commerce-api:test --tests com.loopers.application.catalog.ranking.RankingQueryServiceTest` / `.\gradlew.bat --no-daemon :apps:commerce-streamer:test --tests com.loopers.application.catalog.metrics.ProductMetricsEventServiceTest` | 성공 | API 기간 분기 테스트, Streamer 일간 집계 테스트 통과. Streamer는 잘못된 패키지 필터로 1회 실패 후 실제 패키지명으로 재실행 완료 |

## 환경 메모

- 현재 셸에 `JAVA_HOME`이 없어 Gradle 실행 시 `C:\Users\woodo\.jdks\ms-21.0.9`를 지정했다.
- Docker/Testcontainers 기반 E2E는 현재 환경에서 Docker daemon을 찾지 못해 실패했으나, 사용자가 Docker Desktop을 기동한 뒤 Compose MySQL을 직접 올려 `LOOPERS_TESTCONTAINERS_ENABLED=false`로 검증했다.
- `.codeguide/loopers-10-week.md`와 `tmp/`는 작업 시작 시점부터 Git 미추적 항목이었다.
- 이번 작업에서 `.docs/design/*` 제출 이력 파일은 수정하지 않았다.

## 다음 작업

1. 필요 시 Ranking API E2E에 `period=weekly/monthly` 조회 케이스를 추가한다.
2. 제출 전 전체 회귀 테스트 범위를 선택해 실행한다.
