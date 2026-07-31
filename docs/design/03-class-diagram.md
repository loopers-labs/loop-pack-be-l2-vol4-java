# 03. 클래스 다이어그램 — 상품 목록 / 상품 상세 / 브랜드 조회 / 상품 좋아요

## 전체 클래스 다이어그램

```mermaid
classDiagram
    direction TB

    %% ===== 공통 =====
    class BaseEntity {
        <<abstract>>
        -Long id
        -ZonedDateTime createdAt
        -ZonedDateTime updatedAt
        -ZonedDateTime deletedAt
        #guard()
        +delete()
        +restore()
    }

    class ApiResponse~T~ {
        <<record>>
        +Metadata meta
        +T data
        +success()$ ApiResponse
        +success(T data)$ ApiResponse~T~
        +fail(String code, String msg)$ ApiResponse
    }

    class CoreException {
        -ErrorType errorType
        -String customMessage
    }

    class ErrorType {
        <<enum>>
        INTERNAL_ERROR
        BAD_REQUEST
        NOT_FOUND
        CONFLICT
    }

    %% ===== Brand 도메인 (신규) =====
    class BrandModel {
        -String name
        -String description
        -String logoUrl
        +BrandModel(name, description, logoUrl)
        +getName() String
        +getDescription() String
        +getLogoUrl() String
    }

    class BrandRepository {
        <<interface>>
        +save(BrandModel) BrandModel
        +find(Long) Optional~BrandModel~
    }

    class BrandService {
        -BrandRepository brandRepository
        +getBrand(Long id) BrandModel
    }

    class BrandRepositoryImpl {
        -BrandJpaRepository brandJpaRepository
        +save(BrandModel) BrandModel
        +find(Long) Optional~BrandModel~
    }

    class BrandJpaRepository {
        <<interface>>
        extends JpaRepository~BrandModel, Long~
    }

    class BrandFacade {
        -BrandService brandService
        +getBrand(Long id) BrandInfo
    }

    class BrandInfo {
        <<record>>
        +Long id
        +String name
        +String description
        +String logoUrl
        +from(BrandModel)$ BrandInfo
    }

    class BrandController {
        -BrandFacade brandFacade
        +getBrand(Long brandId) ApiResponse~BrandResponse~
    }

    class BrandDto {
        class BrandResponse {
            <<record>>
            +Long id
            +String name
            +String description
            +String logoUrl
            +from(BrandInfo)$ BrandResponse
        }
    }

    %% ===== Product 도메인 (변경) =====
    class ProductModel {
        -String name
        -String description
        -Long price
        -Integer stock
        -Integer likeCount
        -BrandModel brand
        +ProductModel(name, description, price, stock, brand)
        +getName() String
        +getDescription() String
        +getPrice() Long
        +getStock() Integer
        +getLikeCount() Integer
        +getBrand() BrandModel
        +update(name, description, price, stock)
    }

    class ProductRepository {
        <<interface>>
        +save(ProductModel) ProductModel
        +find(Long) Optional~ProductModel~
        +findAll() List~ProductModel~
        +findByCondition(Long brandId, String sort, Pageable) Page~ProductModel~
        +delete(Long)
    }

    class ProductService {
        -ProductRepository productRepository
        +getProduct(Long id) ProductModel
        +getProducts(Long brandId, String sort, Pageable) Page~ProductModel~
        +createProduct(...) ProductModel
        +updateProduct(...) ProductModel
        +deleteProduct(Long id)
    }

    class ProductRepositoryImpl {
        -ProductJpaRepository productJpaRepository
        +save(ProductModel) ProductModel
        +find(Long) Optional~ProductModel~
        +findAll() List~ProductModel~
        +findByCondition(Long brandId, String sort, Pageable) Page~ProductModel~
        +delete(Long)
    }

    class ProductJpaRepository {
        <<interface>>
        extends JpaRepository~ProductModel, Long~
    }

    class ProductFacade {
        -ProductService productService
        +getProduct(Long id) ProductInfo
        +getProducts(Long brandId, String sort, int page, int size) PagedProductInfo
        +createProduct(...) ProductInfo
        +updateProduct(...) ProductInfo
        +deleteProduct(Long id)
    }

    class ProductInfo {
        <<record>>
        +Long id
        +String name
        +String description
        +Long price
        +Integer stock
        +Integer likeCount
        +Long brandId
        +String brandName
        +from(ProductModel)$ ProductInfo
    }

    class ProductController {
        -ProductFacade productFacade
        +getProduct(Long productId) ApiResponse~ProductResponse~
        +getProducts(Long brandId, String sort, int page, int size) ApiResponse~PagedProductResponse~
        +createProduct(request) ApiResponse~ProductResponse~
        +updateProduct(Long id, request) ApiResponse~ProductResponse~
        +deleteProduct(Long id) ApiResponse~Void~
    }

    class ProductDto {
        class ProductResponse {
            <<record>>
            +Long id, String name, String description
            +Long price, Integer stock, Integer likeCount
            +Long brandId, String brandName
            +from(ProductInfo)$ ProductResponse
        }
        class PagedProductResponse {
            <<record>>
            +List~ProductResponse~ content
            +int page, int size
            +long totalElements, int totalPages
        }
    }

    %% ===== 관계 =====
    BaseEntity <|-- BrandModel
    BaseEntity <|-- ProductModel
    ProductModel --> BrandModel : brand (ManyToOne)

    BrandRepository <|.. BrandRepositoryImpl
    BrandRepositoryImpl --> BrandJpaRepository
    BrandService --> BrandRepository
    BrandFacade --> BrandService
    BrandController --> BrandFacade

    ProductRepository <|.. ProductRepositoryImpl
    ProductRepositoryImpl --> ProductJpaRepository
    ProductService --> ProductRepository
    ProductFacade --> ProductService
    ProductController --> ProductFacade

    CoreException --> ErrorType

    %% ===== User 도메인 (신규) =====
    class UserModel {
        -String username
        +UserModel(username)
        +getUsername() String
    }

    %% ===== ProductLike 도메인 (신규) =====
    class ProductLikeModel {
        -UserModel user
        -ProductModel product
        +ProductLikeModel(user, product)
        +getUser() UserModel
        +getProduct() ProductModel
    }

    class ProductLikeRepository {
        <<interface>>
        +findByUserIdAndProductId(Long, Long) Optional~ProductLikeModel~
        +findByUserId(Long, Pageable) Page~ProductLikeModel~
        +save(ProductLikeModel) ProductLikeModel
        +delete(ProductLikeModel)
    }

    class ProductLikeService {
        -ProductLikeRepository productLikeRepository
        +like(Long userId, Long productId) boolean
        +unlike(Long userId, Long productId) boolean
        +getMyLikes(Long userId, Pageable) Page~ProductLikeModel~
    }

    class ProductLikeRepositoryImpl {
        -ProductLikeJpaRepository productLikeJpaRepository
    }

    class ProductLikeJpaRepository {
        <<interface>>
        extends JpaRepository~ProductLikeModel, Long~
    }

    class ProductLikeFacade {
        -ProductLikeService productLikeService
        -ProductService productService
        -UserService userService
        +like(Long userId, Long productId)
        +unlike(Long userId, Long productId)
        +getMyLikes(Long userId, int page, int size) PagedProductInfo
    }

    class ProductLikeController {
        -ProductLikeFacade productLikeFacade
        +like(Long userId, Long productId) ApiResponse~Void~
        +unlike(Long userId, Long productId) ApiResponse~Void~
        +getMyLikes(Long userId, int page, int size) ApiResponse~PagedProductResponse~
    }

    class UserRepository {
        <<interface>>
        +find(Long) Optional~UserModel~
        +save(UserModel) UserModel
    }

    class UserService {
        -UserRepository userRepository
        +getUser(Long id) UserModel
    }

    %% ===== User 관계 =====
    BaseEntity <|-- UserModel
    BaseEntity <|-- ProductLikeModel
    ProductLikeModel --> UserModel : user (ManyToOne)
    ProductLikeModel --> ProductModel : product (ManyToOne)

    ProductLikeRepository <|.. ProductLikeRepositoryImpl
    ProductLikeRepositoryImpl --> ProductLikeJpaRepository
    ProductLikeService --> ProductLikeRepository
    ProductLikeFacade --> ProductLikeService
    ProductLikeFacade --> ProductService
    ProductLikeFacade --> UserService
    ProductLikeController --> ProductLikeFacade
    UserService --> UserRepository
```

