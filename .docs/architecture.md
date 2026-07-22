# Architecture Decision

이 문서는 현재 10주차 구현의 아키텍처 기준 문서다. 제출 커밋에는 포함하지 않는다.

## 결정

이번 설계는 장기적으로 큰 서비스를 만든다는 전제로 5계층 우선 패키지 구조 안에 도메인 모듈 경계를 둔다.

```text
com.loopers
  interfaces
    api
      catalog
      coupon
      ordering
  application
      catalog
        ranking
      coupon
      ordering
        queue
    payment
    event
  domain
    catalog
      ranking
    coupon
    ordering
      queue
    payment
    event
  infrastructure
    catalog
      ranking
    coupon
    ordering
      queue
    payment
    event
  support
```

## 모듈 경계

| 모듈 | 포함 도메인 | 책임 |
| --- | --- | --- |
| `catalog` | `Brand`, `Product`, `ProductLike` | 상품 탐색, 상품 상태, 재고 수량, 좋아요 |
| `catalog.ranking` | `Ranking` | 상품 행동 이벤트 기반 일간 랭킹 read model, Batch 기반 주간/월간 랭킹 MV, 기간별 랭킹 조회, 상품 상세 순위 조합 |
| `coupon` | `CouponTemplate`, `CouponIssueRequest`, `IssuedCoupon` | 쿠폰 템플릿 관리, 비동기 발급 요청, 실제 발급, 할인 계산, 사용과 복구 |
| `ordering` | `Order`, `OrderLine`, 대기열 | 주문 생성, 주문 상태, 주문 항목 스냅샷, 주문 API 앞단 입장 제어 |
| `payment` | `Payment`, `PaymentGateway` | 결제 요청, 결제 결과, 결제 실패/취소 처리 |
| `event` | `EventOutbox`, Kafka relay | 주문/카탈로그 이벤트 저장, Kafka 전파, relay 상태 관리 |

## 4주차 핵심 트랜잭션

4주차 필수 설계는 재고, 쿠폰, 주문의 RDB 정합성과 동시성 제어다.

| 유스케이스 | 단일 DB 트랜잭션 처리 순서 |
| --- | --- |
| 쿠폰 발급 | 쿠폰 템플릿 row `PESSIMISTIC_WRITE` 조회 -> soft delete/만료 검증 -> 전체 발급 수와 사용자별 발급 수 확인 -> 발급 쿠폰 저장 |
| 주문 생성 | 상품 ID 오름차순 상품 row `PESSIMISTIC_WRITE` 조회 -> 재고 검증 및 차감 -> optional 발급 쿠폰 row `PESSIMISTIC_WRITE` 조회 -> 소유권/상태/만료/최소 주문 금액 검증 및 `USED` 전이 -> 할인 스냅샷 주문 저장 |
| 주문 실패 | 재고 차감 또는 쿠폰 처리 중 하나라도 실패하면 주문 생성 트랜잭션 전체 롤백 |

- 주문 요청의 nullable `couponId`는 쿠폰 템플릿 ID가 아니라 발급 쿠폰 ID다.
- 주문 스냅샷은 `originalAmount`, `discountAmount`, `finalAmount`, nullable `couponId`를 저장한다.
- Redis, Kafka, cache, message broker를 도입하지 않고 RDB row lock으로 처리한다.

## 기존 확장 설계

결제 흐름은 기존 구현을 유지하지만 7주차 이벤트 파이프라인 필수 설계와 분리한다.

| 영역 | 현재 처리 |
| --- | --- |
| 유상 주문 | 주문 생성 트랜잭션에서 `PaymentStatus.REQUESTED` 결제 row를 저장한다. |
| 0원 주문 | 결제 row 없이 즉시 `PAID`로 전이하고 `ORDER_PAID` outbox를 저장한다. 조회 시 `PaymentStatus.NOT_REQUIRED`를 계산한다. |
| 결제 실패/취소/timeout | 주문 row 잠금 -> 상품 ID 오름차순 상품 row 잠금 및 재고 복구 -> optional 발급 쿠폰 row 잠금 및 `AVAILABLE` 복구 -> 주문 상태 전이 순서로 처리한다. |
| Outbox | 도메인 상태 변경과 이벤트 저장을 같은 DB 트랜잭션으로 처리하고 Kafka 발행은 relay로 분리한다. |

## 7주차 이벤트 파이프라인 기준

7주차 구현은 기존 주문 전용 Outbox를 범용 `EventOutbox`로 확장한다.

