# 주간/월간 랭킹 배치 & Materialized View 설계

- 작성일: 2026-07-23
- 브랜치: Volume-9
- 상태: 설계 승인 완료 (그릴링 세션 기반)
- 관련 선행 문서: [`docs/domain/ranking/01-design.md`](../ranking/01-design.md) (일간 랭킹 조회, Redis ZSET), [`docs/domain/ranking/02-ingestion-design.md`](../ranking/02-ingestion-design.md) (일간 랭킹 적재, Redis ZSET)

## 1. 제품 개요

기존 일간 랭킹(Redis ZSET, 실시간)과 별개로, **대규모 데이터 집계/조회 전용 구조**로 주간·월간 TOP 100 랭킹을 제공한다.

- `product_metrics` 테이블을 역할에 따라 두 테이블로 분리한다.
  - `product_metric_summary` — 상품 생성 이후 누적 메트릭 (기존 `product_metrics` 리네임, 실시간 원자적 갱신)
  - `product_metric_daily` — 일자별 메트릭 (신규, 실시간 upsert)
- `product_metric_daily`를 Spring Batch(Chunk-Oriented)로 집계하여 `mv_product_rank_weekly` / `mv_product_rank_monthly`(Materialized View 성격의 조회 전용 테이블)에 적재한다.
- 기존 랭킹 조회 API(`GET /api/v1/rankings`)를 확장해 `period` 파라미터로 일간(Redis)/주간·월간(RDB MV)을 함께 제공한다.
- **일간 Redis ZSET 랭킹 파이프라인은 이번 작업과 완전히 독립적으로 유지**하며 변경하지 않는다.

```
                 (실시간, 이벤트 기반)
Kafka Events ──▶ commerce-streamer 컨슈머 ──┬──▶ product_metric_summary (누적, 원자적 UPDATE)
                                            └──▶ product_metric_daily   (당일 행 upsert)

                 (일 1회, 외부 트리거)
product_metric_daily ──▶ commerce-batch (Chunk-Oriented) ──▶ mv_product_rank_weekly / mv_product_rank_monthly

                 (조회)
commerce-api ── GET /rankings?period=DAILY   ──▶ Redis ZSET (기존, 변경 없음)
             └─ GET /rankings?period=WEEKLY|MONTHLY ──▶ RDB MV (신규)
```

## 2. 주요 결정 사항 (그릴링 Q&A 확정)

