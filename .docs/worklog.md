# Worklog Snapshot

이 문서는 현재 작업 상태를 이어가기 위한 최신 스냅샷이다. 누적 로그가 아니라 현재 기준만 유지한다.

## 현재 상태

| 항목 | 내용 |
| --- | --- |
| 날짜 | 2026-06-28 |
| 브랜치 | `volume-7` |
| 현재 단계 | 7주차 Step 1~3 구현 검증 및 보강 완료 |
| 구현 범위 | `commerce-api` 범용 Outbox/Kafka relay, 좋아요 ApplicationEvent, `commerce-streamer` metrics consumer, `coupon-issue-requests` 발급 consumer, Micrometer/Prometheus 지표 |
| 보강 범위 | Kafka manual ack 설정 명확화, worker/consumer 활성화 env 설정, 7주차 이벤트 스키마 SQL 추가 |
| 제외 범위 | DLQ, Grafana dashboard, 전체 모듈 풀 테스트 |
| 기준 문서 | `AGENTS.md`, `.docs/domain.md`, `.docs/architecture.md`, `.docs/test-plan.md`, `.codeguide/loopers-7-week.md`, `.codeguide/transaction-analysis.md` |
| 제출 문서 | `.docs/design/*` 4개 파일은 volume-2 설계 이력으로 보존하며 이번 작업에서 수정하지 않음 |

## 최근 결정

- 기존 주문 전용 `OrderEventOutbox`를 범용 `EventOutbox`로 전환했다. 테이블명은 기존 `order_event_outbox`를 유지하고 `eventId/topic/partitionKey/eventType/aggregateType/aggregateId/payload/status/retryCount` 형태로 확장한다.
- 좋아요 등록/취소는 `ProductLikeChangedApplicationEvent`를 발행하고, `BEFORE_COMMIT` 리스너가 `catalog-events` Outbox를 같은 DB 트랜잭션에 저장한다.
- `EventRelayWorker`는 pending Outbox 조회 뒤 Kafka 발행을 수행하고, 결과 상태(`SENT`/retry/`FAILED`)는 별도 트랜잭션으로 반영한다.
- Kafka producer는 `acks=all`, `enable.idempotence=true`를 사용한다. Consumer는 batch/manual ack이며 `enable-auto-commit=false`, `auto-offset-reset=latest`를 Spring Boot consumer 설정 위치에 둔다.
- `POST /api/v1/coupons/{couponId}/issue`는 실제 발급 쿠폰 대신 `CouponIssueRequest(PENDING)`를 반환한다. 실제 발급은 `CouponIssueRequestConsumer`가 처리하고, 결과는 `GET /api/v1/coupons/issues/{requestId}` polling으로 확인한다.
- `CouponIssueRequestConsumer`는 `commerce.consumers.coupon-issue.enabled=true`일 때 활성화한다. 환경변수 `COMMERCE_COUPON_ISSUE_CONSUMER_ENABLED=true`로 켤 수 있다.
- `EventRelayWorkerScheduler`는 `commerce.workers.event-relay.enabled=true`일 때 활성화한다. 환경변수 `COMMERCE_EVENT_RELAY_ENABLED=true`로 켤 수 있다.
- 운영/ddl-none 환경 보강을 위해 `docker/mysql/init/02-round7-event-schema.sql`을 추가했다. 새 이벤트 테이블 생성과 기존 `order_event_outbox`, `coupon_template.total_issue_limit` 보강 DDL을 포함한다.

## 수정 파일 요약

| 구분 | 파일 |
| --- | --- |
| 7주차 가이드 | `.codeguide/loopers-7-week.md` |
| 기준 문서 | `.docs/README.md`, `.docs/design-review.md`, `.docs/domain.md`, `.docs/architecture.md`, `.docs/test-plan.md`, `.docs/worklog.md` |
| Outbox/Relay | `apps/commerce-api/src/main/java/com/loopers/{domain,application,infrastructure}/event/**` |
| 좋아요 이벤트 | `apps/commerce-api/src/main/java/com/loopers/application/catalog/like/**` |
| 비동기 쿠폰 발급 | `apps/commerce-api/src/main/java/com/loopers/{domain,application,infrastructure,interfaces}/coupon/**`, `apps/commerce-api/src/main/java/com/loopers/interfaces/consumer/coupon/**` |
| Streamer 집계 | `apps/commerce-streamer/src/main/java/com/loopers/{domain,application,infrastructure,interfaces}/**` |
| Kafka/Monitoring | `modules/kafka/src/main/**`, `supports/monitoring/src/main/**` |
| 설정/스키마 | `apps/commerce-api/src/main/resources/application.yml`, `apps/commerce-streamer/src/main/resources/application.yml`, `modules/kafka/src/main/resources/kafka.yml`, `docker/mysql/init/02-round7-event-schema.sql` |
| 테스트 | `apps/commerce-api/src/test/**`, `apps/commerce-streamer/src/test/**` |
| 제출 문서 | `.docs/design/*` 수정 없음 |

## 검증 결과

| 명령 | 결과 | 메모 |
| --- | --- | --- |
| `.\gradlew.bat --rerun-tasks :apps:commerce-api:test --tests com.loopers.application.coupon.CouponIssueRequestEventServiceTest --tests com.loopers.application.event.relay.EventRelayWorkerTest --tests com.loopers.application.event.catalog.ProductLikeEventOutboxListenerTest :apps:commerce-streamer:test --tests com.loopers.application.catalog.metrics.ProductMetricsEventServiceTest` | 성공 | 쿠폰 발급 이벤트, Outbox relay, 좋아요 Outbox listener, streamer metrics focused tests 통과 |
| `LOOPERS_TESTCONTAINERS_ENABLED=false .\gradlew.bat --rerun-tasks :apps:commerce-api:test --tests com.loopers.interfaces.api.coupon.CouponApiE2ETest --tests com.loopers.application.coupon.CouponConcurrencyTest` | 성공 | compose MySQL/Redis/Kafka 기동 후 쿠폰 E2E와 DB 동시성 테스트 통과 |
| `docker exec docker-mysql-1 mysql -uapplication -papplication loopers -e "SOURCE /docker-entrypoint-initdb.d/02-round7-event-schema.sql"` | 성공 | 7주차 이벤트 스키마 SQL 문법 및 idempotent 실행 확인 |

## 환경 메모

- 로컬 Java는 `C:\Users\woodo\.jdks\ms-21.0.9`를 `JAVA_HOME`으로 지정해 Gradle을 실행했다.
- sandbox 안에서는 Gradle wrapper 배포본 다운로드와 Docker 접근에 권한 상승 실행이 필요했다.
- DB 기반 테스트 전 `docker compose -f docker\infra-compose.yml up -d mysql redis-master redis-readonly kafka kafka-ui`로 local infra를 기동했다.
- Git 상태 조회 시 사용자 홈의 global ignore 접근 권한 경고가 출력될 수 있다. 작업 파일 판단에는 영향 없다.

## 다음 작업

1. DLQ를 7주차 제출 범위에 포함할지 별도 운영 보강으로 넘길지 결정한다.
2. 필요하면 전체 모듈 풀 테스트 범위를 선택해 실행한다.
3. 운영 배포 시 `COMMERCE_EVENT_RELAY_ENABLED`, `COMMERCE_COUPON_ISSUE_CONSUMER_ENABLED` 값을 환경별로 확정한다.
