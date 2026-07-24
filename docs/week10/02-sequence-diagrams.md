# week10 — 시퀀스 다이어그램

## 1. 적재 — 이벤트 소비 시 일자별 지표 누적

`MetricsAggregator` 가 기존 누적 갱신과 **같은 트랜잭션**에서 일자별 델타를 UPSERT 한다.

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka<br/>(catalog/order-events)
    participant C as ProductMetricsConsumer
    participant A as MetricsAggregator
    participant EH as event_handled
    participant PM as product_metrics<br/>(누적)
    participant PMD as product_metrics_daily<br/>(일자별)

    K->>C: 배치 소비 (List<EventEnvelope>)
    C->>A: apply(envelopes)

    Note over A: [1] 배치 내 중복 eventId 제거
    A->>EH: findHandled(group, ids)
    EH-->>A: 이미 처리된 id 집합
    Note over A: [2] fresh 만 남긴다 (멱등)

    loop fresh 이벤트마다
        Note over A: metricDate = RankingKey.dateOf(occurredAt)<br/>← 이벤트 발생시각의 KST 일자
        Note over A: 누적 델타 + (일자,상품) 델타를 동시에 누적<br/>ORDER_PAID 는 건별 orderScore 계산
    end

    A->>PM: applyLikeDeltas / View / Sales (UPDATE +=)
    A->>PMD: applyDailyDeltas<br/>(INSERT ... ON DUPLICATE KEY UPDATE)
    A->>EH: markHandled(group, freshIds)
    Note over A,EH: 여기까지 한 트랜잭션 — 두 테이블이 어긋날 수 없다

    C->>K: acknowledge() (커밋 후에만)
```

**핵심**

- 일자는 **소비 시각이 아니라 이벤트 발생시각**(KST) 기준이다. 소비 시각으로 잡으면 컨슈머 지연이
  자정을 넘길 때 날짜 버킷이 밀린다. 랭킹 ZSET(`RankingKey.dateOf`)과 같은 함수를 재사용해 두 집계를
  대조할 수 있게 한다.
- 행 선생성이 없다. 일자별 행은 그날 첫 이벤트가 와야 존재를 알 수 있어 UPSERT 로 만든다.

## 2. 배치 — 주간/월간 집계 (3-Step)

```mermaid
sequenceDiagram
    autonumber
    participant S as Scheduler<br/>(cron / k8s CronJob)
    participant J as weeklyRankingJob
    participant ST as product_rank_staging
    participant PMD as product_metrics_daily
    participant MV as mv_product_rank_weekly

    S->>J: --job.name=weeklyRankingJob [baseDate=yyyyMMdd]
    Note over J: PeriodRange.resolve()<br/>baseDate 있으면 그 기간, 없으면 직전 확정 기간

    rect rgb(240, 240, 250)
        Note over J,ST: Step 1 — cleanup (Tasklet)
        J->>ST: DELETE WHERE period_type=? AND period_start=?
        Note over ST: 지난 실행에서 필터된 상품이<br/>옛 스코어로 남는 것을 막는다
    end

    rect rgb(235, 250, 240)
        Note over J,ST: Step 2 — aggregate (Chunk, size=500)
        loop 청크마다
            J->>PMD: [Reader] 커서로 구간 집계 스트리밍<br/>SUM(...) GROUP BY product_id
            PMD-->>J: PeriodMetricsSum
            Note over J: [Processor] score 계산<br/>0 이하는 null 반환 → 필터
            J->>ST: [Writer] INSERT (period_type, period_start, product_id, score)
        end
    end

    rect rgb(250, 240, 240)
        Note over J,MV: Step 3 — confirm (Tasklet)
        J->>MV: DELETE WHERE period_start=?
        J->>ST: SELECT ... ROW_NUMBER() OVER (ORDER BY score DESC, product_id DESC) LIMIT 100
        ST-->>J: 순위 확정된 상위 100
        J->>MV: INSERT (period_start, product_id, period_end, rank_no, score)
        J->>ST: DELETE (중간 산출물 회수)
    end
```

**왜 Step 을 셋으로 나눴나**

순위는 **전역 정렬**이라 청크 스트리밍 중에는 "지금 이 아이템이 몇 위인지"를 알 수 없다.
Step 2 가 전 상품 스코어를 확정하고(대량 처리 구간), Step 3 이 `ROW_NUMBER()` 한 문장으로 순위를 매긴다.
정렬·순번은 DB 가 가장 잘하는 일이므로 애플리케이션이 전량을 메모리로 올리지 않는다.

## 3. 조회 — 기간별 랭킹 API

```mermaid
sequenceDiagram
    autonumber
    participant B as Client
    participant CT as RankingV1Controller
    participant F as RankingFacade
    participant RR as RankingRepository<br/>(ZSET + 스냅샷)
    participant PR as PeriodRankingRepository<br/>(MV)
    participant PS as ProductService / BrandService

    B->>CT: GET /rankings?period=WEEKLY&date=20260723&page=1&size=20
    Note over CT: RankingPeriod.from("WEEKLY")<br/>date 생략 시 오늘(KST)
    CT->>F: getRanking(WEEKLY, 20260723, 1, 20)

    alt period == DAILY
        F->>RR: findPage(date, page, size) / size(date)
        Note over RR: 오늘이면 ZSET, 과거면 스냅샷
    else WEEKLY / MONTHLY
        Note over F: periodStart = period.resolveStart(date)<br/>2026-07-23(목) → 2026-07-20(월)
        F->>PR: findPage(period, periodStart, page, size) / size(...)
        Note over PR: (period_start, rank_no) 인덱스<br/>레인지 스캔 + LIMIT
    end

    Note over F: 여기부터는 단위와 무관하게 동일 — assemble()
    F->>PS: findActiveByIds / getLikeCounts / findByIds
    Note over F: N+1 회피 배치 조회<br/>삭제·비활성 상품은 노출 제외
    F-->>CT: RankingPageInfo
    CT-->>B: { period, periodStart, periodEnd, date, totalCount, items[] }
```

**소스 분기는 `period` 하나로 끝난다.** 상품/브랜드/좋아요수를 조합하는 뒷부분은 세 단위가 완전히 같아
`assemble()` 로 공유한다.
