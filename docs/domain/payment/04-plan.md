# Payment 도메인 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Commerce-API에 PG 연동 카드 결제 기능을 추가한다. CompletableFuture 기반 Long Polling으로 PG 콜백을 기다리고, timeout 시 1차 Poll로 최종 상태를 확인한다.

**Architecture:** POST /api/v1/payments 수신 시 PaymentEntity(PENDING) 저장 후 PG에 결제 요청. PG가 비동기 처리 완료 후 callbackUrl로 POST. Commerce-API는 CompletableFuture(orTimeout 10s)로 응답을 보류하고 콜백 수신 시 즉시 반환. timeout 시 PG에 직접 1차 Poll(15s). GET /payments/{id}는 DB가 PENDING이면 PG 직접 조회.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Spring MVC CompletableFuture, RestTemplate, Resilience4j (`resilience4j-spring-boot3`), Spring Data JPA, MySQL 8.0 (Testcontainers), JUnit 5, Mockito

## Global Constraints

- 패키지 베이스: `com.loopers`
- 모든 API 응답: `ApiResponse<T>` 래퍼
- 도메인 위반: `CoreException(ErrorType.*)` 발생
- 도메인 엔티티는 `BaseEntity` 상속, JPA 엔티티는 `BaseJpaEntity` 상속
- DB는 모킹하지 않고 Testcontainers(MySQL) 사용
- `PgClient`는 테스트에서 `@MockBean`으로 대체
- 모든 테스트는 `@AfterEach` → `databaseCleanUp.truncateAllTables()`
- 테스트 네이밍: `메서드명_when조건_then결과` 또는 한글 DisplayName

---

## 파일 구조

```
apps/commerce-api/src/main/java/com/loopers/
├── domain/payment/
│   ├── CardType.java                     (enum: 8개 카드사)
│   ├── PaymentStatus.java                (enum: PENDING, SUCCESS, FAILED)
│   ├── PaymentEntity.java                (도메인 모델: approve, fail, isOwnedBy)
│   ├── PaymentRepository.java            (포트 인터페이스)
│   ├── PaymentService.java               (도메인 서비스: prepare/applyPgResponse/settle/markFailed — TX 경계)
│   └── PgClient.java                     (PG 호출 포트 인터페이스)
├── application/payment/
│   ├── PaymentApplicationService.java    (Facade: PG 호출 + future 오케스트레이션, 클래스 @Transactional 없음)
│   └── PaymentInfo.java                  (Facade 반환 DTO)
├── infrastructure/payment/
│   ├── PaymentJpaEntity.java             (@Entity)
│   ├── PaymentJpaRepository.java         (Spring Data JPA)
│   ├── PaymentMapper.java                (도메인 ↔ JPA 변환)
│   ├── PaymentRepositoryImpl.java        (PaymentRepository 구현체)
│   ├── PgRestClient.java                 (PgClient 구현체, @CircuitBreaker)
│   └── PgClientConfig.java               (RestTemplate 빈, CB 설정)
├── support/payment/
│   └── PaymentWaitingRegistry.java       (transactionKey → CompletableFuture)
└── interfaces/api/payment/
    ├── PaymentV1Dto.java                 (요청/응답 record)
    └── PaymentV1Controller.java          (3개 엔드포인트)

apps/commerce-api/src/main/resources/
└── application.yml                       (resilience4j, graceful shutdown 추가)

apps/commerce-api/
└── build.gradle.kts                      (resilience4j 의존성 추가)

domain/order/OrderEntity.java             (pay() 메서드 추가)
domain/order/OrderRepository.java         (findByIdWithLock 추가 — 비관적 락)
infrastructure/order/OrderJpaRepository.java (@Lock(PESSIMISTIC_WRITE) 쿼리 추가)
infrastructure/order/OrderRepositoryImpl.java (findByIdWithLock 구현)
support/error/ErrorType.java              (PAYMENT_GATEWAY_ERROR, PG_QUERY_ERROR 추가)
```

---

## Task 1: 기반 타입 + OrderEntity.pay()

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/CardType.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentStatus.java`
- Modify: `apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java`
- Modify: `apps/commerce-api/src/main/java/com/loopers/domain/order/OrderEntity.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/domain/order/OrderEntityTest.java`

**Interfaces:**
- Produces: `CardType`, `PaymentStatus`, `ErrorType.PAYMENT_GATEWAY_ERROR`, `ErrorType.PG_QUERY_ERROR`, `OrderEntity.pay()`

- [ ] **Step 1: CardType, PaymentStatus enum 생성**

```java
// CardType.java
package com.loopers.domain.payment;

public enum CardType {
    SHINHAN, SAMSUNG, KB, HYUNDAI, LOTTE, WOORI, HANA, BC
}
```

```java
// PaymentStatus.java
package com.loopers.domain.payment;

public enum PaymentStatus {
    PENDING, SUCCESS, FAILED
}
```

- [ ] **Step 2: ErrorType에 결제 에러 추가**

```java
// ErrorType.java — 기존 enum 값들 아래에 추가
PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", "결제 요청에 실패했습니다."),
PG_QUERY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PG_QUERY_ERROR", "결제 상태 조회에 실패했습니다.");
```

- [ ] **Step 3: OrderEntity.pay() 실패 테스트 작성**

```java
// OrderEntityTest.java — 기존 클래스 내 Nested 클래스 추가
@DisplayName("결제 완료 처리")
@Nested
class Pay {