| 영역 | 처리 |
| --- | --- |
| ApplicationEvent | 좋아요 등록/취소 성공 후 Spring `ApplicationEvent`를 발행하고 `BEFORE_COMMIT` 리스너가 Outbox를 저장한다. |
| Kafka relay | `EventRelayWorker`가 pending Outbox를 읽어 Kafka로 발행하고 성공 시 `SENT`, 실패 시 retry 또는 `FAILED`로 전이한다. |
| Consumer 멱등성 | `commerce-streamer`와 `commerce-api` 쿠폰 발급 consumer는 `event_handled(event_id PK)`로 중복 소비를 방지한다. |
| 상품 집계 | 좋아요 이벤트는 `product_metrics`에 최신 이벤트 시각 기준으로 반영한다. |
| 선착순 쿠폰 발급 | 발급 API는 `CouponIssueRequest(PENDING)`와 `coupon-issue-requests` Outbox를 같은 트랜잭션에 저장하고, consumer가 요청 row와 템플릿 row를 잠근 뒤 `IssuedCoupon` 생성 또는 실패 확정을 처리한다. |
| 결과 조회 | 사용자는 `GET /api/v1/coupons/issues/{requestId}`로 `PENDING`/`SUCCEEDED`/`FAILED` 상태를 polling한다. |
| 모니터링 | Outbox relay, Kafka consumer, product metrics update 지표를 Micrometer/Prometheus로 노출한다. |

## 8주차 대기열 기준

8주차 구현은 주문 API 앞단의 Redis 기반 대기열을 `ordering.queue` 하위 경계에 둔다.

| 영역 | 처리 |
| --- | --- |
| 도메인 경계 | 대기열은 독립 최상위 모듈이 아니라 주문 진입을 제어하는 관문이므로 `ordering.queue`에 둔다. |
| 대기열 범위 | 주문 API 전체를 보호하는 전역 대기열 1개로 시작한다. 상품별/이벤트별 대기열은 후속 확장으로 둔다. |
| 저장소 | Redis Sorted Set `commerce:ordering:queue:v1:waiting`으로 대기 순서를 관리하고, TTL이 있는 입장 토큰을 `commerce:ordering:queue:v1:token:{userId}`로 관리한다. |
| 진입 순서 | Sorted Set score는 Redis `INCR commerce:ordering:queue:v1:sequence` 결과를 사용한다. userId는 member로 저장하고 `ZADD NX`로 중복 진입을 막는다. |
| 입장 토큰 | 토큰 값은 UUID 문자열이며 TTL은 5분이다. 주문 API의 `X-Loopers-Queue-Token` 헤더 값과 Redis 토큰 값을 비교한다. |
| 스케줄러 | `commerce.workers.order-queue.enabled=true`, `initial-delay-ms=1000`, `fixed-delay-ms=1000`, `admit-batch-size=10`을 기본값으로 사용한다. 배치 크기는 DB 커넥션 풀과 평균 주문 처리 시간 기준으로 조정 가능한 설정으로 둔다. |
| API | 대기열 진입은 `POST /api/v1/queue/enter`, 순번 조회는 `GET /api/v1/queue/position`을 사용한다. 사용자 식별은 기존 로그인 헤더를 따른다. |
| 응답 DTO | 진입/조회 API는 `status`, `position`, `waitingCount`, `estimatedWaitSeconds`, `recommendedPollingIntervalSeconds`, `token`을 포함하는 같은 응답 DTO를 사용한다. 상태는 `WAITING`, `READY`, `NOT_QUEUED`이다. |
| 주문 API 연계 | 주문 생성 진입 시 `X-Loopers-Queue-Token` 헤더를 Redis 토큰과 비교한다. 토큰이 없거나 일치하지 않으면 `ORDER_QUEUE_TOKEN_REQUIRED` 403으로 거부하고, 주문 완료 후 토큰을 삭제한다. |
| 구현 이름 | `OrderQueueController`, `OrderQueueDto`, `OrderQueueService`, `OrderQueueAdmissionWorker`, `OrderQueueAdmissionWorkerScheduler`, `OrderQueueRepository`, `RedisOrderQueueRepository`, `OrderQueueStatus`를 사용한다. |
| 조회 우선순위 | 입장 토큰이 있으면 `READY`를 우선 반환한다. 토큰이 없고 대기열에 있으면 `WAITING`, 둘 다 없으면 `NOT_QUEUED`를 반환한다. |
| 예상 대기 시간 | `ceil(position / admitBatchSize) * schedulerIntervalSeconds`로 계산한다. 기본 설정에서는 1~10번 1초, 11~20번 2초다. `READY`는 0초, `NOT_QUEUED`는 null이다. |
| 권장 polling 간격 | `recommendedPollingIntervalSeconds`는 예상 대기 시간을 기준으로 1~10초 사이를 반환한다. `READY`는 즉시 진입해야 하므로 0초, `NOT_QUEUED`는 null이다. |
| 후속 흐름 | 주문 생성 이후 이벤트 발행, Kafka 파이프라인, Metrics 집계는 7주차 구조를 재사용한다. |