| # | 쟁점 | 결정 | 근거 |
|---|------|------|------|
| 1 | 기존 Redis 일간 랭킹과의 관계 | **완전히 별개 데이터 소스, API 계약만 통합** | Redis ZSET(실시간, TTL 2일)과 RDB MV(스냅샷, 무기한)는 근본적으로 다른 저장 모델. 적재 파이프라인은 독립, 조회 엔드포인트(`period` 파라미터)만 공유 |
| 2 | 컨슈머의 쓰기 대상 | **`product_metric_summary`(누적)와 `product_metric_daily`(당일 upsert) 둘 다 이벤트 처리 시점에 실시간 갱신** | 상품 상세의 누적 조회수/좋아요/구매수는 이벤트 기반 즉시 반영이 필요 (배치 지연 불허) |
| 3 | 배치의 역할 | **`product_metric_daily`를 원본으로 읽어 MV에 롤업하는 순수 집계 배치.** daily/summary 자체는 건드리지 않음 | 컨슈머가 이미 daily를 실시간으로 채우므로 배치가 daily를 "새로 만들 필요"가 없음 |
| 4 | 집계 윈도우 | **롤링 윈도우** — 주간: 최근 7일 합산, 월간: 최근 30일 합산 (캘린더 주/월 아님) | 신규 Ranking API가 `period`만으로 일간·주간·월간을 균일하게 제공해야 하므로, 특정 캘린더 경계에 종속되지 않는 롤링 방식이 API 계약과 자연스럽게 맞음 |
| 5 | 오늘자 포함 여부 | **오늘(배치 실행 당일) 제외, 완결된 어제까지 7일/30일.** 즉 `[어제-6, 어제]` / `[어제-29, 어제]` | 오늘은 컨슈머가 daily를 계속 갱신 중이라 포함 시 배치를 여러 번 돌릴 때마다 결과가 달라짐(비결정적). MV는 "완결된 스냅샷"이어야 재계산·재시도 시 결과가 항상 동일함 |
| 6 | MV 저장 형태 | **스냅샷 누적형** — 배치 실행일(`as_of_date`)마다 그날 기준 집계 결과를 새 행으로 추가 (덮어쓰지 않음) | 신규 Ranking API가 특정 날짜(`date` 파라미터) 기준 주간/월간 랭킹 조회를 요구하므로, 과거 특정일 스냅샷을 남겨야 함 |
| 7 | 보관 정책 | **무기한 보관.** 정리(retention) 배치는 이번 스코프 밖, 필요 시 후속 과제 | TOP 없이 전체 상품 저장이라도 연간 데이터 증가량이 RDB 성능에 실질적 영향을 주는 수준이 아님. 과거 랭킹 이력 조회 가치가 더 큼 |
| 8 | TOP 100 필터링 주체 | **API(조회 시점) 책임.** MV는 전체 상품의 집계 score를 저장하고, `ORDER BY score DESC LIMIT 100`은 조회 쿼리에서 수행 | 배치가 TOP 100만 저장하면 향후 TOP 200으로 정책이 바뀔 때 배치를 다시 돌려야 함. 전체 저장 + 조회 시 자르면 정책 변경이 API 레이어로 국한됨 |
| 9 | Chunk-Oriented 집계 방식 | **Reader가 daily 원본 row를 청크로 읽음 → Processor가 row 하나당 부분 점수만 계산 → Writer가 `INSERT ... ON DUPLICATE KEY UPDATE score = score + ?`로 원자적 증분 upsert** | "메모리에 상품별 합계 Map을 통째로 들고 있다가 마지막에 write"하는 방식은 Chunk-Oriented의 메모리 제한 취지와 충돌. DB의 원자적 UPDATE 누적에 합산을 위임하면 메모리 사용량이 청크 크기로 고정되고, 정렬(Reader의 `ORDER BY product_id`) 요구사항도 없어짐 |
| 10 | Score 공식 | **기존 Redis 랭킹과 동일한 가중치 재사용** — `score = view_count*0.1 + like_delta_count*0.2 + purchase_quantity*0.7` (price/log 미적용) | 일간·주간·월간 랭킹이 같은 "무게감"으로 정렬되어야 사용자에게 일관됨. Processor가 row 단위로 계산해 그대로 누적해도 수학적으로 정확한(선형·가산적) 공식 |
| 11 | 재시도/재실행 정합성 | **Step 시작 시 해당 `as_of_date`의 MV 행을 전부 삭제(`DELETE WHERE as_of_date = ?`)한 뒤 처음부터 재계산.** Spring Batch의 체크포인트 재시작 메커니즘에는 의존하지 않음 | 증분 upsert(#9) 특성상, 같은 `as_of_date`를 재처리하면 중복 합산되므로 "그날 기준 완전 재계산"이 가장 단순하고 명확한 멱등성 보장 방법 |
| 12 | Weekly/Monthly Job 분리 | **완전히 별개의 두 Job** (`ProductRankWeeklyJobConfig`, `ProductRankMonthlyJobConfig`) | 읽는 윈도우와 재시도 정책이 다를 수 있고, 하나가 실패해도 다른 하나는 정상 진행되도록 장애 격리 |
| 13 | 배치 트리거 범위 | **이번 스코프는 Job 구현까지만.** 매일 1회 실행을 위한 cron/k8s CronJob 등록은 범위 밖 (후속 작업) | 기존 `commerce-batch`는 `spring.batch.job.name` 프로퍼티로 1회 실행 후 종료하는 구조이며, 이 프로젝트엔 인프로세스 스케줄러(`@Scheduled`)나 cron 관련 설정이 없음. 트리거는 배포 환경(dev/qa/prd)마다 다를 수 있어 별도 결정 필요 |
| 14 | `product_metrics` → `product_metric_summary` | **테이블 리네임(데이터·컬럼 유지).** `ALTER TABLE product_metrics RENAME TO product_metric_summary` | 데이터 손실 없이 이름만 교체. 기존 `ProductMetricsJpaEntity`(commerce-api/streamer 양쪽에 중복 정의됨)의 `@Table(name=...)`만 변경하면 기존 로직 대부분 재사용 가능 |
| 15 | Ranking API 포트 구조 | **`domain/ranking` 패키지는 통합 유지, 포트만 분리** — `RankingRepository`(Redis ZSET, 일간), `ProductRankRepository`(RDB MV, 주간·월간). Application 레이어(`ProductApplicationService` 또는 `RankingFacade`)가 `period`로 라우팅 | Redis ZSET과 RDB MV는 조회 방식(score range vs SQL 페이징)이 근본적으로 달라 하나의 포트로 억지로 추상화하면 최소공배수 인터페이스가 됨. 다만 "랭킹"이라는 동일 관심사이므로 도메인 패키지 자체는 분리하지 않음 |
| 16 | `product_metric_daily` PK | **복합 자연키 `(metric_date, product_id)`.** ULID 미사용, `BaseJpaEntity` 상속 안 함 | daily는 MV와 마찬가지로 컨슈머가 실시간 upsert하는 대상이며, 굳이 대리키(ULID)를 둘 필요가 없고 UNIQUE 제약을 PK로 승격시키면 별도 인덱스 비용도 줄어듦. `BaseJpaEntity` 상속 포기로 `createdAt/updatedAt`은 직접 관리하나, daily는 soft-delete 대상이 아니라 손실 크지 않음 |
| 17 | `mv_product_rank_weekly/monthly` PK | **복합 자연키 `(as_of_date, product_id)`.** ULID 미사용 | MV는 도메인 엔티티가 아니라 배치 산출물(집계 결과)이므로 CLAUDE.md의 엔티티 ID 전략 대상이 아님. FK로 참조되지 않고 API 응답에도 노출되지 않아 ULID가 실질적으로 미사용 컬럼이 됨 |
| 18 | `deleted_at` 필요 여부 | **`product_metric_daily`/`product_metric_summary` 둘 다 불필요.** `BaseJpaEntity` 미상속과 일관 | 이벤트로부터 파생되는 집계 데이터라 "메트릭 행을 삭제/복원한다"는 도메인 유스케이스 자체가 없음 |
| 19 | `created_at`/`updated_at` 필요 여부 | **둘 다 추가.** `product_metric_summary`는 기존 스키마에 없었지만 이번에 추가 (Q17 "순수 리네임" 범위를 넘어서는 예외적 컬럼 추가) | `BaseJpaEntity`를 상속하지 않으므로 상속이 아닌 **엔티티 자체의 `@PrePersist`/`@PreUpdate`로 직접 관리**. daily에만 있고 summary에 없으면 "언제부터 이 상품 메트릭이 이상해졌는지" 감사·디버깅 시 비대칭적으로 불편해짐 |
| 20 | 상품 삭제(`Product.delete()`, soft-delete) 시 메트릭 처리 | **cascade 삭제하지 않고 그대로 남김.** `product_metric_summary`/`daily`/MV는 상품이 삭제돼도 변경하지 않는다. 조회 시점(상품 상세, 랭킹)에 `Product`와 조인해 삭제 상태면 결과에서 제외(skip) | 기존 랭킹 도메인(`docs/domain/ranking/01-design.md`)이 이미 "ZSET 에는 있으나 DB 에서 결손된 상품은 skip" 정책을 채택 중이라 일관성을 유지. daily/MV는 "과거 시점에 실제로 발생한 사실"이므로 상품이 나중에 삭제됐다고 과거 스냅샷에서 지우면 히스토리가 왜곡됨. cascade 삭제를 하려면 컨슈머가 상품 삭제 이벤트까지 구독해야 해서 Q2/Q3에서 정한 "조회/좋아요/구매 이벤트만 처리"라는 컨슈머 책임 범위를 벗어남. 이로써 Q18("`deleted_at` 불필요")도 재확인됨 |
| 21 | `product_metric_summary`/`product_metric_daily` 실시간 증감 방식 | **원자적 upsert로 통일.** `INSERT ... ON DUPLICATE KEY UPDATE count = count + ?` (좋아요 감소는 `GREATEST(count - 1, 0)`으로 0 미만 방지). 컨슈머가 엔티티를 `findByProductId` 후 메모리에서 증감시켜 `save()`하는 read-modify-write 방식은 폐기 | 컨슈머가 여러 파티션/컨슈머 스레드에서 동시에 같은 상품을 갱신할 수 있어, read-modify-write는 두 트랜잭션이 같은 값을 읽고 각자 +1 한 뒤 저장하면 한쪽 증분이 유실되는 lost update(정합성 붕괴) 위험이 있다. `@Transactional`만으로는 MySQL 기본 격리수준(REPEATABLE READ)에서 이 레이스를 막지 못한다. daily(#2)·MV(#9)가 이미 원자적 upsert를 전제로 설계되어 있었으므로, summary만 다른 방식을 쓰는 것은 비일관적이었다. 원자적 upsert는 JPA 영속성 컨텍스트(`@PrePersist`/`@PreUpdate`)를 거치지 않으므로 `created_at`/`updated_at`도 SQL에서 직접 `NOW()`로 채운다. **트레이드오프**: 좋아요 하한(`GREATEST(...,0)`)·구매 수량 유효성(`amount <= 0` 무시) 같은 비즈니스 규칙이 도메인 객체가 아닌 네이티브 SQL·`RepositoryImpl`에 위치하게 된다. 원자성을 위해 의도적으로 감수한 것으로, 도메인 mutator를 되살리면 이 규칙은 다시 캡슐화되지만 lost update 버그가 재발한다 |
| 22 | `commerce-api`의 `ProductRankRepositoryImpl`(MV 조회) 구현 방식 | **`JdbcTemplate` → Spring Data JPA로 전환.** `ProductRankWeeklyMvJpaRepository`/`ProductRankMonthlyMvJpaRepository` 두 개의 `JpaRepository<..., ProductRankMvId>`를 두고, `period`로 둘 중 하나에 위임한다. 임의의 `offset`(페이지 경계에 정렬되지 않은 값)을 `Pageable`로 표현하기 위해 `OffsetBasedPageRequest`(자체 구현 `Pageable`)를 사용한다 | 애초 `JdbcTemplate`을 택한 건 #21(원자적 upsert)의 네이티브 SQL 톤을 조회 경로까지 맞춘 것이었는데, #21의 진짜 근거는 "동시성 lost update 방지"였지 "읽기라서"가 아니었다. 이 MV 조회는 쓰기 경합이 없는 순수 읽기이므로 그 근거가 적용되지 않는다. 반면 `WEEKLY`/`MONTHLY`가 물리적으로 다른 두 테이블이라는 특성은 그대로 남아 있어, 단일 `JpaRepository`로 추상화하지 않고 테이블당 하나씩 두어 `period`로 분기하는 구조를 유지했다. 이 프로젝트의 기본 노선(`product`/`like` 등 대부분 도메인이 JPA 기반)과의 일관성을 우선했다 |
| 23 | `#11`(완전 재계산)이 실제 재시작 시에도 지켜지도록 보장하는 방법 | **cleanup Step에 `allowStartIfComplete(true)`, aggregate Step의 `JdbcCursorItemReader`에 `saveState(false)` + `ORDER BY product_id` 추가.** | 코드 리뷰(Codex)에서 지적됨: cleanup과 aggregate를 별도 Step으로 나눈 실제 구현은, aggregate Step이 실패한 뒤 **같은 `requestDate`로 재시작**하면 Spring Batch가 이미 COMPLETED된 cleanup Step을 기본적으로 건너뛰고, `JdbcCursorItemReader`도 실패 시점까지 읽은 행 수(체크포인트)부터 이어 읽으려 한다 — `#11`이 "체크포인트 재시작 메커니즘에 의존하지 않는다"고 선언한 것과 실제 동작이 어긋났다. `allowStartIfComplete(true)`로 cleanup이 재시작 시에도 항상 다시 실행되게 하고, Reader의 `saveState(false)`로 체크포인트 저장 자체를 꺼서 재시작마다 윈도우 전체를 처음부터 다시 읽게 한다. `ORDER BY product_id`는 필수 요구사항은 아니지만(#9), 커서 재오픈 시 행 순서를 결정적으로 만들어 디버깅을 쉽게 한다. `ProductRankWeeklyJobE2ETest`/`ProductRankMonthlyJobE2ETest`에 `JobRepository`로 "cleanup은 COMPLETED, Job은 FAILED"인 이전 실행을 직접 만들어 재시작을 검증하는 테스트를 추가했다 |
| 24 | `#8`(TOP 100 필터링, API 책임)의 실제 구현 위치 | **`ProductApplicationService.resolveByPeriod`에서 WEEKLY/MONTHLY에 한해 `total`과 조회 `limit`을 100으로 클램프.** `offset >= 100`이면 빈 목록(200, 빈 content)을 반환하고, 100위를 걸치는 페이지는 100위까지만 잘라 반환한다. `DAILY`(Redis ZSET)는 이 캡의 대상이 아니며 기존 동작을 유지한다 | 코드 리뷰(Codex)에서 지적됨: `#8`에서 "API가 LIMIT 100 책임"이라고 결정했지만 실제로 어느 계층에도 구현되지 않아, `page`/`size`를 크게 주면 101위 이후도 그대로 조회할 수 있었다. MV 자체(Repository/DB)는 전체 상품 score를 그대로 보관해야 하므로(#8의 "정책 변경이 API 레이어로 국한" 취지), 캡은 조회 오케스트레이션을 담당하는 Application 계층에 둔다 |
| 25 | 동점(score 동일) 시 순위 결정 방식 | **`score DESC` 다음에 `product_id ASC`를 2차 정렬 키로 추가.** `OffsetBasedPageRequest`에 `Sort.by(DESC, "score").and(Sort.by(ASC, "id.productId"))`를 전달 | 코드 리뷰(Codex)에서 지적됨: 정렬 키가 `score` 하나뿐이라 동점 상품 간 순서가 쿼리마다(페이지 경계에서는 특히) 바뀔 수 있어, 페이지네이션 시 항목이 중복되거나 누락될 수 있었다. `product_id`는 유니크하므로 안정적인 tie-breaker가 된다 |
| 26 | `count == 0`일 때 "빈 스냅샷(정상 실행, 대상 0건)"과 "배치 미실행"의 구분 여부 | **구분하지 않고 현행 유지 — 둘 다 404 NOT_FOUND.** (제품 판단, 확인 완료) | 코드 리뷰(Codex)에서 지적됨: 원인이 다른 두 상황이 같은 응답을 반환한다. 별도 실행 이력 테이블 등을 두는 방안도 검토했으나, 이번 스코프에서는 추가 복잡도 대비 실익이 낮다고 판단해 확장하지 않기로 확정. 필요해지면 배치 실행 이력을 남기는 별도 후속 과제로 다룬다 |
| 27 | MV upsert SQL의 `VALUES()` 함수 사용 여부 | **row-alias 문법(`... AS new_row ... ON DUPLICATE KEY UPDATE col = col + new_row.col`, MySQL 8.0.19+)으로 전환.** `VALUES()` 함수는 미사용 | 제 리뷰에서 지적됨: `VALUES()` 함수는 MySQL 8.0.20부터 deprecated이고 향후 버전에서 제거 예고되어 있다. 이 프로젝트 인프라(`docker/infra-compose.yml`)는 MySQL 8.0이라 row-alias 문법을 바로 쓸 수 있다 |
| 28 | MV upsert/cleanup SQL에 삽입되는 테이블명의 타입 | **`String tableName` → `ProductRankMvTable`(enum: `WEEKLY`/`MONTHLY`) 파라미터로 변경.** `ProductRankMvUpsertWriter`/`ProductRankMvCleanupTasklet` 모두 enum을 받아 `table.tableName()`으로 SQL에 삽입 | Codex 판단: 현재 값은 `JobConfig`의 상수뿐이라 실질적 인젝션 경로는 없었지만, 문자열을 그대로 SQL에 꽂는 구조 자체가 향후 호출부가 늘어날 때 위험을 남긴다. enum으로 제한하면 컴파일 타임에 값이 고정된다 |
| 29 | Weekly/Monthly `JobConfig`의 중복 코드 | **Step/Reader 조립 로직을 `ProductRankJobSupport`(static helper)로 추출.** `@Bean`/`@JobScope`/`@StepScope` 선언 자체는 스코프 프록시 요구사항 때문에 각 `JobConfig`에 남기되, 본문은 helper 호출 한 줄로 위임 | Codex 판단: 두 `JobConfig`가 윈도우 일수·테이블명만 다르고 나머지 Step 구성이 완전히 동일했다. `@StepScope`/`@JobScope` 빈은 CGLIB 프록시가 걸리는 `@Bean` 메서드 자체를 없앨 수는 없어, 완전한 클래스 통합 대신 로직만 공유 |
| 30 | `commerce-api` MV 엔티티(`ProductRankWeeklyMvJpaEntity`/`MonthlyMvJpaEntity`)의 주석 | **"실제 조회는 JdbcTemplate으로 수행한다"는 문구를 "실제 조회는 이 엔티티를 통해 JPA로 수행한다"로 수정** (`#22`에서 JdbcTemplate → JPA로 전환했으나 주석이 갱신되지 않았던 잔존 오류) | 제 리뷰에서 지적됨, 미수정 상태로 남아있던 항목. 코드와 주석의 불일치는 다음 작업자가 조회 방식을 잘못 파악하게 만든다 |
| 31 | `ProductRankRepositoryImpl`의 `period` 분기 중복 | **`findTopN`/`countByAsOfDate` 둘 다 쓰던 `DAILY → IllegalArgumentException` 분기를 `validateMvPeriod()`로 추출.** WEEKLY/MONTHLY 분기 자체는 두 테이블이 서로 다른 엔티티 타입이라 불가피하게 유지 | Codex 판단: 두 메서드가 동일한 3-way 분기(WEEKLY/MONTHLY/DAILY-예외)를 반복하고 있었다. 예외 처리만이라도 공통화해 두 메서드의 사전조건을 한 곳에서 관리한다 |
| 32 | `ProductRankMvCleanupTasklet`의 패키지 위치 | **`batch.job.productrank` → `batch.job.productrank.step`으로 이동.** `ProductRankMvUpsertWriter`는 `ItemWriter`이자 `JobConfig`와 함께 조립되는 성격이 강해 그대로 두고, `Tasklet`만 컨벤션에 맞춰 옮김 | Codex 판단: `CLAUDE.md`의 `commerce-batch` 패키지 구조(`batch.job.<jobName>.step.<JobName>Tasklet`, `DemoTasklet` 예시)와 실제 위치가 어긋나 있었다 |
| 33 | `OffsetBasedPageRequest`의 `limit <= 0` 방어 | **생성자에서 `limit <= 0`/`offset < 0`이면 `IllegalArgumentException`을 즉시 던지도록 변경.** 기존에는 `getPageNumber()`(`offset / limit`)가 호출되는 시점에야 `ArithmeticException`이 났음 | 제 리뷰에서 지적됨: 현재 유일한 호출부(`ProductApplicationService.resolveByPeriod`)는 `pageable.getPageSize() >= 1`이 보장돼 실제로는 발생하지 않지만, 범용 `Pageable` 구현체로서 생성 시점에 실패하는 편이 늦게(그것도 나눗셈 예외로) 실패하는 것보다 안전하다 |

## 3. ERD / 클래스 다이어그램

ERD와 클래스 다이어그램은 [`02-diagrams.md`](./02-diagrams.md) 로 분리했습니다.

운영(dev/qa/prd) DB에 이 ERD를 반영하기 위한 DDL은 [`03-migration.sql`](./03-migration.sql) 참고.
`ddl-auto: none`인 non-local 프로필에서는 이 스크립트를 각 환경 배포 프로세스에 맞게 실행해야
스키마가 만들어진다 (코드 리뷰에서 지적된 "운영 DB 마이그레이션 부재" 갭에 대응. 이 프로젝트에는
Flyway/Liquibase 같은 마이그레이션 도구가 없어, 자동화 없이 순수 SQL 스크립트로만 산출했다 — 실행
자체는 여전히 운영자/배포 파이프라인의 책임이다).

## 4. API 명세 (확장)

```
GET /api/v1/rankings?date=yyyyMMdd&period=DAILY|WEEKLY|MONTHLY&page=0&size=20
```

| 파라미터 | 필수 | 기본값 | 설명 |
| -------- | ---- | ------ | ---- |
| `date`   | O    | —      | 조회 대상 일자 (`yyyyMMdd`). `WEEKLY`/`MONTHLY`는 `as_of_date`로 해석 |
| `period` | X    | `DAILY` | `DAILY`(Redis ZSET) / `WEEKLY`·`MONTHLY`(RDB MV) |
| `page`   | X    | `0`    | 0-base 페이지 번호 |
| `size`   | X    | `20`   | 페이지 크기 |

| 상황 | 응답 |
| ---- | ---- |
| `date` 누락 또는 파싱 실패 | 400 BAD_REQUEST |
| `period` 값이 열거형 밖 | 400 BAD_REQUEST |
| `DAILY`: 해당 일자 ZSET 미존재 | 404 NOT_FOUND (기존 정책 유지) |
| `WEEKLY`/`MONTHLY`: 해당 `as_of_date` MV 스냅샷 미존재(배치 미실행) | 404 NOT_FOUND |
| `WEEKLY`/`MONTHLY`: MV에는 있으나 조회 시점에 상품이 삭제됨 | 해당 항목만 결과에서 제외 (skip) — `DAILY`의 기존 skip 정책과 동일 (Q&A #20) |

## 5. 배치 실행 예시 (수동 트리거, 이번 스코프)

두 Job 모두 `requestDate`(yyyy-MM-dd, `LocalDate.parse` 가능한 형식) Job Parameter가 필수다. 누락 시 `dailyMetricReader`/`ProductRankMvUpsertWriter`/`ProductRankMvCleanupTasklet` 생성 단계에서 파싱 예외로 즉시 실패한다(§6.1 참고).

`requestDate`는 **`--` 없이** `key=value` 형식으로 넘겨야 한다(§6.3 참고). `spring.batch.job.name`은 Spring Boot 프로퍼티라 `--`가 맞다.

```shell
./gradlew :apps:commerce-batch:bootRun --args='--spring.batch.job.name=productRankWeeklyJob requestDate=2026-07-24'
./gradlew :apps:commerce-batch:bootRun --args='--spring.batch.job.name=productRankMonthlyJob requestDate=2026-07-24'
```

## 6. Spring Batch 구현 시 발견한 함정 (Slice 4, `ProductRankWeeklyJobConfig`)

설계와 직접 관련은 없지만, 같은 실수를 반복하지 않도록 원인과 선택한 해결책을 남겨둔다.

### 6.1 `requestDate` Job Parameter는 `String`으로 받고 `LocalDate.parse()`로 직접 변환한다

**증상**: `@StepScope @Bean` 메서드 파라미터를 `@Value("#{jobParameters['requestDate']}") LocalDate requestDate`로 선언하면, 실제 Step 실행 시점에 `requestDate`가 `null`로 주입되어 `NullPointerException`이 발생했다. `LocalDate` 대신 `String`으로 받으면 값 자체는 들어오지만, `"2026-07-23"`이 아니라 `"26. 7. 23."`처럼 **JVM 기본 로케일(ko_KR) 종속 포맷**으로 변환되어 있어 `LocalDate.parse()`가 실패했다 (`DateTimeParseException`).

**원인**: `#{jobParameters['requestDate']}`는 SpEL로 평가된 뒤, 대상 파라미터 타입에 맞춰 Spring의 `ConversionService`를 거쳐 변환된다. 이 변환 경로가 `JobParameters`에 저장된 원본 타입(`String`/`LocalDate` 등)과 대상 타입 조합에 따라 로케일 종속 `DateFormat`을 거치는 등 신뢰할 수 없게 동작한다는 것을 관찰했다 — 즉 "타입을 명시하면 자동으로 안전하게 바인딩될 것"이라는 기대가 깨진다.

**결정**: `JobParametersBuilder`로 넣을 때도, `@Value`로 받을 때도 **항상 `String`으로 다루고, ISO-8601(`yyyy-MM-dd`) 포맷을 명시적으로 직접 `parse`/`toString`한다.** 이미 이 프로젝트의 `DemoTasklet`이 채택하고 있던 관례(`@Value("#{jobParameters['requestDate']}") private String requestDate;`)와 동일하며, 우연이 아니라 **동일한 함정을 피하기 위한 기존 선례**였던 것으로 보인다. 이 컨벤션을 그대로 따름으로써 로케일에 의존하지 않는 명시적 변환 지점을 코드에 남긴다.

### 6.3 CLI에서 `requestDate`를 Job Parameter로 넘기려면 `--` 없이 `key=value` 형식이어야 한다

**증상**(코드 리뷰(Codex)에서 지적, 검증 완료): §5의 실행 예시와 `docker/k8s/productrank-cronjobs.yaml`의 CronJob이 모두 `--requestDate=2026-07-24` 형식을 쓰고 있었는데, 이 형식으로 실행하면 `jobParameters['requestDate']`가 항상 `null`이 되어 `dailyMetricReader`/`ProductRankMvUpsertWriter`/`ProductRankMvCleanupTasklet` 생성 시점에 `LocalDate.parse(null)`이 `NullPointerException`을 던진다.

**원인**: Spring Boot 3.4.4의 `JobLauncherApplicationRunner.run(ApplicationArguments)`는 `args.getNonOptionArgs()`만 꺼내 `StringUtils.splitArrayElementsIntoProperties(..., "=")`로 Job Parameter를 만든다(바이트코드로 직접 확인). `--`로 시작하는 인자는 Spring Boot의 `ApplicationArguments`가 먼저 "옵션"(프로퍼티 오버라이드 후보)으로 분류해가서 `getNonOptionArgs()`에 남지 않는다. 실제로 `DefaultApplicationArguments("--spring.batch.job.name=X", "--requestDate=2026-07-24")`를 만들어 확인하면 `getNonOptionArgs()`가 빈 리스트, `getOptionNames()`에 `requestDate`가 들어가 있다. 반면 `spring.batch.job.name`은 Spring 프로퍼티 자체(`@ConditionalOnProperty`가 Environment에서 읽음)이므로 `--`가 정확한 형식이다.

**왜 테스트가 못 잡았는가**: `ProductRankWeeklyJobE2ETest`/`ProductRankMonthlyJobE2ETest`는 `JobParametersBuilder().addString("requestDate", ...).toJobParameters()`로 `JobParameters`를 프로그래밍적으로 직접 만들어 `jobLauncherTestUtils.launchJob()`에 넘긴다. 이 경로는 `JobLauncherApplicationRunner`의 CLI 인자 파싱(`getNonOptionArgs`)을 전혀 거치지 않으므로, 실제 실행 경로(`bootRun`/jar 실행/CronJob)에서만 발생하는 이 버그를 테스트가 원천적으로 검증할 수 없었다.

**결정**: `requestDate`는 **`--` 없이** `requestDate=2026-07-24` 형식으로 넘긴다. `spring.batch.job.name`처럼 Spring 프로퍼티인 것만 `--`를 쓴다. §5의 실행 예시와 `docker/k8s/productrank-cronjobs.yaml`을 이 형식으로 수정했다.

### 6.2 `@StepScope` Reader `@Bean` 메서드의 반환 타입은 구체 클래스로 선언한다

**증상**: `dailyMetricReader()` 빈을 `ItemReader<ProductMetricDailyRow>` 인터페이스 타입으로 선언했더니, Step 실행 시 `org.springframework.batch.item.ReaderNotOpenException: Reader must be open before it can be read`가 발생했다.

**원인**: `SimpleStepBuilder`(`.reader(...)`)는 전달된 reader가 `ItemStream`을 구현하는지 확인해 자동으로 Step의 스트림 생명주기(`open()`/`update()`/`close()`)에 등록한다. `@StepScope`는 `proxyMode = TARGET_CLASS`(CGLIB)로 프록시를 만드는데, 이 프록시가 어떤 인터페이스까지 노출하는지는 **Bean으로 등록된 정적 타입(=`@Bean` 메서드의 선언된 반환 타입)**의 영향을 받는다. 반환 타입을 `ItemReader<T>` 인터페이스로 선언하면 `JdbcCursorItemReader`가 실제로 구현하는 `ItemStream`이 프록시 계층에서 드러나지 않아, `.reader()`의 `instanceof ItemStream` 판정이 실패하고 `open()`이 한 번도 호출되지 않은 채 `read()`가 호출된다.

**결정**: `@Bean` 메서드의 반환 타입을 실제 구현체 타입(`JdbcCursorItemReader<ProductMetricDailyRow>`)으로 선언한다. Spring Batch 커뮤니티에서도 잘 알려진 함정으로, StepScope로 감싸는 Reader/Writer/Processor `@Bean` 메서드는 인터페이스가 아니라 구체 타입을 반환하도록 하는 것이 안전하다. 이후 Slice(Monthly Job 등)에서도 이 패턴을 그대로 따른다.

## 7. 후속 과제 (이번 스코프 밖)

- 배치 매일 1회 실행을 위한 트리거(k8s CronJob 등) 및 배치 플랫폼 구성.
- MV 스냅샷 보관 정책(retention) — 필요 시 오래된 `as_of_date` 행을 정리하는 별도 배치.
- Score 정규화(log 등) 적용 여부 — 현재는 미적용, 필요 시 조회 계층에서 후처리로 검토.
