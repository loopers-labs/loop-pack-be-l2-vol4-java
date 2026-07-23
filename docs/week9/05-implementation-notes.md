# 05. 구현 노트 — 실시간 랭킹

구현 시점의 설계 트레이드오프와 파일 맵을 기록한다. 요구사항은 [`01-requirements.md`](./01-requirements.md), 데이터 모델은 [`04-redis-model.md`](./04-redis-model.md) 참조.

> **상태**: Must-Have 구현 완료(branch `volume-9`). Nice-to-Have(시간 단위 랭킹·콜드 스타트)는 미포함. 단위·통합 테스트 작성 완료, **Docker 환경에서 전부 green**. **수동 E2E 검증 완료**(2026-07-17) — 이벤트 발행 → Kafka 컨슘 → ZSET → API 노출까지 실측했고, 그 과정에서 발견한 중복 발행 문제로 §2.1의 멱등 설계를 개정했다.

---

## 1. 파일 맵

### commerce-streamer (적재/쓰기)

| 파일 | 역할 |
| --- | --- |
| `interfaces/consumer/ProductRankingConsumer` | `@KafkaListener` 배치, 그룹 `ranking-aggregator`, 수동 커밋 |
| `application/ranking/RankingAggregator` | 배치 파싱 + `(키, productId)` 델타 coalescing + `event_handled` 멱등(§2.1) |
| `application/ranking/RankingScorePolicy` | 가중치 상수 + 스코어 계산(view/like/order) |
| `infrastructure/ranking/RankingKey` | 키 포맷 + TTL + `occurredAt`→KST 일자 환산 |
| `infrastructure/ranking/RankingRedisRepository` | 파이프라인 `ZINCRBY` + `EXPIRE` |

### commerce-batch (스냅샷 적재)

| 파일 | 역할 |
| --- | --- |
| `batch/job/ranking/RankingSnapshotJobConfig` | `rankingSnapshotJob` — 하루 1회, 확정된 어제치를 DB로 |
| `batch/job/ranking/step/RankingSnapshotTasklet` | ZSET 상위 N 읽기 → delete-then-insert(멱등) |

### commerce-api (조회/읽기)

| 파일 | 역할 |
| --- | --- |
| `infrastructure/ranking/RankingCompositeRepository` | **포트의 유일한 구현** — 날짜로 ZSET/스냅샷 소스 선택(§2.6) |
| `infrastructure/ranking/RankingSnapshotEntity`·`JpaRepository`·`RankingSnapshotRepository` | 스냅샷 스키마 + 과거 랭킹 읽기 |
| `interfaces/api/ranking/RankingV1Controller` | `GET /api/v1/rankings` (date/page/size) |
| `interfaces/api/ranking/RankingV1Dto` | 응답 DTO(`RankingPageResponse`/`RankedItem`) |
| `application/ranking/RankingFacade` | 순위+상품 요약 조립(N+1 회피) |
| `application/ranking/RankingPageInfo`·`RankedProductInfo` | 응용 DTO |
| `domain/ranking/RankingRepository` | 조회 포트(인터페이스) |
| `domain/ranking/RankedProduct` | 순위·상품·스코어 레코드 |
| `infrastructure/ranking/RankingRedisRepository` | ZSET `ZREVRANGE`/`ZCARD`/`ZREVRANK` |
| `infrastructure/ranking/RankingKey` | 키 포맷 + `today()`(KST) |

### 변경분(기존 파일)

| 파일 | 변경 |
| --- | --- |
| `infrastructure/metrics/EventHandledRepository`(streamer) | 변경 없음 — `consumer_group` 파라미터 덕에 그대로 재사용(§2.1) |
| `application/outbox/OutboxImmediatePublisher`(api) | 주석 정정 — "브로커 멱등이 중복을 흡수한다"는 서술이 사실과 달라 삭제(§2.1) |
| `domain/order/OrderPaidEvent`(api) | `Item`에 `unitPrice` 추가(원본 주석 보존) |
| `application/product/ProductFacade`(api) | `RankingRepository` 주입, 상세에 `rank` 조합 |
| `application/product/ProductDetailInfo`(api) | `rank` 필드 추가 |
| `application/product/CachedProductDetail`(api) | `toInfo(liked, rank)`(원본 주석 보존) |
| `interfaces/api/product/ProductV1Dto`(api) | `ProductDetailResponse`에 `rank` 추가 |

---

## 2. 설계 트레이드오프

### 2.1 멱등성 — `event_handled` 재사용 (2026-07-17 개정)

> **개정 이력**: 최초 구현은 `event_handled` 없이 "근사 허용"으로 갔다. 근거는 *"`ZINCRBY`는 멱등이 아니라 **컨슈머 재전달 시** 소폭 이중 가산된다 → 랭킹은 근사값이니 수용"* 이었다. **E2E가 이 근거를 반증했다** — 중복의 지배적 출처는 컨슈머 재전달이 아니라 **프로듀서 측 중복 발행**이고, "소폭"이 아니었다.

