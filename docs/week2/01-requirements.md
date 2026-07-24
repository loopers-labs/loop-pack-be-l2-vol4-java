# 감성 이커머스 서비스 요구사항 명세

## 1. 개요
본 문서는 '감성 이커머스' 서비스의 핵심 기능인 유저 관리, 브랜드 및 상품 관리, 그리고 주문 시스템에 대한 요구사항을 정의합니다. 
단순한 기능 구현을 넘어, 데이터의 일관성과 이력 보존을 고려한 설계를 지향합니다.

---

## 2. 핵심 비즈니스 정책

### 2.1 재고 관리 및 결제 정책
*   **재고 차감 시점 (가선점):** 주문 생성 시점에 재고를 차감(가선점)하고 주문 상태를 결제 대기(`PENDING`)로 설정한다. 
*   **주문/결제 트랜잭션 분리:** 비동기 PG 환경을 고려하여 주문 생성 트랜잭션과 외부 결제 요청 트랜잭션을 분리한다. 결제 타임아웃 발생 시 즉시 취소하지 않고 상태를 유지하며, 콜백 혹은 Redis TTL 만료 후 상태를 확정한다. 실패 시 로컬 Outbox를 활용해 보상 트랜잭션(재고 복구)의 완수를 보장한다.
*   **재고 부족 처리:** 주문 요청 수량이 잔여 재고보다 많을 경우, 해당 주문 건 전체를 실패(Rollback) 처리하고 적절한 에러 메시지를 반환한다.
*   **동시성 제어 (재고):** 다수의 유저가 동일 상품을 동시에 주문할 경우, 재고 수량의 정합성이 깨지지 않도록 **비관적 락(Pessimistic Lock)** 메커니즘을 적용하여 순차 처리를 보장해야 한다.
*   **데드락 방지:** 한 주문에서 여러 상품을 함께 결제할 때 발생할 수 있는 데드락(Deadlock)을 방지하기 위해, 반드시 **상품 ID(PK) 오름차순으로 정렬한 후 순서대로 비관적 락(FOR UPDATE)을 획득**해야 한다.

### 2.2 주문 데이터 불변성
*   **스냅샷 전략:** 주문이 완료된 시점의 상품 정보를 `ORDER_ITEM` 테이블에 직접 복제하여 저장한다.
*   **보관 필드:** 상품명(`name`), 가격(`price`), 브랜드명(`brand_name`) 등 핵심 정보를 컬럼 단위로 저장하여, 원본 상품 정보가 변경되거나 삭제되어도 주문 당시의 정보를 보존한다.
*   **금액 스냅샷:** 주문 전체 수준에서 **쿠폰 적용 전 총액(원가), 할인 금액, 최종 결제 금액**을 모두 영속화하여 이력을 추적한다.

### 2.3 데이터 삭제 정책
*   **논리 삭제 적용:** 브랜드 및 상품 삭제 시 DB에서 레코드를 물리적으로 제거하지 않고, `is_deleted`와 같은 플래그를 사용하여 논리적으로 삭제 처리한다.
*   **연쇄 삭제:** 브랜드 삭제 시, 해당 브랜드에 속한 모든 상품도 함께 논리 삭제 처리되어야 한다.

### 2.4 사용자 식별 및 보안
*   **인증/인가:** 별도의 인증 프레임워크를 사용하지 않으며, 아래의 HTTP 헤더를 통해 사용자를 식별한다.
    *   **일반 유저:** `X-Loopers-LoginId` (아이디), `X-Loopers-LoginPw` (비밀번호)
    *   **어드민 유저:** `X-Loopers-Ldap` (값: `loopers.admin`)
*   **접근 제어:** 유저는 본인의 정보 및 주문 내역만 조회/수정할 수 있으며, 타인의 데이터에 직접 접근할 수 없다.

### 2.5 좋아요 및 정렬 정책
*   **좋아요 수 관리 (Eventual Consistency):** 상품 목록의 '좋아요 많은 순' 정렬을 위해 핵심 상품 메타데이터와 집계 데이터를 분리한다. `PRODUCT` 테이블에서는 좋아요 수를 제거하고, 통계 및 집계를 전담하는 분리된 시스템(또는 테이블)인 `PRODUCT_METRICS`에 이벤트를 발행하여 최종 일관성(Eventual Consistency)으로 누적 합산한다.
*   **좋아요 로직과 집계 트랜잭션 분리:** 
    *   좋아요 데이터(Like) 추가/삭제 자체는 사용자의 요청 트랜잭션 안에서 즉시 완료하여 응답 속도를 높인다.
    *   동일 트랜잭션 내에서 `OUTBOX_EVENTS` 테이블에 이벤트를 기록(INIT)하며, 이후의 실제 `like_count` 증감은 모두 **Kafka 컨슈머(Outbox 패턴)**를 통해 처리하여 책임을 완전히 분리한다. (직접 비동기 업데이트는 이중 카운팅 방지를 위해 사용하지 않는다.)
*   **전달 보장 (Guaranteed Delivery):** 배치 스케줄러가 Outbox 테이블의 이벤트를 주기적으로 읽어 Kafka로 유실 없이 안전하게 전송함으로써 정확한 통계 수치를 최종 보장한다.
*   **멱등성 및 롤백 방지:** 이미 좋아요를 누른 상품에 대해 중복 요청이 올 경우, DB의 Unique Key 제약조건 위반 예외가 발생하여 트랜잭션이 강제로 `rollback-only` 상태가 되는 것을 차단하기 위해 **좋아요 이력을 선조회(Exist Check)하여 분기(멱등성 리턴)**해야 한다.
*   **삭제 데이터 처리:**
    *   상품이나 브랜드가 논리 삭제된 경우, '나의 좋아요 목록'에서 자동으로 제외한다.
    *   이미 삭제된 상품에 대해 좋아요를 등록하려고 시도할 경우 에러를 반환한다.

