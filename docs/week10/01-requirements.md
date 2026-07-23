# Requirements — 주간·월간 랭킹 (Batch & Materialized View)

`docs/week9`에서 만든 일간 실시간 랭킹(Kafka → Redis ZSET) 위에 배치 집계 레이어를 얹는다. 공통 정책은 `docs/week2/01-requirements.md`를 따른다.

## 문제 정의 — 읽을 "하루치"가 없다

Quest는 `product_metrics`가 하루치 메트릭 테이블이라고 전제하지만, R7에서 만든 실물은 그게 아니다.

```java
@Id @Column(name = "product_id") private Long productId;   // PK = 상품당 1행
private long salesCount, likeCount, viewCount;             // 서비스 시작 이래 누적
```

날짜 컬럼이 없다. "7월 15일의 판매량"을 뽑아낼 방법이 없으니 주간·월간 집계가 원천적으로 불가능하다.

조회 쪽도 문제다. 주간 랭킹을 요청마다 계산하면 7일치를 `GROUP BY`하고 정렬해야 한다. 랭킹은 조회가 압도적으로 많은 지면이라 그대로 두면 DB가 무너진다. 비싼 계산을 새벽에 한 번 해두고 낮에는 읽기만 하는 구조가 필요하다 — Materialized View다.

## 건드리지 않는 것

R9가 만든 일간 랭킹 기계는 그대로 둔다.

| 구성 | 역할 | 이번 주 변경 |
| --- | --- | --- |
| 랭킹 컨슈머 → Redis ZSET | 오늘의 실시간 랭킹 | 가중치를 설정 주입으로, `occurredAt` null 가드 추가 |
| 23:50 carry-over | 내일 판에 오늘 점수 ×0.1 심기 | 없음 |
| 00:30 finalize → `ranking_snapshot` | 어제 ZSET을 DB에 확정 | 없음 |
| `rebuild` 엔드포인트 | Redis 소실 시 snapshot으로 오늘 판 복구 | 없음 |
| `GET /rankings?date=` 일간 조회 | ZSET에서 오늘·어제 서빙 | `period` 파라미터만 추가 |

`ranking_snapshot`은 Redis 재해 복구 전용이다. 평소 carry-over는 Redis에서 Redis로 복사(`ZUNIONSTORE`)하는데 Redis가 통째로 날아가면 복사할 원본이 없고, 그때 MySQL에 남은 snapshot이 유일한 복구 재료가 된다. MV와 겹치지 않는다 — MV가 잘못되면 `product_metrics`가 남아 있으니 배치를 다시 돌리면 그만이다.

## 전체 그림

```
[실시간]  Kafka ─┬─→ Redis ZSET           (일간, 오늘·어제, TTL 2일)
                 └─→ product_metrics      (일별 원시 카운트)

[새벽]    product_metrics ─→ weeklyRankingJob  ─→ mv_product_rank_weekly
                          └→ monthlyRankingJob ─→ mv_product_rank_monthly

[조회]    period=DAILY            → ZSET   (R9 그대로)
          period=WEEKLY|MONTHLY   → MV
```

두 갈래는 경쟁하지 않는다. ZSET은 "지금 뜨는 것", MV는 "지난 주·지난 달 인기" — 서로 다른 질문에 답한다.

## 저장 구조 — `product_metrics` 일별화

`stat_date`를 PK에 넣어 복합키로 바꾼다. 상품당 1행이던 것이 상품×일자 1행이 된다.

```
product_metrics
  PK (stat_date, product_id)
  sales_count / like_count / view_count
  updated_at
```

| 대안 | 판단 |
| --- | --- |
| **`stat_date`를 PK에 추가 (택)** | 누적은 일별의 `SUM`으로 유도되는 파생값이라 따로 저장할 이유가 없다 |
| `product_metrics_daily` 신설, 누적 테이블 유지 | 모든 이벤트를 이중 기록해야 하고 실패 원자성·재처리 정책이 두 벌이 된다 |
| `ranking_snapshot` 재활용 | score에 carry-over가 섞여 있고 원시 카운트가 없어 가중치를 바꾼 재집계가 불가능하다 |

기존 누적 데이터는 날짜가 없어 일별로 쪼갤 수 없다. 특정 날짜에 몰아넣으면 그날 랭킹만 망가지므로 `product_metrics_legacy`로 rename해 보존하고 새 스키마로 새로 쌓는다. 이 테이블을 읽는 코드는 streamer 쓰기 경로뿐이라 다른 소비자를 깨뜨리지 않는다.

### 이벤트 시각이 없다 — 이번 주 실제 작업량

`stat_date`를 정하려면 이벤트가 언제 일어났는지 알아야 하는데, metrics 쪽에는 그 정보가 없다.

