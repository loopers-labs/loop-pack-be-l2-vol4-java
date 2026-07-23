# 04. Redis 데이터 모델 — 실시간 랭킹 (ZSET)

[`01-requirements.md`](./01-requirements.md) §3·§4와 [`02-sequence-diagrams.md`](./02-sequence-diagrams.md)의 Redis 조작을 키·스코어 수준으로 확정한다. 이 주차는 RDB 테이블을 추가하지 않는다(week7 ERD 불변). 랭킹 상태는 전부 Redis ZSET에 둔다.

## 0. 전제 — 기존 Redis 인프라

`modules/redis/config/redis/RedisConfig.java` 기준:

- **Master-Replica 정적 구성** (`RedisStaticMasterReplicaConfiguration`).
- `defaultRedisTemplate`(`@Primary`) → `ReadFrom.REPLICA_PREFERRED`. **쓰기는 Lettuce가 자동으로 master로 라우팅**하고, 읽기만 복제본 우선.
- 직렬화: 키/값 모두 `StringRedisSerializer`(`RedisTemplate<String,String>`).

> **템플릿 선택**: 랭킹은 근사값을 허용하므로(NFR-1) 쓰기·읽기 모두 `defaultRedisTemplate`을 쓴다. `ZINCRBY`는 master로 라우팅되고, `ZREVRANGE`/`ZREVRANK`는 복제본 우선(소폭 stale 허용). 대기열(week8)처럼 정합성이 결정적인 경우와 달리, 랭킹은 replica 지연을 감수한다.

---

## 1. 키 설계

| 키 | 자료구조 | member | score | TTL | 용도 |
| --- | --- | --- | --- | --- | --- |
| `ranking:all:{yyyyMMdd}` | ZSET | `productId`(문자열) | 누적 가중 점수(double) | **2 Day** | 일간 전체 상품 랭킹 |

- **일자**: `yyyyMMdd`(KST). 이벤트 `occurredAt`을 KST로 환산한 날짜(`RankingKey.dateOf`).
- **네임스페이스**: `ranking:all:` 접두어. Nice-to-Have(시간 단위·카테고리별)는 `ranking:all:{yyyyMMddHH}`, `ranking:{category}:{yyyyMMdd}`로 확장 가능.
- **앱 경계 계약**: 쓰기(streamer)·읽기(api)가 코드를 공유하지 않으므로 **키 포맷 문자열이 유일한 계약**이다. 양쪽 `RankingKey`가 동일 포맷(`ranking:all:` + `BASIC_ISO_DATE`)을 생성해야 한다.

### 1.1 TTL 정책

- ZSET에 쓸 때마다 `EXPIRE key 2d`로 갱신한다(일간 키라 그 날 마지막 쓰기 기준 2일 뒤 만료).
- 효과: **오늘 + 어제** 두 개의 일간 랭킹이 항상 조회 가능. 과거 데이터는 자동 회수(NFR-4).

---

## 2. 연산

### 2.1 적재 (commerce-streamer)

배치 내 상품별 합산 델타를 파이프라인으로 반영한다.

```
PIPELINE
  ZINCRBY ranking:all:20260714 <scoreDelta> <productId>   // 상품마다 1회(coalesced)
  ...
  EXPIRE  ranking:all:20260714 172800                     // 키마다 1회 (2d)
EXEC
```

- `ZINCRBY`로 누적(원자 가산). 좋아요 취소는 음수 델타로 감점.
- 파이프라인으로 배치 왕복(RTT)을 1회로 압축.

### 2.2 조회 (commerce-api)

| API 동작 | Redis 연산 | 비고 |
| --- | --- | --- |
| 페이지 조회 | `ZREVRANGE key (page-1)*size … WITHSCORES` | 내림차순 상위. rank = offset + index + 1 |
| 전체 건수 | `ZCARD key` | 페이지네이션 total |
| 상품 순위 | `ZREVRANK key member` | 0-based → +1 (1-based). 없으면 null |

