# Week 3 구현 체크리스트

> 설계 주도권은 개발자에게 있습니다. 각 단계는 나와 함께 하나씩 설계를 논의하며 진행합니다.
> TDD 순서: **Red (테스트 먼저) → Green (통과 코드) → Refactor**

---

## 현재 상태 파악

| 도메인 | 현재 상태 | 비고 |
|---|---|---|
| Member | ✅ 완성 | Member, Password, MemberService, MemberFacade |
| Brand | ✅ 등록 완성 | Entity, DomainService, ApplicationService, Facade, Controller |
| Product | ⚠️ 등록만 완성 | 재설계 완료. 조회/수정/삭제 미구현 |
| Stock | ⚠️ 생성만 완성 | 독립 분리 완료. deductStock 미구현 |
| Like | ❌ 미구현 | |
| Order | ❌ 미구현 | |

---

## 구현 단위

### Block 1. 공통 기반 (SoftDeletableEntity)

> `Brand`, `Product`가 상속하는 추상 클래스. 먼저 만들어야 이후 작업이 가능하다.

- [x] `domain/SoftDeletableEntity` 추상 클래스 작성
  - **참고:** `BaseEntity`에 이미 `deletedAt`, `delete()`, `restore()` 포함 → 별도 클래스 불필요

---

### Block 2. Brand 도메인

> UC-01 (브랜드 조회), UC-10 (어드민 브랜드 관리) 기반.

**도메인 모델**
- [x] `domain/brand/model/Brand` 엔티티
  - `BaseEntity` 상속 (`deletedAt` 포함)
  - `name` 필드 (null/blank/20자 검증)
  - 정적 팩토리 메서드 `create(name)`
  - `update(name)` 메서드

**Repository**
- [x] `domain/brand/repository/BrandRepository` 인터페이스
  - `findById(Long)`, `save(Brand)`, `existsByName(String)`

**도메인 서비스**
- [x] `domain/brand/service/BrandDomainService`
  - `validateDuplicateName(name)` — 중복 시 `CONFLICT`
  - `getBrand(Long)` — 없으면 `NOT_FOUND`
- [ ] updateBrand / deleteBrand 흐름 (미구현)

**Application Layer**
- [x] `application/brand/BrandApplicationService`
  - `register(name)` — 중복 검증 + 저장 (`@Transactional`)
- [x] `application/brand/BrandInfo` — 도메인→인터페이스 전달 계약 객체
- [x] `application/brand/BrandFacade`
  - `register(name)` → `BrandInfo` 반환

**Interface Layer**
- [x] `interfaces/api/brand/BrandV1Controller` — `POST /api-admin/v1/brands`
- [x] `interfaces/api/brand/BrandV1Dto` — `RegisterRequest`, `BrandResponse`
- [x] `interfaces/api/AdminInterceptor` — `X-Loopers-Ldap` 헤더 검증
- [x] `support/config/WebMvcConfig` — `/api-admin/**` 인터셉터 등록

**Infrastructure**
- [x] `infrastructure/brand/persistence/BrandJpaRepository`
- [x] `infrastructure/brand/persistence/BrandRepositoryImpl`

**단위 테스트**
- [x] `domain/brand/BrandTest` — 생성/수정 검증, 소프트딜리트 검증
- [x] `domain/brand/BrandDomainServiceTest` — 중복 이름 예외, getBrand NOT_FOUND
- [x] `application/brand/BrandApplicationServiceTest` — 정상 등록, 중복 이름 예외
- ~~`interfaces/api/BrandV1ApiE2ETest`~~ — 이번 주차 제외 (단위 테스트만 진행)

---

### Block 3. Product 도메인 재설계

> 기존 `ProductModel`을 `Product`로 재작성. `stock` 인라인 제거, `brandId` / `likeCount` / `description` 추가.

**도메인 모델**
- [x] `domain/product/model/Product` 엔티티
  - `BaseEntity` 상속 (`deletedAt` 포함)
  - `brandId`, `name`, `description`, `price`, `likeCount` 필드
  - `price > 0`, `likeCount >= 0` 제약, `name` 50자, `description` 200자
  - 정적 팩토리 메서드 `create(brandId, name, description, price)`
  - `update(name, description, price)` — 브랜드 변경 불가
  - `incrementLikeCount()`, `decrementLikeCount()` — `likeCount >= 0` 보장