### 2.6 쿠폰 및 할인 정책
*   **쿠폰 발급 및 선착순 정책 (Kafka 비동기):** 
    *   특정 쿠폰의 경우 발급 한도(`totalQuantity`)가 존재하여 선착순 발급이 진행된다.
    *   **1차 검증 (API 계층):** 트래픽이 몰릴 때 시스템 부하를 막기 위해, API 서버(Producer)에서 **Redis의 INCR 연산과 Set**을 활용하여 수량 초과 여부와 중복 발급 여부를 1차적으로 고속 검증한다. (빠른 실패, Fail Fast)
    *   **비동기 발행:** 검증을 통과한 요청만 Kafka에 '발급 요청 이벤트'로 발행하고, 클라이언트에게는 즉시 비동기 처리를 위한 고유 `requestId`와 202 Accepted를 반환한다.
    *   **최종 발급 (Consumer 계층):** Kafka Consumer가 이벤트를 수신하여 DB 트랜잭션 내에서 `COUPON_TEMPLATES`의 비관적 락을 획득, 최종 수량 검증을 수행하고 발급 상태를 갱신(Redis 상태 업데이트 포함)한다.
    *   **발급 결과 확인:** 클라이언트는 `requestId`를 이용해 전용 폴링 API(`GET /api/v1/coupons/requests/{requestId}`)를 호출하여 자신의 발급 요청이 성공했는지, 실패했는지 최종 상태(`IN_PROGRESS`, `SUCCESS`, `FAILED`)를 확인한다.
*   **쿠폰 종류 및 할인 계산:**
    *   **정액 쿠폰(`FIXED`):** 주문 금액에서 고정된 할인 금액(원)을 차감한다. (주문 금액이 할인액보다 작을 경우 최종 금액은 0원 미만이 될 수 없다)
    *   **정률 쿠폰(`RATE`):** 주문 금액의 일정 비율(%)을 할인한다. 단, 쿠폰에 **최대 할인 금액 한도(`maxDiscountAmount`)**가 설정된 경우 해당 금액 한도까지만 할인한다.
    *   **할인 계산 책임:** 할인 금액 계산 및 유효 조건(최소 주문금액 대조 등) 검증 로직은 **Coupon 도메인(Entity)이 직접 스스로 수행**하도록 책임을 분리한다. 주문 도메인은 쿠폰 객체에 금액만 전달하여 최종 차감 금액을 반환받는다.
*   **쿠폰 유효 조건:**
    *   쿠폰 템플릿에 등록된 최소 주문 금액(`minOrderAmount`) 조건보다 주문 상품의 총액이 크거나 같을 때만 사용할 수 있다.
    *   쿠폰 만료일(`expiredAt`)이 지나지 않았고, 상태가 사용 가능(`AVAILABLE`)인 본인 소유의 쿠폰이어야 한다.
    *   **만료 판단 방식:** 내 쿠폰 목록 조회 시 만료 상태는 별도의 동기화 배치 없이, **조회 시점에 서버 현재 시간과 만료일을 동적으로 대조하여 실시간으로 가공 및 반환(WAS 가공)**한다.
*   **쿠폰 일회성:** 사용 완료된 쿠폰은 즉시 `USED` 상태로 변경되며 재사용이 불가합니다.
* **쿠폰 동시 사용 방지:** 동일한 발급 쿠폰을 여러 기기나 브라우저에서 동시에 주문에 사용하더라도, **낙관적 락(Optimistic Lock)**을 통해 단 한 번만 사용되도록 방어하고 중복 요청은 에러를 반환한다.

### 2.7 상품 조회 캐시 전략 및 가용성 정책
*   **상품 상세 조회 캐시 (`getProduct`):**
    *   **전략:** 조회 시 Redis 캐시 확인 후 DB를 조회하는 `Read-Through(Cache-Aside)` 방식을 적용한다. 
    *   **정합성 및 TTL:** 모든 상세 정보를 묶어 캐싱(TTL 10분)하여 성능을 극대화한다. 조회수나 잔여 재고 등의 약간의 지연(Eventual Consistency)은 허용하되, 최종 결제 시점에 DB 락을 통해 정합성을 방어한다.
    *   **무효화:** `ProductAdminFacade`에서 상품 정보가 수정/삭제될 때만 수동으로 무효화(`@CacheEvict`)한다.
*   **상품 목록 조회 캐시 (`getProducts`):**
    *   **전략:** 파라미터 조합(brandId, sort, page, size)을 키로 사용하여 `Read-Through` 방식을 적용한다.
    *   **정합성 및 TTL:** 복잡한 연관 캐시를 찾아 지우는 것이 불가능하므로, 무효화(Evict) 전략은 사용하지 않고 대신 5분의 짧은 TTL을 부여하여 자연 소멸되도록 한다.
*   **장애 대응 (Fallback):** Redis 타임아웃이나 다운 등 캐시 장애 발생 시, 애플리케이션 계층(Facade 등)에서 `RedisTemplate`의 동작을 명시적으로 `try-catch`로 묶어 예외를 무시(Swallow)하고 즉시 원본 DB 조회를 수행하도록 하여 시스템 전체 장애를 방지한다.
*   **메모리 초과 관리:** 상품 목록의 수많은 페이징 조건 등으로 인한 메모리 누수를 방지하기 위해, Redis의 Eviction Policy를 `volatile-lru`로 설정하여 TTL이 지정된 데이터 중 안 쓰는 데이터부터 안전하게 삭제한다.

### 2.8 비동기 결제 및 멱등성 정책
*   **주문 생성 멱등성 (재시도 및 동시성 방어):** 
    *   동일한 주문 요청의 중복 생성을 방지하기 위해 클라이언트로부터 멱등성 키(Idempotency-Key)를 받아 검증한다.
    *   **Redisson의 분산 락(RLock)**을 활용해 동시성(Race Condition)을 제어하며, 사용자 간 충돌을 막기 위해 **키를 유저 ID와 함께 네임스페이스(`order:create:{userId}:{idempotencyKey}`)로 분리**한다.
    *   단순한 TTL 기반의 락이 아닌 **Watchdog을 통한 락 자동 갱신(Auto-Renewal)**과 **락 소유자(Thread ID) 검증을 통한 안전한 해제(Check-and-Delete)**로 타임아웃 및 락 탈취 문제를 방지한다.
    *   주문 생성 중 예외 발생 시, 락을 즉시 해제하여 클라이언트가 동일한 키로 재시도할 수 있도록 허용한다.
    *   성공 시 네임스페이스 키와 **요청 본문의 페이로드 해시(Fingerprint)**, 주문 ID를 함께 캐싱한다.
    *   동일한 요청 재인입 시 **저장된 해시값과 현재 요청의 해시값이 일치하면** 새로운 트랜잭션 없이 기존 주문 ID를 반환(200 OK)하고, **해시값이 다르면 `422 Unprocessable Entity` 에러를 반환**한다.
