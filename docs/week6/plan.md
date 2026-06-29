# Week 6 TDD Implementation Plan

본 문서는 `/docs/week2` 에서 업데이트된 비동기 결제 요구사항(연쇄 Redis TTL 재시도, 최종 실패 시 쿠폰 원복, 능동 알림 유도, Fallback 스케줄러)을 TDD 및 Tidy First 원칙에 따라 구현하기 위한 단계별 계획입니다.

## 핵심 목표
1. **결제 트랜잭션 분리 및 비동기 전환:** 기존 `OrderFacade`에 결합된 동기식 결제 로직을 `PaymentFacade`로 분리.
2. **연쇄 Redis TTL 적용:** 10초 간격 최대 3회 재시도(Retry) 메커니즘.
3. **완전한 보상 트랜잭션:** 타임아웃 최종 실패 시 재고뿐만 아니라 **사용 쿠폰 롤백** 처리.
4. **이중 안전장치:** Redis 유실 대비 Fallback Batch 구현.

---

## 단계별 계획 (Red -> Green -> Refactor)

### Step 1: 결제 요청 분리 및 Redis 단기 TTL(10s) 적재
현재 `OrderFacade.checkout`에 동기적으로 묶인 결제 PG 호출을 분리하고, Redis에 Retry 이력을 남깁니다.

*   **Test (Red):** 
    *   `PaymentFacade.processPayment` 호출 시 결제 데이터가 `READY` 상태로 저장되는지 검증.
    *   결제 요청 시 Redis에 `payment_retry:{paymentId}` 키가 값(count)=0, TTL=10초로 적재되는지 검증.
    *   PG API 호출 시 Timeout(500ms 등) 예외가 발생하더라도 롤백되지 않고(Catch) 결제가 진행 중(READY) 상태를 유지하는지 검증.
*   **Implementation (Green):**
    *   `PaymentFacade` 신규 작성 및 `processPayment` 구현.
    *   `RedisTemplate`을 통한 키 설정 로직 추가.
*   **Refactor (Tidy First):**
    *   기존 `OrderFacade`에서 결제 관련 의존성을 제거하고, Controller 레벨 또는 상위 Facade에서 `OrderFacade.createOrder` 후 `PaymentFacade.processPayment`를 이어 호출하도록 구조 리팩토링.

### Step 2: PaymentExpirationListener (Redis 만료 이벤트 수신)
Redis에서 10초 TTL 키가 만료될 때 이벤트를 낚아채는 Listener를 설정합니다.

*   **Test (Red):**
    *   (Spring Context Test 또는 단위 Test) `KeyExpiredEvent` 발생 시 `PaymentExpirationListener.onMessage`가 트리거되며, 내부에서 `PaymentFacade.retryOrCompensatePayment`를 올바른 인자값으로 호출하는지 검증 (Mock 활용).
*   **Implementation (Green):**
    *   `PaymentExpirationListener` 구현.
    *   `RedisMessageListenerContainer` 빈 등록 및 설정.

### Step 3: PaymentFacade.retryOrCompensatePayment - 재시도(Retry) 및 성공 갱신
PG사 상태를 재조회(Retry)하고, 여전히 지연 중이면 10초 연장, 성공했으면 확정 짓는 로직입니다.

*   **Test (Red):**
    *   PG사 조회 결과 '결제 성공'일 경우, `Payment`가 `APPROVED`, `Order`가 `COMPLETED`로 변경되는가?
    *   PG사 조회 결과 '미결제/대기'이고 현재 `count`가 0이나 1일 때, Redis에 `count + 1` 값으로 다시 10초 TTL 키가 재생성되는가?
*   **Implementation (Green):**
    *   `retryOrCompensatePayment` 내부 분기 처리 및 `RedisTemplate` 재적재 구현.
    *   결제 성공 시 트랜잭션을 통한 DB 상태 업데이트.

### Step 4: PaymentFacade.retryOrCompensatePayment - 최종 실패 및 보상 트랜잭션
최대 재시도(3회) 후에도 콜백이 없고 결제가 미완료인 경우의 롤백 처리입니다.

*   **Test (Red):**
    *   `count >= 2` (즉, 3번째 조회)에서 조회 결과가 '미결제'일 때, 결제 상태가 `FAILED`, 주문이 `CANCELED`로 변경되는가?
    *   (재고 롤백) 가선점된 `Stock` 데이터가 정상적으로 `restore` 되는가?
    *   (쿠폰 롤백) 주문에 사용되었던 `CouponIssue` 상태가 다시 `AVAILABLE`로 롤백되는가?
    *   사용자에게 최종 취소를 알리기 위해 `NotificationService.sendPaymentTimeout` (개념적 인터페이스)이 호출되는가?
*   **Implementation (Green):**
    *   실패 확정 로직 및 재고/쿠폰의 보상 트랜잭션 구현.
    *   단순 Notification 인터페이스 및 Mock 객체(또는 No-op 구현체) 연결.
*   **Refactor:**
    *   도메인 모델(`CouponIssue` 등)에 `restore()` 등의 비즈니스 메서드 명시화.

### Step 5: Fallback Batch Scheduler 구현 (최후의 보루)
Redis 서버 장애 등으로 TTL 이벤트를 타지 못해 `READY`로 장기간 멈춘 결제를 보정합니다.

*   **Test (Red):**
    *   생성된 지 일정 시간(예: 30분)이 지난 `READY` 결제 건들에 대해 스케줄러가 상태 조회를 시도하고, 누락된 경우 최종 실패(FAILED) 및 보상 처리를 정상적으로 수행하는가?
*   **Implementation (Green):**
    *   `PaymentFallbackScheduler` 클래스 작성 및 `@Scheduled` 설정.
    *   해당 조건에 맞는 `Payment` 목록을 조회하여 건별로 `retryOrCompensatePayment(결제ID, 999 /*강제실패유도*/)` 등을 호출하는 배치 구성.

---

이 계획은 `Red -> Green -> Refactor` 사이클을 엄격하게 준수하며, 각 Step마다 모든 테스트가 통과된 후에만 다음 Step 또는 리팩토링 단계로 진행합니다.