| 위치 | 현재 상태 |
| --- | --- |
| `metrics/interfaces/CatalogEventMessage.java` | `occurredAt` **없음** (`eventId, productId, type, delta`뿐) |
| `metrics/interfaces/OrderPaidMessage.java` | `paidAt` 있음 |
| `metrics/application/ProductMetricService.java` | `paidAt`을 받고도 **쓰지 않음** |
| `metrics/infrastructure/ProductMetricJpaRepository.java` | upsert 3개 모두 `NOW()` = **처리시각** |

producer는 이미 보내고 있다. `commerce-api`의 `CatalogEventMessage`는 R9에서 `occurredAt`을 추가했고 팩토리가 항상 채운다. `OrderPaidMessage`도 `paidAt`을 싣는다. 값은 토픽에 실려 다니는데 metrics 쪽 DTO가 그 필드를 선언하지 않아 Jackson이 버리고 있을 뿐이다. producer 변경도 배포 순서 고려도 필요 없다.

바뀌는 곳은 streamer 안쪽 다섯이다.

1. metrics `CatalogEventMessage`에 `occurredAt` 필드 추가 — 선언만 하면 값이 들어온다
2. 컨슈머가 `occurredAt`/`paidAt`에서 `statDate`(KST) 산출 후 서비스로 전달
3. upsert 3개를 `(stat_date, product_id)` 키로 재작성
4. 엔티티에 복합 PK — `@IdClass`로 확정. `product_id` 단독 조회가 사라져 `@EmbeddedId`의 래핑 이득이 없고 기존 필드 접근을 그대로 둘 수 있다. `ProductMetricId(Long productId, LocalDate statDate)`에 `equals`/`hashCode`/`Serializable`, 리포지토리 제네릭을 `JpaRepository<ProductMetric, ProductMetricId>`로 변경, `findById(productId)`를 쓰던 테스트를 함께 고친다
5. 랭킹 컨슈머 두 곳(`RankingCatalogConsumer`·`RankingOrderConsumer`)과 메트릭 컨슈머 두 곳에 null 가드 추가

날짜 산출은 R9와 같은 규칙이다 — 처리시각이 아니라 이벤트 발생시각(`docs/week9/03-event-time-keying.md`). 자정 직전 이벤트가 자정 넘어 처리돼도 어제 집계에 들어간다.

### 늦게 도착한 이벤트는 그대로 받는다

`occurredAt`으로 버킷팅하므로 5일 전 이벤트는 5일 전 행에 정확히 들어간다. `product_metrics`는 언제 도착하든 옳다.

낡는 건 MV다. 7/20에 계산한 `2026-W29`는 7/22에 도착한 7/16 이벤트를 모른다. 그렇다고 가드를 두어 늦은 이벤트를 버리면 원천이 영구히 틀려진다 — 파생 뷰의 일관성을 지키려고 진실을 망가뜨리는 셈이라 방향이 거꾸로다. 드리프트를 감수하고, 문제가 되면 그 기간을 다시 돌린다.

```bash
--job.name=weeklyRankingJob targetDate=20260716 rerun=1   # W29 재계산
```

`targetDate`가 기간을 정하고 `DELETE + INSERT`가 멱등이라 백필 경로가 이미 확보돼 있다. 이미 성공한 기간이므로 `rerun`으로 식별 파라미터를 바꿔야 한다(「멱등성과 재실행」).

다만 형식이 깨진 이벤트는 늦은 이벤트와 다르다. 날짜를 정할 수 없거나 있지도 않은 날을 가리키는 것은 받으면 안 된다.

| 상황 | 처리 |
| --- | --- |
| `occurredAt`/`paidAt` 없음 | 버킷을 정할 수 없음 → DLT |
| 미래 날짜 (clock skew) | 존재하지 않는 집계에 들어감 → DLT |
| 오래됐지만 유효 | 정상 수용, 해당 `stat_date`에 기록 |

R9 랭킹 컨슈머가 `RankingWindow.classify()`로 이미 같은 판별을 한다. metrics도 그 규칙을 따르되 TOO_OLD는 버리지 않는다 — 랭킹 ZSET은 2일 창 밖이 무의미하지만 배치는 30일 전도 읽는다.

판별은 컨슈머에서 한다. DLT 발행에는 원본 `ConsumerRecord`가 필요한데 그건 컨슈머에만 있고, 서비스가 예외만 던지면 DLT가 아니라 재전달로 흐른다. catalog(`occurredAt`)와 order(`paidAt`) 양쪽 모두 필요하다.

### 겸사겸사 고치는 R9 결함 — null `occurredAt` 무한 재전달

랭킹 컨슈머는 메시지 자체의 null만 검사하고 `occurredAt`은 검사하지 않는다. null이면 `RankingWindow.classify()` 안에서 NPE가 나고, 그게 `consume()` 밖으로 전파되면 `acknowledgment.acknowledge()`에 도달하지 못한다. manual ack + 배치 리스너라 같은 배치가 무한 재전달되고 파티션이 멈춘다.

producer가 항상 값을 채우므로 발생한 적은 없지만 방어가 없다. metrics에 같은 판정을 넣는 김에 랭킹 쪽에도 null 가드를 추가한다 — null은 DLT로 보낸다.