*   **결제 중복 방지 및 실패 보정:**
    *   결제 이중 출금을 막되, 결제 실패 후 재결제가 영원히 차단되는 문제를 막기 위해 `PAYMENTS` 테이블의 `order_id` Unique Index는 설정하지 않는다.
    *   대신 결제 요청 시 DB를 조회하여 해당 `order_id`로 `READY` 또는 `APPROVED` 상태인 결제가 존재하는지 애플리케이션 레벨에서 먼저 검증한다.
    *   **조회부터 결제 내역 저장까지의 과정을 Redisson 분산 락(`payment:lock:{orderId}`)으로 묶어** 원자성(Atomicity)을 보장함으로써, 동시 요청 시 중복 활성 결제가 생성되는 것을 원천 차단한다.
    *   중복 결제(이미 진행 중이거나 완료됨)가 감지된 경우, HTTP 409 Conflict 에러를 반환하여 클라이언트가 명확히 인지하도록 한다.
*   **신뢰 기반 콜백 처리 및 동시성 방어:** PG사에서 전송하는 비동기 콜백을 신뢰하여 상태 재조회 없이 처리하되, 10초 TTL 기반의 결제 지연 보정 스케줄러와 동시에 실행될 경우를 대비해 **Redisson 분산 락(`payment:lock:{orderId}`)**을 획득한 후 처리하여 갱신 경합을 방지한다.
*   **보상 트랜잭션의 비동기 분리 (부분 실패 방어):** 결제 실패 콜백 수신 또는 TTL 만료 시 발생하는 재고/쿠폰 복구(보상 트랜잭션) 과정에서 외부/내부 장애로 인한 롤백을 방지하기 위해, 결제 상태를 `FAILED`로 변경하는 본 트랜잭션 내에 **보상 이벤트를 `OUTBOX_EVENTS`에 기록**하고, 별도의 스케줄러가 이를 비동기로 안전하게 복구(Eventual Consistency)하도록 책임을 분리한다.
*   **결제 응답 지연 및 유실 보정 (In-memory Scheduler Retry):** PG사의 콜백 응답이 지연되거나 유실되는 경우를 대비해, 결제 요청 시 애플리케이션 내의 스케줄러(`ScheduledExecutorService` 등)를 통해 10초 뒤 상태를 재확인하도록 비동기 작업을 등록한다. (서버 다운 시 인메모리 스케줄러가 유실되더라도 30분 Fallback 배치가 최종 복구를 보장한다.)
    *   **Retry 정책:** 10초 뒤 스케줄러가 실행되어 PG사에 결제 상태를 재확인(Retry)한다. 미결제/대기 상태라면 재시도 횟수를 증가시키고 다시 10초 뒤 재확인 작업을 스케줄링한다. (최대 3회 재시도, 총 30초 대기)
    *   **최종 실패 및 보상 트랜잭션:** 3회 재시도(30초 경과) 후에도 결제가 완료되지 않으면 결제를 강제 실패(`FAILED`) 처리하고, 가선점된 **재고와 사용된 쿠폰을 모두 롤백(복구)** 한다.
    *   **재시도 유도 (Active Notification):** 최종 실패 처리 완료 즉시, 사용자에게 앱 푸시/알림톡 등 능동적인 알림을 발송하여 결제 시간 초과 및 취소를 안내하고 재주문을 유도한다. (※ 구현 스코프에서는 알림 발송 인프라 직접 연동은 제외하며 개념상 명시)
*   **최후의 보루 (Fallback Batch):** Redis 서버 장애 등으로 인해 TTL 이벤트가 유실되어 DB에 장기간 `READY` 상태로 남는 결제 건을 보정하기 위해, 30분 주기로 스케줄러를 실행하여 생성된 지 30분이 지난 누락 건들에 대해 자동 보정을 수행한다.
    *   **상태 재조회 결과가 실패/대기(`FAILED`/`PENDING`)인 경우:** 최종 실패 처리 및 보상 트랜잭션(재고/쿠폰 복구)을 수행한다. (뒤늦은 스팸을 막기 위해 일반 타임아웃 안내 발송은 제외)
    *   **상태 재조회 결과가 성공(`APPROVED`)인 경우:** 이미 30분이 초과되어 배송 처리가 불가하므로 즉시 **PG사 결제 취소(환불) API를 호출**하고, 사용자에게 **환불 완료 알림**을 발송한 뒤 환불(`REFUNDED`) 상태로 갱신 및 보상 처리한다.

### 2.9 장애 격리 및 서킷 브레이커
*   **서킷 브레이커 (Circuit Breaker) 도입:** 외부 연동(PG사 결제 API 등) 구간에 서킷 브레이커 패턴을 적용하여, 외부 시스템 장애가 내부 시스템의 스레드 고갈 및 연쇄 장애(Cascading Failure)로 이어지지 않도록 보호한다.
    *   **적용 대상 분리 (우회 원칙):** 서킷 브레이커는 실시간 유저 트래픽이 유입되는 **신규 결제 요청(`requestPayment`)에만 적용**한다. 백그라운드에서 동작하는 **결제 상태 재조회(`queryPaymentStatus`) 및 수동 복구 API는 서킷 브레이커를 우회**하도록 설계하여, 신규 결제가 차단된 상태에서도 기존 지연 건에 대한 복구 및 정합성 보정이 정상적으로 수행되도록 보장한다.
*   **빠른 실패 (Fail Fast) 응답:** PG사 시스템 완전 장애로 인해 서킷 브레이커가 `Open`(차단) 상태가 되면, 새로운 결제 요청 유입 시 불필요한 내부 로직(주문 생성, 데이터베이스 접근 등)을 수행하지 않고 즉시 사용자에게 에러 응답("현재 외부 결제 시스템 장애로 결제가 일시 중단되었습니다")을 반환하여 시스템을 보호한다.
*   **상태 복구 및 동기화 (Eventual Consistency):** 일시적인 네트워크 지연이나 타임아웃 발생 시 즉각 실패로 처리하지 않고, 앞서 언급된 Redis TTL 기반의 연쇄 재시도 및 30분 주기 스케줄러(Fallback Batch), 수동 상태 복구 API(`/status-sync`)를 통해 최종적으로 상태를 복구 및 일치시킨다.