    @DisplayName("PENDING 상태에서 pay()를 호출하면 PAID가 된다.")
    @Test
    void pay_changeStatusToPaid_whenStatusIsPending() {
        // arrange
        OrderEntity order = new OrderEntity(VALID_USER_ID, validSnapshot(1L));

        // act
        order.pay();

        // assert
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @DisplayName("PENDING이 아닌 상태에서 pay()를 호출하면 예외가 발생한다.")
    @Test
    void pay_throwsException_whenStatusIsNotPending() {
        // arrange
        OrderEntity order = new OrderEntity(VALID_USER_ID, validSnapshot(1L));
        order.pay();

        // act & assert
        assertThrows(CoreException.class, order::pay);
    }
}
```

- [ ] **Step 4: 테스트 실행 → FAIL 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.OrderEntityTest" 2>&1 | tail -20
```
Expected: `FAILED` — `pay()` 메서드 없음

- [ ] **Step 5: OrderEntity.pay() 구현**

```java
// OrderEntity.java 내부 추가
public void pay() {
    if (this.status != OrderStatus.PENDING) {
        throw new CoreException(ErrorType.BAD_REQUEST, "결제 가능한 주문 상태가 아닙니다.");
    }
    this.status = OrderStatus.PAID;
}
```

- [ ] **Step 6: 테스트 실행 → PASS 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.OrderEntityTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/payment/CardType.java \
        apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentStatus.java \
        apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java \
        apps/commerce-api/src/main/java/com/loopers/domain/order/OrderEntity.java \
        apps/commerce-api/src/test/java/com/loopers/domain/order/OrderEntityTest.java
git commit -m "feat: Payment 기반 타입(CardType, PaymentStatus) 추가 및 OrderEntity.pay() 구현"
```

---

## Task 2: PaymentEntity 도메인 모델

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentEntity.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/domain/payment/PaymentEntityTest.java`

**Interfaces:**
- Consumes: `CardType`, `PaymentStatus`, `BaseEntity`, `CoreException`, `ErrorType`
- Produces: `PaymentEntity(orderId, userId, cardType, cardNo, amount)`, `approve()`, `fail(reason)`, `isOwnedBy(userId)`, `PaymentEntity.of(...)` (재구성용)

- [ ] **Step 1: 실패 테스트 작성**

```java
// PaymentEntityTest.java
package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class PaymentEntityTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final String CARD_NO = "1234-5678-9814-1451";

    private PaymentEntity validPayment() {
        return new PaymentEntity(ORDER_ID, USER_ID, CardType.SAMSUNG, CARD_NO, 10000L);
    }

    @DisplayName("결제 생성")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성하면 status는 PENDING이다.")
        @Test
        void create_withPendingStatus() {
            PaymentEntity payment = validPayment();
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
        }

        @DisplayName("orderId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenOrderIdIsNull() {
            assertThrows(CoreException.class,
                () -> new PaymentEntity(null, USER_ID, CardType.SAMSUNG, CARD_NO, 10000L));
        }

        @DisplayName("userId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIsNull() {
            assertThrows(CoreException.class,
                () -> new PaymentEntity(ORDER_ID, null, CardType.SAMSUNG, CARD_NO, 10000L));
        }

        @DisplayName("cardNo가 빈 문자열이면 예외가 발생한다.")
        @Test
        void throwsException_whenCardNoIsBlank() {
            assertThrows(CoreException.class,
                () -> new PaymentEntity(ORDER_ID, USER_ID, CardType.SAMSUNG, "", 10000L));
        }

        @DisplayName("amount가 0 이하이면 예외가 발생한다.")
        @Test
        void throwsException_whenAmountIsZeroOrNegative() {
            assertThrows(CoreException.class,
                () -> new PaymentEntity(ORDER_ID, USER_ID, CardType.SAMSUNG, CARD_NO, 0L));
        }
    }

    @DisplayName("approve()")
    @Nested
    class Approve {

        @DisplayName("PENDING 상태에서 approve()하면 SUCCESS가 된다.")
        @Test
        void approve_changesStatusToSuccess() {
            PaymentEntity payment = validPayment();
            payment.approve();
            assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        }

        @DisplayName("이미 SUCCESS이면 approve()를 호출해도 예외 없이 무시된다. (멱등)")
        @Test
        void approve_isIdempotent_whenAlreadySuccess() {
            PaymentEntity payment = validPayment();
            payment.approve();
            assertDoesNotThrow(payment::approve);
            assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        }

        @DisplayName("FAILED 상태에서 approve()하면 예외가 발생한다.")
        @Test
        void approve_throwsException_whenStatusIsFailed() {
            PaymentEntity payment = validPayment();
            payment.fail("한도 초과");
            assertThrows(CoreException.class, payment::approve);
        }
    }

    @DisplayName("fail()")
    @Nested
    class Fail {

        @DisplayName("PENDING 상태에서 fail()하면 FAILED가 된다.")
        @Test
        void fail_changesStatusToFailed() {
            PaymentEntity payment = validPayment();
            payment.fail("한도 초과");
            assertEquals(PaymentStatus.FAILED, payment.getStatus());
        }

        @DisplayName("이미 FAILED이면 fail()을 호출해도 예외 없이 무시된다. (멱등)")
        @Test
        void fail_isIdempotent_whenAlreadyFailed() {
            PaymentEntity payment = validPayment();
            payment.fail("한도 초과");
            assertDoesNotThrow(() -> payment.fail("재실패"));
            assertEquals(PaymentStatus.FAILED, payment.getStatus());
        }

        @DisplayName("SUCCESS 상태에서 fail()하면 예외가 발생한다.")
        @Test
        void fail_throwsException_whenStatusIsSuccess() {
            PaymentEntity payment = validPayment();
            payment.approve();
            assertThrows(CoreException.class, () -> payment.fail("오류"));
        }
    }

    @DisplayName("isOwnedBy()")
    @Nested
    class IsOwnedBy {

        @DisplayName("소유자 userId와 일치하면 true를 반환한다.")
        @Test
        void returnsTrue_whenUserIdMatches() {
            assertTrue(validPayment().isOwnedBy(USER_ID));
        }

        @DisplayName("다른 userId이면 false를 반환한다.")
        @Test
        void returnsFalse_whenUserIdDoesNotMatch() {
            assertFalse(validPayment().isOwnedBy(999L));
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.payment.PaymentEntityTest" 2>&1 | tail -10
```
Expected: `FAILED` — `PaymentEntity` 없음

- [ ] **Step 3: PaymentEntity 구현**

```java
package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;

public class PaymentEntity extends BaseEntity {

    private Long orderId;
    private Long userId;
    private String transactionKey;
    private CardType cardType;
    private String cardNo;
    private Long amount;
    private PaymentStatus status;
    private String failureReason;

    protected PaymentEntity() {}

    public PaymentEntity(Long orderId, Long userId, CardType cardType, String cardNo, Long amount) {
        if (orderId == null) throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        if (userId == null) throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        if (cardNo == null || cardNo.isBlank()) throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        if (amount == null || amount <= 0) throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static PaymentEntity of(Long id, Long orderId, Long userId, String transactionKey,
            CardType cardType, String cardNo, Long amount, PaymentStatus status, String failureReason,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        PaymentEntity entity = new PaymentEntity();
        entity.orderId = orderId;
        entity.userId = userId;
        entity.transactionKey = transactionKey;
        entity.cardType = cardType;
        entity.cardNo = cardNo;
        entity.amount = amount;
        entity.status = status;
        entity.failureReason = failureReason;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public void registerTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    public void approve() {
        if (this.status == PaymentStatus.SUCCESS) return; // 멱등
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 승인 처리할 수 없는 상태입니다.");
        }
        this.status = PaymentStatus.SUCCESS;
    }

    public void fail(String reason) {
        if (this.status == PaymentStatus.FAILED) return; // 멱등
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 실패 처리할 수 없는 상태입니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public String getTransactionKey() { return transactionKey; }
    public CardType getCardType() { return cardType; }
    public String getCardNo() { return cardNo; }
    public Long getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
}
```

- [ ] **Step 4: 테스트 실행 → PASS 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.payment.PaymentEntityTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentEntity.java \
        apps/commerce-api/src/test/java/com/loopers/domain/payment/PaymentEntityTest.java
git commit -m "feat: PaymentEntity 도메인 모델 구현 (approve/fail 멱등 가드 포함)"
```

---

## Task 3: PaymentRepository 포트 + JPA 구현체

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentRepository.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PaymentJpaEntity.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PaymentJpaRepository.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PaymentMapper.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PaymentRepositoryImpl.java`

**Interfaces:**
- Consumes: `PaymentEntity`, `PaymentStatus`, `BaseJpaEntity`
- Produces: `PaymentRepository.save()`, `findById()`, `findByTransactionKey()`, `existsByOrderIdAndStatusIn()`

- [ ] **Step 1: PaymentRepository 인터페이스 작성**

```java
package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentRepository {
    PaymentEntity save(PaymentEntity payment);
    Optional<PaymentEntity> findById(Long id);
    Optional<PaymentEntity> findByTransactionKey(String transactionKey);
    boolean existsByOrderIdAndStatusIn(Long orderId, PaymentStatus... statuses);
}
```

- [ ] **Step 2: PaymentJpaEntity 작성**

```java
package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.ZonedDateTime;

@Entity
@Table(name = "payments")
@Getter
public class PaymentJpaEntity extends BaseJpaEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    protected PaymentJpaEntity() {}

    PaymentJpaEntity(Long id, Long orderId, Long userId, String transactionKey,
            CardType cardType, String cardNo, Long amount,
            PaymentStatus status, String failureReason, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.orderId = orderId;
        this.userId = userId;
        this.transactionKey = transactionKey;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
    }
}
```

- [ ] **Step 3: PaymentJpaRepository 작성**

```java
package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {
    Optional<PaymentJpaEntity> findByTransactionKey(String transactionKey);
    boolean existsByOrderIdAndStatusIn(Long orderId, PaymentStatus... statuses);
}
```

- [ ] **Step 4: PaymentMapper 작성**

```java
package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentEntity;

public class PaymentMapper {

    public static PaymentJpaEntity toJpaEntity(PaymentEntity payment) {
        return new PaymentJpaEntity(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getTransactionKey(),
            payment.getCardType(),
            payment.getCardNo(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getDeletedAt()
        );
    }

    public static PaymentEntity toDomain(PaymentJpaEntity jpa) {
        return PaymentEntity.of(
            jpa.getId(),
            jpa.getOrderId(),
            jpa.getUserId(),
            jpa.getTransactionKey(),
            jpa.getCardType(),
            jpa.getCardNo(),
            jpa.getAmount(),
            jpa.getStatus(),
            jpa.getFailureReason(),
            jpa.getCreatedAt(),
            jpa.getUpdatedAt(),
            jpa.getDeletedAt()
        );
    }
}
```

- [ ] **Step 5: PaymentRepositoryImpl 작성**

```java
package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentEntity;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public PaymentEntity save(PaymentEntity payment) {
        return PaymentMapper.toDomain(paymentJpaRepository.save(PaymentMapper.toJpaEntity(payment)));
    }

    @Override
    public Optional<PaymentEntity> findById(Long id) {
        return paymentJpaRepository.findById(id).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<PaymentEntity> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey).map(PaymentMapper::toDomain);
    }

    @Override
    public boolean existsByOrderIdAndStatusIn(Long orderId, PaymentStatus... statuses) {
        return paymentJpaRepository.existsByOrderIdAndStatusIn(orderId, statuses);
    }
}
```

- [ ] **Step 6: OrderRepository에 비관적 락 조회 추가 (동시 결제 직렬화용)**

> 동일 `orderId`에 대한 동시 `initiate`를 직렬화하기 위해 주문 행에 `PESSIMISTIC_WRITE` 락을 건다.
> 결제 실패 후 재시도가 가능해야 하므로 유니크 제약 대신 **비관적 락 + 중복 체크** 조합을 사용한다.

```java
// domain/order/OrderRepository.java — 메서드 추가
Optional<OrderEntity> findByIdWithLock(Long id);
```

```java
// infrastructure/order/OrderJpaRepository.java — import 및 쿼리 추가
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM OrderJpaEntity o WHERE o.id = :id AND o.deletedAt IS NULL")
Optional<OrderJpaEntity> findByIdWithLock(@Param("id") Long id);
```

```java
// infrastructure/order/OrderRepositoryImpl.java — 메서드 추가
@Override
public Optional<OrderEntity> findByIdWithLock(Long id) {
    return orderJpaRepository.findByIdWithLock(id).map(OrderMapper::toDomain);
}
```

> **주의:** `findByIdWithLock`은 반드시 `@Transactional` 메서드(`PaymentService.prepare`) 내부에서 호출해야 락이 트랜잭션 종료까지 유지된다.

- [ ] **Step 7: 컨텍스트 로딩 테스트 실행**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.CommerceApiContextTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentRepository.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/ \
        apps/commerce-api/src/main/java/com/loopers/domain/order/OrderRepository.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/order/OrderJpaRepository.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/order/OrderRepositoryImpl.java
git commit -m "feat: PaymentRepository 포트/구현체 및 Order 비관적 락 조회 추가"
```

---

## Task 4: PgClient 포트 + PgRestClient 구현체 + Resilience4j 설정

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PgClient.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PgPaymentRequest.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PgTransactionResponse.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PgRestClient.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PgClientConfig.java`
- Modify: `apps/commerce-api/build.gradle.kts`
- Modify: `apps/commerce-api/src/main/resources/application.yml`

**Interfaces:**
- Produces: `PgClient.requestPayment(PgPaymentRequest)`, `PgClient.getTransaction(transactionKey)`

- [ ] **Step 1: PgClient 포트 + DTO 작성**

```java
// PgClient.java
package com.loopers.domain.payment;

public interface PgClient {
    PgTransactionResponse requestPayment(PgPaymentRequest request);
    PgTransactionResponse getTransaction(String transactionKey);
}
```

```java
// PgPaymentRequest.java
package com.loopers.domain.payment;

public record PgPaymentRequest(
    String orderId,
    CardType cardType,
    String cardNo,
    Long amount,
    String callbackUrl
) {}
```

```java
// PgTransactionResponse.java
package com.loopers.domain.payment;

public record PgTransactionResponse(
    String transactionKey,
    PgTransactionStatus status,
    String reason
) {}
```

```java
// PgTransactionStatus.java
package com.loopers.domain.payment;

public enum PgTransactionStatus {
    PENDING, SUCCESS, FAILED
}
```

- [ ] **Step 2: build.gradle.kts에 Resilience4j 의존성 추가**

```kotlin
// apps/commerce-api/build.gradle.kts
dependencies {
    // 기존 의존성 유지 ...

    // resilience4j
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-spring6:2.2.0")
    implementation("org.springframework.boot:spring-boot-starter-aop") // @CircuitBreaker AOP 필요
}
```

- [ ] **Step 3: application.yml에 PG 설정 추가**

```yaml
# apps/commerce-api/src/main/resources/application.yml 기존 내용에 추가

server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

pg:
  base-url: http://localhost:8090
  callback-timeout-seconds: 10   # CompletableFuture orTimeout. 콜백 대기 시간

resilience4j:
  circuitbreaker:
    instances:
      pgClient:
        sliding-window-size: 10
        failure-rate-threshold: 50
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
        wait-duration-in-open-state: 15s
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: true

# --- test 프로필 전용 override (별도 document 블록으로 추가) ---
# 통합 테스트에서 콜백 미수신(timeout) 분기를 빠르게 검증하기 위해
# test 프로필에서만 timeout을 짧게 둔다. (운영 코드는 10s 유지)
---
spring:
  config:
    activate:
      on-profile: test
pg:
  callback-timeout-seconds: 2
```

> **주의:** 기존 `application.yml`의 `on-profile: local, test` 블록은 그대로 두고, 위 `on-profile: test` document 블록을 **별도로 추가**한다. 하나의 프로필이 여러 document 블록에 등장해도 무방하며, 뒤 블록의 `pg.callback-timeout-seconds`가 test 실행 시 override된다.

- [ ] **Step 4: PgClientConfig 작성 (RestTemplate 빈)**

```java
package com.loopers.infrastructure.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PgClientConfig {

    @Bean("pgRequestRestTemplate")
    public RestTemplate pgRequestRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(1000); // requestPayment: 1s
        return new RestTemplate(factory);
    }

    @Bean("pgQueryRestTemplate")
    public RestTemplate pgQueryRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(15000); // 1차 Poll: 15s
        return new RestTemplate(factory);
    }
}
```

- [ ] **Step 5: PgRestClient 구현체 작성**

```java
package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class PgRestClient implements PgClient {

    private final RestTemplate pgRequestRestTemplate;
    private final RestTemplate pgQueryRestTemplate;
    private final String pgBaseUrl;
    private final String callbackUrl;

    public PgRestClient(
        @Qualifier("pgRequestRestTemplate") RestTemplate pgRequestRestTemplate,
        @Qualifier("pgQueryRestTemplate") RestTemplate pgQueryRestTemplate,
        @Value("${pg.base-url}") String pgBaseUrl,
        @Value("${pg.callback-url:http://localhost:8080/api/v1/payments/callback}") String callbackUrl
    ) {
        this.pgRequestRestTemplate = pgRequestRestTemplate;
        this.pgQueryRestTemplate = pgQueryRestTemplate;
        this.pgBaseUrl = pgBaseUrl;
        this.callbackUrl = callbackUrl;
    }

    @CircuitBreaker(name = "pgClient", fallbackMethod = "requestPaymentFallback")
    @Override
    public PgTransactionResponse requestPayment(PgPaymentRequest request) {
        String url = pgBaseUrl + "/api/v1/payments";
        return pgRequestRestTemplate.postForObject(url, request, PgTransactionResponse.class);
    }

    @CircuitBreaker(name = "pgClient", fallbackMethod = "getTransactionFallback")
    @Override
    public PgTransactionResponse getTransaction(String transactionKey) {
        String url = pgBaseUrl + "/api/v1/payments/" + transactionKey;
        return pgQueryRestTemplate.getForObject(url, PgTransactionResponse.class);
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    private PgTransactionResponse requestPaymentFallback(PgPaymentRequest request, Throwable t) {
        log.error("PG 결제 요청 실패. 서킷브레이커 fallback 실행. cause: {}", t.getMessage());
        throw new CoreException(ErrorType.PAYMENT_GATEWAY_ERROR, "PG 결제 요청에 실패했습니다.");
    }

    private PgTransactionResponse getTransactionFallback(String transactionKey, Throwable t) {
        log.error("PG 결제 조회 실패. transactionKey: {}, cause: {}", transactionKey, t.getMessage());
        throw new CoreException(ErrorType.PG_QUERY_ERROR, "PG 결제 조회에 실패했습니다.");
    }
}
```

- [ ] **Step 6: 컨텍스트 로딩 테스트 실행**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.CommerceApiContextTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/payment/PgClient.java \
        apps/commerce-api/src/main/java/com/loopers/domain/payment/PgPaymentRequest.java \
        apps/commerce-api/src/main/java/com/loopers/domain/payment/PgTransactionResponse.java \
        apps/commerce-api/src/main/java/com/loopers/domain/payment/PgTransactionStatus.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PgRestClient.java \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PgClientConfig.java \
        apps/commerce-api/build.gradle.kts \
        apps/commerce-api/src/main/resources/application.yml
git commit -m "feat: PgClient 포트 및 PgRestClient 구현체 추가 (Resilience4j CircuitBreaker)"
```

---

## Task 5: PaymentWaitingRegistry

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/support/payment/PaymentWaitingRegistry.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/support/payment/PaymentWaitingRegistryTest.java`

**Interfaces:**
- Produces: `register(transactionKey, future)`, `pop(transactionKey): Optional<CompletableFuture<?>>`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.loopers.support.payment;

import org.junit.jupiter.api.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class PaymentWaitingRegistryTest {

    private PaymentWaitingRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PaymentWaitingRegistry();
    }

