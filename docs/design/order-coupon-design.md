# 주문 시 쿠폰 적용 기능 설계

## 요구사항 요약

- 주문 요청 시 `couponId` 필드 추가 (nullable — 미적용 시 생략 가능)
- 쿠폰은 주문 1건당 1장만 적용
- 존재하지 않거나 이미 사용된 쿠폰, 만료된 쿠폰, 타 유저 소유 쿠폰으로 요청 시 주문 실패
- 쿠폰 템플릿의 `minOrderAmount`를 충족하지 않는 경우 주문 실패
- 주문 성공 시 해당 쿠폰은 즉시 USED 상태로 변경 (재사용 불가)
- 주문 정보 스냅샷에 쿠폰 적용 전 금액(`originalPrice`), 할인 금액(`discountAmount`), 최종 결제 금액(`finalPrice`) 포함

> 요청의 `couponId`는 쿠폰 템플릿 ID가 아닌 **발급쿠폰(`IssuedCouponModel`) ID**다.  
> 소유권·사용 여부는 발급쿠폰의 속성이고, USED 상태 변경도 발급쿠폰에 이뤄진다.

---

## 변경 범위 및 설계

### 1. Interfaces Layer

#### `OrderV1Dto.CreateRequest`

```java
public record CreateRequest(
    @NotEmpty @Valid List<OrderItemRequest> items,
    Long couponId   // nullable, 미적용 시 null
)
```

#### `OrderV1Dto.OrderResponse`

```java
public record OrderResponse(
    Long id,
    String status,
    BigDecimal originalPrice,   // 기존 totalPrice → 이름 변경
    BigDecimal discountAmount,  // 신규 추가
    BigDecimal finalPrice,      // 신규 추가
    List<OrderItemResponse> items,
    ZonedDateTime createdAt
)
```

#### `OrderV1Controller.createOrder()`

```java
orderFacade.createOrder(loginId, loginPw, items, request.couponId())
```

---

### 2. Application Layer

#### `OrderFacade.createOrder()` 흐름 변경

`OrderFacade`에 `IssuedCouponService`, `CouponTemplateService` 의존성을 추가한다.

**변경된 처리 흐름:**

```
1. 유저 인증 조회
2. 상품 일괄 조회
3. originalPrice = 아이템 subtotal 합산
4. discountAmount = BigDecimal.ZERO
5. [issuedCouponId가 있을 때만]
   a. issuedCouponService.getMyIssuedCoupon(issuedCouponId, userId)
      → 발급쿠폰 조회 + 소유권 검증 + templateId 확보
      실패 시: NOT_FOUND (존재하지 않음) / FORBIDDEN (타 유저 소유)
   b. couponTemplateService.getById(issued.getCouponTemplateId())
      → 쿠폰 템플릿 조회
   c. template.validateApplicability(originalPrice)
      → 만료됐거나 최소 주문 금액 미달이면 BAD_REQUEST 예외
   d. issuedCouponService.use(issued.getId())
      → use() 내부에서 USED 상태 검증 후 낙관적 락(@Version)으로 USED 변경
      실패 시: CONFLICT — 이미 사용된 쿠폰(use() 내부 검증) 또는 동시 요청 충돌(ObjectOptimisticLockingFailureException → ApiControllerAdvice에서 CONFLICT 변환)
   e. discountAmount = template.calculateDiscountAmount(originalPrice)
6. 재고 차감 (stockService)
7. orderService.create(userId, itemDataList, discountAmount)
```

> `createOrder()`가 이미 `@Transactional`이므로, 쿠폰 USED 처리와 주문 저장이 같은 트랜잭션에서 원자적으로 수행된다. 주문 실패 시 쿠폰 상태도 자동 롤백된다.

**메서드 시그니처:**

```java
public OrderInfo createOrder(String loginId, String loginPw, List<OrderItemDto> orderItems, Long issuedCouponId)
//                                                                                                ^^^^^^^^^^^^^
//                                                                                                nullable
```

#### `OrderInfo`

```java
public record OrderInfo(
    Long id,
    String status,
    BigDecimal originalPrice,   // 기존 totalPrice → 이름 변경
    BigDecimal discountAmount,  // 신규 추가
    BigDecimal finalPrice,      // 신규 추가
    List<OrderItemInfo> items,
    ZonedDateTime createdAt
)
```