**E2E에서 관측된 것** (13개 이벤트를 발생시킨 시시한 시나리오):

- 좋아요 취소 이벤트(`eventId=12`)가 `catalog-events`에 **레코드 2개로 적재**됨. outbox 행은 1개뿐 → 중복 INSERT가 아니라 **같은 행을 두 번 발행**한 것.
- 원인: `OutboxImmediatePublisher`(afterCommit 즉시 발행)와 `OutboxRelay`(1초 폴링)가 **같은 행을 잠그지 않는 read-then-act** 라, 커밋 직후 폴링 주기가 겹치면 둘 다 `PENDING`을 보고 각자 보낸다. 해당 행만 `created_at`→`sent_at` 간격이 134ms로 튀어 경합 흔적이 남았다(정상 ~20ms).
- 결과: `product_metrics`는 `event_handled`로 걸러 정상(like_count=0). **랭킹만 −0.2가 이중 반영**되어, 조회1+좋아요+취소 상품이 `+0.1`이어야 할 자리에 `−0.1`을 받았다.

**왜 브로커 멱등으로 안 막히나**: `OutboxImmediatePublisher`의 옛 주석은 "브로커 멱등(`enable.idempotence`)이 중복을 흡수한다"고 적고 있었으나 **사실이 아니다**. `enable.idempotence`는 **한 프로듀서 세션 내 재시도**를 PID+시퀀스 번호로 중복 제거할 뿐이라, 서로 다른 트랜잭션에서 나간 별개의 `send()` 두 번은 서로 다른 레코드로 그대로 적재된다. 중복을 흡수하는 건 **소비자 멱등(`event_handled`)뿐**이다. (주석도 함께 정정했다.)

**그래서 채택한 것**: `EventHandledRepository`는 이미 `consumer_group`을 파라미터로 받으므로 `ranking-aggregator` 그룹으로 그대로 재사용한다 — `metrics-aggregator`와 동일한 2단 방어.

1. 배치 내 중복 `eventId` 제거(먼저 온 것 유지) — 같은 배치에 실려온 중복을 접는다.
2. `findHandled`로 이미 처리한 `eventId` 필터 — 다른 배치로 온 중복을 막는다.
3. Redis 반영 → **같은 트랜잭션에서** `markHandled`.

**남는 창(window)**: Redis와 DB는 한 트랜잭션으로 묶을 수 없다. Redis 반영 후 마킹 커밋 전에 죽으면 재전달 시 **그 배치만** 이중 가산된다(유실은 없음 — at-least-once). 이 잔여 오차는 근사 + 2일 TTL 리셋으로 수용한다. 마킹을 먼저 커밋하는 반대 순서는 이중 가산 대신 **유실**이라 더 나쁘다 — 랭킹에서 없는 점수는 되찾을 방법이 없지만, 이중 가산은 TTL이 지우기 때문이다.

**비용**: 배치당 `SELECT` 1회 + `INSERT` 배치 1회가 추가된다. NFR-2(파이프라인 분리)는 유지된다 — 컨슈머 그룹과 오프셋은 여전히 독립이고, `event_handled`는 그룹별 행이라 `metrics-aggregator`와 경합하지 않는다.

### 2.2 파이프라인 분리 — 별도 컨슈머 그룹

랭킹 적재를 기존 `metrics-aggregator`에 얹지 않고 **별도 그룹 `ranking-aggregator`**로 같은 토픽을 독립 소비한다.

- Redis 쓰기 실패가 DB 집계 오프셋에 영향을 주지 않고(그 반대도), 각자 재처리·랙 관리가 독립적이다.
- 비용: 같은 이벤트를 두 번 역직렬화. 처리량이 문제되면 Nice-to-Have의 배치 정제로 최적화.

### 2.3 N+1 회피 — Facade 배치 조립

랭킹 페이지의 `productId`를 모아 **3번의 배치 조회**로 조립한다.

- `ProductService.findActiveByIds` / `ProductMetricsService.getLikeCounts` / `BrandService.findByIds`.
- 페이지 크기(기본 20)만큼의 개별 조회 대신 상수 쿼리. ZSET엔 있으나 비활성/삭제된 상품은 `findActiveByIds` 결과에서 빠져 자연스럽게 제외.

### 2.4 상품 상세 rank·rankYesterday는 캐시 밖

상세의 사용자 무관 데이터는 `ProductReadCache`에 캐시되지만, 순위는 **캐시하지 않는다**. `liked`와 동일하게 Facade가 캐시 결과에 실시간으로 덧붙인다(`toInfo(liked, rank, rankYesterday)`).