    @DisplayName("등록한 transactionKey로 pop하면 future를 반환한다.")
    @Test
    void pop_returnsFuture_whenRegistered() {
        CompletableFuture<String> future = new CompletableFuture<>();
        registry.register("txKey1", future);

        var result = registry.pop("txKey1");

        assertTrue(result.isPresent());
        assertSame(future, result.get());
    }

    @DisplayName("pop은 get + remove — 두 번 호출 시 두 번째는 empty이다.")
    @Test
    void pop_returnsEmpty_whenCalledTwice() {
        CompletableFuture<String> future = new CompletableFuture<>();
        registry.register("txKey1", future);

        registry.pop("txKey1");
        var result = registry.pop("txKey1");

        assertTrue(result.isEmpty());
    }

    @DisplayName("등록하지 않은 key로 pop하면 empty를 반환한다.")
    @Test
    void pop_returnsEmpty_whenNotRegistered() {
        assertTrue(registry.pop("unknown").isEmpty());
    }
}
```

- [ ] **Step 2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.support.payment.PaymentWaitingRegistryTest" 2>&1 | tail -10
```
Expected: `FAILED`

- [ ] **Step 3: PaymentWaitingRegistry 구현**

```java
package com.loopers.support.payment;

import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentWaitingRegistry {

    private final ConcurrentHashMap<String, CompletableFuture<Object>> registry = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void register(String transactionKey, CompletableFuture<T> future) {
        registry.put(transactionKey, (CompletableFuture<Object>) future);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<CompletableFuture<T>> pop(String transactionKey) {
        return Optional.ofNullable((CompletableFuture<T>) registry.remove(transactionKey));
    }
}
```

