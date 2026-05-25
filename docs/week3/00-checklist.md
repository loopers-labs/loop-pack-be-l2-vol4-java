# Week 3 구현 체크리스트

> 설계 주도권은 개발자에게 있습니다. 각 단계는 나와 함께 하나씩 설계를 논의하며 진행합니다.
> TDD 순서: **Red (테스트 먼저) → Green (통과 코드) → Refactor**

---

## 현재 상태 파악

| 도메인 | 현재 상태 | 비고 |
|---|---|---|
| Member | ✅ 완성 | Member, Password, MemberService, MemberFacade |
| Product | ⚠️ 재설계 필요 | `ProductModel`에 stock 포함 — 설계 문서와 불일치. Brand 연결 없음 |
| Brand | ❌ 미구현 | 도메인 객체 없음 |
| Stock | ❌ 미구현 | Product에 인라인으로 존재 → 분리 필요 |
| Like | ❌ 미구현 | |
| Order | ❌ 미구현 | |

---

## 구현 단위

### Block 1. 공통 기반 (SoftDeletableEntity)

> `Brand`, `Product`가 상속하는 추상 클래스. 먼저 만들어야 이후 작업이 가능하다.

- [ ] `domain/SoftDeletableEntity` 추상 클래스 작성
  - `deletedAt`, `softDelete()`, `isDeleted()` 포함
  - `BaseEntity`를 상속

---

### Block 2. Brand 도메인

> UC-01 (브랜드 조회), UC-10 (어드민 브랜드 관리) 기반.

**도메인 모델**
- [ ] `domain/brand/Brand` 엔티티
  - `SoftDeletableEntity` 상속
  - `name` 필드 (null/blank 검증)
  - 정적 팩토리 메서드 `create(name)`
  - `update(name)` 메서드

**Repository**
- [ ] `domain/brand/BrandRepository` 인터페이스
  - `findById(Long)`, `save(Brand)`, `softDeleteById(Long)`

**도메인 서비스**
- [ ] `domain/brand/BrandService`
  - `getBrand(Long)` — 없거나 삭제됨 → `CoreException(NOT_FOUND)`
  - `createBrand(name)` → `Brand`
  - `updateBrand(Long, name)` → `Brand`
  - `deleteBrand(Long)` — 브랜드 소프트딜리트 + 상품 cascade 소프트딜리트 위임

**Infrastructure**
- [ ] `infrastructure/brand/BrandJpaRepository`
- [ ] `infrastructure/brand/BrandRepositoryImpl`

**단위 테스트**
- [ ] `domain/brand/BrandTest` — 생성/수정 검증, 소프트딜리트 검증
- [ ] `domain/brand/BrandServiceTest` — 없는 ID 조회 예외, 정상 흐름

---

### Block 3. Product 도메인 재설계

> 기존 `ProductModel`을 `Product`로 재작성. `stock` 인라인 제거, `brandId` / `likeCount` 추가.

**도메인 모델**
- [ ] `domain/product/Product` 엔티티 (기존 `ProductModel` 대체)
  - `SoftDeletableEntity` 상속
  - `brandId`, `name`, `price`, `likeCount` 필드
  - `price > 0`, `likeCount >= 0` 제약
  - 정적 팩토리 메서드 `create(brandId, name, price)`
  - `update(name, price)` — 브랜드 변경 불가
  - `incrementLikeCount()`, `decrementLikeCount()` — `likeCount >= 0` 보장

**Repository**
- [ ] `domain/product/ProductRepository` 인터페이스 업데이트
  - `findById`, `findByIds`, `findAll(brandId, sort, pageable)`, `save`, `softDeleteById`, `softDeleteAllByBrandId`

**도메인 서비스**
- [ ] `domain/product/ProductService` 업데이트
  - `getProduct(Long)` — 없거나 삭제됨 → `NOT_FOUND`
  - `getByIds(List<Long>)`, `getProducts(brandId, sort, pageable)`
  - `createProduct(brandId, name, price)` → `Product`
  - `updateProduct(Long, name, price)` → `Product`
  - `deleteProduct(Long)`
  - `incrementLikeCount(Long)`, `decrementLikeCount(Long)`
  - `softDeleteByBrandId(Long)` — Brand cascade 삭제용