---

### 3. Domain Layer

#### `IssuedCouponModel`

`validateOwner()`, `use()` 메서드를 추가해 소유권 검증과 상태 전이를 도메인 모델에서 처리한다. `use()` 내부에서 상태 검증(`USED` 여부)도 함께 수행한다.  
만료 여부 검증은 `CouponTemplateModel.validateApplicability()`에 위임하고, 동시성 보호는 `@Version` 낙관적 락이 담당한다.

```java
// OrderModel.validateOwner()와 동일한 패턴
public void validateOwner(Long userId) {
    if (!this.userId.equals(userId)) {
        throw new CoreException(ErrorType.FORBIDDEN, "해당 쿠폰에 접근할 수 없습니다.");
    }
}

// 동시성 보호는 @Version 낙관적 락이 담당한다.
public void use() {
    if (this.status == CouponStatus.USED) {
        throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
    }
    this.status = CouponStatus.USED;
}
```

#### `DiscountPolicy`

`calculateDiscountAmount()` 메서드를 추가한다.

```java
public BigDecimal calculateDiscountAmount(BigDecimal originalPrice) {
    return switch (type) {
        case FIXED -> value.min(originalPrice); // 클램핑: 할인액이 주문 금액 초과 시 originalPrice 반환
        case RATE -> originalPrice.multiply(value).divide(new BigDecimal("100"), 0, RoundingMode.FLOOR);
    };
}
```

- FIXED: `value`가 `originalPrice`를 초과하면 `originalPrice`로 클램핑 — `finalPrice`는 항상 0 이상 보장
- RATE: 0~100% 범위 제약으로 구조상 초과 불가능. 원 단위 미만은 내림(FLOOR) 처리

#### `CouponTemplateModel`

할인 금액 계산 위임 메서드와 쿠폰 적용 가능 여부 검증 메서드를 추가한다.

```java
public BigDecimal calculateDiscountAmount(BigDecimal originalPrice) {
    return discountPolicy.calculateDiscountAmount(originalPrice);
}

public void validateApplicability(BigDecimal originalPrice) {
    if (isExpired()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
    }
    if (this.minOrderAmount != null && originalPrice.compareTo(this.minOrderAmount) < 0) {
        throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다.");
    }
}
```

- `calculateDiscountAmount`: Facade가 `DiscountPolicy` 내부 구조에 의존하지 않도록 위임
- `validateApplicability`: 만료 여부·최소 주문 금액 검증을 도메인 모델에 캡슐화. Facade가 템플릿 조회 후 직접 호출한다.

#### `IssuedCouponRepository`

`findById`와 `save` 메서드를 추가한다.

```java
Optional<IssuedCouponModel> findById(Long id);

// 낙관적 락(@Version)으로 USED 상태 전이를 보호한다.
// 동시 요청 충돌 시 ObjectOptimisticLockingFailureException이 발생하며, ApiControllerAdvice에서 CONFLICT로 변환된다. (ADR-005 참고)
IssuedCouponModel save(IssuedCouponModel issuedCoupon);
```

#### `IssuedCouponService`

두 메서드를 추가한다.

```java
// 발급쿠폰 조회 + 소유권 검증. Facade가 templateId 확보 전에 사용한다.
public IssuedCouponModel getMyIssuedCoupon(Long issuedCouponId, Long userId) {
    IssuedCouponModel issued = getById(issuedCouponId);
    issued.validateOwner(userId);
    return issued;
}

// 낙관적 락(@Version)으로 USED 전이 보호. 충돌 시 ObjectOptimisticLockingFailureException → ApiControllerAdvice에서 CONFLICT 변환.
// 소유권 검증은 Facade의 getMyIssuedCoupon()에서 완결되며, 상태 검증은 use() 내부에서 수행한다.
public void use(Long issuedCouponId) {
    IssuedCouponModel issued = getById(issuedCouponId);
    issued.use();
    issuedCouponRepository.save(issued);
}
```

#### `OrderModel`

주문 금액 필드를 확장한다.

