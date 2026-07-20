# Worklog Snapshot

이 문서는 현재 작업 상태를 이어가기 위한 최신 스냅샷이다. 누적 로그가 아니라 현재 기준만 유지한다.

## 현재 상태

| 항목 | 내용 |
| --- | --- |
| 날짜 | 2026-07-12 |
| 브랜치 | `volume-9` |
| 현재 단계 | 9주차 랭킹 Must-Have 구현 및 리뷰 보강 완료 |
| 작업 범위 | Redis ZSET 랭킹 저장소, Ranking API, 상품 상세 rank, 조회/좋아요/주문 이벤트 기반 랭킹 consumer 구현과 리뷰 지적 보강 |
| 구현 범위 | `catalog.ranking` 경계, `PRODUCT_VIEWED` Outbox, `catalog-events`/`order-events` Ranking consumer, HTTP 예시 |
| 제외 범위 | 시간 단위 랭킹, Weight 실시간 조절, 콜드 스타트 Carry-Over |
| 기준 문서 | `AGENTS.md`, `.docs/README.md`, `.docs/design-review.md`, `.docs/worklog.md`, `.docs/domain.md`, `.docs/architecture.md`, `.docs/dto-spec.md`, `.codeguide/loopers-9-week.md` |
| 제출 문서 | `.docs/design/*` 4개 파일은 volume-2 설계 이력으로 보존하며 이번 작업에서 수정하지 않음 |

## 최근 결정

- 9주차 과제 가이드는 목표, 핵심 범위, 전체 흐름, 단계별 요구사항, 검증 체크리스트, 구현 시 유의사항 순서로 정리한다.
- Must-Have는 Redis Sorted Set, Realtime Ranking, Ranking API로 유지한다.
- Nice-To-Have는 Kafka 배치 리스너, 시간 단위 랭킹, Weight 실시간 조절, 콜드 스타트 완화로 분리한다.
- 랭킹 ZSET 기본 키는 `ranking:all:{yyyyMMdd}`, TTL은 2일 기준으로 문서화한다.
- Ranking API 예시는 프로젝트 페이징 기준에 맞춰 0-based `page=0`으로 정리한다.
- 이번 작업은 문서 가독성 정리이며 랭킹 구현 방식, 패키지 경계, 이벤트 멱등성 정책은 아직 확정하지 않았다.
- 설계 결정이 필요한 구현 작업은 권장안, 대안, 영향 범위, 검증 방법을 제시한 뒤 사용자 승인을 받아 진행한다.
- 승인 전에는 문서/코드 탐색, 현 상태 요약, 선택지 정리, 영향도 분석만 수행한다.
- 패키지 경계, 도메인 소속, API/DTO 계약, 이벤트 종류, Kafka topic/key, Redis key, 점수 계산식, 멱등성 기준은 반드시 먼저 질문한다.
- 랭킹 기능은 상품 조회용 read model로 보고 `catalog.ranking` 하위 경계에 둔다.
- 랭킹 목록 API는 `GET /api/v1/rankings?date=yyyyMMdd&page=0&size=20`을 사용한다.
- 랭킹 목록 응답은 `rank`, `score`, `product`를 포함하고, 상품 상세 응답은 nullable `rank`를 포함한다.
- 랭킹 반영 이벤트는 상품 상세 조회 성공 시 신규 발행할 `PRODUCT_VIEWED`, 기존 `PRODUCT_LIKED`, `PRODUCT_UNLIKED`, `ORDER_PAID`로 한다.
- Ranking consumer는 `catalog-events`와 `order-events`를 소비한다.
- 랭킹 Redis key는 `ranking:all:{yyyyMMdd}`, member는 productId 문자열, TTL은 2일이다.
- 랭킹 날짜는 EventMessage `occurredAt`을 Asia/Seoul 날짜로 변환해 계산한다.
- 점수는 조회 `+0.1`, 좋아요 `+0.2`, 좋아요 취소 `-0.2`, 주문 항목별 `lineAmount * 0.6`으로 누적한다.
- Ranking consumer 멱등성은 Redis Lua로 `ranking:handled:{ranking:{eventId}}` SETNX와 ZSET 점수 반영을 원자 처리하고, DB `event_handled`에는 `ranking:{eventId}`를 저장한다.
- 상품 상세 조회 이벤트 Outbox 저장 실패는 응답 실패로 전파하지 않고 warning 로그만 남긴다.
- 랭킹 API는 음수 page/0 이하 size를 기본값으로 정규화하고, 판매 중지 상품은 응답에서 제외한다.
- Docker Desktop 기동 후 로컬 MySQL/Redis/Kafka compose 기반 `commerce-api`, `commerce-streamer` 전체 테스트를 통과했다.

