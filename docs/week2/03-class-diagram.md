```mermaid
classDiagram
    class BaseTimeEntity {
        <<abstract>>
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }

    class BaseSoftDeleteEntity {
        <<abstract>>
        +boolean isDeleted
        +delete()
    }

    BaseSoftDeleteEntity --|> BaseTimeEntity

    class User {
        +Long id
        +String loginId
        +String password
        +Role role
    }
    User --|> BaseTimeEntity
    
    class Brand {
        +Long id
        +String name
    }
    Brand --|> BaseSoftDeleteEntity
    
    class Product {
        +Long id
        +Long brandId
        +String name
        +BigDecimal price
        +int likeCount
        +Stock stock
        +increaseLikeCount()
        +decreaseLikeCount()
    }
    Product --|> BaseSoftDeleteEntity
    
    class Stock {
        +Long productId
        +Product product
        +int quantity
        +decrease(int amount)
    }
    
    class ProductLike {
        +Long id
        +Long userId
        +Long productId
    }
    ProductLike --|> BaseTimeEntity

    class CouponTemplate {
        +Long id
        +String name
        +CouponType type
        +BigDecimal value
        +BigDecimal minOrderAmount
        +BigDecimal maxDiscountAmount
        +LocalDateTime expiredAt
    }
    CouponTemplate --|> BaseSoftDeleteEntity

    class CouponIssue {
        +Long id
        +Long userId
        +Long couponTemplateId
        +CouponStatus status
        +Long version
        +use(template: CouponTemplate, orderAmount: BigDecimal): BigDecimal
    }
    CouponIssue --|> BaseTimeEntity
    
    class Order {
        +Long id
        +Long userId
        +Long couponIssueId
        +BigDecimal totalOriginalAmount
        +BigDecimal totalDiscountAmount
        +BigDecimal totalPaymentAmount
        +OrderStatus status "PENDING, COMPLETED, CANCELED"
        +List~OrderItem~ items
        +complete()
        +cancel()
    }
    Order --|> BaseTimeEntity
    
    class OrderItem {
        +Long id
        +Order order
        +Long productId
        +ProductSnapshot snapshot
        +int quantity
    }
    
    class ProductSnapshot {
        <<VO>>
        +String name
        +BigDecimal price
        +String brandName
    }

    class Payment {
        +Long id
        +Long orderId
        +PaymentMethod method "CARD, TRANSFER"
        +PaymentStatus status "READY, APPROVED, FAILED"
        +BigDecimal amount
        +String transactionId
        +LocalDateTime approvedAt
    }
    Payment --|> BaseTimeEntity

    class PaymentMethod {
        <<enumeration>>
        CARD
        TRANSFER
    }

    class PaymentStatus {
        <<enumeration>>
        READY
        APPROVED
        FAILED
    }

    class PaymentGateway {
        <<interface>>
        +requestPayment(amount, method): PaymentResponse
        +cancelPayment(transactionId): PaymentResponse
    }

    class MockPaymentGateway {
        +requestPayment(amount, method): PaymentResponse
        +cancelPayment(transactionId): PaymentResponse
    }

    class PaymentResponse {
        +String transactionId
        +boolean isSuccess
        +LocalDateTime approvedAt
    }

    %% 도메인 간 관계
    Brand "1" -- "*" Product : contains
    Product "1" -- "1" Stock : has
    User "1" -- "*" Order : places
    Order "1" -- "*" OrderItem : contains
    User "1" -- "*" ProductLike : likes
    Product "1" -- "*" ProductLike : liked by
    User "1" -- "*" CouponIssue : owns
    CouponTemplate "1" -- "*" CouponIssue : issues
    CouponIssue "1" -- "0..1" Order : applied to
    Order "1" ..> "0..1" Payment : reference (id)
    PaymentGateway <|.. MockPaymentGateway : implements

    %% 파사드(Facade) 계층 (복합 트랜잭션 제어)
    class OrderFacade {
        +createOrder(userId, request)
    }
    class PaymentFacade {
        +processPayment(orderId, method)
    }
    class BrandAdminFacade {
        +deleteBrand(brandId)
    }
    class LikeFacade {
        +addLike(userId, productId)
        +removeLike(userId, productId)
    }
    class CouponFacade {
        +issueCoupon(userId, couponTemplateId)
    }

    %% 서비스(Service) 계층 (단일 도메인 로직 및 조회)
    class OrderService {
        +createOrder(userId, items, discountAmount)
        +getOrder(orderId)
        +completeOrder(orderId)
        +cancelOrder(orderId)
    }
    class PaymentService {
        +savePayment(orderId, amount, method, transactionId)
    }
    class ProductService {
        +getProducts(brandId, sort, pageable)
        +getProductDetail(productId)
        +getProductsByIds(ids)
        +deleteProductsByBrand(brandId)
        +increaseLikeCount(productId)
        +decreaseLikeCount(productId)
    }
    class BrandService {
        +getBrand(brandId)
        +deleteBrand(brandId)
    }
    class StockService {
        +decreaseStocks(stockRequests)
        +restoreStocks(stockRequests)
    }
    class LikeService {
        +existsLikeRecord(userId, productId) boolean
        +addLikeRecord(userId, productId)
        +removeLikeRecord(userId, productId)
    }
    class CouponService {
        +issueCoupon(userId, templateId)
        +getUserCoupons(userId)
        +verifyAndUseCoupon(couponIssueId, orderAmount)
        +completeCouponUse(couponIssueId)
    }

    %% 의존 방향
    OrderFacade ..> OrderService
    OrderFacade ..> ProductService
    OrderFacade ..> StockService
    OrderFacade ..> CouponService
    
    PaymentFacade ..> OrderService
    PaymentFacade ..> PaymentService
    PaymentFacade ..> CouponService
    PaymentFacade ..> PaymentGateway
    
    BrandAdminFacade ..> BrandService
    BrandAdminFacade ..> ProductService
    
    LikeFacade ..> LikeService
    LikeFacade ..> ProductService

    CouponFacade ..> CouponService
```