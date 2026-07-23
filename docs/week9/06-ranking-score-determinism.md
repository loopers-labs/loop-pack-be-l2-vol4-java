# 랭킹 원점수 결정성 재현과 개선

## 결론

`ZINCRBY` 결과가 0 이하일 때 member를 즉시 `ZREM`하거나, poll 내부 합계가 0이라는 이유로 Redis 쓰기를 생략하면 같은 이벤트 집합도 Kafka poll 경계에 따라 저장 상태가 달라진다. Redis ZSET에는 음수와 0을 포함한 원점수를 보존하고, 사용자 조회 경계에서만 `score > 0`을 적용하도록 변경했다.

고정된 이벤트 순서가 음수나 0으로 끝나는 재현 사례에서는 poll을 어떻게 나눠도 같은 원점수가 남는다. Kafka at-least-once 재전달로 이미 성공한 증가분이 다시 반영되는 문제까지 해결하는 것은 아니며, 그 오차는 종료일 D-1 배치 스냅샷이 보정한다.

## 문제 재현

동일한 두 이벤트를 실제 `CatalogRankingEventProcessor → RedisRankingScoreWriter → Redis` 경로로 처리했다.

| 이벤트 | 점수 |
| --- | ---: |
| 좋아요 취소 | -0.2 |
| 상품 조회 | +0.1 |
| 일간 합계 | -0.1 |

음수 합계에 대한 최초 RED 테스트의 관측값은 다음과 같았다.

| 처리 방식 | 기대 원점수 | 수정 전 관측 |
| --- | ---: | ---: |
| 한 poll에서 `-0.2 + 0.1` 합산 | -0.1 | member 없음 (`null`) |
| 두 poll에서 `-0.2`, `+0.1` 순차 처리 | -0.1 | +0.1 |
| D-1 배치에서 -0.2 원점수 발행 | -0.2 | member 없음 (`null`) |

원인은 첫 poll의 -0.2가 반영된 직후 Lua가 member를 제거하면서 누적 상태도 함께 지운 것이다. 다음 poll의 +0.1은 -0.2에 더해지지 않고 새 점수로 시작했다. 배치도 양수만 발행해 종료일 원점수 스냅샷이 같은 정보를 잃었다.

`ZREM`을 제거한 뒤에는 최종 합계가 0인 경우가 추가로 드러났다.

| 처리 방식 | 기대 원점수 | 1차 수정 후 관측 | 최종 수정 후 |
| --- | ---: | ---: | ---: |
| 한 poll에서 `+0.2 - 0.2` 합산 | 0.0 | member 없음 (`null`) | 0.0 |
| 두 poll에서 `+0.2`, `-0.2` 순차 처리 | 0.0 | 0.0 | 0.0 |

한 poll에서는 processor가 상품별 합계 0을 Redis에 전달하지 않았고, 두 poll에서는 `+0.2`와 `-0.2`가 각각 기록돼 0점 member가 남았다. API에서는 둘 다 보이지 않지만, Redis 원점수가 poll 분할에 따라 달라지는 상태였다.

## 변경한 경계

### 쓰기 경계

- ranking consumer는 `ZINCRBY` 결과가 음수나 0이어도 ZSET member를 유지한다.
- `CatalogRankingEventProcessor`는 여러 이벤트를 합친 결과가 0이어도 writer에 전달한다. 다만 개별 점수가 0인 이벤트와 랭킹 대상이 아닌 이벤트는 기존처럼 집계에서 제외한다.
- D-1 배치는 일간 재계산 원점수를 음수와 0까지 모두 임시 ZSET에 발행한다.
- 두 경로 모두 같은 `RankingScoreFormula`와 같은 원점수 표현을 사용한다.

### 읽기 경계

- 랭킹 목록은 Redis `ZREVRANGEBYSCORE`의 `score > 0` 범위만 조회한다.
- 상품 상세는 `ZSCORE`가 없거나 0 이하이면 rank를 `null`로 반환한다.
- 양수 member는 음수·0보다 앞에 있으므로 상세의 `ZREVRANK`는 사용자에게 보이는 양수 집합 안의 순위와 같다.

원점수 저장용 ZSET과 노출용 ZSET을 둘로 나누지 않은 이유는 현재 조회 정책이 단순한 `score > 0` 범위 조회로 표현되고, 단일 ZSET이 이중 쓰기와 동기화 실패 지점을 만들지 않기 때문이다.

## 수정 후 측정

| 검증 | 수정 후 관측 | 결과 |
| --- | --- | --- |
| 실시간 one-poll | -0.1 | 기대값 일치 |
| 실시간 split-poll | -0.1 | poll 경계와 무관 |
| net-zero one-poll | 0.0 | 0점 member 보존 |
| net-zero split-poll | 0.0 | poll 경계와 무관 |
| D-1 batch 음수 | -0.2 | 원점수 보존 |
| D-1 batch 0 | 0.0 | 원점수 보존 |
| API 목록 | +1.0만 노출 | 0·음수 비노출 |
| 상품 상세 | 0·음수 rank `null` | 조회 계약 유지 |

원본 전후 값은 [CSV](benchmark-results/score-determinism-before-after.csv)에 남겼다.

## 자동화된 회귀 테스트

```bash
./gradlew \
  :apps:commerce-streamer:test \
    --tests 'com.loopers.infrastructure.ranking.RedisRankingScoreWriterIntegrationTest' \
  :apps:commerce-api:test \
    --tests 'com.loopers.interfaces.api.ranking.RankingV1ApiE2ETest' \
  :apps:commerce-batch:test \
    --tests 'com.loopers.job.ranking.DailyRankingSnapshotJobE2ETest'
```

회귀 테스트에는 음수 합계뿐 아니라 최종 합계가 0인 이벤트 집합을 한 poll과 여러 poll로 나눈 경우도 포함했다. processor 단위 테스트에서는 합산 결과 0이 writer에 전달되는지도 확인한다.

net-zero 보강 뒤 processor 단위 테스트와 Redis 통합 테스트 전체를 다시 실행했다.

```text
./gradlew :apps:commerce-streamer:test \
  --tests 'com.loopers.application.ranking.CatalogRankingEventProcessorTest' \
  --tests 'com.loopers.infrastructure.ranking.RedisRankingScoreWriterIntegrationTest'
BUILD SUCCESSFUL in 16s
```

전체 저장소 빌드와 실제 별도 JVM·Kafka broker를 사용하는 system E2E도 최신 수정본으로 다시 통과했다.

```text
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home \
  ./gradlew build
BUILD SUCCESSFUL in 1m 43s
51 actionable tasks: 24 executed, 27 up-to-date

./gradlew rankingKafkaE2E
Redis score=0.1
Ranking API rank=1, score=0.1
BUILD SUCCESSFUL in 39s
```

## 트레이드오프와 남은 한계

- 장점: 이벤트 처리 순서가 같다면 음수와 0을 포함한 최종 원점수가 poll 분할과 무관하게 유지된다.
- 장점: 종료일 배치가 실시간 처리와 같은 원점수 표현으로 공개 키를 교체할 수 있다.
- 비용: 사용자에게 보이지 않는 음수·0 상품도 TTL 동안 ZSET cardinality와 메모리를 차지한다.
- 한계: 부동소수점 점수이므로 테스트는 작은 허용 오차로 비교한다.
- 한계: at-least-once 부분 성공 후 재전달의 중복 증가분은 여전히 발생할 수 있다.
- 한계: late event와 D-1 실행 시점은 consumer lag 0과 grace period라는 운영 정책이 필요하다.
