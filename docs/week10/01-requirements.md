# week10 — 주간/월간 랭킹 (Spring Batch + Materialized View)

## 1. 과제 요구사항

| 구분 | 요구 |
|---|---|
| Spring Batch | Job 을 작성하고 **파라미터 기반**으로 동작시킬 수 있다 |
| | **Chunk-Oriented**(Reader/Processor/Writer) 기반 배치 처리 |
| Materialized View | 집계 결과를 조회 전용 테이블에 적재 — `mv_product_rank_weekly`(주간 TOP 100), `mv_product_rank_monthly`(월간 TOP 100) |
| Ranking API | 기존 `GET /api/v1/rankings` 를 확장해 **일간·주간·월간** 제공 |

## 2. 출발점에서의 문제 — 집계할 원천이 없었다

과제 지문은 "이전에 적재했던 `product_metrics` 와 같은 **일간 집계정보**를 기반으로"라고 하지만,
이 프로젝트의 `product_metrics` 는 **일간 집계가 아니라 누적 스냅샷**이다.

```java
@Table(name = "product_metrics")
@Id @Column(name = "product_id")   // PK 가 product_id 단독 — product 와 1:1
private Long productId;
private Long likeCount, salesCount, viewCount;  // 전체 누적 총합 (+= 만)
```

"2월 3일의 조회수"를 알 수 없고 "지금까지의 총 조회수"만 안다. 이 상태로는 **임의 구간 집계가 원천적으로 불가능**하다.

### 검토한 대안

| 안 | 판단 |
|---|---|
| `product_metrics` 에 날짜 컬럼 추가 | ❌ 아래 §3 참고 |
| `ranking_daily_snapshot`(week9) 재사용 | ⚠️ 일자 차원은 있으나 **하루 TOP 100 만** 저장 → 매일 101위권이던 상품이 주간 집계에서 통째로 빠진다. 읽는 행도 주간 최대 700행이라 "대량 처리" 요구와 어긋난다 |
| **`product_metrics_daily` 신설** | ✅ 채택 |

## 3. 왜 `product_metrics` 에 날짜를 넣지 않았나

`product_metrics` 는 **상품 리스팅 read model** 이다. 날짜를 PK 에 추가하면 상품당 N 행이 되어,
리스팅 쿼리가 이렇게 바뀐다.

```sql
-- 현재 (상품당 1행)
SELECT ... FROM product_metrics
 WHERE deleted_at IS NULL AND brand_id = ?
 ORDER BY like_count DESC, product_id DESC LIMIT 20;
-- → idx_pm_brand_active_likes_desc 인덱스만 훑고 끝

-- 날짜 추가 시 (상품당 N행)
SELECT product_id, SUM(like_count) lc FROM product_metrics
 WHERE deleted_at IS NULL AND brand_id = ?
 GROUP BY product_id
 ORDER BY lc DESC, product_id DESC LIMIT 20;
-- → 전체 GROUP BY 후 정렬. 인덱스로 상위 20만 끊어낼 수 없다
```

week5 에서 213ms → 0.12ms 로 만든 최적화가 통째로 무효가 되고, **키셋 페이지네이션 커서도 깨진다**
(커서가 `(like_count, id)` 좌표를 잡는데 집계값에는 커서를 걸 수 없다).
추가로 `brand_id`/`price`/`deleted_at`(commerce-api 소유 차원)이 날짜 행마다 중복 저장된다.

> **리스팅(상품당 1행·현재값·인덱스 정렬)과 기간 랭킹(상품×날짜 N행·구간 합산)은 요구 형태가 상반된다.**
> 읽기 모델을 용도별로 분리하는 것이 CQRS 의 정석이고, `ranking_daily_snapshot` 을 따로 둔 것과 같은 판단이다.

## 4. 확정한 설계 결정

### 4.1 기간 경계 — 주간은 ISO-8601 월요일 시작