## 신규 클래스 설명

### Brand 도메인

| 클래스 | 레이어 | 책임 |
|--------|--------|------|
| `BrandModel` | domain | 브랜드 엔티티. name, description, logoUrl 관리. BaseEntity 상속 |
| `BrandRepository` | domain | 브랜드 저장소 인터페이스. save, find 선언 |
| `BrandService` | domain | 브랜드 조회 비즈니스 로직. 존재 여부 검증 및 NOT_FOUND 처리 |
| `BrandRepositoryImpl` | infrastructure | BrandJpaRepository에 위임하는 구현체 |
| `BrandJpaRepository` | infrastructure | JpaRepository 확장. Spring Data JPA 쿼리 자동 생성 |
| `BrandFacade` | application | BrandModel → BrandInfo 변환 |
| `BrandInfo` | application | 브랜드 정보 전달 record (id, name, description, logoUrl) |
| `BrandController` | interfaces | GET /api/v1/brands/{brandId} 엔드포인트 |
| `BrandApiSpec` | interfaces | Swagger 문서화 인터페이스 |
| `BrandDto` | interfaces | BrandResponse record (BrandInfo → HTTP 응답 변환) |

### Product 도메인 변경 사항

| 클래스 | 변경 내용 |
|--------|-----------|
| `ProductModel` | `brand` (ManyToOne, 필수), `likeCount` (Integer, 기본 0) 필드 추가 |
| `ProductRepository` | `findByCondition(brandId, sort, pageable)` 메서드 추가 |
| `ProductService` | `getProducts(brandId, sort, pageable)` 메서드 추가 |
| `ProductRepositoryImpl` | 조건부 쿼리 구현 (brandId 필터, sort 정렬, 페이징) |
| `ProductFacade` | `getProducts(...)` 메서드 추가. PagedProductInfo 반환 |
| `ProductInfo` | `likeCount`, `brandId`, `brandName` 필드 추가 |
| `ProductController` | GET 목록 엔드포인트에 쿼리 파라미터 추가 |
| `ProductDto` | `ProductResponse`에 brand/likeCount 추가, `PagedProductResponse` 신규 |