## 9주차 랭킹 기준

9주차 구현은 Redis Sorted Set 기반 상품 랭킹 read model을 `catalog.ranking` 하위 경계에 둔다.

| 영역 | 처리 |
| --- | --- |
| 도메인 경계 | 랭킹은 상품 조회용 read model이므로 별도 최상위 모듈이 아니라 `catalog.ranking`에 둔다. |
| API | 랭킹 목록 조회는 `GET /api/v1/rankings?date=yyyyMMdd&page=0&size=20`을 사용한다. |
| 응답 | 랭킹 목록은 `rank`, `score`, `product`를 포함하고, 상품 상세 응답은 nullable `rank`를 포함한다. |
| 상품 정보 조합 | Redis에서 랭킹 productId를 조회한 뒤 기존 catalog 상품 조회 구조로 상품 정보를 조합한다. |
| 반영 이벤트 | 상품 상세 조회 성공 시 신규 `PRODUCT_VIEWED`를 발행하고, 기존 `PRODUCT_LIKED`, `PRODUCT_UNLIKED`, `ORDER_PAID`를 랭킹 점수에 반영한다. |
| 소비 topic | Ranking consumer는 `catalog-events`와 `order-events`를 소비한다. |
| Kafka key | `PRODUCT_VIEWED`, `PRODUCT_LIKED`, `PRODUCT_UNLIKED`는 productId를 partition key로 사용하고, `ORDER_PAID`는 기존 orderId partition key를 유지한다. |
| Redis key | 일간 랭킹은 `ranking:all:{yyyyMMdd}` Sorted Set에 저장하고 TTL은 2일이다. |
| 점수 계산 | 조회 `+0.1`, 좋아요 `+0.2`, 좋아요 취소 `-0.2`, 주문 완료는 주문 항목별 `lineAmount * 0.6`을 누적한다. |
| 멱등성 | Ranking consumer는 Redis Lua로 `ranking:handled:{ranking:{eventId}}` SETNX와 ZSET 점수 반영을 원자 처리해 동시 중복 소비의 점수 중복 누적을 막고, DB `event_handled`에는 `ranking:{eventId}`를 처리 이력으로 저장한다. |

## 10주차 랭킹 Batch 기준

10주차 구현은 `product_metrics` 일간 집계를 원천으로 주간/월간 TOP 100 랭킹 Materialized View를 생성한다.

| 영역 | 처리 |
| --- | --- |
| 앱 경계 | Batch 구현은 기존 `apps/commerce-batch` 모듈에 두고 API/streamer 실행 책임과 분리한다. |
| 패키지 | 랭킹 Batch 코드는 `com.loopers.batch.job.catalog.ranking` 하위에 둔다. |
| Job | 단일 Job `productRankingAggregationJob`을 사용한다. |
| JobParameter | `period=weekly|monthly`, `baseDate=yyyyMMdd`를 필수 파라미터로 받는다. |
| 기간 계산 | `weekly`는 `baseDate`가 속한 주의 월요일부터 일요일, `monthly`는 해당 월의 1일부터 말일까지 집계한다. |
| 실행 주기 | 이번 범위에서는 스케줄러를 추가하지 않고 외부 실행 시 JobParameter로 실행한다. |
| Reader/Processor/Writer | `product_metrics`를 Chunk-Oriented 방식으로 읽고 상품별 점수를 계산한 뒤 MV 테이블에 쓴다. |
| 점수 계산 | 기간 내 `view_count * 0.1 + like_count * 0.2 + sales_amount * 0.6` 합산 점수로 순위를 계산한다. |
| MV 테이블 | 주간은 `mv_product_rank_weekly`, 월간은 `mv_product_rank_monthly`를 사용한다. |
| MV 컬럼 | `period_start_date`, `period_end_date`, `rank`, `product_id`, `score`, 감사 컬럼을 저장한다. |
| 재실행 정책 | 같은 기간 결과를 먼저 삭제한 뒤 TOP 100을 재적재해 중복 적재를 방지한다. |
| API 조회 | `period=daily`는 Redis, `period=weekly/monthly`는 MV 테이블을 조회하고 기존 응답 DTO를 유지한다. |

