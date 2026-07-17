# Round 9 일간 상품 랭킹 설계와 검증

## TL;DR

영속 메트릭과 실시간 랭킹의 장애 경계를 분리하고, Kafka 이벤트를 한국 날짜별 Redis ZSET에 가중 합산해 조회 전용 상품 랭킹을 제공한다.

## 아키텍처

```text
commerce-api → outbox → catalog-events
                            ├─ commerce-streamer-catalog-metrics → product_metrics
                            └─ commerce-streamer-catalog-ranking → Redis daily ZSET

GET /api/v1/rankings → Redis ZSET → 상품/브랜드 일괄 조회 → Redis 순서대로 조립
GET /api/v1/products/{id} → 상품 캐시/DB + 오늘의 Redis 순위 합성
```

메트릭 소비와 랭킹 소비는 같은 토픽을 서로 다른 Consumer Group으로 읽는다. Redis 장애나 랭킹 재시도가 영속 메트릭 적재의 offset을 막지 않게 하기 위한 선택이다. 랭킹 Consumer는 배치 전체가 정상 처리된 뒤에만 offset을 acknowledge한다.

## 공유 계약과 책임

`modules:ranking`은 API와 streamer가 함께 알아야 하는 최소 계약만 제공한다.

- 이벤트 시각을 `Asia/Seoul`로 변환한 날짜
- `ranking:all:yyyyMMdd` 키 형식
- 상품 ID의 Redis member 문자열 형식

가중치 정책은 streamer application에 두고 Redis Lua와 TTL은 infrastructure adapter에 둔다. 범용 Redis 설정 모듈은 상품 랭킹 정책을 알지 않는다.

## 점수 정책과 배치 집계

| 이벤트 | 점수 |
| --- | ---: |
| 조회 | `0.1 × viewCountDelta` |
| 좋아요/취소 | `0.2 × likeCountDelta` |
| 주문 | `0.7 × salesCountDelta` |

주문 1건의 0.7점이 좋아요 3건의 0.6점보다 높다. Kafka 배치 안에서는 동일한 `(한국 날짜, 상품 ID)`의 점수를 먼저 합산해 Redis 호출 수를 줄인다.

## Redis 갱신과 TTL

Lua 스크립트로 다음 작업을 한 번에 실행한다.

1. `ZINCRBY`로 점수를 누적한다.
2. 음수와 0을 포함한 원점수를 유지한다.
3. `EXPIRE 172800`으로 키 만료 시간을 설정한다.

이벤트가 반영될 때마다 TTL이 다시 2일로 설정되는 sliding TTL이다. 읽기와 쓰기는 모두 master Redis를 사용해 갱신 직후 replica 지연으로 이전 순위를 읽는 문제를 피한다.

## 조회 계약

- `GET /api/v1/rankings?date=yyyyMMdd&page=1&size=20`
- page는 1부터 시작하고 size는 1 이상 100 이하이다.
- 과거 날짜를 명시할 수 있고, 날짜가 없으면 주입된 KST `Clock`의 오늘을 사용한다.
- Redis의 정렬 순서와 원래 순위 번호를 유지한다.
- 목록은 `score > 0` 범위만 조회하고, 0 이하 상품의 상세 rank는 null로 반환한다.
- 삭제된 상품은 응답에서 제외하되 뒤 상품의 순위를 당기지 않는다.
- 상품 상세 순위는 10분 상품 캐시에 넣지 않고 응답 시점에 다시 조회한다.
- 상품 상세의 랭킹 조회가 실패하면 상품은 정상 반환하고 rank만 null로 둔다.

## 정합성과 복구 경계

Redis 랭킹은 유실 가능한 read model이다. Must-Have에서는 별도 processed-event Set이나 Redis Inbox, 스냅샷, Kafka 전체 replay 복구를 도입하지 않는다.

이 선택에는 다음 한계가 있다.

- Redis 초기화 뒤에는 새 이벤트부터 다시 점수가 쌓인다.
- 한 배치의 일부 Redis 쓰기 후 실패하면 offset이 acknowledge되지 않는다.
- 재전달 시 이미 성공했던 일부 점수가 중복 반영되어 소폭 오차가 생길 수 있다.

정확한 영속 통계는 `product_metrics`가 담당한다. 랭킹의 정확한 복구가 비즈니스 요구가 되면 시간별 스냅샷이나 별도 Inbox를 비용과 함께 다시 검토한다.

## 자동화 검증

다음 항목을 테스트로 검증했다.

- UTC 이벤트 시각의 KST 날짜 경계와 일간 키/member 계약
- 조회, 좋아요, 취소, 주문 점수와 `주문 1건 > 좋아요 3건`
- 동일 날짜/상품 배치 합산과 날짜가 다른 이벤트의 키 분리
- Consumer 성공 시 ACK, 역직렬화/의미 검증/처리 실패 시 ACK하지 않음
- 배치 전체 의미 검증을 쓰기 전에 완료해 poison event 앞의 점수가 부분 반영되지 않음
- Redis 원점수 누적, 2일 TTL 하한과 갱신, poll 경계와 무관한 합산
- 목록과 상세 조회에서 0점 이하 member 비노출
- 과거 날짜 조회, 1-based 페이지, 잘못된 페이지의 400 응답
- 상품/브랜드 일괄 조합, Redis 순서, 삭제된 상품 뒤 원래 순위 유지
- 상품 상세의 nullable rank, Redis 장애 fallback, 상품 캐시로부터의 순위 분리
- 이벤트별 점수 누적, 랭킹 조회 hit/miss, API 지연 관측 지표

