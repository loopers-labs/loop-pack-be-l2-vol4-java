# week10 — 구현 노트

## 1. 실행 방법

```bash
# 주간 — 직전 확정 기간(지난주 월~일)
./gradlew :apps:commerce-batch:bootRun --args='--job.name=weeklyRankingJob'

# 월간 — 지난달 1일~말일
./gradlew :apps:commerce-batch:bootRun --args='--job.name=monthlyRankingJob'

# 특정 기간 재집계(백필) — 그 날짜가 속한 기간
./gradlew :apps:commerce-batch:bootRun \
  --args='--job.name=weeklyRankingJob --baseDate=20260713'
```

설정(`application.yml`):

```yaml
ranking:
  period:
    top-n: 100        # MV 에 남길 상위 N
    chunk-size: 500   # 청크 커밋 단위 = Writer batch 크기
```

조회:

```
GET /api/v1/rankings?period=WEEKLY&date=20260723&page=1&size=20
GET /api/v1/rankings?period=MONTHLY&date=20260723
GET /api/v1/rankings?date=20260723           # period 생략 → DAILY (기존과 동일)
```

## 2. 설계 판단 기록

### 2.1 Reader 는 커서, 페이징이 아니다

`JdbcPagingItemReader` 는 페이지마다 쿼리를 다시 던진다. 그런데 우리 쿼리는 `GROUP BY` 집계라
**매 페이지에서 전체 그룹화를 반복**하게 된다(사실상 O(n²)).
`JdbcCursorItemReader` 는 서버가 만든 결과집합을 한 번만 훑으므로 대량 구간에서도 비용이 선형이다.
배치라 커넥션을 오래 잡는 것은 허용된다.

`verifyCursorPosition(false)` — 커서 위치 검증은 행마다 확인 비용이 들고, ResultSet 을 한 방향으로만
읽으므로 불필요하다.

### 2.2 순위 확정을 청크로 하지 않는 이유

랭킹은 **전역 연산**이다. 청크는 스트리밍이라 "지금 이 아이템이 몇 위인지"를 알 수 없다.
전량을 메모리에 올려 정렬하면 청크를 쓰는 의미가 없어지므로, 정렬·순번은 `ROW_NUMBER()` 한 문장으로
DB 에 맡긴다.

### 2.3 Processor 가 `null` 을 반환하면 필터된다

Spring Batch 는 Processor 가 `null` 을 반환한 아이템을 Writer 로 넘기지 않는다.
스코어 0 이하(좋아요 취소만 쌓였거나 활동이 상쇄된 상품)는 랭킹에 올릴 의미가 없고 staging 행만 늘려
Step 3 의 정렬 비용을 키우므로 여기서 걸러낸다.

### 2.4 cleanup Step 이 따로 있는 이유

staging Writer 의 UPSERT 만으로는 부족하다. **지난 실행에는 있었지만 이번 재집계에서 스코어가 0 이하로
떨어져 필터된 상품이 옛 스코어 그대로 남아** 순위에 끼어든다. 재실행이 항상 같은 결과로 수렴하려면
쓰기 전에 지워야 한다.

### 2.5 MySQL `VALUES()` 를 유지한다

`INSERT ... ON DUPLICATE KEY UPDATE c = c + VALUES(c)` 는 8.0.20 부터 deprecated 지만 여전히 동작한다.
대체 문법(`... AS new ON DUPLICATE KEY UPDATE c = c + new.c`)은 8.0.19+ 를 요구해, 구버전 호환을 위해
현행을 유지했다. MySQL 버전을 고정할 수 있게 되면 별칭 문법으로 옮기는 편이 좋다.

## 3. 검증 상태

### 완료 (Docker 불필요)

| 항목 | 결과 |
|---|---|
| 3개 앱 `compileJava` / `compileTestJava` | ✅ BUILD SUCCESSFUL (JDK21) |
| `MetricsAggregatorTest` | ✅ 일자 KST 버킷(자정 경계 포함), 매출/주문스코어, `unitPrice` 없는 구 이벤트 호환 |
| `RankingPeriodTest` | ✅ 주 경계(월요일 시작·일요일 귀속·연말 주), 월 경계(30일 달·윤년 2월), `from()` 파싱 |
| `PeriodRangeTest` | ✅ `baseDate` 지정 / 직전 확정 기간 폴백 |
| `PeriodScoreProcessorTest` | ✅ 가중치, 주문 이중가중 방지, 음수 좋아요, 0 이하 필터 |

기존 `MetricsAggregatorTest` 3곳이 생성자 시그니처 변경으로 깨져 함께 수정했다.

### 미완 (Docker 필요)

- **Job E2E** — `RankingSnapshotJobE2ETest` 패턴으로 3-Step 전체 실행 검증
- **`PeriodRankingMvRepository` 통합 테스트** — MV 조회/페이징
- **적재 통합 테스트** — `ProductMetricsAggregationIntegrationTest` 에 일자별 UPSERT 검증 추가
- **전체 흐름** — 이벤트 발행 → 일자별 적재 → 배치 → API 조회

이 PC 에 Docker 가 없어 실행하지 못했다. 코드 결함이 아니라 실행 환경 문제다.

## 4. 알려진 한계

| 한계 | 내용 |
|---|---|
| **진행 중 기간 미노출** | 확정된 기간만 MV 에 올라간다. 이번 주/이번 달을 요청하면 빈 결과다 |
| **`totalCount` 상한 100** | MV 는 TOP 100 만 담으므로 주간/월간의 `totalCount` 는 최대 100. 일간(ZCARD)과 의미가 다르다 |
| **보관 정책 미구현** | `product_metrics_daily` 가 무한 증가한다. 삭제 SQL 은 마이그레이션 파일 주석에만 있다 |
| **스케줄러 없음** | 주 1회/월 1회 실행은 애플리케이션 밖(cron·k8s CronJob) 책임으로 뒀다 |
| **정책 상수 복제** | 가중치가 batch/streamer 양쪽에 있다. 한쪽만 고치면 일간과 기간 랭킹이 다른 스케일이 된다 ([03-class-diagram §4](./03-class-diagram.md) 표 참고) |

## 5. 다음 단계

1. Docker 환경에서 통합 테스트 작성·실행
2. 시드 데이터로 배치 실측 — 청크 크기별 처리 시간, 커서 vs 페이징 비교
3. `product_metrics_daily` 보관 배치
4. (선택) 브랜드별 기간 랭킹