- [ ] **Step 4: 테스트 실행 → PASS 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.support.payment.PaymentWaitingRegistryTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/support/payment/PaymentWaitingRegistry.java \
        apps/commerce-api/src/test/java/com/loopers/support/payment/PaymentWaitingRegistryTest.java
git commit -m "feat: PaymentWaitingRegistry 구현 (transactionKey → CompletableFuture 인메모리 매핑)"
```

---

## Task 6: PaymentApplicationService + 통합 테스트

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentInfo.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentService.java` ← **신규 도메인 서비스 (TX 경계)**
- Create: `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentApplicationService.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/application/payment/PaymentApplicationServiceIntegrationTest.java`

**Interfaces:**
- `PaymentService` (도메인 서비스, `@Transactional` 단위 — 트랜잭션 경계 3분할)
  - Consumes: `PaymentRepository`, `OrderRepository`
  - Produces:
    - `prepare(userId, orderId, cardType, cardNo): PaymentEntity` — **TX1**: 주문 비관적 락 + 검증 + 중복 체크 + PENDING 저장
    - `applyPgResponse(paymentId, PgTransactionResponse): void` — **TX2**: transactionKey 저장 + PG 즉시 SUCCESS/FAILED 반영
    - `settle(transactionKey, status, reason): void` — **TX3**: 콜백·Poll 공통 확정, first-wins 멱등
    - `markFailed(paymentId, reason): void` — PG 요청 자체 실패 시
- `PaymentApplicationService` (Facade, **클래스 @Transactional 없음**)
  - Consumes: `PaymentService`, `PgClient`, `PaymentWaitingRegistry`
  - Produces:
    - `initiate(userId, orderId, cardType, cardNo): CompletableFuture<PaymentInfo>`
    - `processCallback(transactionKey, status, reason): void`
    - `getPayment(userId, paymentId): PaymentInfo`

- [ ] **Step 1: PaymentInfo DTO 작성**

```java
package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentEntity;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
    Long paymentId,
    Long orderId,
    String transactionKey,
    CardType cardType,
    Long amount,
    PaymentStatus status,
    String failureReason
) {
    public static PaymentInfo from(PaymentEntity entity) {
        return new PaymentInfo(
            entity.getId(),
            entity.getOrderId(),
            entity.getTransactionKey(),
            entity.getCardType(),
            entity.getAmount(),
            entity.getStatus(),
            entity.getFailureReason()
        );
    }
}
```

- [ ] **Step 2: 통합 테스트 작성 (Red)**

