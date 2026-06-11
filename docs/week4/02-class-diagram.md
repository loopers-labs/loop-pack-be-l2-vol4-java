# 02. 클래스 다이어그램 (Week 4 — Coupon 도메인)

> 쿠폰 도메인 신규 클래스와 주문 도메인 변경 사항을 중심으로 작성합니다.

---

## 1. 도메인 레이어

```mermaid
classDiagram
    class BaseEntity {
        +Long id
        +ZonedDateTime createdAt
        +ZonedDateTime updatedAt
        +ZonedDateTime deletedAt
        +delete()
        +restore()
    }

    class CouponModel {
        +String name
        +CouponType type
        +int value
        +Integer minOrderAmount
        +ZonedDateTime expiredAt
        +update(name, type, value, minOrderAmount, expiredAt)
        +isExpired() boolean
        +isDeleted() boolean
        +calculateDiscount(int orderAmount) int
    }

    class CouponType {
        <<enumeration>>
        FIXED
        RATE
    }

    class UserCouponModel {
        +Long userId
        +CouponModel coupon
        +UserCouponStatus status
        +Long version
        +use()
        +computedStatus() UserCouponStatus
    }

    class UserCouponStatus {
        <<enumeration>>
        AVAILABLE
        USED
        EXPIRED
    }

    class OrderModel {
        +Long userId
        +OrderStatus status
        +int originalAmount
        +int discountAmount
        +int totalAmount
        +addItem(OrderItemModel)
        +applyPricing(int originalAmount, int discountAmount)
    }

    class OrderStatus {
        <<enumeration>>
        PENDING
        CONFIRMED
        CANCELLED
    }

    BaseEntity <|-- CouponModel
    BaseEntity <|-- UserCouponModel
    BaseEntity <|-- OrderModel
    CouponModel --> CouponType
    UserCouponModel --> CouponModel
    UserCouponModel --> UserCouponStatus
    OrderModel --> OrderStatus
```

---

## 2. 애플리케이션 레이어

```mermaid
classDiagram
    class CouponService {
        -CouponRepository couponRepository
        -UserCouponRepository userCouponRepository
        +getAll(Pageable) Page~CouponInfo~
        +getById(Long) CouponInfo
        +create(CouponCreateCommand) CouponInfo
        +update(Long, CouponUpdateCommand) CouponInfo
        +delete(Long)
        +getIssues(Long, Pageable) Page~UserCouponInfo~
    }

    class UserCouponService {
        -CouponRepository couponRepository
        -UserCouponRepository userCouponRepository
        +issue(Long userId, Long couponId) UserCouponInfo
        +getMyCoupons(Long userId, Pageable) Page~UserCouponInfo~
    }

    class OrderFacade {
        -OrderRepository orderRepository
        -ProductRepository productRepository
        -StockRepository stockRepository
        -OrderDomainService orderDomainService
        -UserCouponRepository userCouponRepository
        +createOrder(OrderCreateCommand) OrderInfo
        +getMyOrders(Long, Pageable) Page~OrderInfo~
        +getOrder(Long, Long) OrderInfo
        +getAllOrders(Pageable) Page~OrderInfo~
        +getOrderByAdmin(Long) OrderInfo
    }

    class CouponCreateCommand {
        +String name
        +CouponType type
        +int value
        +Integer minOrderAmount
        +ZonedDateTime expiredAt
        +toDomain() CouponModel
    }

    class OrderCreateCommand {
        +Long userId
        +List~OrderItemCommand~ items
        +Long couponId
    }

    CouponService --> CouponCreateCommand
    OrderFacade --> OrderCreateCommand
```

---

## 3. 인터페이스 레이어

```mermaid
classDiagram
    class CouponAdminV1Controller {
        -CouponService couponService
        +getCoupons(Pageable)
        +getCoupon(Long)
        +createCoupon(CouponCreateRequest)
        +updateCoupon(Long, CouponUpdateRequest)
        +deleteCoupon(Long)
        +getIssues(Long, Pageable)
    }

    class CouponV1Controller {
        -UserCouponService userCouponService
        +issueCoupon(UserModel, Long couponId)
        +getMyCoupons(UserModel, Pageable)
    }

    class CouponV1Dto {
        <<static nested>>
        +CouponCreateRequest
        +CouponUpdateRequest
        +CouponResponse
        +UserCouponResponse
    }

    CouponAdminV1Controller --> CouponV1Dto
    CouponV1Controller --> CouponV1Dto
```

---

## 4. 레이어 간 의존 관계

```
interfaces/api/coupon
    ↓ (uses)
application/coupon (CouponService, UserCouponService)
    ↓ (uses)
domain/coupon (CouponModel, UserCouponModel, CouponRepository, UserCouponRepository)
    ↑ (implements)
infrastructure/coupon (CouponRepositoryImpl, UserCouponRepositoryImpl)

interfaces/api/order (OrderV1Controller)
    ↓
application/order (OrderFacade)
    ↓ (uses domain/coupon.UserCouponRepository for coupon apply)
domain/coupon + domain/order + domain/product + domain/stock
```

> `OrderFacade`는 `UserCouponRepository`를 직접 주입해 쿠폰 사용 처리를 수행합니다.  
> 쿠폰 도메인 서비스가 없는 이유: 쿠폰 사용은 `UserCouponModel.use()` 1줄로 캡슐화되어 있어 별도 Domain Service가 필요 없습니다.