## Materialized View

기간별 상위 150개만 담는다.

```sql
CREATE TABLE mv_product_rank_weekly (
  period_key   VARCHAR(16) NOT NULL,   -- '2026-W30'
  product_id   BIGINT      NOT NULL,
  score        DOUBLE      NOT NULL,
  view_count   BIGINT      NOT NULL,
  like_count   BIGINT      NOT NULL,
  sales_count  BIGINT      NOT NULL,
  created_at   DATETIME    NOT NULL,
  PRIMARY KEY (period_key, product_id),
  KEY idx_period_score (period_key, score DESC, product_id ASC)
);
```

`mv_product_rank_monthly`도 같은 형태이며 `period_key`만 다르다. MySQL 8.0은 내림차순 인덱스를 실제로 지원하므로 `score DESC`가 무시되지 않는다.

**왜 150인가.** 과제 요구는 상위 100이지만 순위 근처 상품이 삭제되거나 노출에서 빠지면 화면에 채울 100이 모자랄 수 있다. 여유분 50을 더 저장한다. 개수는 상수(`RANK_LIMIT = 150`) 하나로 둔다.

**노출 불가 상품은 배치가 거른다.** 집계 SQL이 `products`를 join해 삭제·판매중지를 제외하므로 MV에는 노출 가능한 것만 들어간다. 그래서 조회는 앞에서 자르기만 하면 되고, 여유분 50은 **배치 이후에 빠진 상품**만 감당하면 된다.

조회 쪽에서 거르는 방식도 검토했다가 접었다. 필터와 페이지네이션이 섞이면 offset이 어긋난다 — 2페이지를 요청했을 때 앞 페이지에서 몇 개가 걸러졌는지 알아야 시작 위치가 정해지는데, 그건 상태 없이 풀리지 않는다. 기간당 150행뿐이니 매 조회마다 150행을 통째로 읽고 필터한 뒤 자르면 정확해지지만, 상품 정보 조회가 `size`개에서 150개로 늘어난다. 랭킹은 조회가 압도적으로 많은 지면이라 그 비용을 배치 한 번으로 옮기는 쪽을 택했다.

대가는 배치가 `product_metrics`(streamer 소유)와 `products`(commerce-api 소유)를 가로질러 읽는다는 것이다. 읽기 전용이라 쓰기만큼 무겁지 않고, 노출 상태가 배치 시점으로 고정되는 것도 지난 기간 결산이라는 성격에 맞는다.

**동점 처리.** 가중치가 `0.1/0.2/0.6`이라 score 동점이 흔하다. `score`만으로 정렬하면 실행마다 순서가 흔들리므로 `product_id ASC`를 두 번째 정렬키로 고정하고, 배치가 상위 150을 자르는 기준도 이 순서를 쓴다.

조회는 인덱스를 앞에서부터 읽고 멈춘다. 기간당 150행뿐이라 정렬 부담이 없다. 순위는 조회 결과의 순번이므로 읽을 때 매긴다 — R9 `RankingQueryService`가 이미 `start + i + 1`로 그렇게 한다.

```sql
SELECT * FROM mv_product_rank_weekly
WHERE period_key = '2026-W30' ORDER BY score DESC, product_id ASC LIMIT 20 OFFSET 0;
```

### `period_key` 규격

| period | 형식 | 예 | 기준 |
| --- | --- | --- | --- |
| WEEKLY | `<week-based-year>-'W'<ww>` | `2026-W30` | ISO 8601, 월요일 시작 |
| MONTHLY | `yyyy-MM` | `2026-07` | KST 월 |

**주 형식을 `DateTimeFormatter`의 `yyyy`로 만들면 안 된다.** `yyyy`는 달력 연도라 ISO 주 경계와 어긋난다 — 2025-12-29는 ISO로 `2026-W01`인데 `yyyy`는 `2025`를 준다. 연도는 `WeekFields.ISO.weekBasedYear()`, 주차는 `WeekFields.ISO.weekOfWeekBasedYear()`로 각각 뽑아 조립한다(`getYear()`/`getMonthValue()` 금지). 그 주의 날짜 범위(월~일) 계산도 같은 기준으로 한다.

경계 테스트로 고정한다 — 12월/1월을 걸치는 주, 두 달을 걸치는 주, 윤년 2월, 월의 1일/말일.

### 확정된 지난 기간을 보여준다

주간 랭킹은 끝난 지난 주, 월간 랭킹은 끝난 지난 달을 보여준다. 진행 중인 이번 주를 실시간으로 갱신하지 않는다.

역할이 겹치지 않게 하기 위해서다. "지금 뜨는 것"은 일간 ZSET이 맡고, 주간·월간은 완결된 기간의 결산을 맡는다. 진행 중인 주를 집계하면 주 초에는 표본이 하루치뿐이라 순위가 크게 출렁이는데, 그 실시간성은 일간이 이미 담당한다.