- `rank` — 매초 바뀌는 실시간 값이라 캐시 부적합.
- `rankYesterday` — 값 자체는 고정(어제 ZSET은 더 안 바뀜)이라 캐시해도 될 것 같지만, **캐시 키에 일자가 없어 자정을 넘기면 stale**이 된다(어제 순위가 그제 순위로 남는다). TTL 5분이라 최대 5분 오차지만, 같은 이유로 캐시 밖에 두는 게 단순하다.

`ZREVRANK` 2회(오늘·어제)가 나가지만 상세 1건이라 파이프라이닝 없이 그대로 둔다.

### 2.5 상세는 왜 델타가 아니라 순위 둘을 주나

`rankChange: +2` 같은 델타 하나로 압축하지 않고 `rank`/`rankYesterday` 원본을 준다. 델타는 **한쪽이 null인 경우를 표현하지 못하기** 때문이다 — 그리고 그 null이 의미를 갖는다.

| rank | rankYesterday | 의미 |
| --- | --- | --- |
| 1 | 3 | 2계단 상승 |
| 1 | null | 오늘 신규 진입 |
| null | 1 | 오늘 이탈(어제 1위였으나 오늘 활동 없음) |
| null | null | 이틀간 랭킹 무관 |

"신규 진입"과 "2계단 상승"을 같은 델타로 뭉갤 수 없고, "이탈"과 "무관"은 `rank`만 봐선 구분되지 않는다. 해석은 클라이언트 몫으로 두고 서버는 사실만 준다.

### 2.6 과거 랭킹 스냅샷 — 왜 TTL 연장이 아닌가

TTL 2일 밖의 과거 랭킹을 보존하기 위해 배치가 상위 N을 DB로 내린다(설계는 [`04-redis-model.md`](./04-redis-model.md) §5).

**TTL 연장이 더 싸다는 걸 먼저 인정해야 한다.** "과거를 더 오래 보고 싶다"가 목적이라면 `Duration.ofDays(30)` 한 줄이면 되고, ZSET은 그날 활동이 있었던 상품만 담으므로 메모리 증가도 수십 MB 수준이다. 스냅샷은 **배치 잡 + 테이블 + 이중 읽기 경로**를 추가한다 — 그 비용을 "과거 조회"만으로 정당화할 수는 없다.

스냅샷을 택하는 근거는 **ZSET이 줄 수 없는 것**에 있다.

| | ZSET(TTL 연장) | 스냅샷(DB) |
| --- | --- | --- |
| 과거 상위 N 조회 | ✅ | ✅ |
| SQL 조인·집계 ("지난달 브랜드별 평균 순위") | ❌ 불가 | ✅ |
| 수개월~년 장기 보관 | 메모리 비용 | ✅ 디스크 |
| Redis 유실 시 복구 재료 | ❌ | ✅ |

> **복구 목적이라면 Kafka 재생이 더 정확하다** — §4에서 우리가 한 게 정확히 그거였다. 오프셋을 되감으면 ZSET이 완벽히 복원된다(`event_handled`의 해당 그룹 행은 지워야 하고, 토픽 보존기간 안이어야 하지만). 스냅샷은 **찍은 시점까지만** 복원되고 그 이후는 어차피 재생이 필요하다. 스냅샷의 강점은 정확도가 아니라 **읽기 편의**(O(1) 조회)와 **분석 가능성**이다.

**두 가지 한계를 감수한다** (§5.4):
- 스냅샷 테이블은 TTL이 없어 **자동으로 줄지 않는다**. ZSET의 TTL이 하던 회수 역할이 사라진다 — 보관 정책을 정해 주기 삭제를 붙여야 한다.
- **배치가 안 돌면 그날 랭킹은 TTL과 함께 사라진다.** 배치 실패가 조용한 데이터 유실이 된다.

### 2.7 일자 버킷 = 이벤트 발생시각

컨슘 시각이 아니라 `occurredAt`(KST 환산)으로 날짜 키를 정한다. 컨슈머 랙이나 자정 근처 이벤트도 올바른 날짜에 반영된다. 파싱 실패/누락 시 현재 KST 일자로 폴백(유실 방지).

---

## 3. 남은 일 (TODO)

