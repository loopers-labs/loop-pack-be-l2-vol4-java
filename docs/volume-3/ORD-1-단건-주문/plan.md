# Plan: ORD-1 단건 주문

**Spec**: ./spec.md
**작성일**: 2026-05-27 (리뷰 반영 갱신)

## 요약

회원이 `POST /api/v1/orders`로 항목 1개를 주문한다. Order aggregate 골격(`OrderModel` + `OrderItemModel` + `Quantity` VO + `OrderStatus` enum + `OrderRepository`)을 신설한다. `OrderItemModel`은 `@OneToMany` 연관이 아니라 **`orderId` 참조 컬럼**으로 주문에 속한다(코드베이스 전반의 ID 참조 패턴과 일관 — `ProductModel.brandId`·`LikeModel.userId/productId`와 동일). `OrderFacade`가 회원 인증(`@LoginUser`)을 받아, 항목별로 ① 상품 활성 조회(404) ② 브랜드명 조회(스냅샷용) ③ **원자적 조건부 재고 차감**(결정 4 A안, 0건이면 409)을 수행하고, 총액을 합산해 `OrderModel`을 만든 뒤 `OrderRepository.save(order, items)`로 주문·항목을 한 트랜잭션에 저장한다. 요청 본문은 ORD-2와 공유하기 위해 처음부터 항목 리스트(`items: [{productId, quantity}]`)다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수 (Facade가 Repository 주입, 도메인 서비스 없음)
- [x] 검증: 수량 1 이상은 `Quantity.from()` VO 단일 원천. DTO엔 `@NotEmpty`(items)·`@NotNull`·`@Positive`(quantity)로 1차 방어
- [x] 인증: 회원 인증 `@LoginUser AuthenticatedUser loginUser` → `loginUser.userId()`. 실패 시 401 UNAUTHENTICATED
- [x] 결정 2(즉시 차감): 주문 생성 시점에 즉시 재고 차감
- [x] 결정 4(동시성 A안): **원자적 조건부 갱신** `UPDATE products SET stock = stock - :qty WHERE id = :id AND deleted_at IS NULL AND stock >= :qty`. 0건 → 409. in-memory `Stock.decrease`는 도입하지 않음(race 방지, 단일 메커니즘)
- [x] 결정 5(스냅샷): `OrderItemModel`에 productId·productName·productBrandName·unitPrice(int)·quantity 평탄 보존
- [x] 결정 7(soft delete): `OrderModel`/`OrderItemModel`은 `BaseEntity` 상속. 조회는 전부 Active 기준(`...AndDeletedAtIsNull`)으로 일관 (리뷰 결정 B-1)
- [x] 총액: 공용 `Money` 미도입. `OrderFacade`가 항목 `totalPrice()` 합을 계산해 `OrderModel`에 주입(int)

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/order/OrderV1Controller.java` (신규) — `@RequestMapping("/api/v1/orders")`. `POST` `createOrder(@Valid @RequestBody OrderV1Dto.CreateRequest, @LoginUser AuthenticatedUser)`, `@ResponseStatus(CREATED)`. `orderFacade.createOrder(userId, request.toCommandItems())` 후 `OrderResponse.from`.
- `interfaces/api/order/OrderV1Dto.java` (신규) — `CreateRequest(List<OrderItemRequest> items @NotEmpty @Valid)` + `toCommandItems()`, `OrderItemRequest(productId @NotNull, quantity @NotNull @Positive)`, `OrderResponse(orderId, status(String), orderedAt, totalPrice, items) + from(OrderInfo)`, `OrderItemResponse(...) + from(OrderItemInfo)`.
- `interfaces/api/order/OrderV1ApiSpec.java` (신규) — SpringDoc.

### application
- `application/order/OrderFacade.java` (신규) — `@Service @Transactional`. 주입: `ProductRepository`·`BrandRepository`·`OrderRepository`. `createOrder(Long userId, List<OrderItemCommand> items)`:
  - 항목 순회: `Quantity.from(qty)` → `productRepository.getActiveById(productId)`(404) → `brandRepository.getActiveById(brandId)`(브랜드명) → `productRepository.decreaseStock(productId, qty)` 0건이면 `CoreException(CONFLICT)` → `OrderItemModel.builder()...build()`.
  - `int totalPrice = items.stream().mapToInt(OrderItemModel::totalPrice).sum()`, `ZonedDateTime now = ZonedDateTime.now()`(중앙 1회 선언).
  - `OrderModel.builder().userId(userId).orderedAt(now).totalPrice(totalPrice).build()` → `orderRepository.save(order, orderItems)` → `OrderInfo.from(savedOrder, orderItems)`.
- `application/order/OrderItemCommand.java` (신규) — `record(Long productId, Integer quantity)`.
- `application/order/OrderInfo.java` (신규) — `record(orderId, OrderStatus status, orderedAt, totalPrice, List<OrderItemInfo> items)` + `from(OrderModel order, List<OrderItemModel> items)`.
- `application/order/OrderItemInfo.java` (신규) — `record(productId, productName, brandName, unitPrice, quantity)` + `from(OrderItemModel)`.

### domain
- `domain/order/OrderModel.java` (신규) — `@Entity @Table(name="orders")` extends BaseEntity, **클래스 레벨 `@Builder`**. 필드: `Long userId`, `@Enumerated(STRING) @Builder.Default OrderStatus status = CREATED`, `ZonedDateTime orderedAt`, `Integer totalPrice`. **items 컬렉션 없음.** 빌더는 `userId·orderedAt·totalPrice`만 받고 status는 기본값.
- `domain/order/OrderItemModel.java` (신규) — `@Entity @Table(name="order_items")` extends BaseEntity. 필드: `Long orderId`, `Long productId`, `String productName`, `String productBrandName`, `Integer unitPrice`, `@Embedded Quantity quantity`. `@Builder` 생성자(orderId 제외 — 스냅샷 필드만). `assignOrder(Long orderId)`(영속 시점 1회 배선), `totalPrice() = unitPrice * quantity.value()`.
- VO: `domain/order/Quantity.java` (신규) — `record @Embeddable`, `from(value)`: null/`< 1` → BAD_REQUEST, `MIN_QUANTITY=1`.
- `domain/order/OrderStatus.java` (신규) — `enum { CREATED("주문 생성됨") }` (description 보유).
- `domain/order/OrderRepository.java` (신규) — `save(OrderModel order, List<OrderItemModel> items)`, `getActiveById`, `getActiveByIdAndUserId`, `findActiveByUserIdAndOrderedAtBetween`, `findActiveByPage`, `findActiveItemsByOrderId`. (ORD-1 범위는 save만, 나머지는 후속 cycle이 채우지만 인터페이스는 함께 정의)
- 도메인 서비스: **없음**.

### infrastructure
- `infrastructure/order/OrderJpaRepository.java` (신규) — `extends JpaRepository<OrderModel, Long>`. `findByIdAndDeletedAtIsNull`, `findByIdAndUserIdAndDeletedAtIsNull`, `findByUserIdAndDeletedAtIsNullAndOrderedAtGreaterThanEqualAndOrderedAtLessThan`, `findByDeletedAtIsNull`(Pageable). (JOIN FETCH 없음 — 연관 끊음)
- `infrastructure/order/OrderItemJpaRepository.java` (신규) — `extends JpaRepository<OrderItemModel, Long>`. `findByOrderIdAndDeletedAtIsNull(Long orderId)`.
- `infrastructure/order/OrderRepositoryImpl.java` (신규) — `@Component`, 두 JpaRepository 위임. `save(order, items)`: `orderJpaRepository.save(order)` → `items.forEach(assignOrder(savedId))` → `orderItemJpaRepository.saveAll(items)`. `getActive*`는 `orElseThrow(NOT_FOUND)`. 페이징은 `PageRequest.of(page,size,Sort.by(DESC,"orderedAt"))`.
- `infrastructure/product/ProductJpaRepository.java` (편집) — `@Transactional @Modifying @Query` `decreaseStock(Long, int)` 반환 int.
- `infrastructure/product/ProductRepositoryImpl.java`·`domain/product/ProductRepository.java` (편집) — `decreaseStock` 추가·위임.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `OrderItemModel`을 `@OneToMany` 연관이 아니라 `orderId` 참조로 (리뷰 결정 A-1) | 코드베이스 전체가 JPA 연관 없이 ID 참조 + 명시적 쿼리(`...AndDeletedAtIsNull`)를 쓴다. `@OneToMany`는 유일한 예외가 되고 lazy/N+1·단방향 extra UPDATE·`@SQLRestriction` 비용을 부른다. 항상 전부 조회하므로 lazy 이점도 없음 | `@OneToMany(cascade) @JoinColumn` 내장 컬렉션(코드베이스 비일관·lazy 함정) |
| 총액을 `OrderFacade`에서 합산 후 빌더에 주입 (리뷰 결정) | items를 안 들고 빌더가 `totalPrice`를 받는 형태. per-item 규칙(`OrderItemModel.totalPrice()`)은 도메인에 남고, 합산은 create 유스케이스의 조립으로 봄 | Order 정적 팩토리가 items 받아 내부 합산(엔티티에 정적 팩토리 추가, "빌더만" 패턴 이탈) |
| `orderedAt`을 Facade에서 `now` 1회 선언해 주입 (리뷰 결정) | 시각 생성을 중앙화해 파편화 방지·테스트 시각 통제 용이 | 모델 생성자 내부에서 `ZonedDateTime.now()`(호출처마다 파편) |
| in-memory `Stock.decrease` 미도입, 원자적 조건부 UPDATE만 | 결정 4 A안 핵심(검사·차감 원자성 DB 보장). in-memory 차감은 read-modify-write race | 클래스 다이어그램대로 in-memory 차감(동시성 비보장) |
| 요청 본문을 처음부터 리스트로 | ORD-2와 API 공유. 단건→리스트 변경은 계약 churn | ORD-1 단건 본문 후 ORD-2에서 리스트 전환 |
| 주문 조회 전부 Active 필터(`...AndDeletedAtIsNull`) (리뷰 결정 B-1) | 주문·항목도 soft delete 대상. 코드베이스의 Active 조회 컨벤션과 일관 | 필터 생략(soft delete 도메인인데 활성/삭제 구분 없음) |