실행 결과:

```text
./gradlew :modules:ranking:test :apps:commerce-streamer:test :apps:commerce-api:test
BUILD SUCCESSFUL in 4m 31s

./gradlew :apps:commerce-streamer:test \
  --tests 'com.loopers.application.ranking.RankingScorePolicyTest'
BUILD SUCCESSFUL

JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home \
  ./gradlew build
BUILD SUCCESSFUL in 1m 58s
```

## Kafka E2E 검증

로컬 인프라에서 commerce-api와 commerce-streamer를 함께 실행하고 다음 흐름을 검증했다.

1. 관리자 API로 브랜드와 상품을 생성한다.
2. 상품 상세 API를 호출해 조회 이벤트를 Outbox에 기록한다.
3. Outbox relay가 실제 Kafka `catalog-events` 토픽에 이벤트를 발행한다.
4. `commerce-streamer-catalog-ranking` Consumer Group이 이벤트를 소비한다.
5. Redis `ranking:all:20260717`의 상품 점수가 `0.1` 증가한다.
6. 랭킹 API가 동일 상품을 `rank=1`과 누적 점수로 반환한다.

검증 당시 기존 조회 점수 `0.1`에 새 조회 이벤트가 반영되어 Redis 점수가 `0.2`가 되었고, `GET /api/v1/rankings?date=20260717&page=1&size=20` 응답에서도 `rank=1`, `score=0.2`를 확인했다. 이 검증은 실제 브로커를 사용한 수동 live smoke이며, 자동화 테스트의 구성 요소별 검증과 구분한다.

이후 수동 smoke를 별도 프로세스 기반 system E2E로 자동화했다.

```text
./gradlew rankingKafkaE2E
BUILD SUCCESSFUL in 43s
```

격리·정리까지 포함한 반복 실행도 `1m 10s`, `1m 31s`에 연속 통과했고, localhost bind와 JVM fail-fast를 보강한 최종 실행은 `40s`에 통과했다. 실행 시간에는 container와 JVM 시작, Kafka consumer group 할당 대기가 포함되므로 처리 성능 수치로 해석하지 않는다.

이 task는 기존 로컬 DB·Redis·Kafka와 다른 포트에 전용 Docker Compose를 띄우고, `commerce-streamer`와 `commerce-api` bootJar를 별도 JVM으로 실행한다. 테스트는 브랜드·상품 생성 뒤 상품 상세 조회로 `PRODUCT_VIEWED`를 발생시키고, Outbox `PUBLISHED`, ranking consumer의 실제 Kafka 소비, Redis score `0.1`, Ranking API의 `rank=1`, `score=0.1`을 순서대로 polling해 검증한다. 종료 시 두 JVM과 E2E 전용 container·volume을 제거하며, 실패 시 애플리케이션 로그·Outbox·consumer group·Redis 상태를 보고서 디렉터리에 보존한다. 상세 실행 경계는 [Kafka System E2E](05-ranking-kafka-e2e.md)에 기록했다.

## 벤치마크

- [실행 가이드](02-ranking-benchmark-guide.md)
- [측정 결과와 설계 판단](03-ranking-benchmark-report.md)
- [이벤트 랭킹과 배치 스냅샷 비교](04-event-vs-batch-ranking.md)
- [Kafka System E2E](05-ranking-kafka-e2e.md)
- [랭킹 원점수 결정성 재현과 개선](06-ranking-score-determinism.md)

로컬 측정에서는 Hot 분포의 batch 3,000이 batch 1보다 약 26.6배 높은 processor-to-Redis 처리량을 보였다. 재시도 실험에서는 부분 성공한 고유 aggregate write가 많을수록 점수 오차 반경도 커졌다. API cardinality 기준선(concurrency 1)은 10만 member까지 Redis 조회 p95가 1ms 미만이었고 full HTTP 경로가 지연을 지배했다. 이 수치는 운영 용량이 아니라 현재 설계의 병목과 trade-off를 확인하기 위한 상대 비교값이다.

### 배치 비교 확장

현재 이벤트 기반 구현은 유지하면서 `product_metric_hourly`를 읽어 종료된 날짜의 절대 점수를 다시 발행하는 배치 스냅샷을 추가했다. 구현 구조, 10만 건/10만 상품 측정, 재실행 정확성과 신선도 경계는 [이벤트 랭킹과 배치 스냅샷 비교](04-event-vs-batch-ranking.md)에 기록했다. 기존 벤치마크 결론을 대체하는 것이 아니라 실시간 이벤트와 closed-date reconciliation의 역할 차이를 검증한 확장이다.

## 후속 과제

- 시간별 랭킹과 일/주/월 집계
- 전일 점수 carry-over
- 가중치 버전 관리와 주문 매출 정규화
- 정확한 복구가 필요해질 때 시간별 스냅샷 또는 Inbox 도입