### 2.10 이벤트 기반 부가 로직 분리
*   **유저 행동 로깅 (Action Logging) - 중요도에 따른 이원화:**
    *   조회, 일반 상품 클릭, 단순 좋아요 시도 등 일반적인 행동 수집은 시스템 응답성과 성능을 최우선으로 하여 `@EventListener`와 `@Async`를 통해 **Fire-and-Forget (단순 비동기 전송, 유실 허용)** 방식으로 처리한다.
    *   **결제 시도, 장바구니 담기, 주문 실패** 등 마케팅/매출 및 CS 대응과 직결된 핵심 퍼널 로그는 이벤트 유실을 방지하기 위해 **Outbox 패턴 (로컬 DB 저장 후 스케줄러 보정)**을 적용하여 전달을 보장(Guaranteed Delivery)한다.
    *   두 경우 모두 본 트랜잭션의 성공/롤백 여부와 무관하게 사용자의 **"시도 자체"**를 기록하는 것을 원칙으로 한다.
*   **주문/결제 완료 알림 (Notification) 발송 분리:**
    *   결제 성공 시 푸시/알림톡을 보내는 행위는 외부 써드파티 장애 시 커머스 API에 영향을 주지 않도록 **외부 알림 전용 시스템(Notification System)**으로 분리한다.
    *   이를 위해 커머스 API는 트랜잭션 내에서 결제 완료 이벤트를 `OUTBOX_EVENTS`에 저장하고 Kafka로 발행하기만 하며, 알림 발송 및 실패 시 재시도 책임은 알림 전용 컨슈머에게 완벽히 위임한다.

### 2.11 Kafka를 활용한 시스템 간 이벤트 파이프라인
*   **목적:** 커머스 API(주문, 좋아요 등)의 핵심 트랜잭션과 무관하게, 통계 데이터 집계 및 **외부 알림 발송** 등의 책임을 외부 시스템(Collector, Notification)으로 완벽히 분리한다.
*   **Transactional Outbox 패턴 도입 (발행 보장):**
    *   이벤트 발행 중 유실을 방지하기 위해, 비즈니스 엔티티 저장과 함께 동일 트랜잭션 내에서 `OUTBOX_EVENTS` 테이블에 이벤트(상태 `INIT`)를 저장한다.
    *   애플리케이션 내의 폴링(Polling) 스케줄러(`@Scheduled`)가 주기적으로 `INIT` 상태의 이벤트를 읽어 Kafka로 전송하고, 전송 성공(Ack) 시 `PUBLISHED` 상태로 갱신한다.
*   **Producer 설정:**
    *   `acks=all`, `idempotence=true` 설정을 통해 Kafka 브로커 측의 유실 방지 및 중복 발행을 방어한다.
    *   **Partition Key 정책:** `product_id`를 파티션 키로 지정하여, 특정 상품에 대한 이벤트 소비 순서를 완벽하게 보장한다. (단, 핫 파티션 문제 발생 시 Consumer 랙 모니터링 필요)
*   **Consumer 멱등성 및 순서 보장 (소비 보장):**
    *   수동 커밋(Manual Ack)을 사용하여 일자별 `product_metrics` 집계 및 DB 반영 트랜잭션이 완벽히 성공했을 때만 오프셋을 갱신한다.
    *   `event_handled` 테이블을 도입하여 메시지의 고유 식별자(새로운 UUID 생성 없이 `OUTBOX_EVENTS`의 `bigint id`를 그대로 재사용)를 단건 저장해 중복을 필터링한다. (단, 데이터가 무한히 쌓이는 것을 방지하기 위해 주기적 삭제 배치가 필요하다.)
    *   파티션 키로 순서가 보장되며, 멱등성 테이블로 중복 수신이 방어되므로 `PRODUCT_METRICS`는 `(metric_date, product_id)` 단위 증감(Delta) 처리를 수행하여 하루치 메트릭을 유지한다.

### 2.12 상품 랭킹 정책
*   **목적:** 조회/좋아요/주문 이벤트 기반의 인기상품 랭킹을 일간/주간/월간으로 제공한다. Redis ZSET은 실시간 일간 랭킹 조회를 위한 파생 Read Model로 사용하고, `PRODUCT_METRICS`는 주간/월간 Batch 집계의 기준이 되는 일자별 메트릭 테이블로 유지한다.
*   **이벤트 날짜 기준:** 랭킹 일자는 Consumer 처리 시각이 아니라 **이벤트 발생 시각**을 기준으로 계산한다. 지연 소비가 발생하더라도 실제 7월 14일에 발생한 이벤트는 `ranking:all:20260714`에 반영한다.
*   **Redis Key 전략 및 TTL:**
    *   랭킹 ZSET Key는 `ranking:all:{yyyyMMdd}` 형식을 사용한다.
    *   Redis 멱등성 Set Key는 `ranking:handled:{yyyyMMdd}` 형식을 사용한다.
    *   두 Key 모두 TTL은 2일로 설정한다.
*   **랭킹 콜드 스타트 완화:** 일자 변경 직후 오늘 랭킹이 비어 있거나 부족한 문제를 줄이기 위해 `RankingCarryOverJob`이 전일 랭킹 일부를 오늘 랭킹 초기 점수로 이월한다.
    *   매일 00:10 이전에 전일 `ranking:all:{yesterday}` Top 1,000을 읽어 오늘 `ranking:all:{today}`에 `yesterdayScore * 0.1` 점수로 합산한다.
    *   00:10 이후에는 오늘 실시간 이벤트가 이미 쌓이기 시작했다고 보고 carry over를 skip한다.
    *   carry over 점수는 오늘 실시간 이벤트 점수와 같은 ZSET에 합산하며, `ranking:handled:{yyyyMMdd}`는 Kafka 이벤트 중복 방지 전용으로 유지한다.
    *   중복 실행 방지는 `ranking:carry-over:done:{yyyyMMdd}` Key로 처리하고 TTL은 2일로 둔다.
    *   전일 랭킹 Key가 없거나 비어 있으면 점수 적재 없이 done key만 기록한다.
    *   carry over 전 Top 1,000 상품을 DB 조회해 삭제/미존재 상품은 제외한다. 삭제/미존재 상품 제외는 정상 처리로 보고, DB 인프라 예외는 carry over 실패로 처리한다.
    *   carry over 실패는 `RankingKafkaConsumer`의 실시간 랭킹 적재를 막지 않는다.
    *   `RankingRebuildJob`은 Redis 유실 복구, `RankingCarryOverJob`은 일자 변경 콜드 스타트 완화로 책임을 분리한다.
