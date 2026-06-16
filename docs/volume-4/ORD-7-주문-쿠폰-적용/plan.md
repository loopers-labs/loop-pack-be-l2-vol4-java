# Plan: ORD-7 주문 쿠폰 적용

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

기존 주문 생성(`POST /api/v1/orders`)을 확장해 발급 쿠폰 한 장(`userCouponId`, 선택)을 적용한다. `OrderFacade.createOrder`에 쿠폰 검증·할인 계산·사용 전이 단계를 끼우고, `OrderModel`의 단일 금액(`totalPrice`)을 원 주문 금액·할인 금액·최종 결제 금액 + `userCouponId`로 교체한다. 흐름은 `02-sequence-diagrams.md`의 ORD-7 다이어그램을 따른다. 본 변경은 기존 주문 생성·조회(ORD-1·2·3·4)의 금액 모델을 함께 바꾸므로 blast radius가 넓다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (기존 Order·Coupon 골격 확장)

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수
- [x] 회원 인증 `@LoginUser`, 회원 존재 `getActiveById`(02 공통 분기)
- [x] 쿠폰 소유 검증은 `UserCouponRepository.getActiveByIdAndUserId`(부재·타인 한 조회로 404, 결정 7) — `isOwnedBy` 도메인 메서드 두지 않음(02 다이어그램)
- [x] 사용 가능 검증 `UserCoupon.isAvailable(now)`(getStatus 기반), 사용됨·만료 409
- [x] 할인 계산 `UserCoupon.calculateDiscount(orderAmount)`(최소금액 미달 409 + `DiscountType.calculate` 위임), 정액 캡·정률 내림(결정 8)
- [x] 쿠폰 사용 전이 `use(now)`, 트랜잭션 한 경계(`@Transactional`)에서 재고·쿠폰·주문 — 실패 시 전체 롤백
- [x] **Order는 정적 팩토리 대신 Lombok `@Builder` 유지**(매개변수 증가). 세 금액 정합(`final = original − discount`)은 Facade가 계산해 build(기존 `totalPrice` 계산 위치 승계)
- [x] 금액 스냅샷 3분리 + `userCouponId`(결정 6). `userCouponId` nullable(미적용 시 null)
- [x] 동시성(USED 전이·재고 경합)은 단계 3~4 — 본 cycle은 트랜잭션 원자성만 일반 테스트로 검증

## 레이어별 설계 결정 & 파일 맵

### domain
- `domain/order/OrderModel.java` (편집) — `totalPrice` 필드 제거, `Integer originalAmount`·`Integer discountAmount`·`Integer finalAmount`(모두 `nullable=false`)·`Long userCouponId`(nullable) 추가. 기존 `@Builder` 유지(필드만 확장). `OrderItem`·`Quantity`·`OrderStatus`는 불변.
- `domain/coupon/UserCouponModel.java` (편집) — `apply(int orderAmount, ZonedDateTime now)`(쿠폰 적용 단일 진입점 — `isAvailable` 미충족 시 CONFLICT → 최소금액 검증 포함 할인 계산 → `usedAt = now` 기록 → 할인액 반환. 자기일관성: 실패 시 `usedAt` 미기록), `isAvailable(ZonedDateTime now)`(`getStatus(now) == AVAILABLE`), `use(ZonedDateTime now)`(`usedAt = now` 기록 — 발급분 사용 처리 primitive). `calculateDiscount`는 private(최소금액 검증 + `DiscountType.calculate` 위임). (소유 검증은 레포지토리)
- `domain/coupon/DiscountType.java` (편집) — `public abstract int calculate(int orderAmount, int value)` 추가, 각 상수 오버라이드: FIXED `Math.min(value, orderAmount)`, RATE `orderAmount * value / 100`(정수 나눗셈 내림). (결정 8)

### application
- `application/order/OrderFacade.java` (편집) — `createOrder(Long userId, List<OrderItemCommand> itemCommands, Long userCouponId, ZonedDateTime now)`:
  1. `UserModel user = userRepository.getActiveById(userId)`
  2. 중복 상품 검증 + 재고 차감해 `orderItems` 생성(기존 흐름)
  3. `int originalAmount = calculateOriginalAmount(orderItems)`
  4. `int discountAmount = 0; if (userCouponId != null) { UserCouponModel userCoupon = userCouponRepository.getActiveByIdAndUserId(userCouponId, user.getId()); discountAmount = userCoupon.apply(originalAmount, now); }` (가용·최소금액 검증·할인 계산·사용 전이를 `apply` 한 메서드가 응집)
  5. `int finalAmount = originalAmount - discountAmount`
  6. `OrderModel order = OrderModel.builder().userId(user.getId()).orderedAt(now).originalAmount(originalAmount).discountAmount(discountAmount).finalAmount(finalAmount).userCouponId(userCouponId).build()`
  7. `orderRepository.save(order, orderItems)` → `OrderInfo.of(...)`
  의존 추가: `UserCouponRepository`. `calculateTotalPrice` → `calculateOriginalAmount`로 개명.