**Infrastructure**
- [ ] `infrastructure/product/ProductJpaRepository` 업데이트
- [ ] `infrastructure/product/ProductRepositoryImpl` 업데이트

**단위 테스트**
- [ ] `domain/product/ProductTest` — likeCount 증감 경계값, 음수 방지
- [ ] `domain/product/ProductServiceTest` — 없는 ID, 삭제된 상품 조회 예외

---

### Block 4. Stock 도메인

> `Product`에서 분리된 독립 도메인. 원자적 차감으로 동시성 처리.

**도메인 모델**
- [ ] `domain/stock/Stock` 엔티티
  - `BaseEntity` 상속
  - `productId`, `quantity` 필드
  - `quantity >= 0` 제약
  - `isAvailable(int quantity)` — 조회 전용 (차감 전 guard로 사용하지 않음)
  - `deduct(int quantity)` — `quantity < 0`이면 `StockInsufficientException`

**Repository**
- [ ] `domain/stock/StockRepository` 인터페이스
  - `findByProductId(Long)`, `save(Stock)`
  - `deductStock(Long productId, int quantity) int` — `UPDATE ... WHERE quantity >= ?` 반환 affected rows

**도메인 서비스**
- [ ] `domain/stock/StockService`
  - `createStock(Long productId, int initialQuantity)`
  - `deductStock(Long productId, int quantity)` — affected rows = 0이면 `StockInsufficientException`

**Infrastructure**
- [ ] `infrastructure/stock/StockJpaRepository`
- [ ] `infrastructure/stock/StockRepositoryImpl`
  - `deductStock`: `@Modifying @Query("UPDATE Stock s SET s.quantity = s.quantity - :qty WHERE s.productId = :id AND s.quantity >= :qty")`

**단위 테스트**
- [ ] `domain/stock/StockTest` — `isAvailable`, `deduct` 경계값 (0, 양수, 음수 시도)
- [ ] `domain/stock/StockServiceTest` — 재고 부족 예외 흐름

---

### Block 5. Like 도메인

> UC-04/05/06 (좋아요 등록/취소/목록). 멱등 처리 포함.

**도메인 모델**
- [ ] `domain/like/Like` 엔티티
  - `BaseEntity` 상속 (소프트딜리트 없음 — 하드딜리트)
  - `userId`, `productId` 필드
  - `(userId, productId)` 복합 유니크 제약

**Repository**
- [ ] `domain/like/LikeRepository` 인터페이스
  - `existsByUserIdAndProductId(Long, Long)`, `findAllByUserId(Long)`, `save(Like)`, `deleteByUserIdAndProductId(Long, Long)`

**도메인 서비스**
- [ ] `domain/like/LikeService`
  - `getLikes(Long requestUserId, Long targetUserId)` — requestUserId ≠ targetUserId → `FORBIDDEN`
  - `addLike(Long userId, Long productId) boolean` — 이미 있으면 false 반환
  - `removeLike(Long userId, Long productId) boolean` — 없으면 false 반환

**Application Layer (Facade)**
- [ ] `application/like/LikeFacade`
  - `addLike(Long userId, Long productId)` — `LikeService.addLike()` 후 true면 `ProductService.incrementLikeCount()` 호출
  - `removeLike(Long userId, Long productId)` — `LikeService.removeLike()` 후 true면 `ProductService.decrementLikeCount()` 호출

**Infrastructure**
- [ ] `infrastructure/like/LikeJpaRepository`
- [ ] `infrastructure/like/LikeRepositoryImpl`

**단위 테스트**
- [ ] `domain/like/LikeServiceTest` — 중복 좋아요 멱등(false 반환), 없는 좋아요 취소(false 반환), 타인 목록 조회 금지
- [ ] `application/like/LikeFacadeTest` — addLike 시 ProductService.incrementLikeCount 호출 여부, removeLike 시 decrement 호출 여부

---

### Block 6. Order 도메인

> UC-07/08/09/12 (주문 생성/조회). 올-오어-낫싱 정책.

**도메인 모델**
- [ ] `domain/order/Order` 엔티티
  - `BaseEntity` 상속
  - `userId`, `totalAmount`, `orderedAt` 필드
  - `totalAmount > 0` 제약
  - `addItem(OrderItem)` 메서드
