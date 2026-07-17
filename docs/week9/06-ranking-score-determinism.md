# 랭킹 원점수 결정성 재현과 개선

## 결론

`ZINCRBY` 결과가 0 이하일 때 member를 즉시 `ZREM`하면 같은 이벤트 집합도 Kafka poll 경계에 따라 최종 점수가 달라진다. Redis ZSET에는 음수와 0을 포함한 원점수를 보존하고, 사용자 조회 경계에서만 `score > 0`을 적용하도록 변경했다.

이 변경은 poll 경계에 따른 비결정성을 제거한다. Kafka at-least-once 재전달로 이미 성공한 증가분이 다시 반영되는 문제까지 해결하는 것은 아니며, 그 오차는 종료일 D-1 배치 스냅샷이 보정한다.

## 문제 재현

동일한 두 이벤트를 실제 `CatalogRankingEventProcessor → RedisRankingScoreWriter → Redis` 경로로 처리했다.

| 이벤트 | 점수 |
| --- | ---: |
| 좋아요 취소 | -0.2 |
| 상품 조회 | +0.1 |
| 일간 합계 | -0.1 |

수정 전 RED 테스트의 관측값은 다음과 같았다.

| 처리 방식 | 기대 원점수 | 수정 전 관측 |
| --- | ---: | ---: |
| 한 poll에서 `-0.2 + 0.1` 합산 | -0.1 | member 없음 (`null`) |
| 두 poll에서 `-0.2`, `+0.1` 순차 처리 | -0.1 | +0.1 |
| D-1 배치에서 -0.2 절대 점수 발행 | -0.2 | member 없음 (`null`) |

원인은 첫 poll의 -0.2가 반영된 직후 Lua가 member를 제거하면서 누적 상태도 함께 지운 것이다. 다음 poll의 +0.1은 -0.2에 더해지지 않고 새 점수로 시작했다. 배치도 양수만 발행해 종료일 원점수 스냅샷이 같은 정보를 잃었다.

## 변경한 경계

### 쓰기 경계

- live consumer는 `ZINCRBY` 결과가 음수나 0이어도 ZSET member를 유지한다.
- D-1 batch는 계산된 절대 원점수를 음수와 0까지 모두 임시 ZSET에 발행한다.
- 두 경로 모두 같은 `RankingScoreFormula`와 같은 원점수 표현을 사용한다.

### 읽기 경계

- 랭킹 목록은 Redis `ZREVRANGEBYSCORE`의 `score > 0` 범위만 조회한다.
- 상품 상세는 `ZSCORE`가 없거나 0 이하이면 rank를 `null`로 반환한다.
- 양수 member는 음수·0보다 앞에 있으므로 상세의 `ZREVRANK`는 사용자에게 보이는 양수 집합 안의 순위와 같다.

원점수 저장용 ZSET과 노출용 ZSET을 둘로 나누지 않은 이유는 현재 조회 정책이 단순한 `score > 0` 범위 조회로 표현되고, 단일 ZSET이 이중 쓰기와 동기화 실패 지점을 만들지 않기 때문이다.

## 수정 후 측정

| 검증 | 수정 후 관측 | 결과 |
| --- | --- | --- |
| live one-poll | -0.1 | 기대값 일치 |
| live split-poll | -0.1 | poll 경계와 무관 |
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

수정 후 대상 테스트 실행 결과는 `BUILD SUCCESSFUL in 53s`였다.

전체 랭킹 관련 모듈 회귀도 통과했다.

```text
./gradlew :modules:ranking:test :apps:commerce-streamer:test \
  :apps:commerce-api:test :apps:commerce-batch:test
BUILD SUCCESSFUL in 1m 42s
37 actionable tasks: 5 executed, 32 up-to-date
```

실제 별도 JVM과 Kafka broker를 거치는 system E2E도 다시 실행해 양수 점수의 기존 사용자 경로가 유지되는지 확인했다.

```text
./gradlew rankingKafkaE2E
Redis score=0.1
Ranking API rank=1, score=0.1
BUILD SUCCESSFUL in 1m 2s
```

전체 저장소 빌드는 Java 21 경로를 명시해 통과했다. 경로를 명시하지 않은 첫 시도는 소스 오류가 아니라 로컬 Gradle toolchain 탐색 실패로 0.6초 만에 종료됐다.

```text
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home \
  ./gradlew build
BUILD SUCCESSFUL in 1m 52s
51 actionable tasks: 31 executed, 20 up-to-date
```

## 트레이드오프와 남은 한계

- 장점: 이벤트 처리 순서가 같다면 poll 분할과 무관하게 덧셈의 원점수가 유지된다.
- 장점: 종료일 batch가 live와 같은 원점수 표현으로 canonical 키를 교체할 수 있다.
- 비용: 사용자에게 보이지 않는 음수·0 상품도 TTL 동안 ZSET cardinality와 메모리를 차지한다.
- 한계: 부동소수점 점수이므로 테스트는 작은 허용 오차로 비교한다.
- 한계: at-least-once 부분 성공 후 재전달의 중복 증가분은 여전히 발생할 수 있다.
- 한계: late event와 D-1 실행 시점은 consumer lag 0과 grace period라는 운영 정책이 필요하다.
