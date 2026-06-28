# [Plan 3] 주문 및 결제 멱등성 방어 로직 리팩토링 및 구현 (TDD)

## 1. 개요
최근 `/docs/week2`의 설계 문서 변경(결제 Unique Index 제거, 앱 레벨 상태 방어, 주문 Redis 락 방어)에 따라, 기존 구현된 결제 멱등성 코드의 리팩토링 및 신규 주문 멱등성 로직 구현을 TDD 원칙(Red -> Green -> Refactor)과 Tidy First 원칙에 입각하여 진행합니다.

## 2. Phase 1: 결제 도메인 리팩토링 (Unique 해제 및 상태 기반 방어)

### Step 1.1: Repository 구조 변경 (Structural Change)
*   **목표**: 한 주문(`orderId`)에 여러 결제 시도(FAILED 포함)가 존재할 수 있게 되었으므로, 기존의 단건 조회(`findByOrderId`)를 다건 조회 또는 상태 기반 존재 여부 확인으로 변경합니다.
*   **작업 내용**:
    *   `PaymentRepository` 인터페이스 및 구현체에서 `findByOrderId`를 제거하고, `existsByOrderIdAndStatusIn(Long orderId, List<PaymentStatus> statuses)`를 추가합니다.

### Step 1.2: 결제 재시도 및 중복 방어 실패 테스트 작성 (RED)
*   **목표**: `PaymentFacadeTest`에서 변경된 비즈니스 요구사항을 명세하는 실패하는 테스트를 작성합니다.
*   **작업 내용**:
    1.  `processPayment_ExistingFailedPayment_ShouldAllowNewPayment`: 기존 결제가 `FAILED` 상태일 때, 방어 로직을 통과하고 새로운 `READY` 결제가 정상 생성되어야 합니다.
    2.  `processPayment_ExistingReadyOrApprovedPayment_ShouldThrowConflict`: 기존 결제가 `READY` 또는 `APPROVED` 상태일 때, 예외(`CoreException` - 409 CONFLICT)가 발생해야 합니다.

### Step 1.3: 비즈니스 로직 구현 및 엔티티 수정 (GREEN)
*   **목표**: 작성한 실패하는 테스트를 통과시킵니다.
*   **작업 내용**:
    *   `PaymentModel.java`: `orderId` 컬럼의 `@Column(unique = true)` 속성을 과감히 제거합니다.
    *   `PaymentFacade.java`: `processPayment` 내에서 `existsByOrderIdAndStatusIn`을 호출하여, 결과가 `true`면 중복 예외를 던지고, `false`면 새 `READY` 상태의 `PaymentModel`을 저장합니다.
    *   테스트 실행 및 통과 확인.

### Step 1.4: 코드 정리 (Refactor)
*   **작업 내용**: 불필요한 import 제거, 매직 넘버(상태 리스트 등) 상수화, 메서드 추출 등 Tidy First를 적용합니다.
*   **Commit**: `refactor: 결제 멱등성 로직을 상태 기반 방어로 재구현 (Unique 인덱스 해제)`

---

## 3. Phase 2: 주문 도메인 멱등성 구현 (Redis SETNX 도입)

### Step 2.1: 멱등성 검증 인터페이스 및 테스트 작성 (RED)
*   **목표**: 주문 생성 시 `Idempotency-Key`를 검증하는 로직에 대한 실패하는 테스트를 작성합니다.
*   **작업 내용**:
    *   `OrderFacadeTest`의 `createOrder`에 `idempotencyKey` 파라미터(또는 헤더 값 추출 컴포넌트) 처리 추가.
    *   `createOrder_WithDuplicateSuccessKey_ShouldReturnCachedOrderId`: 이미 성공 처리되어 캐싱된 키로 요청 시, DB 트랜잭션을 타지 않고 즉시 기존 `orderId` 반환.
    *   `createOrder_WithConcurrentKey_ShouldThrowConflict`: 락이 걸려있는 상태(다른 스레드가 처리 중)에서 진입 시 예외 발생 방어.

### Step 2.2: 멱등성 방어 로직 구현 (GREEN)
*   **목표**: Redis 기반의 락 및 캐싱 컴포넌트(예: `IdempotencyManager` 인터페이스 생성 및 Stubbing)를 활용하여 테스트를 통과시킵니다.
*   **작업 내용**:
    *   `OrderFacade.createOrder` 진입 시 `IdempotencyManager.lock(key)` 호출.
    *   `try-catch` 구문을 활용하여 에러 발생 시 `finally` (또는 예외 catch) 블록에서 반드시 락을 해제(unlock)하도록 구현.
    *   주문 트랜잭션 성공 시 `IdempotencyManager.saveSuccess(key, orderId)` 호출.

### Step 2.3: 인프라스트럭처 구현 (Structural/GREEN)
*   **작업 내용**: 실제 `RedisTemplate`의 `setIfAbsent`(SETNX)를 활용하는 `RedisIdempotencyManager` 실제 구현체를 인프라 레이어에 작성합니다.

### Step 2.4: 코드 정리 (Refactor)
*   **작업 내용**: 비즈니스 로직(OrderFacade)에 인프라 로직이 섞여 가독성을 해친다면, AOP(Aspect)나 어노테이션 기반으로 멱등성 로직을 분리하는 Tidy First 적용을 검토합니다.
*   **Commit**: `feat: 주문 생성 시 Redis 분산 락 기반 멱등성 검증 로직 추가`
