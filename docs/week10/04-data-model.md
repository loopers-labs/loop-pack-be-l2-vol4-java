# week10 — 데이터 모델

DDL 전문은 [`migration_period_ranking.sql`](./migration_period_ranking.sql).
local/test 는 `ddl-auto: create` + `import.sql` 이 만들고, 이 파일은 설계 근거를 남긴다.

## 1. `product_metrics_daily` — 일자별 지표 (집계 원천)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `metric_date` | DATE | **PK1**. 이벤트 발생시각(KST) 기준 일자 |
| `product_id` | BIGINT | **PK2** |
| `view_count` | BIGINT | 그날 조회 수 |
| `like_delta` | BIGINT | 그날 좋아요 증감. **음수 가능**(취소가 더 많은 날) |
| `sales_count` | BIGINT | 그날 판매 수량 |
| `sales_amount` | BIGINT | 그날 매출액(단가×수량). **원자료** |
| `order_score` | DOUBLE | `Σ 0.6 × log10(1+건별매출)` |

### PK 순서가 `(metric_date, product_id)` 인 이유

배치의 구간 조회가 `WHERE metric_date BETWEEN ? AND ? GROUP BY product_id` 다.
선두 컬럼이 날짜라 **PK 레인지 스캔으로 커버**되어 별도 인덱스가 필요 없다.
순서를 뒤집으면(`product_id, metric_date`) 구간 조회가 전체 스캔이 된다.

### 누적이 아니라 델타를 담는다

`product_metrics` 는 전체 총합을 `+=` 로 유지하지만 이 테이블은 **그날 발생한 양**만 기록한다.
그래야 임의 구간을 `SUM` 으로 합산할 수 있다.

### `like_delta` 에 `GREATEST(0, ...)` 가드를 걸지 않는다

`product_metrics.like_count` 는 "현재 좋아요 수"라 음수가 의미를 갖지 않아 0 으로 눌렀다.
그러나 여기 `like_delta` 는 "그날의 증감"이라 **음수가 정상**이고, 구간 합산 시 그대로 상쇄되어야 한다.

### `order_score` 를 `sales_amount` 와 별도로 두는 이유

`log` 는 합과 교환되지 않는다.

```
Σ log(xᵢ)  ≠  log(Σ xᵢ)
```

배치가 `SUM(sales_amount)` 에 `log10` 을 적용하면 실시간 일간 랭킹(주문 **건별** log)과 산식이 달라져
**일간과 주간이 서로 다른 스케일**이 된다. 이벤트를 볼 수 있는 시점(streamer)에 건별 스코어를 미리 더해
두면 기간 집계는 단순 `SUM` 으로 끝나고, 어느 기간이든 동일한 스코어 정의를 유지한다.

원자료 `sales_amount` 는 정책(가중치·정규화 방식)이 바뀌었을 때 **과거 구간을 재계산**할 수 있게 함께 남긴다.

## 2. `mv_product_rank_weekly` / `_monthly` — 기간 랭킹 MV

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `period_start` | DATE | **PK1**. 기간 식별자 — 주간=월요일, 월간=1일 |
| `product_id` | BIGINT | **PK2** |
| `period_end` | DATE | 기간 종료일(포함) |
| `rank_no` | INT | 배치가 확정한 1-based 순위 |
| `score` | DOUBLE | 구간 합산 스코어 |

보조 인덱스: `(period_start, rank_no)` — PK 로는 순위 정렬을 못 타므로 페이지 조회용으로 필요하다.

### 왜 MV(조회 전용 테이블)인가

요청 시점에 계산하면 매번 `product_metrics_daily` 7~30일치를 `GROUP BY` 하고 전역 정렬해야 한다.
상품 수가 늘면 조회 지연이 그대로 커지고, 같은 결과를 사용자마다 다시 계산하는 낭비가 생긴다.
배치가 하루 한 번 미리 확정해 두면 조회는 인덱스 레인지 스캔 한 번으로 끝난다.

### `rank_no` 를 저장한다 (조회 시 재계산하지 않는다)

조회 시점에 `ROW_NUMBER()` 를 돌리면 페이지마다 전체 정렬이 필요해 MV 를 둔 의미가 사라진다.
배치가 확정한 값을 그대로 읽는다.

### 컬럼명이 `rank` 가 아닌 이유

`rank` 는 MySQL 8 예약어(`RANK()` 윈도우 함수)다. `ranking_daily_snapshot`(week9)에서 이미 밟은 지뢰라
같은 규약(`rank_no`)을 쓴다.

### PK 가 `(period_start, product_id)` — 멱등

같은 기간을 두 번 적재해도 중복 행이 생기지 않는다. 배치는 해당 기간을 지우고 다시 넣으므로
(delete-then-insert) 재실행이 안전하다.

## 3. `product_rank_staging` — Step1 → Step3 중간 적재

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `period_type` | VARCHAR(10) | **PK1**. `WEEKLY` \| `MONTHLY` |
| `period_start` | DATE | **PK2** |
| `product_id` | BIGINT | **PK3** |
| `score` | DOUBLE | 구간 스코어(순위 없음) |

랭킹은 전역 정렬이 필요한데 청크 처리는 스트리밍이라 한 Step 으로 순위를 매길 수 없다.
Step 2 가 전 상품 스코어를 여기 쌓고, Step 3 이 `ROW_NUMBER()` 로 상위 N 을 확정해 MV 로 옮긴다.

`period_type` 을 PK 에 포함해 주간/월간 잡이 같은 테이블을 공유하면서도 **동시 실행 시 서로 침범하지 않는다.**

JPA 엔티티가 아니다(commerce-batch 가 `JdbcTemplate` 로 write) — `event_handled` 와 같은 방식으로
`import.sql` 이 만든다. 배치가 매 실행 시 지우고 쓰므로 누적되지 않는다.

## 4. 테이블 소유권 (쓰기 주체)

| 테이블 | 쓰기 | 읽기 |
|---|---|---|
| `product_metrics` | commerce-streamer(측정값) / commerce-api(차원) | commerce-api |
| `product_metrics_daily` | **commerce-streamer** | **commerce-batch** |
| `product_rank_staging` | **commerce-batch** | commerce-batch |
| `mv_product_rank_*` | **commerce-batch** | **commerce-api** |
| `ranking_daily_snapshot` | commerce-batch | commerce-api |

앱 경계를 넘는 테이블은 **JPA 엔티티를 공유하지 않고** 쓰는 쪽이 `JdbcTemplate` 로 직접 접근한다.
엔티티는 스키마 정의와 테스트 정리(`DatabaseCleanUp` 이 JPA 엔티티만 순회해 TRUNCATE)를 위해
commerce-api 에 둔다.

## 5. 보관 정책 (미구현)

`mv_product_rank_*` 는 기간당 100행이라 방치해도 무해하다(주간 연 5,200행).
그러나 **`product_metrics_daily` 는 상품수 × 일수로 증가**한다 — 상품 10만 개가 매일 활동하면 연 3,650만 행이다.

```sql
DELETE FROM product_metrics_daily WHERE metric_date < CURRENT_DATE - INTERVAL 1 YEAR;
```

월간 랭킹이 최대 31일치만 보므로 실제 필요한 보관 기간은 훨씬 짧다. 다만 과거 기간을 재계산(백필)할
여지를 남기려면 여유를 두는 편이 낫다. **주기 배치화는 이번 범위 밖**이다.