*   **점수 계산 정책:** 이벤트 타입별 Weight와 Score를 곱해 ZSET 점수를 누적한다.
    *   조회 이벤트: `0.1 * 1`
    *   좋아요 이벤트: `0.2 * 1`
    *   주문 이벤트: `0.6 * log(price * amount + 1)`
    *   주문 점수는 주문 금액의 영향은 반영하되, 고가 상품 1건이 랭킹을 과도하게 지배하지 않도록 로그 정규화를 적용한다.
    *   `MetricsKafkaConsumer`는 같은 점수 정책으로 계산한 `daily_ranking_score`를 `product_metrics`에 일자별로 누적한다. 주간/월간 Batch는 이벤트를 다시 해석하지 않고 기간 내 `daily_ranking_score`를 합산한다.
*   **Consumer 처리 책임:** `MetricsKafkaConsumer`와 `RankingKafkaConsumer`를 분리한다. 두 Consumer는 같은 상품 이벤트 토픽을 서로 다른 Consumer Group으로 독립 소비한다. `MetricsKafkaConsumer`는 일자별 `product_metrics` 집계를 담당하고, `RankingKafkaConsumer`는 Redis 일간 랭킹 Read Model 갱신만 담당한다.
*   **독립 Projection 및 최종 일관성:** `PRODUCT_METRICS`와 Redis 랭킹은 서로 다른 목적의 Projection이다. 한쪽 Consumer의 지연이나 장애가 다른 쪽 처리를 막지 않으며, 두 Projection은 Consumer 재시도를 통해 최종적으로 수렴한다.
*   **공통 계약 모듈 분리:** 랭킹 관련 비즈니스 계약과 이벤트 로그 조회 계약은 기술 모듈에 두지 않는다.
    *   `modules/ranking-contract`: `ProductRankingEvent`, `RankingEventType`, `RankingScorePolicy`, `RankingKeyPolicy`를 제공한다. `commerce-api`와 `commerce-streamer`는 같은 랭킹 Key/date/score 정책을 사용한다.
    *   `modules/event-contract`: `OutboxEventLog` 읽기 전용 계약을 제공한다. `OutboxEventLog`는 `id`, `eventType`, `status`, `payload`, `createdAt`을 가진다. JPA Entity는 공용화하지 않고 각 애플리케이션의 인프라 구현에 둔다.
*   **Ranking Consumer Ack 정책:** `RankingKafkaConsumer`는 Redis 반영에 성공한 뒤에만 Kafka offset을 커밋한다. Redis 장애 중에는 Ranking Consumer lag이 쌓일 수 있으나, Redis 복구 후 미처리 이벤트를 재소비해 랭킹을 따라잡는다.
*   **Redis 멱등성:** Kafka 재처리로 인한 `ZINCRBY` 중복 가산을 막기 위해, 조회/좋아요/주문 이벤트는 `ranking:handled:{yyyyMMdd}` Set에 `eventId`를 먼저 저장한다. 최초 저장에 성공한 이벤트에 대해서만 `ranking:all:{yyyyMMdd}`에 점수를 누적한다. 상품 삭제 이벤트는 `ZREM` 자체가 멱등적이므로 별도 Redis handled Set을 사용하지 않는다.
*   **랭킹 표준 이벤트:** Outbox에는 랭킹 표준 이벤트를 `PRODUCT_RANKING_EVENT` 단일 `event_type`으로 기록한다. 실제 행위는 payload 내부 `rankingEventType`(`VIEW`, `LIKE`, `ORDER`, `PRODUCT_DELETED`)으로 구분한다. DB에 저장되는 raw payload에는 `eventId`를 넣지 않고, Kafka Relay 또는 재빌드 조회 구현이 `OUTBOX_EVENTS.id`를 `ProductRankingEvent.eventId`로 주입한다.
*   **Kafka 배치 리스너 (선택적 최적화):** 기본 설계는 단건 이벤트 처리로 검증한다. 트래픽 증가로 ZSET/DB 연산이 과도해질 경우 배치 리스너를 적용해 `(date, productId)` 단위로 점수를 합산한 뒤 Redis Pipeline 및 DB Batch Update로 처리량을 높인다.
*   **주간/월간 랭킹 Batch:** Spring Batch Job은 `period`, `startDate`, `endDate` 파라미터를 받아 Chunk-Oriented 방식으로 `product_metrics`를 읽고, 상품별 `daily_ranking_score`를 기간 합산한다.
    *   `WEEKLY`: 월요일~일요일 7일 범위만 허용한다.
    *   `MONTHLY`: 매월 1일~말일 범위만 허용한다.
    *   Batch는 현재 논리 삭제된 상품을 제외하고 Top 100만 `mv_product_rank_weekly`, `mv_product_rank_monthly`에 저장한다.
    *   MV 테이블은 DB의 네이티브 Materialized View가 아니라 Batch가 적재하는 조회 전용 물리 테이블로 정의한다.
    *   Batch는 `batch_run_id` 기반 Versioned Snapshot 방식으로 동작한다. 새 실행 결과는 `is_active=false` 상태로 먼저 적재하고, 검증이 끝난 뒤 짧은 트랜잭션에서 기존 active 결과를 비활성화하고 새 결과를 active로 전환한다.
    *   같은 기간 재실행 시 새 `batch_run_id`로 별도 Snapshot을 만들며, 실행 중 실패하면 기존 active 랭킹을 유지한다.
    *   **잠재 리스크:** 실행 이력이 누적되므로 오래된 `batch_run_id` 결과를 정리하는 보관/삭제 정책이 필요하다.
    *   **잠재 리스크:** 주간/월간 랭킹은 Batch 실행 시점의 상품 삭제 상태를 기준으로 하므로, 과거 기간 랭킹도 현재 삭제 상태의 영향을 받을 수 있다.
