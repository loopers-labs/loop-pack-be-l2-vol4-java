# Round 9 Ranking Kafka System E2E

## 검증 목적

구성 요소별 테스트만으로는 Outbox, Kafka topic·consumer group, 직렬화 설정, Redis 연결과 Ranking API가 실제 실행 설정에서도 이어지는지 증명할 수 없다. 이 E2E는 `commerce-api`와 `commerce-streamer`를 별도 JVM으로 실행해 다음 경로를 한 번에 검증한다.

```text
상품 상세 HTTP 요청
  → Outbox READY
  → Outbox Relay가 실제 Kafka에 발행
  → Outbox PUBLISHED
  → commerce-streamer ranking batch consumer
  → Redis ranking:all:yyyyMMdd ZINCRBY
  → Ranking API rank/score 응답
```

## 실행

필수 도구는 Docker Compose, curl, Python 3, lsof와 Java 21이다. Java 21이 기본 경로에 없다면 `E2E_JAVA`로 실행 파일을 지정할 수 있다.

```bash
./gradlew rankingKafkaE2E

# Java 21 경로를 직접 지정하는 경우
E2E_JAVA=/path/to/java21/bin/java ./gradlew rankingKafkaE2E
```

Gradle task는 API와 streamer의 `bootJar`를 먼저 생성한 다음 `scripts/e2e/ranking-kafka-e2e.sh`를 실행한다. 일반 `check`에는 연결하지 않은 opt-in system test다.

## 격리 전략

`docker/ranking-kafka-e2e-compose.yml`은 기존 개발 인프라와 겹치지 않는 포트를 사용한다.

| 구성 요소 | E2E 포트 |
| --- | ---: |
| MySQL | 13306 |
| Redis master | 16379 |
| Redis replica | 16380 |
| Kafka host listener | 19093 |
| commerce-api / management | 18080 / 18081 |
| commerce-streamer / management | 18082 / 18083 |

Compose project와 volume도 `loopers-ranking-kafka-e2e` 전용이다. 실행 전 해당 포트가 사용 중이면 즉시 실패하며 병렬 실행은 지원하지 않는다. 종료 시 E2E가 시작한 JVM에만 TERM을 보내고, 해당 Compose project를 `down -v`로 제거한다. 기존 `loopers` DB와 기본 Redis/Kafka에는 접근하지 않는다.

모든 container와 애플리케이션 포트는 `127.0.0.1`에만 bind해 로컬 네트워크 외부에 E2E DB와 관리자 API가 노출되지 않게 한다. host lock을 먼저 획득하므로 같은 E2E가 이미 실행 중이면 기존 실행을 중단시키지 않고 즉시 실패한다.

## 자동 검증 단계

1. 전용 MySQL, Redis master·replica, Kafka의 readiness를 기다린다.
2. 전용 `catalog-events` topic을 생성한다.
3. streamer bootJar를 별도 JVM으로 실행한다.
4. Actuator readiness와 ranking consumer group의 topic assignment를 함께 확인한다.
5. API bootJar를 별도 JVM으로 실행하고 readiness를 확인한다.
6. 관리자 API로 고유한 브랜드와 상품을 생성한다.
7. 상품 상세 API를 호출해 `PRODUCT_VIEWED` Outbox 이벤트를 만든다.
8. Outbox 상태가 `PUBLISHED`가 될 때까지 기다린다.
9. Redis member 점수가 `0.1`이 될 때까지 기다린다.
10. Ranking API에서 같은 상품이 `rank=1`, `score=0.1`인지 확인한다.

Kafka batch consumer는 단일 이벤트에서도 fetch wait가 생길 수 있으므로 고정 `sleep`을 쓰지 않는다. 각 조건을 500ms 간격으로 polling하며 기본 timeout은 90초다. 필요하면 `RANKING_E2E_TIMEOUT_SECONDS`로 조정한다.

## 실행 결과

2026-07-17 로컬 실행에서 전체 경로가 통과했다.

```text
[ranking-kafka-e2e] ready: streamer readiness and ranking consumer assignment
[ranking-kafka-e2e] ready: API readiness
[ranking-kafka-e2e] ready: outbox event publication
[ranking-kafka-e2e] ready: Redis score 0.1
[ranking-kafka-e2e] ready: Ranking API rank=1 and score=0.1
[ranking-kafka-e2e] PASS: Outbox -> Kafka -> streamer -> Redis -> Ranking API

BUILD SUCCESSFUL in 43s
```

전용 인프라 제거 후 같은 명령을 두 번 더 실행해 `1m 10s`, `1m 31s`에 연속 통과했다. localhost 전용 bind와 JVM fail-fast를 보강한 최종 실행도 `40s`에 통과했다. 차이는 주로 container·JVM 시작과 Kafka consumer group 할당 대기에서 발생했으며, 이벤트 처리량 비교값으로 사용하지 않는다.

결과와 실행 로그는 `build/reports/ranking-kafka-e2e/{yyyyMMdd-HHmmss}`에 저장된다. 성공 시에도 API·streamer 로그와 각 HTTP 응답, 최종 `result.txt`를 남긴다.

## 실패 진단

실패하면 다음 파일을 같은 report 디렉터리에 추가한다.

- `api.log`, `streamer.log`: 별도 JVM 전체 로그
- `outbox.log`: 최근 Outbox 상태와 실패 원인
- `consumer-group.log`: ranking consumer의 partition·offset 상태
- `redis-ranking.log`: 해당 날짜 Top-N과 score
- `compose.log`, `compose-ps.log`: 전용 인프라 상태

따라서 실패 지점을 `HTTP/Outbox`, `Kafka 발행·할당`, `streamer 처리`, `Redis 반영`, `API 조회`로 나눠 확인할 수 있다.

## 검증 경계

- 실제 Kafka broker와 별도 API·streamer JVM을 사용하지만 모두 한 로컬 장비에서 실행한다.
- 성공 여부를 검증하는 system test이며 throughput이나 운영 SLO를 측정하지 않는다.
- 한 개의 `PRODUCT_VIEWED` 이벤트를 사용하므로 대량 이벤트, 재전달과 consumer lag drain은 별도 벤치마크 대상이다.
- 전용 인프라를 매번 새로 만들기 때문에 기존 데이터가 있는 상태의 migration·호환성은 검증하지 않는다.