```java
package com.loopers.application.payment;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.user.UserApplicationService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgTransactionResponse;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentApplicationServiceIntegrationTest {

    @Autowired PaymentApplicationService paymentApplicationService;
    @Autowired UserApplicationService userApplicationService;
    @Autowired OrderApplicationService orderApplicationService;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @MockBean PgClient pgClient;

    private Long userId;
    private Long orderId;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        // 유저, 브랜드, 상품, 재고, 주문 설정은 기존 통합 테스트 패턴 참고
        // 여기서는 userId와 orderId만 준비
        var user = userApplicationService.signup("testuser", "Password1!", "홍길동",
            LocalDate.of(1990, 1, 1), "test@test.com");
        userId = user.userId();
        // 주문 생성 (기존 OrderApplicationServiceIntegrationTest 참조)
        // orderId = ...
    }

    @DisplayName("initiate()")
    @Nested
    class Initiate {

        @DisplayName("PG 요청 성공 시 PaymentEntity가 PENDING으로 저장된다.")
        @Test
        void initiate_savesPaymentAsPending_whenPgRequestSucceeds() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            assertNotNull(future);
            assertFalse(future.isDone());
        }

        @DisplayName("PG 요청 실패 시 PaymentEntity가 FAILED로 저장되고 예외가 발생한다.")
        @Test
        void initiate_savesPaymentAsFailed_whenPgRequestFails() {
            when(pgClient.requestPayment(any()))
                .thenThrow(new CoreException(ErrorType.PAYMENT_GATEWAY_ERROR, "PG 오류"));

            assertThrows(CoreException.class,
                () -> paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451"));
        }

        @DisplayName("동일 orderId에 PENDING 결제가 이미 있으면 예외가 발생한다.")
        @Test
        void initiate_throwsConflict_whenDuplicatePaymentExists() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));

            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            assertThrows(CoreException.class,
                () -> paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451"));
        }

        @DisplayName("PG가 즉시 FAILED를 응답하면 PaymentEntity가 FAILED로 확정되고 future도 FAILED로 완료된다.")
        @Test
        void initiate_completesAsFailed_whenPgRespondsFailedImmediately() throws Exception {
            // PG가 PENDING이 아닌 FAILED를 즉시 응답하면 applyPgResponse가 즉시 FAILED로 확정하고
            // initiate는 콜백 대기 없이 completedFuture를 반환한다. (getTransaction 미호출)
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-IMM-FAIL", PgTransactionStatus.FAILED, "한도 초과"));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.failureReason()).isEqualTo("한도 초과");
        }
    }

    @DisplayName("콜백 미수신 (timeout → 1차 Poll)")
    @Nested
    class CallbackTimeout {
        // 콜백을 의도적으로 보내지 않아 orTimeout(test 프로필 2s)이 발생하고,
        // exceptionally 블록의 1차 Poll(getTransaction mock)이 실행되는 경로를 검증한다.
        // future.get(5s)는 2s timeout 이후 Poll 결과로 완료되므로 5s 내에 반환된다.

        @DisplayName("timeout 후 1차 Poll이 SUCCESS면 future가 SUCCESS로 완료되고 주문이 PAID가 된다.")
        @Test
        void timeout_pollSuccess_completesAsSuccess() throws Exception {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-T1", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction("TX-T1"))
                .thenReturn(new PgTransactionResponse("TX-T1", PgTransactionStatus.SUCCESS, null));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            // (선택) 주문 조회 API가 있다면 OrderStatus.PAID 도 함께 검증
        }

        @DisplayName("timeout 후 1차 Poll이 FAILED면 future가 FAILED로 완료된다.")
        @Test
        void timeout_pollFailed_completesAsFailed() throws Exception {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-T2", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction("TX-T2"))
                .thenReturn(new PgTransactionResponse("TX-T2", PgTransactionStatus.FAILED, "한도 초과"));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.failureReason()).isEqualTo("한도 초과");
        }

        @DisplayName("timeout 후 1차 Poll이 여전히 PENDING이면 future가 PENDING으로 완료된다. (Scheduler 후속 처리)")
        @Test
        void timeout_pollPending_completesAsPending() throws Exception {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-T3", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction("TX-T3"))
                .thenReturn(new PgTransactionResponse("TX-T3", PgTransactionStatus.PENDING, null));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("timeout 후 1차 Poll 자체가 PG_QUERY_ERROR로 실패하면 예외를 삼키고 PENDING으로 완료된다.")
        @Test
        void timeout_pollThrows_completesAsPending() throws Exception {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-T4", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction("TX-T4"))
                .thenThrow(new CoreException(ErrorType.PG_QUERY_ERROR, "PG 조회 실패"));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @DisplayName("processCallback()")
    @Nested
    class ProcessCallback {

        @DisplayName("SUCCESS 콜백 수신 시 PaymentEntity가 SUCCESS, OrderEntity가 PAID가 된다.")
        @Test
        void processCallback_updatesStatusToSuccess_andPaysOrder() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            paymentApplicationService.processCallback("TX-001", PgTransactionStatus.SUCCESS, null);

            PaymentInfo payment = paymentApplicationService.getPayment(userId,
                getPaymentIdByTransactionKey("TX-001"));
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("FAILED 콜백 수신 시 PaymentEntity가 FAILED가 된다.")
        @Test
        void processCallback_updatesStatusToFailed_whenPgFails() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            paymentApplicationService.processCallback("TX-001", PgTransactionStatus.FAILED, "한도 초과");

            PaymentInfo payment = paymentApplicationService.getPayment(userId,
                getPaymentIdByTransactionKey("TX-001"));
            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.failureReason()).isEqualTo("한도 초과");
        }

        @DisplayName("동일 transactionKey로 SUCCESS 콜백을 2회 수신해도 멱등하게 SUCCESS를 유지한다.")
        @Test
        void processCallback_isIdempotent_whenSuccessReceivedTwice() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-IDEM", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            paymentApplicationService.processCallback("TX-IDEM", PgTransactionStatus.SUCCESS, null);
            assertDoesNotThrow(() ->
                paymentApplicationService.processCallback("TX-IDEM", PgTransactionStatus.SUCCESS, null));

            PaymentInfo payment = paymentApplicationService.getPaymentByTransactionKey("TX-IDEM");
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("1차 Poll이 SUCCESS로 확정한 뒤 늦은 SUCCESS 콜백이 도착해도 멱등하게 SUCCESS를 유지한다.")
        @Test
        void processCallback_isIdempotent_whenLateCallbackAfterPoll() throws Exception {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-RACE", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction("TX-RACE"))
                .thenReturn(new PgTransactionResponse("TX-RACE", PgTransactionStatus.SUCCESS, null));

            // 콜백 미수신 → timeout(2s) 후 1차 Poll이 SUCCESS로 확정
            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            // 뒤늦게 SUCCESS 콜백이 도착
            assertDoesNotThrow(() ->
                paymentApplicationService.processCallback("TX-RACE", PgTransactionStatus.SUCCESS, null));

            PaymentInfo payment = paymentApplicationService.getPaymentByTransactionKey("TX-RACE");
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("존재하지 않는 transactionKey로 콜백을 수신하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void processCallback_throwsNotFound_whenTransactionKeyUnknown() {
            var exception = assertThrows(CoreException.class,
                () -> paymentApplicationService.processCallback("UNKNOWN", PgTransactionStatus.SUCCESS, null));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getPayment()")
    @Nested
    class GetPayment {

        @DisplayName("DB가 PENDING이면 PgClient를 직접 조회하여 상태를 갱신한다.")
        @Test
        void getPayment_pollsPg_whenStatusIsPending() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));
            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            Long paymentId = getPaymentIdByTransactionKey("TX-001");

            when(pgClient.getTransaction("TX-001"))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.SUCCESS, null));

            PaymentInfo result = paymentApplicationService.getPayment(userId, paymentId);

            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("DB가 PENDING이고 PG Poll이 FAILED면 FAILED로 갱신하여 반환한다.")
        @Test
        void getPayment_updatesToFailed_whenPollFailed() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-005", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            Long paymentId = getPaymentIdByTransactionKey("TX-005");

            when(pgClient.getTransaction("TX-005"))
                .thenReturn(new PgTransactionResponse("TX-005", PgTransactionStatus.FAILED, "한도 초과"));

            PaymentInfo result = paymentApplicationService.getPayment(userId, paymentId);

            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.failureReason()).isEqualTo("한도 초과");
        }

        @DisplayName("DB가 PENDING이고 PG 조회가 PG_QUERY_ERROR로 실패하면 예외가 그대로 전파된다.")
        @Test
        void getPayment_throwsPgQueryError_whenPgQueryFails() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-006", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            Long paymentId = getPaymentIdByTransactionKey("TX-006");

            when(pgClient.getTransaction("TX-006"))
                .thenThrow(new CoreException(ErrorType.PG_QUERY_ERROR, "PG 조회 실패"));

            var exception = assertThrows(CoreException.class,
                () -> paymentApplicationService.getPayment(userId, paymentId));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PG_QUERY_ERROR);
        }

        @DisplayName("소유자가 아닌 유저가 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void getPayment_throwsNotFound_whenNotOwner() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            Long paymentId = getPaymentIdByTransactionKey("TX-001");

            var exception = assertThrows(CoreException.class,
                () -> paymentApplicationService.getPayment(999L, paymentId));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("DB가 이미 SUCCESS로 확정된 결제는 PG 조회 없이 그대로 반환한다.")
        @Test
        void getPayment_doesNotPollPg_whenAlreadyConfirmed() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-CONFIRMED", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            paymentApplicationService.processCallback("TX-CONFIRMED", PgTransactionStatus.SUCCESS, null);
            Long paymentId = getPaymentIdByTransactionKey("TX-CONFIRMED");

            PaymentInfo result = paymentApplicationService.getPayment(userId, paymentId);

            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            verify(pgClient, never()).getTransaction("TX-CONFIRMED");
        }
    }

    private Long getPaymentIdByTransactionKey(String transactionKey) {
        return paymentApplicationService.getPaymentByTransactionKey(transactionKey).paymentId();
    }
}
```

- [ ] **Step 3: 테스트 실행 → FAIL 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.payment.PaymentApplicationServiceIntegrationTest" 2>&1 | tail -10
```
Expected: `FAILED`

- [ ] **Step 4-1: PaymentService 도메인 서비스 구현 (TX 경계 3분할)**

```java
package com.loopers.domain.payment;