*   **Ranking API 조회:** `GET /api/v1/rankings?period=DAILY&startDate=yyyyMMdd&endDate=yyyyMMdd&size=20&page=1` 형식으로 기간 정보를 전달받는다. `DAILY`는 `startDate == endDate`인 경우만 허용하고 Redis ZSET에서 조회한다. `WEEKLY`, `MONTHLY`는 Batch가 적재한 MV 테이블의 active Snapshot에서 상품 ID와 점수를 읽고, 상품 Repository로 상품/브랜드 정보를 조회해 랭킹 응답을 조합한다. active Snapshot이 아직 없으면 빈 페이지를 반환한다.
*   **삭제 상품 처리:** 상품이 논리 삭제되면 상품 삭제 트랜잭션 안에서 `PRODUCT_RANKING_EVENT` Outbox 이벤트를 기록하고, payload의 `rankingEventType`은 `PRODUCT_DELETED`로 둔다. 기존 Outbox Relay가 Kafka 발행 시 `OUTBOX_EVENTS.id`를 `eventId`로 주입하고, `RankingKafkaConsumer`는 이 이벤트를 소비해 최근 TTL 범위의 랭킹 ZSET에서 해당 `productId`를 제거한다. 현재 TTL이 2일이므로 삭제 시점 기준 오늘/전일 Key(`ranking:all:{yyyyMMdd}`)에서 `ZREM`을 수행한다. API 조회 시에는 삭제 이벤트 처리 지연 등으로 ZSET에 남아 있는 삭제 상품을 2차 방어로 제외한다.
*   **Redis 유실 복구:** Redis 데이터가 유실된 경우, `commerce-streamer`의 `RankingRebuildEventRepository` 구현이 `OUTBOX_EVENTS`에서 `PRODUCT_RANKING_EVENT`를 조회해 `ProductRankingEvent`로 변환한다. 조회 대상 상태는 `INIT`, `COMPLETED`이며 `FAILED`는 제외한다. 후보 조회는 `createdAt` 기준 최근 3일 버퍼를 두고, 실제 랭킹 반영 여부는 payload 내부 `occurredAt`이 오늘/전일인지로 판단한다. 재빌드 Job은 조회한 이벤트를 `ranking:rebuild:all:{yyyyMMdd}` 임시 Key에 재계산한 뒤 운영 Key(`ranking:all:{yyyyMMdd}`)로 교체한다. 운영 Key 교체 중 `RankingKafkaConsumer`와의 충돌 방지는 후속 운영 절차로 남기며, Consumer 일시 중단 또는 기준 시각 이후 이벤트 replay 중 하나를 선택해야 한다.
*   **상품 상세 랭킹 포함:** `GET /api/v1/products/{productId}` 응답에는 서버의 오늘 날짜 기준 랭킹 정보를 함께 반환한다. 해당 상품이 오늘 랭킹에 없으면 랭킹 정보는 `null`로 반환한다.

### 2.13 글로벌 대기열 (Queue) 정책
*   **목적:** 이벤트 등 대규모 트래픽 발생 시, 백엔드 서버(DB 포함)를 보호하고 사용자에게 명확한 대기 상태를 안내하기 위해 인바운드 트래픽을 제어한다.
*   **적용 범위 (Global Scope):** 시스템 전반에 걸쳐 단일 글로벌 대기열을 운용한다. 특정 상품뿐만 아니라 이벤트 기간의 접속 자체를 통제하여 시스템 전체의 안정성을 확보한다.
*   **상태 분리 및 스케줄러 전환 (Waiting / Active):**
    *   **Waiting Queue:** 유저 진입 시 Redis Sorted Set에 저장된다. Score는 진입 시간(Timestamp)이다.
    *   **Active Set (입장 토큰):** 실제로 주문 등 핵심 API에 접근할 수 있는 권한을 가진 유저들의 Set이다. Active 상태가 된 유저만 주문 API에 진입할 수 있다.
    *   **전환 및 TTL (재시도 허용):** 백그라운드 스케줄러가 주기적으로 Waiting Queue에서 가장 오래 대기한 N명을 `pop`하여 Active Set으로 이동시킨다. 발급된 토큰은 5분 등의 TTL을 가지며, 이 시간 내에는 결제가 실패(잔고 부족 등)하더라도 토큰이 파기되지 않고 유지되어 고객의 재시도를 보장한다. (단, 주문/결제가 최종 완료되면 명시적으로 삭제한다.)
    *   **검증 경계 (Interceptor 차단):** 토큰 검증은 비즈니스 로직(Facade) 내부가 아닌, Spring 웹 계층(Interceptor 등)에서 수행하여 토큰이 없는 요청을 조기에 차단(`401 Unauthorized` 등)함으로써 DB 커넥션 풀을 원천 보호한다.
*   **스케줄러 통과 인원(배치 크기 N) 산정 근거 및 K6 부하테스트 결과:**
    *   유저 관점에서 대기열 통과는 "주문 생성 -> 결제 수단 입력 -> 최종 결제 승인"까지의 전체 여정(Journey)을 의미하므로, 단일 주문 DB 트랜잭션 속도만 고려해서는 안 되며 **전체 파이프라인의 종합 병목(Bottleneck)**을 기준으로 산정해야 한다.
    *   **이론적 수식:** `N = Min(주문 DB TPS, 결제 DB TPS, 외부 PG사 연동 TPS) * 스케줄러 주기 * 안전율`
    *   **k6 부하 테스트 측정 (End-to-End 결제 시나리오):** 더미 데이터(상품 10만 개, 유저 1만 명) 적재 후, 사용자 50명이 30초간 지속적으로 주문 후 결제를 요청한 결과 평균 응답 속도 약 79.77ms 및 시스템의 **종합 병목 TPS가 약 42.41건/초**로 측정되었다. 
    *   **최종 산정 (N 튜닝):** 측정된 42.41 TPS에, 결제 실패(PG 40% 등) 시 재시도하는 유저 트래픽을 고려해 안전율 50%(0.5)를 적용한다. 스케줄러 주기를 1초로 설정할 경우, `N = 42.41 * 1 * 0.5 = 21.2` 이 산출되므로 스케줄러는 1초마다 **N=21명**을 통과시키도록 튜닝한다.
*   **대기 시간 계산 (고정 처리량 방식):**
    *   예상 대기 시간은 `(내 앞의 대기 인원 수) / (시스템의 초당 고정 처리 허용량 N)` 으로 단순 계산하여 반환한다. (메모리 연산 기반으로 속도 최적화)
*   **실시간 순번 조회 및 Polling 정책:**
    *   대기 중인 유저가 실시간으로 순번과 예상 대기 시간을 확인할 수 있도록 `GET /position` API를 제공한다.
    *   **부하 통제 (동적 Polling 주기):** 클라이언트는 서버가 응답으로 내려주는 `pollingInterval`에 따라 다음 조회를 수행한다. (순번 1~100: 1초마다 조회, 순번 100~1000: 3초마다 조회, 순번 1000+: 5초마다 조회) 서버는 IP 또는 유저 단위로 Rate Limit을 설정하여 과도한 호출 시 `HTTP 429 Too Many Requests` 로 방어한다.
    *   **토큰 발급 책임 분리:** 조회 API가 직접 토큰을 생성하지 않는다. 토큰 발급은 오로지 위에서 명시된 백그라운드 **스케줄러**가 담당하며, 순번 조회 API는 단순히 Active 상태인지 확인하고 발급된 토큰을 응답에 포함하여 반환한다.
