# 클래스 다이어그램

## 1. 도메인 모델

도메인 객체의 책임과 의존 방향, 비즈니스 로직이 Service에 몰리지 않고 적절히 분산되어 있는지 확인한다.

```mermaid
classDiagram
    class User {
        Long id
        String loginId
        String password
        String name
        LocalDate birthDate
        String email
        getMaskedName() String
        updatePassword(encoded) void
    }

    class Brand {
        Long id
        String name
        String description
        ZonedDateTime deletedAt
        delete()
        isDeleted() boolean
    }

    class Product {
        Long id
        Long brandId
        String name
        String description
        Long price
        ProductStatus status
        ZonedDateTime deletedAt
        delete()
        isDeleted() boolean
    }

    class ProductStatus {
        <<enumeration>>
        ON_SALE
        SOLD_OUT
        HIDDEN
        DELETED
    }

    class Stock {
        Long id
        Long productId
        int quantity
        long version
        deduct(quantity)
        restore(quantity)
        changeQuantity(newQuantity)
        hasEnough(quantity) boolean
        isAvailable() boolean
        getDisplayQuantity() Integer
    }

    class Like {
        Long id
        Long userId
        Long productId
        ZonedDateTime createdAt
    }

    class Order {
        Long id
        Long userId
        OrderStatus status
        Long totalPrice
        ZonedDateTime orderedAt
        List~OrderItem~ items
        complete()
        cancel()
        calculateTotalPrice() Long
    }

    class OrderItem {
        Long id
        Long productId
        String productName
        Long productPrice
        int quantity
        calculateSubtotal() Long
    }

    class OrderStatus {
        <<enumeration>>
        PENDING
        COMPLETED
        CANCELLED
    }

    class Payment {
        Long id
        Long orderId
        String pgTransactionId
        PaymentStatus status
        Long amount
        String failureReason
        ZonedDateTime requestedAt
        ZonedDateTime respondedAt
        markSuccess(txId)
        markFailed(reason)
    }

    class PaymentStatus {
        <<enumeration>>
        REQUESTED
        SUCCESS
        FAILED
    }

    Product --> ProductStatus
    Order --> OrderStatus
    Payment --> PaymentStatus
    Order "1" *-- "N" OrderItem
    Order "1" -- "1" Payment
    Product "N" --> "1" Brand
    Product "1" -- "1" Stock
    User "1" -- "N" Like : likes
    Product "1" -- "N" Like : liked by
    User "1" -- "N" Order : places
```

**읽는 포인트**
- `User`는 비밀번호를 평문으로 저장하지 않는다. `updatePassword(encoded)`는 인코딩된 값만 받아 저장하는 도메인 메서드이며, 인코딩 자체는 `PasswordEncoder` 책임이다. `getMaskedName()`은 마지막 글자를 `*`로 치환한 값을 반환한다.
- `Stock`은 `Product`와 1:1로 분리된 별도 엔티티다. 재고는 주문마다 변경되는 반면 상품 정보(이름, 가격)는 거의 변하지 않아 쓰기 빈도가 다르다. 분리함으로써 동시 주문 시 `stocks` row에만 락이 집중되고 `products` row는 캐싱 가능한 상태를 유지한다.
- `Stock.version`은 낙관적 락(optimistic lock)용 컬럼이다. 동시 주문 충돌 감지에 사용된다 (요구사항 P-12).
- `Stock.deduct()`이 재고 부족 예외를 던지고, `Stock.restore()`은 결제 실패 시 보상용으로 호출된다. Service가 수량 비교를 직접 하지 않는다.
- `Like`는 `userId`, `productId`를 FK 없이 Long 값으로만 보유한다. User/Product 삭제 시 Like가 직접 영향받지 않도록 느슨하게 참조.
- `Brand.delete()`는 `deletedAt`을 채우는 메서드로 BaseEntity에서 상속된다. `Product.delete()`는 이를 오버라이드하여 `deletedAt` 채움과 함께 `status = DELETED`로도 변경한다.
- `OrderItem`은 `Order` 없이 존재할 수 없는 구조(Aggregate Root 패턴). `productName`, `productPrice`는 주문 시점 스냅샷이라 이후 상품 변경에 영향받지 않는다.
- `Payment`는 주문과 1:1로 분리된 별도 애그리거트다. 결제 시도/결과를 명시적으로 기록하여 추후 PG 대조나 재조회에 활용할 수 있다.
- 가격 관련 필드(`totalPrice`, `productPrice`, `amount`)는 `Long`으로 선언한다. `int` 범위(약 21억)를 초과하는 상품이 존재할 수 있기 때문이다.