| 기존 | 변경 후 |
|---|---|
| `totalPrice` (아이템 합계) | `originalPrice` (이름 변경, 값 동일 — 쿠폰 적용 전 합계) |
| — | `discountAmount` 신규 추가 (할인 금액, 쿠폰 없으면 0) |
| — | `finalPrice` 신규 추가 (최종 결제 금액 = originalPrice - discountAmount) |

`OrderModel.create()` 시그니처 변경:

```java
public static OrderModel create(Long userId, List<OrderItemData> itemDataList, BigDecimal discountAmount)
```

- `originalPrice` = 아이템 subtotal 합계 (기존 `totalPrice`와 동일한 값)
- `discountAmount` = 전달받은 값 (쿠폰 없으면 `BigDecimal.ZERO`)
- `finalPrice` = `originalPrice - discountAmount` (`DiscountPolicy` 클램핑으로 항상 0 이상 보장)

---

### 4. Infrastructure Layer

#### `IssuedCouponJpaRepository`

`findById`, `save` 모두 `JpaRepository`가 이미 제공하므로 추가 선언 불필요.  
`IssuedCouponModel`에 `@Version` 컬럼을 추가하면 JPA가 `save()` 시 자동으로 낙관적 락을 적용한다.

#### `IssuedCouponRepositoryImpl`

`findById`, `save` 위임 구현 추가.

---

## 테스트 전략

### `DiscountPolicyTest` — 단위 테스트 (신규)

| 시나리오 | 기대 결과 |
|---|---|
| FIXED 타입: `value < originalPrice`로 `calculateDiscountAmount()` 호출 | `value` 반환 |
| FIXED 타입: `value > originalPrice`로 `calculateDiscountAmount()` 호출 | `originalPrice` 반환 (클램핑) |
| RATE 타입: `calculateDiscountAmount()` 호출 | `originalPrice × (value / 100)` 반환 |

### `CouponTemplateModelTest` — 단위 테스트 (신규)

| 시나리오 | 기대 결과 |
|---|---|
| 유효한 템플릿 + 최소 주문 금액 이상으로 `validateApplicability()` 호출 | 정상 통과 |
| 유효한 템플릿 + `minOrderAmount=null`로 `validateApplicability()` 호출 | 정상 통과 (최소 주문 금액 제한 없음) |
| 만료된 템플릿으로 `validateApplicability()` 호출 | `BAD_REQUEST` 예외 |
| 최소 주문 금액 미달로 `validateApplicability()` 호출 | `BAD_REQUEST` 예외 |

### `IssuedCouponModelTest` — 단위 테스트 (신규)

| 시나리오 | 기대 결과 |
|---|---|
| 일치하는 userId로 `validateOwner()` 호출 | 정상 통과 |
| 불일치하는 userId로 `validateOwner()` 호출 | `FORBIDDEN` 예외 |
| AVAILABLE 상태 쿠폰으로 `use()` 호출 | `USED` 상태로 변경 |
| USED 상태 쿠폰으로 `use()` 호출 | `CONFLICT` 예외 |

### `IssuedCouponServiceTest` — 단위 테스트 (수정)

| 시나리오 | 기대 결과 |
|---|---|
| 존재하는 id + 일치하는 userId로 `getMyIssuedCoupon()` 호출 | 해당 모델 반환 |
| 존재하지 않는 id로 `getMyIssuedCoupon()` 호출 | `NOT_FOUND` 예외 |
| 존재하는 id + 불일치하는 userId로 `getMyIssuedCoupon()` 호출 | `FORBIDDEN` 예외 |
| AVAILABLE 상태 쿠폰에 `use()` 호출 | status = USED, 예외 없음 |
| USED 상태 쿠폰에 `use()` 호출 | `CONFLICT` 예외 |

### `OrderFacadeIntegrationTest` — 통합 테스트 (수정)

| 시나리오 | 기대 결과 |
|---|---|
| 쿠폰 없이 주문 | discountAmount=0, finalPrice=originalPrice, 주문 저장 |
| 유효한 쿠폰으로 주문 | 할인 금액 적용, 쿠폰 USED 상태로 변경, 주문 저장 |
| 존재하지 않는 issuedCouponId | `NOT_FOUND` 예외, 주문 미저장 |
| 타 유저 소유 쿠폰 | `FORBIDDEN` 예외, 주문 미저장 |
| 이미 사용된 쿠폰 | `CONFLICT` 예외, 주문 미저장 |
| 만료된 쿠폰 | `BAD_REQUEST` 예외, 주문 미저장 |
| 최소 주문 금액 미달 | `BAD_REQUEST` 예외, 주문 미저장 |