## 수정 파일 요약

| 구분 | 파일 |
| --- | --- |
| 9주차 가이드 | `.codeguide/loopers-9-week.md` |
| 협업 규칙 | `AGENTS.md` |
| 기준 문서 | `.docs/domain.md`, `.docs/architecture.md`, `.docs/dto-spec.md` |
| 랭킹 API | `apps/commerce-api/src/main/java/com/loopers/interfaces/api/catalog/ranking/*`, `apps/commerce-api/src/main/java/com/loopers/application/catalog/ranking/*` |
| 랭킹 Redis adapter | `apps/commerce-api/src/main/java/com/loopers/domain/catalog/ranking/*`, `apps/commerce-api/src/main/java/com/loopers/infrastructure/catalog/ranking/*`, `apps/commerce-streamer/src/main/java/com/loopers/domain/catalog/ranking/*`, `apps/commerce-streamer/src/main/java/com/loopers/infrastructure/catalog/ranking/*` |
| 상품 상세 연계 | `ProductResult.java`, `ProductV1Dto.java`, `ProductV1Controller.java` |
| 조회 이벤트 | `ProductViewEventPublisher.java`, `ProductViewEventPayload.java`, `EventOutbox.java` |
| Ranking consumer | `apps/commerce-streamer/src/main/java/com/loopers/application/catalog/ranking/*`, `apps/commerce-streamer/src/main/java/com/loopers/interfaces/consumer/ProductRankingConsumer.java` |
| 테스트 | `RankingQueryServiceTest.java`, `RedisRankingRepositoryTest.java`, `ProductViewEventPublisherTest.java`, `ProductRankingEventServiceTest.java`, `CatalogApiE2ETest.java` |
| HTTP 예시 | `http/commerce-api/rankings.http` |
| 작업 스냅샷 | `.docs/worklog.md` |
| 제출 문서 | `.docs/design/*` 수정 없음 |

## 검증 결과

| 명령 | 결과 | 메모 |
| --- | --- | --- |
| `docker compose -f docker\infra-compose.yml up -d mysql redis-master redis-readonly kafka` | 성공 | 로컬 MySQL/Redis/Kafka 기동 |
| `$env:JAVA_HOME='C:\Users\woodo\.jdks\ms-21.0.9'; $env:LOOPERS_TESTCONTAINERS_ENABLED='false'; .\gradlew.bat --rerun-tasks :apps:commerce-api:test :apps:commerce-streamer:test` | 실패 후 재실행 성공 | 첫 실행에서 기존 `RedisOrderQueueRepositoryTest` 동시성 테스트 1건 실패. 단독 재실행 통과 후 전체 재실행 통과 |
| `$env:JAVA_HOME='C:\Users\woodo\.jdks\ms-21.0.9'; $env:LOOPERS_TESTCONTAINERS_ENABLED='false'; .\gradlew.bat --rerun-tasks :apps:commerce-api:test --tests com.loopers.infrastructure.ordering.queue.RedisOrderQueueRepositoryTest` | 성공 | 앞선 전체 회귀에서 실패한 기존 대기열 동시성 테스트 단독 재확인 |
| `$env:JAVA_HOME='C:\Users\woodo\.jdks\ms-21.0.9'; $env:LOOPERS_TESTCONTAINERS_ENABLED='false'; .\gradlew.bat --rerun-tasks :apps:commerce-api:test :apps:commerce-streamer:test` | 성공 | commerce-api/commerce-streamer 전체 테스트 최종 통과 |

## 환경 메모

- `.codeguide/loopers-9-week.md`는 작업 시작 시점에 Git 미추적 파일이었다.
- 작업 시작 시점에 `tmp/`도 Git 미추적 항목으로 존재했으며 이번 작업에서는 건드리지 않았다.
- 현재 셸에 `JAVA_HOME`이 없어 Gradle 실행 시 `C:\Users\woodo\.jdks\ms-21.0.9`를 지정했다.
- Testcontainers 대신 로컬 compose 인프라를 사용하기 위해 `LOOPERS_TESTCONTAINERS_ENABLED=false`를 지정했다.

## 다음 작업

1. 랭킹 consumer 실제 Kafka 흐름은 Outbox relay와 streamer를 함께 띄워 수동 API 또는 E2E로 확인한다.
2. 필요 시 시간 단위 랭킹, Weight 실시간 조절, Carry-Over 중 다음 개선 범위를 선택한다.