## 외부 경계

| 경계 | 이번 설계에서의 처리 | 이유 |
| --- | --- | --- |
| `identity` | `userId` 식별자만 참조 | 회원가입/회원 상세는 volume-2 설계 범위가 아니므로 내부 테이블과 필드는 설계하지 않는다. |

## 계층 책임

| 계층 | 책임 |
| --- | --- |
| `interfaces` | HTTP 요청/응답 변환, API DTO, 헤더 검증 |
| `application` | 유스케이스 조합, 모듈 간 협력, 트랜잭션 시작점 |
| `domain` | 도메인 모델, 상태 전이, 검증 규칙, repository interface |
| `infrastructure` | JPA 구현, 외부 API client, worker 구현 세부사항 |

## 의존 규칙

- 같은 도메인 경계 내부에서는 `interfaces -> application -> domain` 방향으로 의존한다.
- `infrastructure`는 `domain`의 repository interface를 구현한다.
- 다른 도메인 경계의 `infrastructure`를 직접 참조하지 않는다.
- 모듈 간 협력은 application 계층의 유스케이스 또는 명시적인 domain interface를 통해 연결한다.
- 외부 시스템 연동은 `infrastructure`에 둔다.
- 도메인 레이어는 JPA, Spring, HTTP 같은 프레임워크 타입을 직접 사용하지 않는다.
- 영속성 객체는 `infrastructure`의 `*JpaEntity`로 분리하고, repository adapter가 도메인 엔티티와 JPA 엔티티를 매핑한다.
- 도메인 서비스는 상태를 가지지 않는 순수 객체로 두며, Spring bean 등록은 infrastructure configuration에서 처리한다.

## 구현 아키텍처 기준

현재 구현은 Onion/Hexagonal/CQRS 방향을 명시적으로 따른다.

| 관점 | 기준 |
| --- | --- |
| Onion | 도메인 엔티티와 VO가 중심이며, application/infrastructure가 바깥에서 의존한다. |
| Hexagonal | Repository, PaymentGateway, Kafka event publisher는 port이고 구현체는 infrastructure adapter다. |
| CQRS | command service와 query service를 분리해 변경 유스케이스와 조회 조합의 책임을 나눈다. |
| Persistence 분리 | `domain.catalog`, `domain.coupon`, `domain.ordering`, `domain.payment`, `domain.event` 도메인 객체는 JPA 어노테이션을 갖지 않고, infrastructure JPA entity가 DB 스키마를 담당한다. |

## 현재 코드와의 관계

현재 구현은 기존 5계층 패키지를 유지하고, `catalog`, `coupon`, `ordering`, `payment`, `event`는 각 계층 하위 도메인 패키지로 둔다. 8주차 대기열은 `ordering.queue` 하위 패키지로 두고, 9주차 랭킹은 `catalog.ranking` 하위 패키지로 둔다.

구현 대상 도메인은 순수 도메인 엔티티와 infrastructure JPA 엔티티를 분리한다. 기존 예제 코드의 JPA Entity 구조는 과제 핵심 범위가 아니므로 별도 리팩터링 대상에서 제외한다.

## 확장 개선 목표

현재 `PaymentWorker.processRequestedPayments()`는 `Payment` row lock을 유지하는 DB 트랜잭션 안에서 외부 PG 호출까지 수행한다. 외부 호출 지연이 DB lock 유지 시간으로 이어지므로 아래 목표 구조로 후속 개선한다.

```text
1. 짧은 DB 트랜잭션에서 REQUESTED -> PROCESSING 선점과 lease 만료시각 저장
2. DB 트랜잭션 밖에서 외부 PG authorize/capture/void 호출
3. 별도 DB 트랜잭션에서 payment row와 order row를 잠그고 결과 반영
4. lease가 만료된 PROCESSING 건은 worker 중단 건으로 보고 재처리
```

- `PaymentStatus.PROCESSING`은 6주차 PG 요청 접수 상태로 코드에 반영했다. lease 만료시각은 목표 구조이며 현재 코드에는 아직 반영되지 않았다.
- 0원 주문의 payment row 저장 정책은 이 개선 작업 전에 별도로 확정한다.