### User 도메인

| 클래스 | 레이어 | 책임 |
|--------|--------|------|
| `UserModel` | domain | 사용자 엔티티. username만 관리. BaseEntity 상속 |
| `UserRepository` | domain | 사용자 저장소 인터페이스 |
| `UserService` | domain | 사용자 조회 및 존재 여부 검증 |
| `UserRepositoryImpl` | infrastructure | UserJpaRepository에 위임하는 구현체 |
| `UserJpaRepository` | infrastructure | JpaRepository 확장 |

### ProductLike 도메인

| 클래스 | 레이어 | 책임 |
|--------|--------|------|
| `ProductLikeModel` | domain | 좋아요 엔티티. User-Product 매핑. BaseEntity 상속 (id, createdAt 활용) |
| `ProductLikeRepository` | domain | 좋아요 저장소 인터페이스. 조회/저장/삭제 |
| `ProductLikeService` | domain | 좋아요 등록/취소 멱등 로직. 존재 여부 확인 후 처리 |
| `ProductLikeRepositoryImpl` | infrastructure | ProductLikeJpaRepository에 위임 |
| `ProductLikeJpaRepository` | infrastructure | JpaRepository 확장 |
| `ProductLikeFacade` | application | 도메인 서비스 조합 (UserService + ProductService + ProductLikeService). likeCount 증감 조율 |
| `ProductLikeController` | interfaces | 좋아요 등록/취소/목록 엔드포인트. `X-User-Id` 헤더 수신 |
| `ProductLikeApiSpec` | interfaces | Swagger 문서화 인터페이스 |
| `ProductLikeDto` | interfaces | 응답 DTO (목록 조회 시 PagedProductResponse 재사용) |

## 설계 원칙

- **레이어 의존 방향**: interfaces → application → domain ← infrastructure
- **domain 레이어 순수성**: domain은 외부 프레임워크에 의존하지 않음 (Repository는 인터페이스만)
- **DTO 변환 위치**: Model → Info (Facade), Info → Response (Controller/Dto)
- **에러 처리**: domain에서 CoreException 발생 → ApiControllerAdvice에서 ApiResponse.fail()로 변환

---

# 03-2. 클래스 다이어그램 — 주문 생성 및 결제 흐름

## 전체 클래스 다이어그램