| 랭킹 | 화면에 뜨는 것 | 성질 |
| --- | --- | --- |
| 일간 (ZSET) | 오늘, 지금까지 | 실시간 |
| 주간 (MV) | 지난 주(월~일) 확정본 | 7일 완성, 자동 갱신 없음 |
| 월간 (MV) | 지난 달(1일~말일) 확정본 | 한 달 완성, 자동 갱신 없음 |

덕분에 정기 실행 후에는 MV가 자동으로 갱신되지 않고, 배치도 매일이 아니라 주 1회·월 1회로 준다. 주가 막 바뀐 월요일에 "이번 주가 아직 비어 있다"는 공백 문제도 생기지 않는다. 불변은 아니다 — 늦은 이벤트나 가중치 변경에 따른 **명시적 재집계는 가능**하다(「멱등성과 재실행」).

Job은 `targetDate`가 **속한** 기간을 집계한다. 월요일 새벽에 도는 정기 실행은 `targetDate`로 어제(일요일)를 넘기므로 그 일요일이 속한 주, 곧 지난 주가 대상이 된다.

| Job | 실행 | `targetDate` | 집계 대상 |
| --- | --- | --- | --- |
| weekly | 매주 월요일 새벽 | 어제(일) | 지난 주(월~일) |
| monthly | 매월 1일 새벽 | 어제(말일) | 지난 달(1일~말일) |

**`targetDate`는 필수다. 기본값을 두지 않는다.** 생략을 허용하면 `JobParameters`가 비는데, `SimpleJobRepository`의 완료 가드가 식별 파라미터가 비어 있을 때는 적용되지 않는다.

```java
if (!identifyingJobParameters.isEmpty()
        && (status == BatchStatus.COMPLETED || status == BatchStatus.ABANDONED)) {
```

그래서 두 번째 실행이 같은 `JobInstance`를 재사용하고, 두 Step 모두 이전 실행에서 `COMPLETED`라 스킵된다 — **2주차부터 아무것도 하지 않고 성공으로 끝난다.** 실패도 로그도 없다. `@StepScope` 안에서 어제를 계산하는 것은 `JobInstance` 식별 시점보다 늦어 소용없다. 외부 스케줄러가 항상 넘긴다.

진행 중인 기간을 집계하는 것은 배치에서 막지 않는다. 조회 계약은 조회 쪽에서 지키고(「Ranking API 확장」), 잘못 만들어진 행은 다음 정기 실행이 같은 `period_key`를 `DELETE`하고 덮어쓴다.

## 배치 Job

### Step 둘 — DELETE, 그리고 Reader / Processor / Writer

```
Step1 (Tasklet)  해당 period_key 를 지운다
Step2 (chunk)    DB 가 집계·정렬·자르기를 한 번에 하고, Reader 가 그 150행을 받는다
                 Processor 가 MV 엔티티로 매핑, Writer 가 INSERT
```

```sql
SELECT m.product_id,
       SUM(m.view_count)  AS view_count,
       SUM(m.like_count)  AS like_count,
       SUM(m.sales_count) AS sales_count,
       SUM(m.view_count) * :viewWeight
         + SUM(m.like_count)  * :likeWeight
         + SUM(m.sales_count) * :orderWeight AS score
FROM product_metrics m
JOIN products p ON p.id = m.product_id
WHERE m.stat_date BETWEEN :from AND :to
  AND p.deleted_at IS NULL
  AND p.status = 'ON_SALE'
GROUP BY m.product_id
ORDER BY score DESC, product_id ASC
LIMIT 150
```

가중치는 SQL에 박지 않고 바인딩 파라미터로 주입한다. 값은 「점수 정책 공유」에서 설정으로 관리한다.

### Reader — 커서를 기본으로, 페이징도 구현

`JdbcCursorItemReader`를 기본으로 쓴다. 이유는 위 쿼리의 `LIMIT 150` 한 줄이다.

`JdbcPagingItemReader`는 완성된 SQL을 받지 않는다. select/from/where/group/sortKeys 조각만 받아 `PagingQueryProvider`가 조립하고, `LIMIT` 자리는 `pageSize`가 차지한다. "상위 150"을 SQL에 쓸 데가 없어서 `maxItemCount(150)`이라는 별개 노브로 표현해야 한다. (`PagingQueryProvider`를 직접 구현하면 임의 SQL도 가능하지만, 그러면 페이징 자체가 성립하지 않는다.)

| | 커서 | 페이징 |
| --- | --- | --- |
| 150이 사는 곳 | SQL의 `LIMIT 150` | `pageSize` + `maxItemCount` |
| `maxItemCount` 누락 시 | — | 컷이 사라져 전 상품이 들어감. 에러 없음 |
| `pageSize < 150`이면 | — | 페이지마다 집계 쿼리를 다시 실행 |