### `OrderV1ApiE2ETest` — E2E 테스트 (수정)

| 시나리오 | HTTP 상태 | 추가 검증 |
|---|---|---|
| 유효한 쿠폰 포함 주문 | 200 | 응답에 originalPrice/discountAmount/finalPrice 포함, 쿠폰 USED 상태 DB 확인 |
| 존재하지 않는 issuedCouponId | 404 | — |
| 타 유저 소유 쿠폰 | 403 | — |
| 이미 사용된 쿠폰 | 409 | — |
| 만료된 쿠폰 | 400 | — |
| 최소 주문 금액 미달 | 400 | — |

---

## 변경 파일 목록

| 파일 | 변경 종류 |
|---|---|
| `domain/coupon/DiscountPolicy.java` | `calculateDiscountAmount(BigDecimal)` 추가 (FIXED 클램핑, RATE 비율 계산) |
| `domain/coupon/CouponTemplateModel.java` | `calculateDiscountAmount(BigDecimal)`, `validateApplicability(BigDecimal)` 추가 |
| `domain/coupon/IssuedCouponModel.java` | `@Version` 컬럼 추가, `validateOwner(Long)`, `use()` 추가 (상태 검증 내장) |
| `domain/coupon/IssuedCouponRepository.java` | `findById(Long)`, `save(IssuedCouponModel)` 추가 |
| `domain/coupon/IssuedCouponService.java` | `getMyIssuedCoupon(Long, Long)`, `use(Long)` 추가 |
| `infrastructure/coupon/IssuedCouponRepositoryImpl.java` | `findById`, `save` 위임 구현 추가 |
| `domain/order/OrderModel.java` | `totalPrice` → `originalPrice` 이름 변경, `discountAmount`·`finalPrice` 신규 추가 |
| `domain/order/OrderService.java` | `create(userId, itemDataList)` → `create(userId, itemDataList, discountAmount)` 시그니처 변경 |
| `application/order/OrderFacade.java` | 쿠폰 의존성 추가, `createOrder()` 쿠폰 처리 로직 추가 |
| `application/order/OrderInfo.java` | `totalPrice` → `originalPrice` 이름 변경, `discountAmount`·`finalPrice` 신규 추가 |
| `interfaces/api/order/OrderV1Dto.java` | `CreateRequest.couponId` 추가, `OrderResponse` 필드 추가 |
| `interfaces/api/order/OrderV1Controller.java` | `createOrder()` couponId 전달 |
| `test/.../DiscountPolicyTest.java` | `calculateDiscountAmount()` 단위 테스트 추가 (신규) |
| `test/.../CouponTemplateModelTest.java` | `validateApplicability()` 단위 테스트 추가 |
| `test/.../IssuedCouponModelTest.java` | `validateOwner()`, `use()` 단위 테스트 추가 |
| `test/.../IssuedCouponServiceTest.java` | `getMyIssuedCoupon()`, `use()` 단위 테스트 추가 |
| `test/.../OrderModelTest.java` | `getTotalPrice()` → `getOriginalPrice()`, `create()` 시그니처 변경 반영 |
| `test/.../OrderServiceTest.java` | `create()` 시그니처 변경 및 `getTotalPrice()` → `getOriginalPrice()` 반영 |
| `test/.../OrderFacadeIntegrationTest.java` | 쿠폰 적용 통합 테스트 추가 |
| `test/.../OrderV1ApiE2ETest.java` | 쿠폰 포함 주문 E2E 테스트 추가; 기존 `totalPrice()` → `originalPrice()` 검증 변경, `CreateRequest` 생성자에 `couponId` 인자(null) 추가 |

## 참고

- [ADR-004](../adr/ADR-004-issued-coupon-use-parameter.md) — IssuedCouponService.use() 파라미터를 ID로 결정
- [ADR-005](../adr/ADR-005-issued-coupon-use-optimistic-lock.md) — 쿠폰 사용 처리에 낙관적 락 채택
