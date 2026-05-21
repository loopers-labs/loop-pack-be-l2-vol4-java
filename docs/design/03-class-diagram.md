# 도메인 클래스 다이어그램

> 설계 기준
> - 모든 Model은 `BaseEntity`를 상속한다
> - 도메인 유효성 검증은 생성자에서 수행한다 (`CoreException` throw)
> - Soft Delete는 `BaseEntity.deletedAt`으로 처리한다
> - 상태 전이는 Model 내부 메서드로만 수행한다

---

```mermaid
classDiagram
    class BaseEntity {
        +Long id
        +ZonedDateTime createdAt
        +ZonedDateTime updatedAt
        +ZonedDateTime deletedAt
        +guard()
        +delete()
        +restore()
    }

    class BrandModel {
        +String name
        +BrandStatus status
        +BrandModel(name)
        +update(name)
        +delete()
    }

    class ProductModel {
        +Long brandId
        +String name
        +Long price
        +ProductStatus status
        +ProductModel(brandId, name, price)
        +update(name, price)
        +suspend()
        +delete()
    }

    class ProductStockModel {
        +Long productId
        +Integer stockQuantity
        +ProductStockModel(productId, stockQuantity)
        +increase(quantity)
        +decrease(quantity)
    }

    class OrderModel {
        +String orderNumber
        +Long userId
        +OrderStatus status
        +OrderModel(orderNumber, userId)
        +complete()
    }

    class OrderItemModel {
        +Long orderId
        +Long productId
        +String productName
        +Long productPrice
        +Integer quantity
        +OrderItemModel(orderId, productId, productName, productPrice, quantity)
    }

    class WishlistModel {
        +Long userId
        +Long productId
        +WishlistModel(userId, productId)
    }

    class UserModel {
        +String userid
        +String password
        +String name
        +String birthDay
        +String email
        +UserRole role
        +UserModel(userid, encodedPassword, name, birthDay, email, role)
        +changePassword(encodedPassword)
    }

    class BrandStatus {
        <<enumeration>>
        ACTIVE
        INACTIVE
    }

    class ProductStatus {
        <<enumeration>>
        ACTIVE
        INACTIVE
    }

    class OrderStatus {
        <<enumeration>>
        REQUESTED
        COMPLETED
    }

    class UserRole {
        <<enumeration>>
        USER
        ADMIN
    }

    BaseEntity <|-- BrandModel
    BaseEntity <|-- ProductModel
    BaseEntity <|-- ProductStockModel
    BaseEntity <|-- OrderModel
    BaseEntity <|-- OrderItemModel
    BaseEntity <|-- WishlistModel
    BaseEntity <|-- UserModel

    BrandModel --> BrandStatus
    ProductModel --> ProductStatus
    OrderModel --> OrderStatus
    UserModel --> UserRole

    BrandModel "1" --o "0..*" ProductModel : brandId
    ProductModel "1" *-- "1" ProductStockModel : productId
    UserModel "1" --o "0..*" WishlistModel : userId
    ProductModel "1" --o "0..*" WishlistModel : productId
    UserModel "1" --o "0..*" OrderModel : userId
    OrderModel "1" *-- "1..*" OrderItemModel : orderId
```

---

## 도메인 규칙 (생성자 검증)

### BrandModel
| 필드 | 규칙 |
|------|------|
| name | null 불허, 2글자 이상 |
| status | 생성 시 `ACTIVE` 고정 |

### ProductModel
| 필드 | 규칙 |
|------|------|
| brandId | null 불허, 등록 후 변경 불가 |
| name | null 불허, 2글자 이상 |
| price | null 불허, 0 이상 |
| status | 생성 시 `ACTIVE` 고정 |

### ProductStockModel
| 필드 | 규칙 |
|------|------|
| productId | null 불허, 상품 생성 시 함께 생성, 변경 불가 |
| stockQuantity | null 불허, 0 이상 |

### OrderModel
| 필드 | 규칙 |
|------|------|
| orderNumber | 주문 생성 시 자동 발급. 포맷: `ORD-YYYYMMDD-NNNN` (일별 시퀀스). 유니크 제약 |
| userId | null 불허 |
| status | 생성 시 `REQUESTED` 고정. 결제 승인(`APPROVED`) 후 `COMPLETED`로 전이 |

### OrderItemModel
| 필드 | 규칙 |
|------|------|
| orderId | null 불허 |
| productId | null 불허 (상품 삭제 후에도 이력 보존용으로 유지) |
| productName | 주문 시점 상품명 스냅샷 |
| productPrice | 주문 시점 단가 스냅샷 |
| quantity | 1 이상 |

### WishlistModel
| 필드 | 규칙 |
|------|------|
| userId | null 불허 |
| productId | null 불허 |
| (userId, productId) | 유니크 제약 |

---

## 상태 전이

### BrandStatus
```
ACTIVE → INACTIVE : delete()
```

### ProductStatus
```
ACTIVE → INACTIVE : suspend()   (브랜드 삭제 시 연쇄)
ACTIVE → INACTIVE : delete()    (관리자 직접 삭제)
```

### OrderStatus
```
REQUESTED → COMPLETED : complete()
```