- `ZREVRANGE`는 순서(내림차순)를 보존하므로 반환 순서대로 rank를 매긴다.
- `page`는 1-based(API 계약). 0-based offset = `(page-1) × size`.

---

## 3. 스코어 계산식 (`RankingScorePolicy`)

최종 가산치 = **Weight × Score**.

```
조회   : +VIEW_WEIGHT               = +0.1
좋아요 : +LIKE_WEIGHT × delta       = ±0.2         (delta = ±1)
주문   : +ORDER_WEIGHT × log10(1 + unitPrice×quantity)
        = +0.6 × log10(1 + 매출)                    (매출 ≤ 0 이면 0)
```

**스케일 감각 (예시)**

| 신호 | 입력 | 가산치 |
| --- | --- | --- |
| 조회 1회 | — | +0.1 |
| 좋아요 +1 | delta=+1 | +0.2 |
| 좋아요 취소 | delta=-1 | -0.2 |
| 주문 (10,000원 × 2) | 매출 20,000 | +0.6 × log10(20,001) ≈ **+2.58** |
| 주문 (1,000,000원 × 1) | 매출 1,000,000 | +0.6 × log10(1,000,001) ≈ **+3.60** |

> log 정규화 덕에 매출이 100배(2만→100만) 늘어도 가산치는 ~1.4배만 증가한다 → 고가 1건이 조회·좋아요 신호를 삼키지 않는다(D1).

---

## 4. 주문 매출을 위한 이벤트 계약 변경

랭킹 주문 스코어(`price×amount`)를 위해 `ORDER_PAID` payload에 **`unitPrice`** 를 추가했다.

```jsonc
// order-events / ORDER_PAID payload
{
  "orderId": 100,
  "items": [
    { "productId": 30, "quantity": 2, "unitPrice": 10000 }  // unitPrice = week9 추가
  ]
}
```

- **하위 호환**: `product_metrics` 집계 컨슈머는 payload를 `JsonNode`로 읽어 `productId`/`quantity`만 사용하므로 `unitPrice` 추가에 무영향. 구(舊) 이벤트에 `unitPrice`가 없으면 랭킹은 0으로 처리(가산 없음).
- 원본 `OrderPaidEvent.Item(productId, quantity)` 계약은 주석으로 보존.

---

## 5. TTL 밖 과거 랭킹 — 영속 스냅샷

§1.1의 TTL 2일은 **저장 비용 상한을 위한 의도된 결정**이다(NFR-4). 그 대가로 3일 전 랭킹은 조회할 수 없다. 이를 보존하기 위해 확정된 하루치 상위 N을 DB로 내린다.

> **먼저 짚을 것**: 단순히 "과거를 더 오래 보고 싶다"가 목적이라면 **TTL 연장(`Duration.ofDays(N)` 한 줄)이 압도적으로 싸다.** ZSET은 그날 활동이 있었던 상품만 담으므로(전체 상품이 아니라) 30일 보관해도 수십 MB 수준이다. 스냅샷은 배치·테이블·이중 읽기 경로를 추가하는 대가를 치른다. 그 대가는 **ZSET이 줄 수 없는 것**으로 정당화된다 — SQL 조인·집계(분석 쿼리), 수개월 이상 장기 보관, Redis 유실 시 복구 재료.

### 5.1 테이블

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `ranking_date` | DATE | PK ①. 스냅샷 대상 일자(KST) |
| `product_id` | BIGINT | PK ②. 같은 날짜 중복 적재 방지 |
| `rank_no` | INT | 적재 시점에 확정된 1-based 순위. `rank`는 MySQL 8 예약어(RANK() 윈도우 함수) |
| `score` | DOUBLE | ZSET score 그대로 |
| `created_at` | DATETIME(6) | 적재 시각 |