```mermaid
classDiagram
    direction TB

    %% ===== Order 도메인 (신규) =====
    class OrderStatus {
        <<enum>>
        PENDING
        PAID
        SHIPPED
        DELIVERED
    }

    class OrderModel {
        -Long memberId
        -OrderStatus status
        -Long totalPrice
        +OrderModel(memberId, totalPrice)
        +getMemberId() Long
        +getStatus() OrderStatus
        +getTotalPrice() Long
        +pay()
        +isOwnedBy(Long memberId) boolean
    }

    class OrderItemModel {
        -Long orderId
        -Long productId
        -String productName
        -Long unitPrice
        -Integer quantity
        +OrderItemModel(orderId, productId, productName, unitPrice, quantity)
        +getOrderId() Long
        +getProductId() Long
        +getProductName() String
        +getUnitPrice() Long
        +getQuantity() Integer
    }

    class OrderRepository {
        <<interface>>
        +save(OrderModel) OrderModel
        +findById(Long) Optional~OrderModel~
        +findByMemberIdAndDateRange(Long memberId, LocalDate start, LocalDate end) List~OrderModel~
        +findAll(Pageable) Page~OrderModel~
        +saveItem(OrderItemModel) OrderItemModel
        +findItemsByOrderId(Long orderId) List~OrderItemModel~
    }

    class OrderService {
        -OrderRepository orderRepository
        +createOrder(Long memberId, List~OrderItemModel~ items, Long totalPrice) OrderModel
        +getOrder(Long orderId) OrderModel
        +getOrderByMember(Long orderId, Long memberId) OrderModel
        +getOrders(Long memberId, LocalDate startAt, LocalDate endAt) List~OrderModel~
        +getAllOrders(Pageable) Page~OrderModel~
        +updateStatus(Long orderId, OrderStatus status)
    }

    class OrderRepositoryImpl {
        -OrderJpaRepository orderJpaRepository
        -OrderItemJpaRepository orderItemJpaRepository
    }

    class OrderJpaRepository {
        <<interface>>
        extends JpaRepository~OrderModel, Long~
    }

    class OrderItemJpaRepository {
        <<interface>>
        extends JpaRepository~OrderItemModel, Long~
    }

    class OrderFacade {
        -OrderService orderService
        -ProductService productService
        +createOrder(Long memberId, List~OrderItemRequest~ items) OrderInfo
        +getOrder(Long orderId, Long memberId) OrderInfo
        +getOrders(Long memberId, LocalDate startAt, LocalDate endAt) List~OrderInfo~
        +getAllOrders(int page, int size) PagedOrderInfo
    }

    class OrderInfo {
        <<record>>
        +Long orderId
        +String status
        +Long totalPrice
        +List~OrderItemInfo~ items
        +LocalDateTime createdAt
        +from(OrderModel, List~OrderItemModel~)$ OrderInfo
    }

    class OrderController {
        -OrderFacade orderFacade
        +createOrder(request, memberId) ApiResponse~OrderResponse~
        +getOrder(Long orderId, Long memberId) ApiResponse~OrderResponse~
        +getOrders(Long memberId, LocalDate startAt, LocalDate endAt) ApiResponse~List~OrderResponse~~
    }

    class OrderAdminController {
        -OrderFacade orderFacade
        +getAllOrders(int page, int size) ApiResponse~PagedOrderResponse~
        +getOrder(Long orderId) ApiResponse~OrderResponse~
    }

    class OrderDto {
        class OrderResponse {
            <<record>>
            +Long orderId
            +String status
            +Long totalPrice
            +List~OrderItemResponse~ items
            +LocalDateTime createdAt
            +from(OrderInfo)$ OrderResponse
        }
        class OrderItemResponse {
            <<record>>
            +Long productId
            +String productName
            +Long unitPrice
            +Integer quantity
        }
        class CreateOrderRequest {
            +List~OrderItemRequest~ items
        }
    }

    %% ===== Payment 도메인 (신규) =====
    class PaymentStatus {
        <<enum>>
        PAID
    }

    class PaymentModel {
        -Long orderId
        -PaymentStatus status
        -Long amount
        -LocalDateTime paidAt
        +PaymentModel(orderId, amount)
        +getOrderId() Long
        +getStatus() PaymentStatus
        +getAmount() Long
        +getPaidAt() LocalDateTime
    }

    class PaymentRepository {
        <<interface>>
        +save(PaymentModel) PaymentModel
        +findById(Long) Optional~PaymentModel~
        +findByOrderId(Long) Optional~PaymentModel~
    }

    class PaymentService {
        -PaymentRepository paymentRepository
        +createPayment(Long orderId, Long amount) PaymentModel
    }

    class PaymentRepositoryImpl {
        -PaymentJpaRepository paymentJpaRepository
    }

    class PaymentJpaRepository {
        <<interface>>
        extends JpaRepository~PaymentModel, Long~
    }

    class PaymentFacade {
        -PaymentService paymentService
        -OrderService orderService
        +processPayment(Long memberId, Long orderId) PaymentInfo
    }

    class PaymentInfo {
        <<record>>
        +Long paymentId
        +Long orderId
        +String status
        +Long amount
        +LocalDateTime paidAt
        +from(PaymentModel)$ PaymentInfo
    }

    class PaymentController {
        -PaymentFacade paymentFacade
        +processPayment(request, memberId) ApiResponse~PaymentResponse~
    }

    %% ===== 관계 =====
    BaseEntity <|-- OrderModel
    BaseEntity <|-- OrderItemModel
    BaseEntity <|-- PaymentModel

    OrderModel --> OrderStatus
    PaymentModel --> PaymentStatus

    OrderRepository <|.. OrderRepositoryImpl
    OrderRepositoryImpl --> OrderJpaRepository
    OrderRepositoryImpl --> OrderItemJpaRepository
    OrderService --> OrderRepository
    OrderFacade --> OrderService
    OrderFacade --> ProductService
    OrderController --> OrderFacade
    OrderAdminController --> OrderFacade

    PaymentRepository <|.. PaymentRepositoryImpl
    PaymentRepositoryImpl --> PaymentJpaRepository
    PaymentService --> PaymentRepository
    PaymentFacade --> PaymentService
    PaymentFacade --> OrderService
    PaymentController --> PaymentFacade
```