- `application/order/OrderInfo.java` (편집) — `totalPrice` → `originalAmount`·`discountAmount`·`finalAmount`·`userCouponId`.
- `application/order/OrderAdminInfo.java`·`OrderAdminSummaryInfo.java` (편집) — `totalPrice` → 세 금액(+ admin 상세는 `userCouponId` 포함 검토). 관리자 주문 조회(ORD-3·4) 출력 동반 변경.

### interfaces
- `interfaces/api/order/OrderV1Dto.java` (편집) — `CreateRequest`에 `Long userCouponId`(선택, 검증 없음) 추가, `toCommand`/컨트롤러가 전달. `OrderResponse`의 `totalPrice` → `originalAmount`·`discountAmount`·`finalAmount`·`userCouponId`.
- `interfaces/api/order/OrderV1Controller.java` (편집) — `createOrder`가 `request.userCouponId()` 전달.
- `interfaces/api/order/OrderV1ApiSpec.java` (편집) — `@Operation` 설명에 쿠폰 적용 추가.
- `interfaces/api/order/OrderAdminV1Dto.java` (편집) — 관리자 응답 `totalPrice` → 세 금액.

### infrastructure
- `infrastructure/coupon/UserCouponJpaRepository.java` (편집) — `Optional<UserCouponModel> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId)`.
- `infrastructure/coupon/UserCouponRepositoryImpl.java` (편집) — `getActiveByIdAndUserId`: `findByIdAndUserIdAndDeletedAtIsNull(...).orElseThrow(NOT_FOUND)`.
- `domain/coupon/UserCouponRepository.java` (편집) — `UserCouponModel getActiveByIdAndUserId(Long userCouponId, Long userId)` 추가.

### 설계 문서 정정
- `docs/volume-4/02-sequence-diagrams.md` — ORD-7 다이어그램의 `OrderModel.of(...)` → `OrderModel.builder()...build()`로 갱신(Builder 결정 반영).
- `docs/volume-4/03-class-diagram.md` — `Order`의 정적 팩토리 `create(...)` 표기를 Builder 사용으로 정정, `UserCoupon`의 `isOwnedBy` 제거(소유 검증은 레포지토리). (analyze에서 02↔03 정합 확정 후 반영)

## 기존 계약 변경 (totalPrice blast radius)

`Order.totalPrice` 필드 → 세 금액. 아래 동반 수정(테스트 포함). `OrderItem.totalPrice()`(항목 단가×수량 메서드)는 **불변**.

- main: `OrderModel`·`OrderInfo`·`OrderAdminInfo`·`OrderAdminSummaryInfo`·`OrderV1Dto`·`OrderAdminV1Dto`
- test: `OrderModelTest`·`OrderFacadeTest`·`OrderRepositoryIntegrationTest`·`OrderV1ApiE2ETest`·`OrderAdminV1ApiE2ETest`(픽스처의 `.totalPrice(...)` 빌더·응답 키 단언 변경)

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| Order에 정적 팩토리 대신 `@Builder` 유지 | 매개변수 증가(7개)로 팩토리 가독성 저하, 기존 OrderModel이 이미 `@Builder` | `create`/`of` 정적 팩토리 — 매개변수 과다, 02↔03 시그니처도 어긋남 |
| 세 금액 정합을 Facade에서 계산 | Builder는 정합 강제 못 함 → 단일 호출자(Facade)가 `final = original − discount` 보장(기존 totalPrice 계산 위치 승계) | 엔티티 팩토리가 final 계산 — Builder 포기해야 함 |
| 소유 검증을 레포지토리 `getActiveByIdAndUserId`로 | 부재·타인을 한 조회로 묶어 존재 추론 차단(결정 7), 기존 Order 패턴 | `findById` 후 `isOwnedBy` 도메인 검사 — 부재/타인 분기 노출·왕복 증가 |
| `use(now)`는 기록만(가용성 재검증 없음) | 02 다이어그램이 `isAvailable()` 선검사 후 `use()` 호출로 분리 | `use`가 가용성까지 검증 — 검증 이중화 |