마지막 줄은 `MySqlPagingQueryProvider`가 `groupClause`가 있을 때 2페이지부터 `SELECT * FROM (...) AS MAIN_QRY` 로 감싸는데 그 서브쿼리에 `LIMIT`이 없기 때문이다. 실제 물리 비용이 매번 전체 재집계인지는 실행계획에 달렸다 — `EXPLAIN ANALYZE`로 확인한다.

동점은 페이징을 배제할 근거가 아니다. `sortKeys`에 `product_id`를 넣으면 `SqlPagingQueryUtils.buildSortConditions`가 `(score < ?) OR (score = ? AND product_id > ?)`로 정확한 keyset 술어를 만들어준다.

위 SQL의 `:viewWeight` 표기는 페이징 기준이다. 커서는 positional `?` + `PreparedStatementSetter`를 쓰므로 두 구현이 파라미터 표기를 달리한다.

**커넥션 점유.** 페이징이 배치의 표준인 건 커서가 Step 내내 커넥션을 잡기 때문이다. 수백만 건을 몇 시간 처리하는 Job이면 장시간 쿼리·네트워크 단절·DB `wait_timeout`·풀 슬롯 장기 점유가 전부 위험이 된다.

우리 Step은 그 상황이 아니다. Step 시간의 대부분은 집계 쿼리인데 그건 페이징도 똑같이 낸다. 게다가 Connector/J는 기본적으로 `ResultSet`을 전부 클라이언트 메모리로 받은 뒤 첫 `next()`를 주므로 `open()` 시점에 서버 쪽 일이 끝난다. 커서가 쓰는 것도 트랜잭션 커넥션이 아니라 풀의 별도 커넥션 하나다(`useSharedExtendedConnection` 기본 `false`라 `DataSourceUtils.getConnection()`이 아니라 `dataSource.getConnection()`을 직접 호출한다).

행 단위로 진짜 스트리밍하려면 `fetchSize = Integer.MIN_VALUE`를, 서버 커서를 쓰려면 `useCursorFetch=true` + 양의 fetch size를 준다. 둘은 다른 기능이고 우리는 어느 쪽도 필요 없다.

**둘 다 구현하는 이유는 이 판단을 실측으로 확인하려는 것이다.** `RankingItemReaderFactory`에 `cursor`/`paging`을 두고 설정으로 전환한다. 페이징 `pageSize`는 청크와 같은 100으로 둔다 — 150이면 1페이지에 끝나 커서와 구분이 안 되고, 100이면 2페이지가 생겨 서브쿼리 재집계 비용이 드러난다. 재는 것은 쿼리 수, Step 시간, 커넥션 점유 시간, 두 리더의 결과 동일성.

`chunkSize`는 100이다. Reader/Processor/Writer는 `@StepScope`로 둔다 — 그래야 `targetDate`가 실행 시점에 늦게 바인딩된다.

### Job은 둘, 구현은 공유

| Job | 스케줄 | 실패 시 영향 |
| --- | --- | --- |
| `weeklyRankingJob` | 매주 월요일 새벽 | 지난 주 랭킹 갱신 안 됨 |
| `monthlyRankingJob` | 매월 1일 새벽 | 지난 달 랭킹 갱신 안 됨 |

로직은 같고 기간 범위, `period_key` 만드는 법, 대상 테이블만 다르다. Job을 나누는 이유는 실패 격리와 이력 식별이다. 하나의 Job에 Step 둘을 넣으면 앞 Step 실패가 뒤를 막고, Spring Batch 메타테이블이 `JOB_NAME`으로 이력을 남기므로 합치면 "주간이 3주 연속 실패 중"을 보려고 `JobParameters`를 파야 한다.

`JobConfig`만 두 벌 두고 `Reader`/`Processor`/`Writer`는 기간을 파라미터로 받는 하나를 공유한다. 기존 `@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ...)` 게이팅을 따른다.

대상 테이블은 파라미터로 넘길 수 없다 — JDBC 플레이스홀더는 값만 바인딩하고 식별자는 못 바인딩한다(`INSERT INTO :table`은 실패). Writer는 weekly/monthly 두 SQL을 enum으로 화이트리스트해 분기한다.

```bash
./gradlew :apps:commerce-batch:bootRun --args='--job.name=weeklyRankingJob targetDate=20260722'
```

**`job.name`은 `--`를 붙이고 `targetDate`는 붙이지 않는다.** 둘이 가는 곳이 다르다. `job.name`은 `application.yml`의 `${job.name:NONE}`이 참조하는 프로퍼티라 `--`가 필요하고, `targetDate`는 JobParameter라 `--`가 없어야 한다. `JobLauncherApplicationRunner`는 `--`가 없는 인자만 `JobParameters`로 변환한다. `--targetDate=...`로 주면 값이 프로퍼티로 가서 `JobParameters`가 비고, 위의 "식별 파라미터가 비면 2주차부터 스킵" 함정에 그대로 빠진다. `targetDate`가 없으면 즉시 실패하도록 검증한다.

### 스케줄 실행 주체