---

## 2. 외부 시스템 추상화

외부 결제 시스템(PG)과 비밀번호 인코딩은 인터페이스로 추상화하여 도메인이 구현 세부사항에 의존하지 않도록 한다.

```mermaid
classDiagram
    class PaymentGateway {
        <<interface>>
        +request(orderId, amount) PaymentResult
    }

    class PaymentResult {
        PaymentResultStatus status
        String pgTransactionId
        String failureReason
    }

    class PaymentResultStatus {
        <<enumeration>>
        SUCCESS
        FAILED
        TIMEOUT
    }

    class FakePaymentGateway {
        +request(orderId, amount) PaymentResult
    }

    class PgPaymentGatewayClient {
        -RestClient restClient
        +request(orderId, amount) PaymentResult
    }

    PaymentResult --> PaymentResultStatus
    PaymentGateway <|.. FakePaymentGateway : implements
    PaymentGateway <|.. PgPaymentGatewayClient : implements

    class PasswordEncoder {
        <<interface>>
        +encode(rawPassword) String
        +matches(raw, encoded) boolean
    }

    class BCryptPasswordEncoder {
        +encode(rawPassword) String
        +matches(raw, encoded) boolean
    }

    PasswordEncoder <|.. BCryptPasswordEncoder : implements
```

**읽는 포인트**
- `PaymentGateway`는 외부 PG와의 통신을 추상화한다. 도메인/Service 코드는 인터페이스에만 의존하므로 테스트에서는 `FakePaymentGateway`(고정 응답 반환)로, 운영에서는 `PgPaymentGatewayClient`(실제 HTTP 호출)로 교체된다.
- `PaymentResult`는 외부 응답을 도메인이 이해할 수 있는 결과 객체로 변환한 형태다. HTTP 응답 코드/바디 같은 외부 표현이 도메인까지 흘러들어오지 않도록 경계를 둔다.
- `PasswordEncoder`도 동일하게 인터페이스로 분리하여 알고리즘 교체(BCrypt → Argon2 등) 시 도메인 코드 변경 없이 구현체만 갈아낄 수 있다.

---

## 3. 레이어별 구조

각 Service가 어떤 Repository에 의존하는지, 의존 방향이 domain → infrastructure로 향하는지 확인한다.

