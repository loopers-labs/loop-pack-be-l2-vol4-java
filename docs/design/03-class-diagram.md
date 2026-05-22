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
        +ProductStatus status
        +ProductModel(brandId, name)
        +update(name)
        +suspend()
        +delete()
    }

    class ProductStockModel {
        +Long productId
        +Long price
        +Integer stockQuantity
        +ProductStockModel(productId, price, stockQuantity)
        +increase(quantity)
        +decrease(quantity)
    }

    class OrderModel {
        +String orderNumber
        +Long userId
        +Long totalAmount
        +OrderStatus status
        +OrderModel(orderNumber, userId, totalAmount)
        +complete()
        +cancel()
    }

    class PaymentModel {
        +Long orderId
        +Long amount
        +PaymentStatus status
        +PaymentModel(orderId, amount)
        +approve()
        +fail()
        +expire()
    }

    class OrderItemModel {
        +Long orderId
        +Long productStockId
        +Long productId
        +String productName
        +Long productPrice
        +Integer quantity
        +OrderItemModel(orderId, productStockId, productId, productName, productPrice, quantity)
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
        CANCELLED
    }

    class PaymentStatus {
        <<enumeration>>
        PENDING
        APPROVED
        FAILED
        EXPIRED
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
    BaseEntity <|-- PaymentModel
    BaseEntity <|-- WishlistModel
    BaseEntity <|-- UserModel

    BrandModel --> BrandStatus
    ProductModel --> ProductStatus
    OrderModel --> OrderStatus
    PaymentModel --> PaymentStatus
    UserModel --> UserRole

    BrandModel "1" --o "0..*" ProductModel : brandId
    ProductModel "1" *-- "1..*" ProductStockModel : productId
    UserModel "1" --o "0..*" WishlistModel : userId
    ProductModel "1" --o "0..*" WishlistModel : productId
    UserModel "1" --o "0..*" OrderModel : userId
    OrderModel "1" *-- "1..*" OrderItemModel : orderId
    ProductStockModel "1" --o "0..*" OrderItemModel : productStockId
    OrderModel "1" *-- "1..*" PaymentModel : orderId
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
| status | 생성 시 `ACTIVE` 고정 |

### ProductStockModel
| 필드 | 규칙 |
|------|------|
| productId | null 불허, 변경 불가. 상품당 1개 이상 존재 (옵션 단위 관리) |
| price | null 불허, 0 이상 |
| stockQuantity | null 불허, 0 이상. DB CHECK 제약으로도 보장 |

### OrderModel
| 필드 | 규칙 |
|------|------|
| orderNumber | 주문 생성 시 자동 발급. 포맷: `ORD-YYYYMMDD-NNNN` (일별 시퀀스). 유니크 제약 |
| userId | null 불허 |
| totalAmount | null 불허, 0 이상. 주문 시점 총액 스냅샷 (order_items의 price × quantity 합산) |
| status | 생성 시 `REQUESTED` 고정. 결제 승인(`APPROVED`) 후 `COMPLETED`로 전이 |

### PaymentModel
| 필드 | 규칙 |
|------|------|
| orderId | null 불허, 변경 불가. 주문당 여러 번 존재 가능 (재시도 허용) |
| amount | null 불허, 0 이상. 주문 생성 시 orders.totalAmount와 동일 |
| status | 생성 시 `PENDING` 고정. PG 승인 → `APPROVED`, PG 실패 → `FAILED`, 배치 만료 → `EXPIRED` |

### OrderItemModel
| 필드 | 규칙 |
|------|------|
| orderId | null 불허 |
| productStockId | null 불허. 주문 시점의 특정 옵션(재고 항목) 참조 |
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
REQUESTED → COMPLETED  : complete()
REQUESTED → CANCELLED  : cancel()
```

### PaymentStatus
```
PENDING → APPROVED : approve()
PENDING → FAILED   : fail()
PENDING → EXPIRED  : expire()
```