**Repository**
- [x] `domain/product/repository/ProductRepository` 인터페이스
  - `findById`, `save`
- [ ] `findByIds`, `softDeleteAllByBrandId` — 추후 필요 시 추가

**도메인 서비스**
- [x] `domain/product/service/ProductDomainService`
  - `createProduct(brandId, name, description, price)` → `Product`
- [ ] `getProduct(Long)` — NOT_FOUND 처리 (미구현)
- [ ] `updateProduct`, `deleteProduct`, `incrementLikeCount`, `decrementLikeCount` (미구현)

**Application Layer**
- [x] `application/product/ProductApplicationService`
  - `createProduct(brandId, name, description, price, initialQuantity)` — 브랜드 확인 + 상품 + 재고 트랜잭션
- [x] `application/product/ProductInfo` — 도메인→인터페이스 전달 계약 객체
- [x] `application/product/ProductFacade`
  - `createProduct(...)` → `ProductInfo` 반환

**Interface Layer**
- [x] `interfaces/api/product/ProductV1Controller` — `POST /api-admin/v1/products`
- [x] `interfaces/api/product/ProductV1Dto` — `RegisterRequest`, `ProductResponse`

**Infrastructure**
- [x] `infrastructure/product/ProductJpaRepository`
- [x] `infrastructure/product/ProductRepositoryImpl`

**단위 테스트**
- [x] `domain/product/ProductTest` — 생성 검증, likeCount 증감 경계값
- [x] `application/product/ProductApplicationServiceTest` — 정상 등록, 브랜드 없음 예외
- [ ] `domain/product/ProductDomainServiceTest` — 없는 ID 조회 예외 (getProduct 구현 후)

---

### Block 4. Stock 도메인

> `Product`에서 분리된 독립 도메인. 원자적 차감으로 동시성 처리.

**도메인 모델**
- [x] `domain/stock/model/Stock` 엔티티
  - `BaseEntity` 상속
  - `productId`, `quantity` 필드
  - `quantity >= 0` 제약
  - `isAvailable(int quantity)` — 조회 전용
- [ ] `deduct(int quantity)` — 재고 부족 시 예외 (미구현)

**Repository**
- [x] `domain/stock/repository/StockRepository` 인터페이스
  - `findByProductId(Long)`, `save(Stock)`
- [ ] `deductStock(Long productId, int quantity) int` — 원자적 차감 (미구현)

**도메인 서비스**
- [x] `domain/stock/service/StockDomainService`
  - `createStock(Long productId, int initialQuantity)`
- [ ] `deductStock(Long productId, int quantity)` — affected rows = 0이면 예외 (미구현)

**Infrastructure**
- [x] `infrastructure/stock/StockJpaRepository`
- [x] `infrastructure/stock/StockRepositoryImpl`
- [ ] `deductStock` 원자적 쿼리 (미구현)

**단위 테스트**
- [x] `domain/stock/StockTest` — `isAvailable` 경계값, 생성 검증
- [x] `domain/stock/StockDomainServiceTest` — createStock 정상 흐름
- [ ] `domain/stock/StockDomainServiceTest` — deductStock 재고 부족 예외 (미구현)

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

### Block 7. ProductFacade 확장 (상세 조회 조합)

> 상품 등록은 완성. 조회/수정/삭제 + 브랜드명 조합은 추후 추가.

- [x] `createProduct(...)` — 완성
- [ ] `getProductDetail(Long productId)` → `Product` + `Brand` 조합 후 `ProductInfo` 반환
- [ ] `getProducts(Long brandId, sort, pageable)` → 목록 조회
- [ ] `updateProduct`, `deleteProduct`

- [ ] `application/product/ProductInfo` 업데이트
  - `brandName` 포함한 상세 정보 DTO (현재는 `brandId`만 포함)

---

## 완료 기준

> **이번 주차는 단위 테스트만 진행 (E2E 테스트 제외)**

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