- [x] 랭킹 **단위 테스트**: `RankingScorePolicy`(가중치·log·delta 부호), `RankingKey`(포맷·KST 환산·폴백), `RankingAggregator`(배치 coalescing·이벤트 분기·**멱등 3종**, Mockito).
- [x] 랭킹 **통합 테스트**: `RankingRedisRepository` 쓰기/읽기(실제 Redis Testcontainer) — `ZINCRBY`+TTL, `ZREVRANGE`/`ZCARD`/`ZREVRANK`.
- [x] Docker로 **E2E** 확인(이벤트 발행 → 컨슘 → ZSET → API 노출) — §4.
- [x] 조회 경로 **E2E 자동화**(`RankingV1ApiE2ETest`) — 일자 파라미터·상품정보 조합·상세 rank(null 포함) — §4.1.
- [ ] (Nice-to-Have) 시간 단위 랭킹, 콜드 스타트 워밍업.
- [ ] (선택) 가중치 `application.yml` 외부화.
- [ ] (후속·랭킹 밖) 하이브리드 Outbox의 중복 발행 자체를 줄일지 검토 — 즉시 발행 경로가 행을 `SELECT … FOR UPDATE`로 잡거나 `markSent`를 조건부 UPDATE(`WHERE status='PENDING'`)로 선점하면 경합 창이 좁아진다. 지금은 소비자 멱등으로 흡수되므로 급하지 않고, 발행측 변경은 week7 자산 전체에 영향이라 별도 판단이 필요하다.

---

## 4. E2E 검증 기록 (2026-07-17)

로컬 실기동(commerce-api:8080 / commerce-streamer:8090 / pg-simulator:8082 + `docker/infra-compose.yml`)으로 전 구간을 실측했다.

**시나리오**: 상품 3개에 서로 다른 신호를 넣고 랭킹 순서·스코어를 손계산과 대조.

| 상품 | 신호 | 기대 스코어 | 실측 |
| --- | --- | --- | --- |
| P1 (30,000원) | 조회3 + 좋아요 + 결제완료 주문 1건 | `0.3 + 0.2 + 0.6·log10(30001)` = 3.1862814 | **3.1862814385766738** ✅ |
| P2 (20,000원) | 조회5 | 0.5 | **0.5** ✅ |
| P3 (10,000원) | 조회1 + 좋아요 + 취소 | 0.1 | **−0.1** ❌ → §2.1 중복 발행 |

- 주문 결제는 pg-simulator 콜백으로 `PAID` 확정까지 실제로 태웠고, P1 스코어가 손계산과 소수점까지 일치해 **`unitPrice` 전달 + log 정규화**가 실동작함을 확인했다.
- 랭킹 API가 순위·브랜드명·좋아요수를 배치 조회로 조립(§2.3), 상품 상세 `rank`도 노출 확인.
- 키 `ranking:all:20260717`, `TTL 172751s`(≈2일) — 스펙대로.
- P3의 어긋남이 §2.1의 멱등 개정으로 이어졌다. **E2E가 아니었으면 단위·통합 테스트만으론 절대 못 잡았을 결함**이다 — 두 테스트 모두 "이벤트는 한 번만 온다"를 전제로 짜여 있었기 때문이다.

### 4.1 자동화된 조회 경로 E2E (`RankingV1ApiE2ETest`)

위 수동 E2E는 **적재까지 포함한 전 구간**(이벤트 발행 → Kafka → ZSET)을 한 번 꿴 기록이고, 조회 경로는 그 뒤 `RankingV1ApiE2ETest`로 자동화했다 — ZSET을 직접 시드해 API 응답을 검증하므로 Kafka 없이 결정적으로 돈다. 아래는 손으로 다시 확인할 필요가 없다.

| 검증 | 케이스 |
| --- | --- |
| 상품정보 Aggregation | 순위·이름·가격·브랜드명·좋아요수·스코어가 조합되어 반환(ID만 주지 않음) |
| 일자 파라미터 | `date=어제`로 이전 날짜 랭킹 조회 + 오늘 페이지에 어제 것이 섞이지 않음(버킷 분리) |
| 페이지네이션 | `page`/`size`로 나눠도 순위가 이어짐(1,2 → 3), `totalCount` 유지 |
| 빈 날짜 | 랭킹 없는 날짜는 빈 목록 + `totalCount=0` |
| 상품 상세 rank | 랭킹에 오르면 순위 반환 / **없으면 null**(미등재·빈 랭킹) |
| 상품 상세 추세 | `rank`+`rankYesterday` 조합으로 상승·하락·신규진입·이탈 4가지 상태 판별 |

**상세의 `rank`는 "오늘 뜨는가"다.** `LocalDate.now(KST)` 고정이라 자정을 넘기면 어제 1위도 `rank=null`이 된다 — 랭킹 페이지가 `date`로 과거를 보는 것과 대비되는 의도된 비대칭이다. 다만 그것만으로는 **"어제 1위였다가 오늘 이탈"과 "이틀간 랭킹 무관"이 구분되지 않아**(둘 다 null), 상세에 `rankYesterday`를 함께 준다.

가중치가 **순서**로 이어지는지는 `RankingScorePolicyTest`의 상대 크기 테스트로 못 박았다(주문 1건 > 좋아요 3건, 주문 1건 > 조회 20건, 좋아요 1건 > 조회 1건). 개별 가산치만 검증하면 상수를 잘못 바꿔도 통과하지만, 상대 크기는 신호 간 서열이 뒤집히는 순간 깨진다.