## 신규 클래스 설명

### Order 도메인

| 클래스 | 레이어 | 책임 |
|--------|--------|------|
| `OrderModel` | domain | 주문 엔티티. memberId, status, totalPrice 관리. `pay()` 메서드로 상태 전이 |
| `OrderItemModel` | domain | 주문 아이템 엔티티. productName·unitPrice 스냅샷 저장 |
| `OrderStatus` | domain | 주문 상태 enum (PENDING / PAID / SHIPPED / DELIVERED) |
| `OrderRepository` | domain | 주문 저장소 인터페이스 |
| `OrderService` | domain | 주문 생성·조회·상태 변경 비즈니스 로직 |
| `OrderRepositoryImpl` | infrastructure | OrderJpaRepository + OrderItemJpaRepository에 위임 |
| `OrderFacade` | application | 재고 확인/차감(ProductService) + 주문 생성(OrderService) 조율 |
| `OrderInfo` | application | 주문 정보 전달 record |
| `OrderController` | interfaces | 유저용 주문 API (`/api/v1/orders`) |
| `OrderAdminController` | interfaces | Admin용 주문 API (`/api-admin/v1/orders`). `X-Loopers-Ldap` 헤더 검증 |

### Payment 도메인

| 클래스 | 레이어 | 책임 |
|--------|--------|------|
| `PaymentModel` | domain | 결제 엔티티. orderId, amount, paidAt 관리 |
| `PaymentStatus` | domain | 결제 상태 enum (PAID — stub이므로 단일 값) |
| `PaymentRepository` | domain | 결제 저장소 인터페이스 |
| `PaymentService` | domain | PaymentModel 생성 및 저장 |
| `PaymentRepositoryImpl` | infrastructure | PaymentJpaRepository에 위임 |
| `PaymentFacade` | application | OrderService 상태 변경 + PaymentService 생성 조율 |
| `PaymentInfo` | application | 결제 정보 전달 record |
| `PaymentController` | interfaces | `POST /api/v1/payments` 엔드포인트 |

### Product 도메인 변경 사항

| 클래스 | 변경 내용 |
|--------|-----------|
| `ProductModel` | `deductStock(int quantity)` 메서드 추가 — 재고 부족 시 `CoreException(BAD_REQUEST)` |
| `ProductService` | `deductStock(Long productId, int quantity)` 메서드 추가 |