import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /** TX1: 주문 비관적 락 + 검증 + 중복 체크 + PENDING 저장 */
    @Transactional
    public PaymentEntity prepare(Long userId, Long orderId, CardType cardType, String cardNo) {
        OrderEntity order = orderRepository.findByIdWithLock(orderId)   // SELECT ... FOR UPDATE
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 가능한 주문 상태가 아닙니다.");
        }
        if (paymentRepository.existsByOrderIdAndStatusIn(orderId, PaymentStatus.PENDING, PaymentStatus.SUCCESS)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 진행 중이거나 완료된 결제가 있습니다.");
        }
        return paymentRepository.save(new PaymentEntity(orderId, userId, cardType, cardNo, order.finalAmount()));
    }

    /** TX2: transactionKey 저장 + PG 즉시 SUCCESS/FAILED 반영 (#4) */
    @Transactional
    public void applyPgResponse(Long paymentId, PgTransactionResponse pgResponse) {
        PaymentEntity payment = getOrThrow(paymentId);
        payment.registerTransactionKey(pgResponse.transactionKey());
        if (pgResponse.status() == PgTransactionStatus.SUCCESS) {
            approveAndPayOrder(payment);
        } else if (pgResponse.status() == PgTransactionStatus.FAILED) {
            payment.fail(pgResponse.reason());
        }
        paymentRepository.save(payment);
    }

    /** PG 요청 자체 실패 시 FAILED 확정 */
    @Transactional
    public void markFailed(Long paymentId, String reason) {
        PaymentEntity payment = getOrThrow(paymentId);
        payment.fail(reason);
        paymentRepository.save(payment);
    }

    /** TX3: 콜백 / 1차 Poll 공통 확정. first-wins 멱등 (#1) */
    @Transactional
    public void settle(String transactionKey, PgTransactionStatus status, String reason) {
        PaymentEntity payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            // 이미 확정 → 첫 결정 유지. 중복/지연 콜백(at-least-once 재전송)을 멱등하게 무시.
            // OrderEntity.pay()가 비멱등이므로 이 가드가 없으면 중복 SUCCESS에서 throw가 발생한다.
            return;
        }
        if (status == PgTransactionStatus.SUCCESS) {
            approveAndPayOrder(payment);
        } else if (status == PgTransactionStatus.FAILED) {
            payment.fail(reason);
        }
        paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentEntity getOrThrow(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PaymentEntity getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    private void approveAndPayOrder(PaymentEntity payment) {
        payment.approve();
        OrderEntity order = orderRepository.findById(payment.getOrderId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.pay();
        orderRepository.save(order);
    }
}
```

- [ ] **Step 4-2: PaymentApplicationService(Facade) 구현**

```java
package com.loopers.application.payment;

import com.loopers.domain.payment.*;
import com.loopers.infrastructure.payment.PgRestClient;
import com.loopers.support.error.CoreException;
import com.loopers.support.payment.PaymentWaitingRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class PaymentApplicationService {

    private final PaymentService paymentService;   // 주입된 빈 → exceptionally에서 호출해도 프록시 경유(TX 적용)
    private final PgClient pgClient;
    private final PaymentWaitingRegistry registry;

    // 콜백 대기 timeout (test 프로필에서 2s로 override)
    @Value("${pg.callback-timeout-seconds:10}")
    private long callbackTimeoutSeconds;

    public CompletableFuture<PaymentInfo> initiate(Long userId, Long orderId, CardType cardType, String cardNo) {
        // TX1: 락 + 검증 + PENDING 저장
        PaymentEntity payment = paymentService.prepare(userId, orderId, cardType, cardNo);
        Long paymentId = payment.getId();

        // TX 외부: PG 호출
        PgPaymentRequest pgRequest = new PgPaymentRequest(
            String.valueOf(orderId), cardType, cardNo, payment.getAmount(),
            ((PgRestClient) pgClient).getCallbackUrl()
        );
        PgTransactionResponse pgResponse;
        try {
            pgResponse = pgClient.requestPayment(pgRequest);
        } catch (Exception e) {
            paymentService.markFailed(paymentId, "PG 요청 실패");
            throw e;
        }

        // TX2: transactionKey 저장 + PG 즉시 확정 반영
        paymentService.applyPgResponse(paymentId, pgResponse);
        String transactionKey = pgResponse.transactionKey();

        // PG가 즉시 확정(SUCCESS/FAILED)이면 콜백 대기 없이 즉시 반환 (#4)
        if (pgResponse.status() != PgTransactionStatus.PENDING) {
            return CompletableFuture.completedFuture(infoOf(transactionKey));
        }

        // 콜백 대기 future 구성
        CompletableFuture<PaymentInfo> innerFuture = new CompletableFuture<>();
        registry.register(transactionKey, innerFuture);
        return innerFuture
            .orTimeout(callbackTimeoutSeconds, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                registry.pop(transactionKey);
                try {
                    PgTransactionResponse poll = pgClient.getTransaction(transactionKey); // 1차 Poll
                    if (poll.status() != PgTransactionStatus.PENDING) {
                        paymentService.settle(transactionKey, poll.status(), poll.reason()); // TX3 (프록시 경유)
                    }
                } catch (CoreException pollEx) {
                    // 1차 Poll 실패 → 현재 상태(PENDING) 반환, Scheduler 후속
                }
                return infoOf(transactionKey);
            });
    }

    public void processCallback(String transactionKey, PgTransactionStatus status, String reason) {
        paymentService.settle(transactionKey, status, reason); // first-wins 멱등 (#1)
    }

    public PaymentInfo getPayment(Long userId, Long paymentId) {
        PaymentEntity payment = paymentService.getOrThrow(paymentId);
        if (!payment.isOwnedBy(userId)) {
            throw new CoreException(com.loopers.support.error.ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다.");
        }
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getTransactionKey() != null) {
            // PG 조회 실패(PG_QUERY_ERROR)는 그대로 전파 → HTTP 500 (정책 ②)
            PgTransactionResponse poll = pgClient.getTransaction(payment.getTransactionKey());
            if (poll.status() != PgTransactionStatus.PENDING) {
                paymentService.settle(payment.getTransactionKey(), poll.status(), poll.reason());
                return infoOf(payment.getTransactionKey());
            }
        }
        return PaymentInfo.from(payment);
    }

    public PaymentInfo getPaymentByTransactionKey(String transactionKey) {
        return infoOf(transactionKey);
    }

    private PaymentInfo infoOf(String transactionKey) {
        return PaymentInfo.from(paymentService.getByTransactionKey(transactionKey));
    }
}
```

- [ ] **Step 5: 테스트 실행 → PASS 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.payment.PaymentApplicationServiceIntegrationTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentService.java \
        apps/commerce-api/src/main/java/com/loopers/application/payment/ \
        apps/commerce-api/src/test/java/com/loopers/application/payment/
git commit -m "feat: PaymentService(TX 3분할/비관적 락/first-wins 멱등) 및 PaymentApplicationService(Facade) 구현"
```

---

## Task 7: PaymentV1Controller + Dto + E2E 테스트

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/PaymentV1Dto.java`
- Create: `apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/PaymentV1Controller.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/interfaces/api/payment/PaymentV1ApiE2ETest.java`

**Interfaces:**
- Consumes: `PaymentApplicationService`, `PaymentInfo`, 인증 인터셉터(`LoginUser`)
- Produces:
  - `POST /api/v1/payments` → `CompletableFuture<ApiResponse<PaymentV1Dto.Response>>`
  - `POST /api/v1/payments/callback` → `ResponseEntity<Void>`
  - `GET /api/v1/payments/{paymentId}` → `ApiResponse<PaymentV1Dto.Response>`

- [ ] **Step 1: PaymentV1Dto 작성**

```java
package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgTransactionStatus;

public class PaymentV1Dto {

    public record PaymentRequest(
        Long orderId,
        CardType cardType,
        String cardNo
    ) {}

    public record CallbackRequest(
        String transactionKey,
        String orderId,
        CardType cardType,
        String cardNo,
        Long amount,
        PgTransactionStatus status,
        String reason
    ) {}