`LocalDate.with(DayOfWeek.MONDAY)`. 일요일 시작(미국식)과 갈리는 지점이라 배치(`RankingPeriodType`)와
API(`RankingPeriod`) **양쪽이 같은 규칙**을 가져야 한다. 한쪽만 바꾸면 배치가 적재한 `period_start` 로
조회가 안 되어 **영영 빈 결과**가 된다.

### 4.2 확정된 기간만 노출한다

배치는 파라미터 없이 돌리면 **직전 확정 기간**(지난주/지난달)을 집계한다.

**왜**: 진행 중인 주를 집계하면 순위가 매일 뒤집히고, 조회하는 쪽은 그것이 확정값인지 중간값인지 알 수 없다.
"이번 주 랭킹"이라는 이름으로 매일 다른 답을 주는 것보다, "지난주 랭킹"이 확정값인 편이 해석이 명확하다.

**대가**: 이번 주/이번 달을 요청하면 **빈 결과**다. 데모나 시연에서 주의가 필요하다.
필요하면 `baseDate` 파라미터로 과거 기간을 지정하거나, 배치를 매일 돌려 진행 중 구간을 갱신할 수 있다
(그 경우 위의 "확정값" 성질을 포기하는 것이다 — 선택의 문제이지 버그가 아니다).

### 4.3 스코어는 일간과 동일한 체계를 유지한다

`RankingScorePolicy`(week9)의 가중치를 그대로 쓴다 — 조회 0.1 / 좋아요 0.2×delta / 주문 0.6×log10(1+매출).

주의점: **`log` 는 합과 교환되지 않는다**(`Σ log(xᵢ) ≠ log(Σ xᵢ)`).
배치가 `SUM(sales_amount)` 에 `log10` 을 적용하면 실시간 일간 랭킹(주문 **건별** log)과 산식이 달라져
일간과 주간이 서로 다른 스케일이 된다. 그래서 **건별 스코어를 `order_score` 에 미리 더해 적재**하고
기간 집계는 단순 `SUM` 으로 끝낸다. 원자료(`sales_amount`)는 정책 변경 시 재계산할 수 있게 함께 남긴다.

### 4.4 TOP 100 상한과 `totalCount` 의 의미

MV 에는 기간당 상위 100 만 적재한다(`ranking.period.top-n`).
따라서 **주간/월간 응답의 `totalCount` 는 최대 100** 이다 — "그 기간에 활동한 전체 상품 수"가 아니다.
과거 랭킹을 500위까지 되짚는 수요가 없고, 전량 적재는 저장 비용만 늘린다.
(일간은 ZSET `ZCARD` 라 실제 전체 수가 나온다 — 단위별로 의미가 다르다는 점을 문서에 남긴다.)

### 4.5 배치 재실행 안전성

모든 Step 이 멱등이다. 같은 기간으로 몇 번을 돌려도 결과가 같다.

- staging: 집계 전에 해당 `(period_type, period_start)` 를 **지우고 시작**
- MV: `delete-then-insert`, Step 이 한 트랜잭션이라 중간 상태가 외부에 보이지 않음

**실패 시 재시도는 Spring Batch 의 재실행에 맡긴다.** 별도 재시도 로직을 넣지 않는 이유는 멱등이
보장되어 "그냥 다시 돌리면" 되기 때문이다. 스케줄링(주 1회/월 1회)은 애플리케이션 밖(cron·k8s CronJob)의
책임으로 둔다 — 배치 앱은 한 번 실행되고 끝나는 단위여야 재실행·백필이 단순해진다.

## 5. 범위 밖 (후속 과제)

- **`product_metrics_daily` 보관 정책**: 상품수 × 일수로 계속 증가한다. 삭제 SQL 은
  `migration_period_ranking.sql` 에 주석으로 남겼고, 주기 배치화는 하지 않았다.
- **진행 중 기간의 부분 집계**: §4.2 참고.
- **브랜드별/카테고리별 기간 랭킹**: 현재는 전체 랭킹만.