- **인덱스** `idx_rds_date_rank (ranking_date, rank_no)` — PK가 `(ranking_date, product_id)`라 순위 정렬을 타지 못한다. 커버링은 노리지 않는다(페이지당 최대 size행이라 lookup 비용이 무의미).
- **소유**: 쓰기 `commerce-batch`(JdbcTemplate) / 스키마·읽기 `commerce-api`(`RankingSnapshotEntity`). `product_metrics`를 streamer가 쓰는 앱 경계 패턴과 동일.
- DDL: local/test는 ddl-auto:create + `import.sql`(인덱스), 운영은 [`migration_ranking_snapshot.sql`](./migration_ranking_snapshot.sql).

### 5.2 적재 (commerce-batch `rankingSnapshotJob`)

```
ZREVRANGE ranking:all:{어제} 0 (N-1) WITHSCORES   -- 상위 N만
  ↓
DELETE FROM ranking_daily_snapshot WHERE ranking_date = {어제}
INSERT INTO ranking_daily_snapshot ... (batch)
```

- **대상은 어제** — 오늘 랭킹은 아직 계속 변한다. 확정된 날짜만 찍는다. 어제 키는 TTL(2일) 안이라 자정 직후에도 살아있다.
- **상위 N만**(`ranking.snapshot.top-n`, 기본 100). 과거 500위를 되짚는 수요는 없다고 보고 전량 적재를 포기했다 → **과거 날짜의 `totalCount`는 실제 그날 랭킹 크기가 아니라 보존된 행 수**(최대 N)다.
- **멱등**: delete-then-insert. 같은 날짜로 몇 번을 돌려도 같은 상태로 수렴한다(배치 재시도·수동 재실행 안전).

### 5.3 조회 — 날짜로 소스를 고른다

`RankingCompositeRepository`(포트의 유일한 구현)가 분기한다. 응용 계층은 출처를 모른다.

| 날짜 | 소스 |
| --- | --- |
| ZSET에 존재(오늘·어제) | Redis — 실시간 |
| ZSET에 없음(TTL 만료) | 스냅샷 DB |

> **페이지 단위로 폴백하면 버그다.** "Redis 결과가 비면 스냅샷"으로 짜면, 그 날짜가 ZSET에 살아있는데 범위 밖 페이지를 요청했을 때(3건뿐인데 `page=10`) 빈 결과를 보고 스냅샷으로 넘어가 **같은 날짜인데 두 소스가 섞인다.** 그래서 `ZCARD > 0`으로 날짜의 소스를 확정한다.
>
> 다만 ZCARD를 **먼저** 던지면 정상 경로(Redis 히트)의 왕복이 하나 늘므로, `ZREVRANGE`를 먼저 하고 **결과가 비었을 때만** ZCARD로 "날짜가 없는 것"과 "페이지가 범위 밖인 것"을 가른다 → 정상 경로 비용은 기존과 동일하다.

`findRank`(상품 상세)는 **폴백하지 않는다.** 상세는 오늘/어제만 조회하고 그 두 날짜는 항상 TTL 안이다. 폴백을 넣으면 랭킹에 없는 상품을 조회할 때마다 헛된 DB 조회가 2회씩 hot path에 생긴다.

### 5.4 한계

- **스냅샷 테이블은 TTL이 없어 자동으로 줄지 않는다.** ZSET의 TTL이 하던 회수 역할을 대신할 주체가 없다. 상위 100 × 365일 ≈ 36,500행/년이라 당장은 문제없지만, 무한 증가는 정책이 아니다 — 보관 기간을 정해 주기 삭제를 붙여야 한다(migration SQL에 쿼리만 적어둠).
- **배치가 안 돌면 그날 랭킹은 TTL과 함께 영영 사라진다.** 배치 실패가 **조용한 데이터 유실**이 된다. 스케줄러 + 실패 알림이 필요하다(현재 수동 실행).