`commerce-batch`는 한 번 실행하고 종료하는 프로세스다. 스케줄러가 앱 안에 없으므로 매주 월요일·매월 1일에 배치를 띄우는 건 외부가 한다 — 운영에선 Jenkins, AWS EventBridge+ECS, K8s CronJob 중 하나로 `job.name`을 지정해 실행한다. 이 저장소 범위 밖이며 로컬 검증은 명령어로 직접 돌린다.

R9의 carry-over·finalize가 `commerce-streamer` 내부 `@Scheduled`인 건 그 앱이 상시 떠 있기 때문이다. 배치는 한 번 돌고 꺼지므로 외부가 띄우는 게 맞다.

### 멱등성과 재실행

`DELETE`는 Step2의 청크 루프 안이 아니라 별도 Tasklet Step으로 둔다. 청크마다 지우면 앞 청크가 넣은 것을 뒤 청크가 지운다.

**재시작 관련 옵션은 붙이지 않는다.** 기본 동작을 그대로 쓴다 — `SimpleStepHandler`가 `COMPLETED`된 Step1을 건너뛰고, Reader는 `saveState=true`라 `read.count`를 복원해 읽던 자리부터 이어간다.

`allowStartIfComplete(true)`와 `saveState(false)`를 검토했다가 뺐다. 둘은 독립된 안전장치가 아니라 "처음부터 전부 다시"를 만드는 한 쌍이고, 하나만 넣으면 오히려 깨진다.

| `allowStartIfComplete` | `saveState` | 첫 청크 커밋 후 실패했을 때 재시작 결과 |
| --- | --- | --- |
| 기본 | 기본 | Step1 스킵, 100부터 이어받음 |
| `true` | 기본 | `DELETE` 했는데 Reader가 100을 건너뜀 → 50행만 남음 |
| 기본 | `false` | 기존 100행이 남은 채 1번부터 INSERT → PK 충돌 |
| `true` | `false` | `DELETE` 후 처음부터 → 전체 재실행 |

기본 조합으로 두면 원천이 그대로일 때는 정확하다. 실패와 재시작 사이에 늦은 이벤트가 도착하면 이미 INSERT된 상위 100이 낡은 값으로 남거나(순위는 그대로고 점수만 바뀐 경우) 재시작이 PK 충돌로 다시 실패할 수 있다. 세 조건이 겹쳐야 하고 원천 손실은 아니라서 감수한다 — 다음 재집계까지 낡을 뿐이고, `rerun`으로 돌리면 `DELETE`부터 다시 한다.

Step2의 트랜잭션 매니저는 실제 DataSource를 쓰는 것을 명시한다. 기존 `DemoJobConfig`는 `ResourcelessTransactionManager`를 쓰는데(`DemoJobConfig:44`), 그대로 복사하면 청크의 JDBC 쓰기가 트랜잭션에 묶이지 않아 위 롤백·재시작이 설명대로 돌지 않는다.

**`RunIdIncrementer`는 쓰지 않는다.** `targetDate`가 이미 식별 파라미터라 매 실행이 다른 `JobInstance`가 되므로 incrementer가 할 일이 없다. 오히려 붙이면 `run.id`가 매번 달라져 아래 두 가드가 약해진다. (`JobLauncherApplicationRunner`는 직전 실행이 `FAILED`/`STOPPED`면 그 식별 파라미터를 재사용하므로 실패 재시작까지 완전히 막히는 것은 아니다.)

| 가드 | 막는 것 |
| --- | --- |
| `JobExecutionAlreadyRunningException` | 같은 식별 파라미터로 두 프로세스가 동시에 도는 것 |
| `JobInstanceAlreadyCompleteException` | 이미 성공한 파라미터로 실수로 다시 도는 것 |

`FAILED`는 어느 가드에도 걸리지 않으므로 실패 후 재실행은 같은 파라미터로 그대로 된다.

의도적인 재집계(늦은 이벤트, 가중치 변경)는 식별 파라미터를 바꿔서 실행한다. `rerun`은 `DefaultJobParametersConverter`가 식별 파라미터로 변환하므로 값을 바꿀 때마다 새 `JobInstance`가 된다 — 두 번째 재집계는 `rerun=2`여야 한다.

```bash
--job.name=weeklyRankingJob targetDate=20260726 rerun=1
```

**가드는 파라미터 단위지 기간 단위가 아니다.** `targetDate=20260726`과 `targetDate=20260726 rerun=1`은 서로 다른 `JobInstance`라 동시에 실행된다. 정기 실행 중에 백필을 띄우면 둘이 같은 `period_key`를 지우고 넣는다. 주 1회 새벽 배치에 사람이 손으로 백필을 치는 상황이라 확률이 낮다고 보고 감수하되, 필요해지면 기간 단위 락을 건다.

가드에 걸리면 `createJobExecution()`이 `JobExecution`을 만들기 **전에** 예외를 던지므로 `JobListener.afterJob`이 호출되지 않는다. 「미결」의 실패 알림은 이 둘을 launcher/프로세스 레벨에서 따로 잡아야 한다.