*   **중복 진입 방지:** 동일한 `userId`가 대기열에 이미 존재할 경우 새로 추가하지 않고 기존 순번을 반환한다.

---

## 3. 기능 요구사항 (API List)

### 3.1 유저 (Users)
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| PUT | `/api/v1/users/password` | O | 비밀번호 변경 |

### 3.2 브랜드 & 상품 (Brands / Products)
#### 고객용 기능
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 상품 목록 조회 (필터 및 정렬 지원) |
| GET | `/api/v1/products/{productId}` | X | 상품 상세 정보 조회 |

*   **상품 목록 조회 파라미터:**
    *   `brandId`: 특정 브랜드 필터링 (다중 선택은 제외하고 단일 ID 매칭 원칙 유지)
    *   `sort`: `latest` (기본), `likes_desc`
        *   `likes_desc` 정렬 시, 무거운 조인(Group By) 없이 `PRODUCT_METRICS` 테이블과 1:1 JOIN하여 `like_count` 기준 내림차순 정렬을 수행한다.
        *   `likes_desc` 정렬 시, 좋아요 개수가 동일한 상품은 최신 등록 순(`latest`)으로 2차 정렬한다.
    *   `page`, `size`: 페이징 지원 (기본 0, 20)

*   **상품 상세 응답 확장:**
    *   상품 상세 조회 응답에는 오늘 날짜 기준 랭킹 정보(`rank`, `score`, `date`)를 포함한다.
    *   해당 상품이 오늘 랭킹에 없으면 랭킹 정보는 `null`로 반환한다.

#### 어드민 기능
| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/brands` | O | 등록된 브랜드 목록 조회 |
| POST | `/api-admin/v1/brands` | O | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | O | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | O | 브랜드 삭제 (연관 상품 논리 삭제 포함) |
| GET | `/api-admin/v1/products` | O | 등록된 상품 목록 조회 (brandId 필터 포함) |
| POST | `/api-admin/v1/products` | O | 상품 등록 (유효한 브랜드 ID 필수) |
| PUT | `/api-admin/v1/products/{productId}` | O | 상품 정보 수정 (브랜드 변경 불가) |
| DELETE | `/api-admin/v1/products/{productId}` | O | 상품 삭제 (논리 삭제) |

### 3.3 랭킹 (Rankings)
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/rankings?period=DAILY&startDate=yyyyMMdd&endDate=yyyyMMdd&size=20&page=1` | X | 일간/주간/월간 인기상품 랭킹 페이지 조회 |

*   **랭킹 조회 파라미터:**
    *   `period`: 조회 기간 유형 (`DAILY`, `WEEKLY`, `MONTHLY`).
    *   `startDate`, `endDate`: 조회 기간 (`yyyyMMdd`).
    *   `DAILY`: `startDate`와 `endDate`가 같아야 하며, Redis 일간 랭킹을 조회한다.
    *   `WEEKLY`: 월요일~일요일 7일 범위만 허용하며, `mv_product_rank_weekly`를 조회한다.
    *   `MONTHLY`: 매월 1일~말일 범위만 허용하며, `mv_product_rank_monthly`를 조회한다.
    *   `page`, `size`: 기존 프로젝트 페이징 정책을 따른다.
*   **응답 정책:**
    *   일간 Redis ZSET 및 주간/월간 MV의 공통 식별자는 `productId`, score는 가중치가 반영된 누적 점수이다.
    *   응답은 단순 상품 ID 목록이 아니라 상품명, 가격, 브랜드명, 랭킹 순위, 랭킹 점수를 함께 제공한다.
    *   주간/월간 active Snapshot이 아직 생성되지 않은 기간은 빈 페이지를 반환한다.
    *   논리 삭제된 상품은 상품 삭제 시 Redis ZSET에서 제거하고, 주간/월간 Batch 집계 시 제외한다. 조회 시점에 남아 있는 삭제 상품은 2차 방어로 제외한다.

### 3.4 좋아요 (Likes)
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 등록 (중복 요청 시 성공 반환) |
| DELETE | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 취소 |
| GET | `/api/v1/users/me/likes` | O | 내가 좋아요 한 상품 목록 조회 (삭제된 상품 제외) |

### 3.5 주문 (Orders)
#### 고객용 기능
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | O | 주문 생성 및 사전 재고 차감 (`orderId` 반환) |
| POST | `/api/v1/payments` | O | 결제 요청 및 재시도 스케줄러 등록 |
| POST | `/api/v1/payments/callback` | X | PG사 비동기 결제 결과 웹훅 (서버 간 통신) |
| GET | `/api/v1/orders` | O | 유저의 주문 목록 조회 (`startAt`, `endAt` 기간 필터) |
| GET | `/api/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

#### 어드민 기능
| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/orders` | O | 전체 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