```mermaid
classDiagram
    class UserService {
        +register(command) User
        +getUser(loginId) User
        +getUserById(userId) User
        +changePassword(userId, current, new)
        +authenticate(loginId, password) User
    }

    class BrandService {
        +createBrand(name, description) Brand
        +getBrand(brandId) Brand
        +getBrands(page, size) List~Brand~
        +updateBrand(brandId, name, description) Brand
        +deleteBrand(brandId)
    }

    class ProductService {
        +createProduct(brandId, ...) Product
        +getProduct(productId) Product
        +getProducts(brandId, sort, page, size) List~Product~
        +updateProduct(id, ...) Product
        +deleteProduct(id)
    }

    class StockService {
        +createStock(productId, quantity) Stock
        +getStock(productId) Stock
        +getStocksByProductIds(productIds) List~Stock~
        +deduct(productId, quantity)
        +restore(productId, quantity)
        +changeQuantity(productId, newQuantity)
    }

    class LikeService {
        +like(userId, productId)
        +unlike(userId, productId)
        +getLikedProducts(userId) List~Product~
    }

    class OrderService {
        +createOrder(userId, items) Order
        +getOrders(userId, startAt, endAt) List~Order~
        +getOrder(userId, orderId) Order
    }

    class PaymentService {
        +process(orderId, amount) PaymentResult
    }

    class AdminOrderService {
        +getAllOrders(page, size) List~Order~
        +getOrder(orderId) Order
    }

    class UserRepository {
        <<interface>>
        +findByLoginId(loginId) Optional~User~
        +findById(userId) Optional~User~
        +existsByLoginId(loginId) boolean
        +save(user) User
    }

    class BrandRepository {
        <<interface>>
        +findById(id) Optional~Brand~
        +findAll(page, size) List~Brand~
        +save(brand) Brand
    }

    class ProductRepository {
        <<interface>>
        +findById(id) Optional~Product~
        +findAll(brandId, sort, page, size) List~Product~
        +findAllByBrandId(brandId) List~Product~
        +save(product) Product
    }

    class StockRepository {
        <<interface>>
        +findByProductId(productId) Optional~Stock~
        +findAllByProductIdIn(productIds) List~Stock~
        +save(stock) Stock
    }

    class LikeRepository {
        <<interface>>
        +existsByUserIdAndProductId(userId, productId) boolean
        +findByUserId(userId) List~Like~
        +save(like) Like
        +deleteByUserIdAndProductId(userId, productId)
    }

    class OrderRepository {
        <<interface>>
        +save(order) Order
        +findById(id) Optional~Order~
        +findByUserIdAndOrderedAtBetween(userId, start, end) List~Order~
        +findAll(page, size) List~Order~
    }

    class PaymentRepository {
        <<interface>>
        +save(payment) Payment
        +findByOrderId(orderId) Optional~Payment~
    }

    UserService --> UserRepository
    UserService --> PasswordEncoder
    BrandService --> BrandRepository
    BrandService --> ProductRepository
    ProductService --> ProductRepository
    StockService --> StockRepository
    LikeService --> LikeRepository
    LikeService --> ProductRepository
    OrderService --> OrderRepository
    OrderService --> ProductRepository
    OrderService --> StockService
    OrderService --> PaymentService
    PaymentService --> PaymentRepository
    PaymentService --> PaymentGateway
    AdminOrderService --> OrderRepository
```

**읽는 포인트**
- `UserService.authenticate`는 `UserModel`을 반환하고, 이를 `UserFacade.authenticate`가 `AuthUserContext(loginId, userId)`로 변환해 `AuthUserArgumentResolver`에 돌려준다. 인증 한 번으로 `userId`까지 확보되므로 다른 Facade(Order/Like 등)에서는 `UserService.getUser()`를 다시 호출하지 않는다.
- `UserService.getUser(loginId)`는 회원 본인 조회용(인증 시), `getUserById(userId)`는 인증 이후 다른 흐름에서 PK 기준 조회용으로 사용한다.
- `UserService`가 `PasswordEncoder`에 의존하는 이유: 회원가입 시 비밀번호 인코딩, 비밀번호 변경 시 현재 비밀번호 검증 및 새 비밀번호 인코딩.
- `BrandService`가 `ProductRepository`에 의존하는 이유: 브랜드 삭제 시 연관 상품도 soft delete 처리해야 하기 때문이다.
- `LikeService`가 `ProductRepository`에 의존하는 이유: 좋아요 등록 전 상품 존재 여부 확인이 필요하기 때문이다. 멱등 보장은 `LikeService` 내부에서 `existsByUserIdAndProductId` 사전 체크 + UK 위반 예외 캐치로 이중 방어한다.
- `OrderService`가 `PaymentService`에 의존하는 이유: 주문 생성 트랜잭션 내에서 결제 요청을 동기 호출해야 하기 때문이다. 결제 실패 시 `Stock.restore()`을 통한 보상도 OrderService가 조율한다.
- `PaymentService`는 `PaymentGateway`(외부 시스템 추상화)에 의존한다. 도메인은 외부 HTTP 호출 세부사항을 모른다.
- `OrderService`와 `AdminOrderService`를 분리했다. 고객 주문 흐름(생성/조회 + 본인 검증)과 어드민 전체 조회는 접근 주체와 책임이 다르기 때문이다.
- `ProductService`가 `StockRepository`에 의존하지 않고, 그 책임을 `StockService`로 분리했다. 재고 차감/복구/일괄 조회 같은 동작이 `Stock` 도메인 내부에 응집된다.
- Repository는 모두 interface로 선언하여 domain이 infrastructure 구현체에 직접 의존하지 않는다.
