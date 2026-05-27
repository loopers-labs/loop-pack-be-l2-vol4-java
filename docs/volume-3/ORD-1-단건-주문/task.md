# Task: ORD-1 단건 주문

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase F: Foundational (Order aggregate 골격 + Product 원자 차감)

- [X] T001 `Quantity` VO 작성 + 단위 테스트 — `main/.../domain/order/Quantity.java`, `test/.../domain/order/QuantityTest.java` (`record @Embeddable`, `@Column(name="quantity", nullable=false) Integer value`; 1 통과 / 0·음수·null BAD_REQUEST, `MIN_QUANTITY=1` 상수)
- [X] T002 `OrderStatus` enum 작성 — `main/.../domain/order/OrderStatus.java` (`CREATED`만)
- [X] T003 `OrderItemModel` 작성 + 단위 테스트 — `main/.../domain/order/OrderItemModel.java`, `test/.../domain/order/OrderItemModelTest.java` (`@Entity @Table(name="order_items")` extends BaseEntity, 필드 `orderId`·productId·productName·productBrandName·unitPrice(Integer)·`@Embedded Quantity`, `@Builder`(orderId 제외 스냅샷 필드), `assignOrder(Long orderId)`(영속 1회 배선), `totalPrice() = unitPrice * quantity.value()`)
- [X] T004 `OrderModel` 작성 + 단위 테스트 — `main/.../domain/order/OrderModel.java`, `test/.../domain/order/OrderModelTest.java` (`@Entity @Table(name="orders")` extends BaseEntity, 클래스 레벨 `@Builder`. 필드 userId·`@Enumerated(STRING) @Builder.Default status=CREATED`·`ordered_at` ZonedDateTime·totalPrice(Integer). **items 컬렉션 없음**(orderId 참조). 빌더는 userId·orderedAt·totalPrice 수신)
- [X] T005 `OrderRepository` 인터페이스 — `main/.../domain/order/OrderRepository.java` (`save(OrderModel, List<OrderItemModel>)`·`getActiveById`·`getActiveByIdAndUserId`·`findActiveByUserIdAndOrderedAtBetween`·`findActiveByPage`·`findActiveItemsByOrderId`)
- [X] T006 `OrderJpaRepository`·`OrderItemJpaRepository`·`OrderRepositoryImpl` — `main/.../infrastructure/order/OrderJpaRepository.java`(`...AndDeletedAtIsNull` 파생 쿼리, JOIN FETCH 없음), `OrderItemJpaRepository.java`(`findByOrderIdAndDeletedAtIsNull`), `OrderRepositoryImpl.java`(`@Component`, 두 JpaRepository 위임. save는 order 저장 → `assignOrder(savedId)` → `saveAll(items)`)
- [X] T007 Product 원자적 차감 추가 — `main/.../domain/product/ProductRepository.java`(`int decreaseStock(Long productId, int quantity)` 추가), `main/.../infrastructure/product/ProductJpaRepository.java`(`@Transactional @Modifying @Query` UPDATE ... WHERE id AND deletedAt IS NULL AND stock.value >= :quantity, 반환 int), `main/.../infrastructure/product/ProductRepositoryImpl.java`(위임)
- [X] T008 `OrderRepository` 통합 테스트 — `test/.../infrastructure/order/OrderRepositoryIntegrationTest.java` (save(order,items)로 주문+항목 저장·항목에 orderId 배정 / Active 조회(get·find)·soft delete 필터)
- [X] T009 Product 원자 차감 통합 테스트 — `test/.../infrastructure/product/ProductRepositoryIntegrationTest.java`에 추가 (재고≥수량 → 1건·재고 감소 / 재고<수량 → 0건·재고 미변경)
- [X] T010 Product 원자 차감 동시성 통합 테스트 — `test/.../infrastructure/product/ProductRepositoryIntegrationTest.java`에 추가 (같은 상품 동시 차감 시 재고 음수 불가, 성공 건수 = floor(초기재고/수량))

## Phase 1: 구현 (단건 주문 유스케이스)

- [X] T011 `OrderItemCommand`·`OrderInfo`·`OrderItemInfo` 작성 — `main/.../application/order/OrderItemCommand.java`(`record(Long productId, Integer quantity)`), `main/.../application/order/OrderInfo.java`(`record(orderId, OrderStatus status, ZonedDateTime orderedAt, Integer totalPrice, List<OrderItemInfo> items)` + `from(OrderModel order, List<OrderItemModel> items)`), `main/.../application/order/OrderItemInfo.java`(`record(productId, productName, brandName, unitPrice, quantity)` + `from(OrderItemModel)`)
- [X] T012 `OrderFacade.createOrder` 작성 + 단위 테스트 — `main/.../application/order/OrderFacade.java`, `test/.../application/order/OrderFacadeTest.java` (`@Service @Transactional`; 항목 순회 — 상품 미존재 → NOT_FOUND, 원자 차감 0건 → CONFLICT, 정상 → 스냅샷. 총액은 facade에서 `mapToInt(totalPrice).sum()`, `now` 1회 선언, `orderRepository.save(order, items)` 후 `OrderInfo.from(savedOrder, items)`. ORD-1은 정렬·중복검사 없음)
- [X] T013 `OrderV1Dto` 작성 — `main/.../interfaces/api/order/OrderV1Dto.java` (`CreateRequest(List<OrderItemRequest> items @NotEmpty)` + `toCommandItems()`, `OrderItemRequest(productId @NotNull, quantity @NotNull @Positive)`, `OrderResponse(orderId, status(String), orderedAt, totalPrice, List<OrderItemResponse>) + from(OrderInfo)`, `OrderItemResponse(productId, productName, brandName, unitPrice, quantity) + from(OrderItemInfo)`)
- [X] T014 `OrderV1ApiSpec` 작성 — `main/.../interfaces/api/order/OrderV1ApiSpec.java` (`@Tag`/`@Operation`)
- [X] T015 `OrderV1Controller` 작성 — `main/.../interfaces/api/order/OrderV1Controller.java` (`@RequestMapping("/api/v1/orders")`, `POST` `@ResponseStatus(CREATED)`, `@Valid @RequestBody`, `@LoginUser AuthenticatedUser`)
- [X] T016 E2E 테스트 — `test/.../interfaces/api/OrderV1ApiE2ETest.java` (정상 201+meta.result SUCCESS+응답 키(orderId·status·orderedAt·totalPrice·items) / 인증 헤더 없음 401 / 상품 미존재 404 / 수량 0 → 400 / 재고 초과 → 409. statusCode+meta.result+errorCode까지, 메시지 비단언. fixture는 Brand/Product JpaRepository.save 직접 + 회원은 UserJpaRepository.save 직접)

## Phase 2: 마무리

- [X] T017 spec 테스트 계획 대비 누락 점검 (Quantity 경계 / OrderItemModel·OrderModel 불변식 / Facade 분기(404·409·정상) / Integration 저장·원자차감·동시성 / E2E 5분기 매핑). in-memory Stock.decrease 미도입에 따라 "Stock(차감) VO 단위" 행은 Integration 원자차감 테스트로 대체됨을 확인
- [X] T018 `.http` 파일 — `http/commerce-api/order-v1.http` (단건 주문: 정상 / 인증 헤더 누락 / 상품 미존재 / 수량 0 / 재고 초과)