### 감수하는 것 — 적재 중 조회 공백

Step1의 `DELETE`가 커밋된 뒤 Step2가 끝나기 전까지 그 기간 조회는 비어 보인다. 상위 150행뿐이라 순식간이고 배치가 새벽에 주 1회·월 1회 도는 것이라 감수한다.

Step2가 중간에 실패해 다음 실행까지 반쪽으로 남는 것도 감수한다. 정도는 어디서 실패하느냐에 따라 다르다.

| 실패 지점 | 남는 것 |
| --- | --- |
| 첫 청크 커밋 전 | 0행. 그 기간 조회가 비어 있음 |
| 두 번째 청크 | 100행 = 1~100위. 화면 노출분은 온전하고 여유분 50만 없음 |

두 번째 줄은 Reader가 `score DESC`로 읽어 첫 청크가 정확히 1~100위가 되기 때문인데, **청크 크기 100과 노출 개수 100이 같아서 성립한다.** 청크를 50으로 바꾸면 첫 청크가 1~50위가 되어 깨진다.

## 점수 정책 공유

가중치(view 0.1 / like 0.2 / order 0.6)를 streamer와 batch가 모두 알아야 하는데 `apps`끼리는 서로 의존할 수 없다.

각 앱이 자기 상수를 갖고 값 일치를 테스트로 고정하는 안을 잡았다가 접었다. 복제는 한쪽만 배포되면 조용히 어긋나고, 컴파일러도 테스트도 두 앱에 걸친 불일치는 잡지 못한다.

`ranking.yml`을 공유 위치에 두고 두 앱이 `spring.config.import`로 가져와 `@ConfigurationProperties`로 바인딩한다. 각 앱 리소스에 복사하면 코드 복제가 설정 복제로 바뀔 뿐이라, 공유 위치에 두는 것이 핵심이다.

다만 이건 의도적으로 규칙을 굽히는 결정이다. CLAUDE.md는 `modules`/`supports`가 도메인에 의존하지 않는다고 못박는데, 랭킹 가중치는 인프라 설정이 아니라 도메인 정책이다. 설정 파일만 담는 모듈은 코드 의존이 없어 의존 방향은 깨지 않지만 도메인 지식이 들어가는 건 사실이다. 기존 `modules:jpa` 같은 데 슬쩍 얹지 않고 별도 위치에 두어 그 사실이 드러나게 한다.

## Ranking API 확장

```
GET /api/v1/rankings?period=DAILY&date=20260722&size=20&page=1
```

`period`를 추가한다. 생략 시 `DAILY`라 기존 호출 형태는 그대로 동작한다. `date`는 그 기간을 대표하는 날짜로 해석한다 — `period=WEEKLY&date=20260722`는 7월 22일이 속한 주(`2026-W30`)다. `date`를 생략하면 가장 최근 확정본을 준다.

| period | 소스 | 없을 때 |
| --- | --- | --- |
| DAILY | Redis ZSET (`RankingDatePolicy` 2일 창 그대로) | Redis 장애 시 좋아요순 폴백, `degraded` 플래그 |
| WEEKLY / MONTHLY | MySQL MV | 404 |

**진행 중인 기간인지는 API가 판정한다.** `period_key`를 계산한 뒤 그 기간의 종료일이 오늘보다 이전이 아니면 MV를 조회하지 않고 404를 준다. 배치 쪽에 검증을 두지 않는 이유는 조회 계약을 조회 쪽 한 곳에서 지키기 위해서다 — 배치에 두면 weekly·monthly 두 곳에 흩어지고, 그러고도 MV에 이미 들어간 행은 막지 못한다.

port는 둘로 나눈다 — `RankingRepository`(Redis)와 `RankingMvRepository`(MySQL). 하나로 합치면 서빙 창 검증이나 폴백 같은 일간 전용 정책이 infrastructure로 숨고 시그니처가 둘의 최소공배수로 뭉뚱그려진다. 두 저장소는 성질이 다르다 — 하나는 휘발성이라 폴백이 필요하고, 하나는 영속이라 없으면 그냥 없는 것이다.

어디를 읽을지 정하는 건 순수 규칙이므로(입력 `period`, 출력 `SPEED | BATCH`, I/O 없음) `RankingDatePolicy`와 같은 자리에 정책 객체로 둔다.

## 테스트 데이터 — 배치를 돌릴 게 없다

현재 DB는 전부 더미이고 스키마를 바꾸면 `product_metrics`는 0일치에서 시작한다. 주간 배치를 돌리려면 최소 7일, 월간은 30일치 과거가 필요한데 기다릴 수 없다. 시드는 편의 기능이 아니라 선행 조건이다.

| 대상 | 방법 |
| --- | --- |
| brand / product | 기존 admin 엔드포인트 (`POST /api/v1/admin/brands`, `/admin/products`, 헤더 `X-Loopers-Admin-Id: admin`) |
| `product_metrics` 일별 | 신규 — commerce-streamer에 시드 엔드포인트 |