- [ ] `domain/order/OrderItem` 엔티티
  - `productId`, `quantity`, `OrderItemStatus` 필드
  - `quantity >= 1` 제약
  - `isOrdered()`, `cancel()` 메서드
- [ ] `domain/order/OrderItemSnapshot` 엔티티
  - `orderItemId`, `productName`, `brandName`, `price` 필드 — 모두 NOT NULL
- [ ] `domain/order/OrderItemStatus` enum — `ORDERED`, `CANCELLED`

**Repository**
- [ ] `domain/order/OrderRepository` 인터페이스
  - `findById(Long)`, `findAllByUserIdAndDateRange(Long, LocalDate, LocalDate)`, `findAll(Pageable)`, `save(Order)`
- [ ] `domain/order/OrderItemSnapshotRepository` 인터페이스
  - `saveAll(List<OrderItemSnapshot>)`

**도메인 서비스**
- [ ] `domain/order/OrderService`
  - `getOrders(Long userId, LocalDate start, LocalDate end)` — start > end → `BAD_REQUEST`
  - `getOrder(Long userId, Long orderId)` — 없음 → `NOT_FOUND`, 타인 주문 → `FORBIDDEN`
  - `createOrder(Long userId, List<OrderItem>, List<OrderItemSnapshot>)` → `Order`

**Application Layer (Facade)**
- [ ] `application/order/OrderFacade`
  - `createOrder(Long userId, List<{productId, quantity}>)` — 핵심 흐름:
    1. 상품 존재 확인 (`ProductService.getByIds`)
    2. 재고 차감 (`StockService.deductStock`) — 하나라도 실패 시 전체 롤백
    3. 스냅샷 생성 (상품명 + 브랜드명 + 가격 캡처)
    4. 주문 저장 (`OrderService.createOrder`)
  - `getOrders(Long userId, LocalDate, LocalDate)` → `OrderService` 위임
  - `getOrder(Long userId, Long orderId)` → `OrderService` 위임

**Infrastructure**
- [ ] `infrastructure/order/OrderJpaRepository`
- [ ] `infrastructure/order/OrderRepositoryImpl`
- [ ] `infrastructure/order/OrderItemSnapshotJpaRepository`
- [ ] `infrastructure/order/OrderItemSnapshotRepositoryImpl`

**단위 테스트**
- [ ] `domain/order/OrderItemTest` — cancel() 상태 전환, quantity 경계값
- [ ] `domain/order/OrderServiceTest` — 날짜 역순 예외, 타인 주문 조회 금지, 없는 주문 예외
- [ ] `application/order/OrderFacadeTest` — 정상 주문 흐름, 재고 부족 시 전체 실패, 없는 상품 포함 시 실패

---

### Block 7. ProductFacade 재설계 (상세 조회 조합)

> `ProductFacade.getProductDetail()` = `Product + Brand + likeCount` 조합은 Application Layer 역할.

- [ ] `application/product/ProductFacade` 재작성
  - `getProductDetail(Long productId)` → `Product` + `Brand` 조합 후 `ProductInfo` 반환
  - `getProducts(Long brandId, sort, pageable)` → 목록 조회
  - `createProduct(Long brandId, name, price, initialStock)` → `ProductService` + `StockService` 조합
  - `updateProduct(Long productId, name, price)` → `ProductService` 위임
  - `deleteProduct(Long productId)` → `ProductService` 위임

- [ ] `application/product/ProductInfo` 업데이트
  - `brandName` 포함한 상세 정보 DTO

---

## 완료 기준

- [ ] `./gradlew :apps:commerce-api:test` 전체 통과
- [ ] 각 도메인 단위 테스트에서 **정상/예외/경계 케이스** 모두 커버
- [ ] `LikeFacadeTest`, `OrderFacadeTest`에서 도메인 서비스 간 협력 검증
- [ ] Repository 구현체가 아닌 **인터페이스에만** 의존하는 단위 테스트 구조

---

## 구현 순서 권장

```
Block 1 (SoftDeletableEntity)
  → Block 2 (Brand)
  → Block 3 (Product 재설계)
  → Block 4 (Stock)
  → Block 5 (Like)
  → Block 6 (Order)
  → Block 7 (ProductFacade 조합)
```

각 Block은 **Red → Green → Refactor** 순서로 진행합니다.