### 3.6 쿠폰 (Coupons)
#### 고객용 기능
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/coupons/{couponId}/issue` | O | 선착순 쿠폰 발급 요청 (비동기 처리 후 requestId 반환) |
| GET | `/api/v1/coupons/requests/{requestId}` | O | 쿠폰 발급 비동기 요청 결과 상태 조회 (IN_PROGRESS, SUCCESS, FAILED) |
| GET | `/api/v1/users/me/coupons` | O | 내 쿠폰 목록 조회 (AVAILABLE / USED / EXPIRED 상태 반환) |

#### 어드민 기능
| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/coupons?page=0&size=20` | O | 쿠폰 템플릿 목록 조회 |
| GET | `/api-admin/v1/coupons/{couponId}` | O | 쿠폰 템플릿 상세 조회 |
| POST | `/api-admin/v1/coupons` | O | 쿠폰 템플릿 등록 (정액/정률 타입 및 할인값, 최대 할인 한도 등 입력) |
| PUT | `/api-admin/v1/coupons/{couponId}` | O | 쿠폰 템플릿 수정 |
| DELETE | `/api-admin/v1/coupons/{couponId}` | O | 쿠폰 템플릿 삭제 |
| GET | `/api-admin/v1/coupons/{couponId}/issues?page=0&size=20` | O | 특정 쿠폰의 발급 내역 조회 |
### 3.7 대기열 (Queue)
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/queue/enter` | O | 대기열 진입 요청 (이미 진입한 경우 기존 정보 반환) |
| GET | `/api/v1/queue/position` | O | 현재 대기 순번, 예상 대기 시간 및 전체 대기 인원 조회 |


## 4. 기술적 제약 사항 및 고려 사항
- **데이터 정합성:** 재고 확인 및 차감은 원자적(Atomic)으로 수행되어야 한다.
- **보상 트랜잭션 기반 분산 처리:** 비동기 PG 연동의 응답 지연(최대 5초)으로 인한 DB 커넥션 고갈을 막기 위해, 주문 생성과 결제 요청 트랜잭션을 분리한다. 트랜잭션 분리로 인해 결제 실패/타임아웃 시 자동 롤백이 불가하므로, 명시적인 보상 트랜잭션(Compensating Transaction)을 통해 재고와 쿠폰을 복구해야 한다.
- **예외 처리 규칙:** 비즈니스 예외는 모두 `CoreException(ErrorType, customMessage?)`로 통일한다. HTTP 상태/에러 코드는 `ErrorType` enum에서 통합 관리하여 응답한다. 새로운 예외가 필요한 경우 enum 내에서 정의하여 사용한다.
- **확장성 (DIP):** 외부 결제사 연동 등 인프라스트럭처 확장에 대처할 수 있도록 결제 처리는 `PaymentGateway` 인터페이스에 의존하며, 테스트 및 로컬 환경을 위해 가짜 승인을 처리하는 `MockPaymentGateway`를 제공한다.
- **삭제 정책:** 모든 삭제는 `Logical Delete`를 원칙으로 하여 주문 이력과의 참조 무결성을 유지한다.

---

## 5. 설계 검증 체크리스트
### 5.1 Ranking Consumer
*   랭킹 ZSET의 TTL, 키 전략이 `ranking:all:{yyyyMMdd}`, 2일 TTL로 구성되어 있다.
*   Redis 멱등성 Set의 TTL, 키 전략이 `ranking:handled:{yyyyMMdd}`, 2일 TTL로 구성되어 있다.
*   이벤트 발생 시각 기준으로 날짜별 적재 Key를 계산한다.
*   이벤트 발생 후 조회/좋아요/주문 Weight가 Redis ZSET 점수에 반영된다.
*   주문 이벤트 점수는 `0.6 * log(price * amount + 1)`로 계산된다.
*   동일 `eventId`가 재처리되어도 Redis ZSET 점수가 중복 가산되지 않는다.
*   `MetricsKafkaConsumer`와 `RankingKafkaConsumer`는 서로 다른 Consumer Group으로 독립 소비한다.
*   `RankingKafkaConsumer`는 Redis 반영 성공 후 Kafka offset을 커밋한다.
*   Redis 데이터 유실 시 `OUTBOX_EVENTS`의 `PRODUCT_RANKING_EVENT`를 조회해 오늘/전일 랭킹을 재빌드할 수 있다.
*   `OUTBOX_EVENTS` 조회 시 `INIT`, `COMPLETED` 상태를 포함하고 `FAILED` 상태는 제외한다.
*   재빌드 후보는 `createdAt` 기준 버퍼 기간으로 조회하되, 실제 랭킹 날짜는 payload의 `occurredAt` 기준으로 계산한다.

*   `RankingCarryOverJob`이 전일 Top 1,000 랭킹 점수의 10%를 오늘 랭킹 초기 점수로 적재한다.
*   carry over 중복 실행은 `ranking:carry-over:done:{yyyyMMdd}` Key로 방지하고 TTL은 2일이다.
*   carry over는 `ranking:handled:{yyyyMMdd}`를 수정하지 않으며, Kafka 이벤트 멱등성 정책과 분리된다.
*   carry over는 00:10 이후 실행 시 skip된다.
*   carry over 시 삭제/미존재 상품은 제외하고, DB 인프라 예외는 실패로 처리한다.
*   carry over 실패와 무관하게 `RankingKafkaConsumer`의 실시간 랭킹 적재는 계속된다.

### 5.2 Ranking API
*   랭킹 Page 조회 시 정상적으로 랭킹 정보가 반환된다.
*   랭킹 Page 조회 시 단순 상품 ID가 아닌 상품 정보가 Aggregation 되어 제공된다.
*   `period=DAILY`는 `startDate == endDate`인 경우만 허용하고 Redis 일간 랭킹을 조회한다.
*   `period=WEEKLY`는 월요일~일요일 7일 범위만 허용하고 `mv_product_rank_weekly`를 조회한다.
*   `period=MONTHLY`는 매월 1일~말일 범위만 허용하고 `mv_product_rank_monthly`를 조회한다.
*   주간/월간 active Snapshot이 아직 생성되지 않은 기간은 빈 페이지를 반환한다.
*   상품 논리 삭제 시 최근 TTL 범위의 랭킹 ZSET에서 해당 상품이 제거된다.
*   삭제 이벤트 처리 지연 등으로 ZSET에 남아 있는 삭제 상품은 API 응답에서 제외된다.
*   상품 상세 조회 시 오늘 기준 해당 상품의 순위가 함께 반환된다.
*   상품이 오늘 랭킹에 없다면 상품 상세 응답의 랭킹 정보는 `null`이다.

### 5.3 Ranking Batch
*   Spring Batch Job은 `period`, `startDate`, `endDate` 파라미터 기반으로 실행된다.
*   Batch Reader는 Chunk-Oriented 방식으로 기간 내 `product_metrics`를 읽는다.
*   Batch Processor는 상품별 `daily_ranking_score`를 합산하고 현재 논리 삭제된 상품을 제외한다.
*   Batch Writer는 새 `batch_run_id`의 inactive Snapshot으로 Top 100 결과를 INSERT한다.
*   Snapshot 검증이 끝난 뒤 active 전환 Step에서 기존 active Snapshot을 비활성화하고 새 Snapshot을 활성화한다.
*   같은 파라미터로 재실행 중 실패해도 기존 active Snapshot은 유지된다.

### 5.4 E2E 검증
*   이벤트 발행 -> Kafka Consumer 수신 -> `PRODUCT_METRICS` 갱신 -> Redis ZSET 점수 반영 -> API 조회 흐름이 정상 동작한다.
*   일자가 변경되어도 이전 날짜의 랭킹 조회가 TTL 내에서 정상 동작한다.
*   가중치 적용이 의도대로 랭킹 순서에 반영된다.
*   Kafka 배치 리스너 적용 시에도 단건 처리와 동일한 점수 결과가 나온다.
*   주간/월간 Batch 실행 후 Ranking API가 MV 기반 랭킹을 반환한다.