`product_metrics`의 소유는 streamer다. commerce-api가 남의 앱 테이블에 쓰면 R7에서 미러 3개를 걷어내며 정리한 경계가 다시 무너진다. streamer엔 이미 `RankingRebuildController`·`DlqV1Controller`가 있으니 같은 자리에 둔다.

```
POST /admin/metrics/seed
  { "from": "20260701", "to": "20260722", "productIds": [...] }
```

상품마다 인기 편차를 줘야 순위가 의미를 갖는다. 전 상품이 균등하면 상위권이 사실상 무작위라 검증이 안 된다. 상품별 기본 인기도에 일별 변동을 얹는다.

이 엔드포인트는 실제 지표를 덮어쓰므로 `@Profile("local")`로 막는다. 자동 검증은 Testcontainers 통합테스트에서 데이터를 직접 넣어 Job을 돌리므로(`test` 프로파일) 엔드포인트에 의존하지 않는다. 수동 검증은 `http/commerce-streamer/metrics-seed.http`, `http/commerce-api/ranking-v1.http`에 남긴다.

### 테이블 만들기

`ddl-auto`는 `local`도 `none`이라(테스트만 `create`) 새 테이블 — MV 2개와 바뀐 `product_metrics` — 이 자동으로 생기지 않는다.

1. `ddl-auto`를 `create`로 잠깐 바꿔 앱을 한 번 띄운다 → 엔티티 기준으로 테이블 생성
2. `none`으로 되돌린다
3. 시드로 더미 데이터를 넣고 배치를 돌려 확인

2번을 건너뛰면 다음 기동 때 `create`가 테이블을 다시 비워 시드가 사라진다. 프로덕션 DDL 관리(Flyway 등)는 이 과제 범위 밖이다.

## 되돌린 결정

**score를 `product_metrics`에 저장하려다 접었다.** 가중치 복제를 피하려는 의도였다. 접은 이유 둘 — 파생값을 데이터에 박으면 가중치가 박제돼 과거를 새 기준으로 재집계할 수 없고, 쓰기 시점 누적은 `0.1`을 수백만 번 더해 부동소수점 오차가 쌓인다. 배치가 정수 `SUM` 후 곱셈을 한 번만 하면 오차가 없다.

**일간까지 MV로 옮기고 `ranking_snapshot`을 은퇴시키려다 접었다.** "일·주·월이 한 원천에서 나와야 한다"는 논리였는데 두 겹으로 틀렸다. `product_metrics`는 재전달 시 중복 누적되는 best-effort 지표라 "정확한 단일 계보"가 애초에 달성 가능한 목표가 아니었다. 그리고 라우팅을 "오늘만 ZSET"으로 바꾸면 어제 일간 조회가 신규 배치의 성공 여부에 종속된다 — R9에서 멀쩡히 동작하던 경로의 회귀다.

**한때 MV에 전 상품을 저장하려 했다.** Top-N을 뽑으려면 "전부 본 뒤 상위를 골라내는" 힙이 필요하고 그 상태가 청크를 가로질러 살아남아야 한다고 봤기 때문이다. 그런데 `ORDER BY score DESC LIMIT 150` 한 줄이면 DB가 해주므로 힙도 청크를 넘나드는 상태도 필요 없다. 전체를 저장하면 MV가 상품 수만큼 부풀기도 해서 상위 150만 저장하는 쪽으로 돌아왔다.

## 미결

- [ ] 집계 쿼리 성능 — PK가 `(stat_date, product_id)`라 `GROUP BY product_id`가 정렬을 유발한다. 대량 데이터로 `EXPLAIN`을 떠서 디스크 temp table로 떨어지는지 확인하고, 필요하면 커버링 인덱스를 추가한다. 구현 전에 잰다
- [ ] `ranking.yml`을 둘 모듈 — "공유 위치"만 정하고 어디인지는 안 정했다. `modules` 아래 신설할지, 기존 위치에 얹을지
- [ ] score 수식 중복 — 가중치 값은 `ranking.yml`로 공유하지만 "가중합"이라는 형태는 streamer Java와 batch SQL 두 곳에 있다. 한쪽만 바뀌어도 감지되지 않는다
- [ ] 소비 지연 watermark — 월요일 새벽 실행 시점에 일요일 이벤트가 모두 반영됐다는 보장이 없다. Kafka lag가 남아 있으면 첫 확정본부터 낡는다. 실행 전 lag 확인, 실행 시각 늦추기, 재집계로 흡수 중 하나를 정한다
- [ ] MV 보존 기간 — 지난 기간 `period_key`가 계속 쌓인다. 언젠가 정리 배치가 필요하나 당장은 아니다
- [ ] 배치 실패 알림 — `JobListener.afterJob`에 `getStatus() == FAILED` 분기 추가 (Slack appender는 이미 붙어 있음)