    public record Response(
        Long paymentId,
        String transactionKey,
        String status,
        String reason
    ) {
        public static Response from(PaymentInfo info) {
            return new Response(
                info.paymentId(),
                info.transactionKey(),
                info.status().name(),
                info.failureReason()
            );
        }
    }
}
```

- [ ] **Step 2: PaymentV1Controller 작성**

```java
package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentApplicationService paymentApplicationService;
    private final com.loopers.support.payment.PaymentWaitingRegistry registry;

    @PostMapping
    public CompletableFuture<ApiResponse<PaymentV1Dto.Response>> pay(
        @RequestBody PaymentV1Dto.PaymentRequest request,
        LoginUser loginUser
    ) {
        return paymentApplicationService
            .initiate(loginUser.userId(), request.orderId(), request.cardType(), request.cardNo())
            .thenApply(info -> ApiResponse.success(PaymentV1Dto.Response.from(info)));
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentApplicationService.processCallback(
            request.transactionKey(),
            request.status(),
            request.reason()
        );

        registry.<ApiResponse<PaymentV1Dto.Response>>pop(request.transactionKey())
            .filter(f -> !f.isDone())
            .ifPresent(f -> {
                PaymentInfo info = paymentApplicationService.getPaymentByTransactionKey(request.transactionKey());
                f.complete(ApiResponse.success(PaymentV1Dto.Response.from(info)));
            });

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentV1Dto.Response> getPayment(
        @PathVariable Long paymentId,
        LoginUser loginUser
    ) {
        PaymentInfo info = paymentApplicationService.getPayment(loginUser.userId(), paymentId);
        return ApiResponse.success(PaymentV1Dto.Response.from(info));
    }
}
```

- [ ] **Step 3: E2E 테스트 작성 (Red)**

```java
package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.application.user.UserApplicationService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgTransactionResponse;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String ENDPOINT = "/api/v1/payments";

    @Autowired TestRestTemplate testRestTemplate;
    @Autowired UserApplicationService userApplicationService;
    @Autowired PaymentApplicationService paymentApplicationService;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @MockBean PgClient pgClient;

    private Long userId;
    private Long orderId;
    private String loginId = "testuser";
    private String loginPw = "Password1!";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        var user = userApplicationService.signup(loginId, loginPw, "홍길동",
            LocalDate.of(1990, 1, 1), "test@test.com");
        userId = user.userId();
        // orderId 준비 (기존 OrderApplicationService 사용하여 주문 생성)
        // orderId = ...
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, loginPw);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class Pay {

        @DisplayName("PG 콜백이 10초 내 도착하면 SUCCESS를 반환한다.")
        @Test
        void pay_returnsSuccess_whenCallbackArrives() throws Exception {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-001", PgTransactionStatus.PENDING, null));

            var executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                Thread.sleep(300);
                testRestTemplate.postForEntity(
                    ENDPOINT + "/callback",
                    new HttpEntity<>("""
                        {"transactionKey":"TX-E2E-001","orderId":"%s","cardType":"SAMSUNG",
                        "cardNo":"1234-5678-9814-1451","amount":10000,"status":"SUCCESS","reason":null}
                    """.formatted(orderId), new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                    Void.class
                );
                return null;
            });

            var response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(
                    """{"orderId":%d,"cardType":"SAMSUNG","cardNo":"1234-5678-9814-1451"}""".formatted(orderId),
                    userHeaders()
                ),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo("SUCCESS");
            executor.shutdown();
        }

        @DisplayName("존재하지 않는 주문으로 결제 요청 시 404를 반환한다.")
        @Test
        void pay_returns404_whenOrderNotFound() {
            var response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(
                    """{"orderId":99999,"cardType":"SAMSUNG","cardNo":"1234-5678-9814-1451"}""",
                    userHeaders()
                ),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문으로 결제 요청 시 404를 반환한다.")
        @Test
        void pay_returns404_whenOrderOwnedByAnotherUser() {
            // 다른 유저 가입 후 그 유저의 헤더로 본인 소유가 아닌 orderId 결제 시도
            var other = userApplicationService.signup("otheruser", "Password1!", "이몽룡",
                LocalDate.of(1991, 2, 2), "other@test.com");
            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set(HEADER_LOGIN_ID, "otheruser");
            otherHeaders.set(HEADER_LOGIN_PW, "Password1!");
            otherHeaders.setContentType(MediaType.APPLICATION_JSON);

            var response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(
                    """{"orderId":%d,"cardType":"SAMSUNG","cardNo":"1234-5678-9814-1451"}""".formatted(orderId),
                    otherHeaders
                ),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("확정 이후 PG가 동일 SUCCESS 콜백을 재전송(at-least-once)해도 200으로 멱등 처리되고 상태가 유지된다. (#1)")
        @Test
        void pay_handlesDuplicateSuccessCallback_idempotently() throws Exception {
            // 현실적 시나리오: PG 콜백은 at-least-once. 우리 200 ack 유실 시 동일 SUCCESS 콜백이 재전송된다.
            // 이때 OrderEntity.pay()는 비멱등(PAID 재호출 시 throw)이므로, settle()의 first-wins 가드로 보호해야 한다.
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-DUP", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction("TX-DUP"))
                .thenReturn(new PgTransactionResponse("TX-DUP", PgTransactionStatus.SUCCESS, null));

            // pay 요청 → timeout(2s) 후 1차 Poll SUCCESS 확정, 응답 SUCCESS (order PAID)
            var response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(
                    """{"orderId":%d,"cardType":"SAMSUNG","cardNo":"1234-5678-9814-1451"}""".formatted(orderId),
                    userHeaders()
                ),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo("SUCCESS");

            // 확정 이후 동일 SUCCESS 콜백 재전송 → settle() first-wins(no-op)로 200, order.pay() 재호출 없음
            HttpHeaders cbHeaders = new HttpHeaders();
            cbHeaders.setContentType(MediaType.APPLICATION_JSON);
            var callbackResponse = testRestTemplate.postForEntity(
                ENDPOINT + "/callback",
                new HttpEntity<>("""
                    {"transactionKey":"TX-DUP","orderId":"%s","cardType":"SAMSUNG",
                    "cardNo":"1234-5678-9814-1451","amount":10000,"status":"SUCCESS","reason":null}
                """.formatted(orderId), cbHeaders),
                Void.class
            );
            assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // DB는 여전히 SUCCESS
            Long paymentId = paymentApplicationService.getPaymentByTransactionKey("TX-DUP").paymentId();
            assertThat(paymentApplicationService.getPayment(userId, paymentId).status())
                .isEqualTo(com.loopers.domain.payment.PaymentStatus.SUCCESS);
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class Callback {

        @DisplayName("콜백 엔드포인트는 인증 없이 호출해도 200을 반환한다.")
        @Test
        void callback_returns200_withoutAuth() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-NOAUTH", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            HttpHeaders noAuth = new HttpHeaders();
            noAuth.setContentType(MediaType.APPLICATION_JSON);

            var response = testRestTemplate.postForEntity(
                ENDPOINT + "/callback",
                new HttpEntity<>("""
                    {"transactionKey":"TX-NOAUTH","orderId":"%s","cardType":"SAMSUNG",
                    "cardNo":"1234-5678-9814-1451","amount":10000,"status":"SUCCESS","reason":null}
                """.formatted(orderId), noAuth),
                Void.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api/v1/payments/{paymentId}")
    @Nested
    class GetPayment {

        @DisplayName("DB가 PENDING이면 PG 조회 후 SUCCESS를 반환한다.")
        @Test
        void getPayment_returnSuccess_afterPgPoll() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-002", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            when(pgClient.getTransaction("TX-E2E-002"))
                .thenReturn(new PgTransactionResponse("TX-E2E-002", PgTransactionStatus.SUCCESS, null));

            Long paymentId = paymentApplicationService.getPaymentByTransactionKey("TX-E2E-002").paymentId();

            var response = testRestTemplate.exchange(
                ENDPOINT + "/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo("SUCCESS");
        }

        @DisplayName("다른 유저가 타인의 결제를 조회하면 404를 반환한다.")
        @Test
        void getPayment_returns404_whenNotOwner() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-OWNER", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            Long paymentId = paymentApplicationService.getPaymentByTransactionKey("TX-E2E-OWNER").paymentId();

            userApplicationService.signup("otheruser", "Password1!", "이몽룡",
                LocalDate.of(1991, 2, 2), "other@test.com");
            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set(HEADER_LOGIN_ID, "otheruser");
            otherHeaders.set(HEADER_LOGIN_PW, "Password1!");

            var response = testRestTemplate.exchange(
                ENDPOINT + "/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(otherHeaders),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("DB가 PENDING이고 PG 조회가 PG_QUERY_ERROR로 실패하면 HTTP 500을 반환한다.")
        @Test
        void getPayment_returns500_whenPgQueryFails() {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-ERR", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            Long paymentId = paymentApplicationService.getPaymentByTransactionKey("TX-E2E-ERR").paymentId();

            when(pgClient.getTransaction("TX-E2E-ERR"))
                .thenThrow(new com.loopers.support.error.CoreException(
                    com.loopers.support.error.ErrorType.PG_QUERY_ERROR, "PG 조회 실패"));

            var response = testRestTemplate.exchange(
                ENDPOINT + "/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            // ErrorType.PG_QUERY_ERROR → HttpStatus.INTERNAL_SERVER_ERROR (ApiControllerAdvice 매핑)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DisplayName("동시성")
    @Nested
    class Concurrency {

        @DisplayName("동일 orderId에 initiate를 동시 2회 호출하면 하나만 성공하고 나머지는 CONFLICT가 된다.")
        @Test
        void initiate_allowsOnlyOne_whenCalledConcurrently() throws Exception {
            when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransactionResponse("TX-CONCURRENT", PgTransactionStatus.PENDING, null));

            var executor = Executors.newFixedThreadPool(2);
            var latch = new java.util.concurrent.CountDownLatch(1);
            var successCount = new java.util.concurrent.atomic.AtomicInteger();
            var conflictCount = new java.util.concurrent.atomic.AtomicInteger();

            Runnable task = () -> {
                try {
                    latch.await();
                    paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
                    successCount.incrementAndGet();
                } catch (com.loopers.support.error.CoreException e) {
                    if (e.getErrorType() == com.loopers.support.error.ErrorType.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            };
            var f1 = executor.submit(task);
            var f2 = executor.submit(task);
            latch.countDown(); // 동시 출발
            f1.get(10, java.util.concurrent.TimeUnit.SECONDS);
            f2.get(10, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();

            // prepare()가 주문 행을 PESSIMISTIC_WRITE로 잠그므로 두 스레드의 TX1이 직렬화된다.
            // 먼저 커밋한 쪽만 PENDING 결제를 생성하고, 나머지는 중복 체크에서 CONFLICT.
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(1);
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 → FAIL 확인**

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest" 2>&1 | tail -15
```
Expected: `FAILED`

- [ ] **Step 5: 전체 테스트 실행 → PASS 확인**

```bash
./gradlew :apps:commerce-api:test 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: .http 파일 작성**

```http
# .http/payment.http
@commerce-api = http://localhost:8080

### 결제 요청
POST {{commerce-api}}/api/v1/payments
X-Loopers-LoginId: testuser
X-Loopers-LoginPw: Password1!
Content-Type: application/json

{
  "orderId": 1,
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451"
}

### 결제 상태 조회 (Client Polling)
GET {{commerce-api}}/api/v1/payments/1
X-Loopers-LoginId: testuser
X-Loopers-LoginPw: Password1!
```

- [ ] **Step 7: 커밋**

```bash
git add apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/ \
        apps/commerce-api/src/test/java/com/loopers/interfaces/api/payment/ \
        .http/payment.http
git commit -m "feat: PaymentV1Controller 구현 및 E2E 테스트 추가"
```

---

## Self-Review

### Spec Coverage

| 설계 요구사항 | 구현 Task |
|-------------|----------|
| POST /api/v1/payments | Task 7 |
| POST /api/v1/payments/callback | Task 7 |
| GET /api/v1/payments/{paymentId} | Task 7 |
| CardType 8개 | Task 1 |
| PaymentEntity (approve/fail 멱등) | Task 2 |
| CompletableFuture + orTimeout(10s, test=2s) | Task 6 |
| 1차 Poll on timeout (SUCCESS/FAILED/PENDING/오류) | Task 6 (T1~T4) |
| getPayment PG Poll FAILED / PG_QUERY_ERROR 전파 | Task 6 (T5~T6) |
| 서킷브레이커 (Resilience4j) | Task 4 |
| Graceful Shutdown | Task 4 (application.yml) |
| PaymentWaitingRegistry | Task 5 |
| TX1/TX2/TX3 경계 (PaymentService 도메인 서비스) | Task 6 |
| 동시 결제 직렬화 (주문 행 비관적 락) | Task 3 |
| 콜백 first-wins 멱등 (settle) | Task 6 |
| approve()/fail() 멱등성 가드 | Task 2 |
| GET 시 DB PENDING → PG 조회 | Task 6, 7 |
| OrderEntity.pay() | Task 1 |

### Placeholder 검사

- Task 6 setUp()에 `orderId` 준비 코드가 완전하지 않음 → 실제 구현 시 기존 `OrderApplicationServiceIntegrationTest`의 상품/재고 생성 패턴을 참고하여 완성 필요
- Task 7 E2E setUp()의 `orderId` 동일하게 보완 필요

### Type Consistency

- `PgTransactionStatus` (Task 4) → `PaymentApplicationService` (Task 6) → `PaymentV1Dto.CallbackRequest` (Task 7) 에서 동일하게 사용 ✅
- `PaymentInfo.from(PaymentEntity)` (Task 6) → `PaymentV1Dto.Response.from(PaymentInfo)` (Task 7) 체인 일관성 ✅

### 트랜잭션 설계 (Task 6)

- 트랜잭션 경계는 **도메인 서비스 `PaymentService`** 가 보유한다 (CLAUDE.md 컨벤션). Facade(`PaymentApplicationService`)는 클래스 `@Transactional`을 두지 않는다.
  - **TX1 `prepare`** : 주문 행 `PESSIMISTIC_WRITE` 락 + 검증 + 중복 체크 + PENDING 저장. 메서드 종료 시 커밋 → 락 해제. 동시 `initiate`가 직렬화되어 두 번째는 `CONFLICT`.
  - **TX2 `applyPgResponse`** : transactionKey 저장 + PG 즉시 SUCCESS/FAILED 반영.
  - **TX3 `settle`** : 콜백/1차 Poll 공통 확정. `status != PENDING`이면 no-op → **first-wins 멱등**. PG 콜백은 at-least-once라 중복 SUCCESS가 재전송될 수 있고, `OrderEntity.pay()`가 비멱등이므로 이 가드로 중복 처리 시 throw를 방지한다.
- `initiate()`의 `.exceptionally()` 람다는 **주입된 `paymentService` 빈**의 `settle()`을 호출하므로, 다른 스레드에서 실행되어도 **프록시 경유로 `@Transactional`이 정상 적용**된다 (self-invocation 문제 해소).
- PG HTTP 호출은 어떤 트랜잭션에도 포함되지 않아 DB 커넥션을 PG 응답까지 점유하지 않는다.

### 추가 테스트 케이스 (편입 완료)

서킷브레이커 동작 테스트는 합의에 따라 제외. 아래는 Task 6/7에 **정식 편입**되었다.

**Task 6 — PaymentApplicationService (통합)**
- ✅ `processCallback` **멱등성**: 동일 transactionKey로 SUCCESS 콜백 2회 수신 시 `approve()` 가드로 SUCCESS 유지 (design L410).
- ✅ `processCallback` **콜백-Poll 경합**: 1차 Poll이 SUCCESS 확정 후 늦은 SUCCESS 콜백 → `settle()` first-wins 멱등 유지 (design L408).
- ✅ `processCallback` **미존재 transactionKey** → `NOT_FOUND`.
- ✅ `initiate` **PG 즉시 FAILED 응답** → `applyPgResponse`가 즉시 FAILED 확정, 콜백 대기 없이 반환 (#4).
- ✅ `getPayment` **이미 확정 상태**면 Poll 미발생 — `verify(pgClient, never()).getTransaction(...)`.

**Task 7 — PaymentV1Controller (E2E)**
- ✅ **중복 SUCCESS 콜백(at-least-once 재전송)**: 확정 후 동일 SUCCESS 콜백 재수신 → 200 + 상태 SUCCESS 유지. `OrderEntity.pay()`가 비멱등이라 `settle` first-wins 가드로 보호 (#1).
- ✅ `POST /payments` **타인 주문** 결제 → 404.
- ✅ `POST /payments/callback` **인증 불필요** → 헤더 없이 200.
- ✅ `GET /payments/{id}` **타인 결제 조회** → 404.
- ✅ `GET /payments/{id}` PG 조회 `PG_QUERY_ERROR` → **HTTP 500**.
- ✅ **동시성**: 동일 orderId `initiate` 동시 2회 → `prepare`의 주문 비관적 락(#2,#5)으로 직렬화 → 1건 성공 / 1건 CONFLICT.
