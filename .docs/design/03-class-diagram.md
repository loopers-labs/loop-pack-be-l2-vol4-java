# 클래스 설계 이력 및 3주차 반영 기준

## 읽는 포인트

- 도메인 모델은 비즈니스 규칙과 상태 전이에 집중하고, 여러 도메인의 조합은 application 계층에서 담당한다.
- `OrderFacade`와 `PaymentService`는 주문 생성, 결제 요청, outbox 저장의 경계를 확인하기 위한 핵심 클래스다.
- 실제 구현 전에는 클래스 이름보다 책임 배치와 의존 방향이 적절한지 먼저 검토한다.

## 설계 의도

이 클래스 설계는 도메인 책임과 의존 방향을 확인하기 위해 작성한다. 핵심 규칙은 도메인 모델과 도메인 서비스에 두고, 여러 도메인의 조합과 외부 시스템 호출은 application 계층에서 조정한다.

## 모듈 경계

이번 설계는 도메인 우선 모듈러 모놀리스 구조를 기준으로 한다.

| 모듈 | 포함 클래스 |
| --- | --- |
| `catalog` | `Brand`, `Product`, `ProductLike`, `BrandService`, `ProductService`, `ProductLikeService`, `StockService` |
| `ordering` | `Order`, `OrderLine`, `OrderFacade`, `OrderService` |
| `payment` | `Payment`, `PaymentWorker`, `PaymentService`, `PaymentGateway`, `PaymentRepository` |
| `event` | `OrderEventPublisher`, `OrderEventOutbox`, `EventRelayWorker`, `DataPlatformClient` |

`StockService`는 재고가 향후 옵션/창고/예약 재고로 확장될 수 있다는 전제로 별도 도메인 서비스로 둔다. 현재 저장 구조에서는 별도 재고 테이블을 만들지 않고 `Product.stockQuantity`를 비관적 락으로 차감/복구한다.

## 클래스 다이어그램

```mermaid
classDiagram
    class Product {
        Long id
        Long brandId
        String name
        String description
        Long price
        ProductStatus status
        Integer stockQuantity
        Long likeCount
        validateOrderable(quantity)
        validateLikeable()
        decreaseStock(quantity)
        increaseLikeCount()
        decreaseLikeCount()
        stopSelling()
    }

    class Brand {
        Long id
        String name
        String description
        LocalDateTime deletedAt
        markDeleted()
    }

    class ProductLike {
        Long id
        String userId
        Long productId
        like()
        unlike()
    }

    class Order {
        Long id
        String userId
        Long totalAmount
        OrderStatus status
        markPaid()
        markPaymentFailed()
        markCanceled()
        cancel()
    }

    class OrderLine {
        Long id
        Long orderId
        Long productId
        String productName
        Long unitPrice
        Integer quantity
        Long lineAmount
    }

    class Payment {
        Long id
        Long orderId
        Long amount
        PaymentStatus status
        String transactionKey
        String failureReason
        markSuccess(transactionKey)
        markFailed(reason)
        markCanceled(reason)
    }

    class OrderFacade {
        placeOrder(userId, request)
    }

    class BrandService {
        getBrand(brandId)
        getAdminBrands(page, size)
        createBrand(request)
        updateBrand(brandId, request)
        deleteBrand(brandId)
        ensureActiveBrandExists(brandId)
    }

    class ProductService {
        getVisibleProducts(brandId, sort, page, size)
        getVisibleProduct(productId)
        validateOrderable(items)
        getAdminProducts(page, size, brandId)
        createProduct(brandId, request)
        updateProduct(productId, requestWithoutBrandId)
        stopProduct(productId)
    }

    class ProductLikeService {
        like(userId, productId)
        unlike(userId, productId)
        getLikedProducts(userId)
    }

    class OrderService {
        createPendingOrder(userId, items)
        getOrders(userId, startAt, endAt)
        getOrder(userId, orderId)
        getAdminOrders(page, size)
        getAdminOrder(orderId)
        markPaid(orderId)
        markPaymentFailed(orderId)
        markCanceled(orderId)
        restoreStock(orderId)
    }

    class StockService {
        decrease(items)
        restore(items)
    }

    class PaymentWorker {
        processRequestedPayments()
        expireOverduePayments()
    }

    class PaymentService {
        processRequestedPayment(orderId)
        authorizePayment(orderId, amount)
        capturePayment(orderId)
        voidAuthorization(orderId, reason)
        createRequestedPayment(orderId, amount)
        expirePayment(orderId, reason)
        failPaymentAndRestoreStock(orderId, reason)
        cancelPaymentAndRestoreStock(orderId, reason)
    }

    class PaymentGateway {
        authorize(orderId, amount, idempotencyKey)
        capture(transactionKey)
        voidAuthorization(transactionKey)
        refund(transactionKey)
    }

    class PaymentRepository {
        saveRequested(orderId, amount)
        findRequestedPayments(orderIds)
        markSuccess(orderId, transactionKey)
        markFailed(orderId, reason)
        markCanceled(orderId, reason)
    }

    class OrderRepository {
        findByUserIdAndCreatedAtBetween(userId, startAt, endAt)
        findByIdAndUserId(orderId, userId)
        findAllForAdmin(page, size)
        findExpiredPaymentPendingOrders(deadline)
    }

    class AdminHeaderValidator {
        validateLdap(ldap)
    }

    class BrandAdminController {
        getBrands(page, size)
        getBrand(brandId)
        createBrand(request)
        updateBrand(brandId, request)
        deleteBrand(brandId)
    }

    class ProductAdminController {
        getProducts(page, size, brandId)
        getProduct(productId)
        createProduct(request)
        updateProduct(productId, requestWithoutBrandId)
        deleteProduct(productId)
    }

    class OrderAdminController {
        getOrders(page, size)
        getOrder(orderId)
    }

    class OrderEventPublisher {
        publishOrderPaid(orderId)
    }

    class OrderEventOutbox {
        save(event)
        findPendingEvents()
        markSent(eventId)
        increaseRetryCount(eventId)
        markFailed(eventId)
    }

    class EventRelayWorker {
        relayPendingEvents()
    }

    class DataPlatformClient {
        sendOrderPaid(event)
    }

    Brand "1" --> "*" Product
    Product "1" --> "*" ProductLike
    Order "1" --> "*" OrderLine
    Order "1" --> "1" Payment
    BrandAdminController --> AdminHeaderValidator
    BrandAdminController --> BrandService
    ProductAdminController --> AdminHeaderValidator
    ProductAdminController --> ProductService
    OrderAdminController --> AdminHeaderValidator
    OrderAdminController --> OrderService
    OrderFacade --> OrderService
    OrderFacade --> PaymentService
    ProductService --> BrandService
    OrderService --> ProductService
    OrderService --> StockService
    ProductLikeService --> Product
    ProductLikeService --> ProductLike
    PaymentWorker --> PaymentService
    PaymentWorker --> OrderRepository
    PaymentWorker --> PaymentRepository
    PaymentService --> PaymentRepository
    PaymentService --> PaymentGateway
    PaymentService --> OrderEventPublisher
    OrderEventPublisher --> OrderEventOutbox
    EventRelayWorker --> OrderEventOutbox
    EventRelayWorker --> DataPlatformClient
```

## 주요 책임

### Product

- 상품명, 가격, 판매 상태, 재고 수량, 좋아요 수를 가진다.
- 주문 가능 여부를 판단한다.
- 좋아요 등록 가능 여부를 판단한다.
- 주문 시 재고를 차감한다.
- 좋아요 등록/취소 시 `likeCount`를 변경한다.
- ADMIN 상품 삭제 요청 시 물리 삭제되지 않고 `STOPPED` 상태로 전환된다.

### Brand

- 상품의 브랜드 정보를 표현한다.
- 상품과 1:N 관계를 가진다.
- ADMIN 브랜드 삭제 요청 시 물리 삭제되지 않고 `deletedAt`이 표시된다.

### BrandService

- 브랜드 조회와 ADMIN 브랜드 등록/수정/삭제를 담당한다.
- 브랜드 삭제 시 브랜드 row를 보존하고, 해당 브랜드 상품을 `STOPPED` 상태로 전환하는 작업까지 같은 트랜잭션에서 처리한다.
- 신규 상품 등록 시 삭제되지 않은 활성 브랜드인지 검증한다.
- 브랜드 목록 ADMIN 조회는 `page=0`, `size=20` 기본값을 사용한다.

### ProductService

- 판매 가능한 상품 목록과 상세 조회를 담당한다.
- 상품 목록은 `brandId`, `sort`, `page`, `size` 조건을 처리한다.
- `sort` 허용 값은 `latest`, `price_asc`, `likes_desc`다.
- 상품 목록의 기본 페이지 조건은 `page=0`, `size=20`이다.
- ADMIN 상품 등록/수정/삭제를 담당한다.
- 상품 등록 시 브랜드 존재 여부를 확인한다.
- 상품 수정 시 브랜드 변경을 허용하지 않는다.
- 상품 삭제는 물리 삭제가 아니라 `STOPPED` 상태 전환으로 처리한다.
- `STOPPED` 상품은 대고객 상품 목록/상세와 주문 가능 상품에서 제외한다.

### ProductLike

- 사용자와 상품의 좋아요 관계를 표현한다.
- `userId + productId` 기준 유일성을 가진다.
- 좋아요 등록/취소 멱등성의 기준 데이터다.

### ProductLikeService

- 좋아요 등록/취소와 `Product.likeCount` 증감을 같은 DB 트랜잭션에서 처리한다.
- 좋아요 등록은 판매 가능한 상품에만 허용한다.
- 판매 중지/품절 상품에는 새 좋아요를 등록하지 않는다.
- 좋아요 취소는 상품 상태와 무관하게 허용한다.
- 내 좋아요 목록은 `product_like` 이력을 기준으로 조회하고, 판매 중지/품절 상품도 포함한다.
- 신규 좋아요 등록 시에만 `likeCount`를 증가시킨다.
- 실제 좋아요 이력이 삭제된 경우에만 `likeCount`를 감소시킨다.
- `product_like`를 정합성 기준 데이터로 두고, `likeCount`는 조회용 카운터로 관리한다.

### AdminHeaderValidator

- ADMIN API의 `X-Loopers-Ldap` 헤더를 검증한다.
- 헤더 값은 `loopers.admin`이어야 한다.
- 유효하지 않은 ADMIN 요청은 application 계층으로 진입시키지 않는다.

### BrandAdminController / ProductAdminController / OrderAdminController

- `/api-admin/v1` 하위 ADMIN API의 HTTP 요청/응답 변환을 담당한다.
- 브랜드/상품/주문 ADMIN API는 모두 `X-Loopers-Ldap` 헤더 검증을 먼저 수행한다.
- 상품과 브랜드의 등록/수정/삭제는 public `/api/v1`이 아니라 ADMIN API에 둔다.
- 브랜드 삭제 API는 실제 DB row 삭제가 아니라 `Brand.deletedAt` 표시와 관련 상품 `ProductStatus.STOPPED` 전환이다.
- 상품 삭제 API는 실제 DB row 삭제가 아니라 `ProductStatus.STOPPED` 전환이다.

### StockService

- `catalog` 모듈의 재고 도메인 서비스다.
- 주문 생성 시 `Product` 행을 비관적 락으로 잠그고 재고 부족 여부를 확인한 뒤 차감한다.
- 결제 실패, 취소, 타임아웃 시 주문 항목 기준으로 차감했던 재고를 복구한다.
- 현재는 `Product.stockQuantity`를 조작하지만, 향후 옵션 재고나 창고 재고로 확장될 수 있어 `ProductService`와 분리한다.

### OrderFacade

- 주문 생성 유스케이스의 application 계층 조정자다.
- 주문 생성 트랜잭션에서 `OrderService.createPendingOrder`와 `PaymentService.createRequestedPayment`를 함께 호출한다.
- 외부 결제 요청은 직접 수행하지 않고, 주문/결제 row 생성까지만 사용자 응답 전에 보장한다.

### Order

- 주문의 생명주기와 상태 전이를 관리한다.
- 주문 생성 직후 `PAYMENT_PENDING` 상태가 된다.
- 결제 성공 시 `PAID`, 실패 시 `PAYMENT_FAILED`, 취소 시 `CANCELED`로 전이한다.
- 사용자는 `X-Loopers-LoginId` 기준으로 본인의 주문 목록과 단일 주문 상세만 조회할 수 있다.
- 주문 목록 조회는 `startAt`, `endAt` 기간 조건으로 주문 생성 시각을 필터링한다.
- 어드민은 `/api-admin/v1/orders`에서 전체 주문 목록과 주문 상세를 조회할 수 있다.

### OrderLine

- 주문 당시 상품 스냅샷을 보관한다.
- 상품명과 단가를 저장해 상품 정보 변경이 과거 주문에 영향을 주지 않도록 한다.

### Payment

- 결제 요청과 결과를 기록한다.
- 외부 결제 시스템의 거래 키와 실패 사유를 보관한다.
- `TIMEOUT`은 별도 컬럼이 아니라 소스 레벨에서 판정하는 실패 사유 값이다.
- 주문 생성 트랜잭션에서 `REQUESTED` 상태로 먼저 생성되어 주문 조회 응답의 `paymentStatus` 기준이 된다.
- `orderId` 기준 유일성을 통해 같은 주문의 결제 요청이 중복 처리되지 않게 한다.

### PaymentWorker

- `REQUESTED` 결제 row를 내부 비동기로 처리한다.
- 주문 생성 시각 기준 결제 대기 시간 1분 초과 여부를 소스 레벨에서 판정한다.
- `PAYMENT_PENDING` 주문과 `REQUESTED` 결제를 스캔해 응답 지연 또는 worker 중단 후에도 만료 처리를 수행한다.
- 1분을 넘긴 주문을 `PAYMENT_FAILED`와 실패 사유 값 `TIMEOUT`으로 전이한다.

### PaymentService

- 주문 생성 트랜잭션에서 `Payment(REQUESTED)` 저장을 수행한다.
- worker는 이미 생성된 `Payment(REQUESTED)`를 읽어 외부 결제 요청을 수행한다.
- 외부 결제 요청에는 `orderId` 기반 idempotency key를 전달한다.
- 외부 PG는 `auth/capture/void` 계약을 지원한다고 가정한다.
- 주문 생성 시각 기준 1분을 넘긴 대기/요청 건은 `TIMEOUT`으로 만료 처리한다.
- 이미 성공/실패/취소로 확정된 결제는 다시 만료 처리하지 않는다.
- 결제 실패/취소/타임아웃 시 결제 결과 기록, 주문 상태 전이, 재고 복구를 하나의 DB 트랜잭션으로 처리한다.
- 결제 성공 시 외부 데이터 플랫폼을 직접 호출하지 않고 `OrderEventPublisher`에 주문 완료 이벤트 저장을 요청한다.

### OrderEventPublisher

- 주문 완료 이벤트를 outbox에 저장하는 진입점이다.
- 주문 `PAID` 상태 전이와 이벤트 저장이 같은 DB 트랜잭션 경계에 들어오도록 조정한다.
- 외부 데이터 플랫폼에는 직접 전송하지 않는다.

### OrderEventOutbox

- 외부 데이터 플랫폼으로 보낼 주문 이벤트를 저장한다.
- 아직 전송되지 않은 이벤트를 조회할 수 있게 한다.
- 전송 성공 시 상태를 갱신하고, 실패 시 재시도 횟수를 증가시킨다.
- 최대 재시도 횟수를 초과하면 `FAILED` 상태로 확정한다.

### EventRelayWorker

- outbox에 남아 있는 주문 이벤트를 조회한다.
- `DataPlatformClient`를 통해 외부 데이터 플랫폼에 이벤트를 전송한다.
- 전송 결과에 따라 outbox 상태를 갱신한다.
- `PENDING` 이벤트만 전송 대상으로 조회하고, `FAILED` 이벤트는 수동 확인 대상으로 남긴다.

### DataPlatformClient

- 외부 데이터 플랫폼 전송 API 호출을 담당한다.
- `PaymentService`가 아니라 `EventRelayWorker`에서 사용한다.

## 의존 방향

- 최상위 구조는 도메인 모듈을 먼저 나누고, 각 모듈 내부에 계층을 둔다.
- `interfaces`는 HTTP 요청/응답 변환만 담당한다.
- `application`은 여러 도메인 서비스를 조합한다.
- `domain`은 비즈니스 규칙을 가진다.
- `infrastructure`는 Repository와 외부 시스템 구현을 담당한다.
- 도메인 모델은 외부 결제 시스템이나 HTTP 계층을 직접 알지 않는다.
- 도메인 모델은 JPA/Spring 타입을 직접 알지 않고, infrastructure `*JpaEntity`가 DB 매핑을 담당한다.
- repository adapter는 domain port를 구현하며 domain entity와 JPA entity를 변환한다.
- `PaymentService`는 `DataPlatformClient`를 직접 알지 않고, `OrderEventPublisher`를 통해 outbox 저장만 요청한다.

## 현재 구현 반영 상태

- 대고객 `/api/v1/products`는 목록/상세 조회만 담당한다.
- 상품 등록, 수정, 삭제는 `/api-admin/v1/products` 하위의 `ProductAdminController`가 담당한다.
- 3주차 구현 대상 도메인은 POJO domain entity와 infrastructure `*JpaEntity`로 분리했다.
- `StockService`는 순수 도메인 서비스이며, Spring bean 등록은 infrastructure configuration에서 담당한다.
- ADMIN 상품 삭제는 과거 주문 이력을 보호하기 위해 물리 삭제하지 않고 `STOPPED` 상태로 전환한다.

## 상태 정의와 전이 요약

```text
ProductStatus
- ON_SALE
- SOLD_OUT
- STOPPED

OrderStatus
- PAYMENT_PENDING
- PAID
- PAYMENT_FAILED
- CANCELED

PaymentStatus
- REQUESTED
- SUCCESS
- FAILED
- CANCELED

OutboxStatus
- PENDING
- SENT
- FAILED
```

- 주문은 생성 직후 `PAYMENT_PENDING`이 되며, 결제 성공 시 `PAID`, 실패/타임아웃 시 `PAYMENT_FAILED`, 취소 시 `CANCELED`로 전이한다.
- 이미 `PAID`, `PAYMENT_FAILED`, `CANCELED`로 확정된 주문은 늦게 도착한 결제 결과로 되돌리지 않는다.
- 결제는 주문 생성 트랜잭션에서 `REQUESTED`로 먼저 저장하고, 성공/실패/취소/타임아웃 결과에 따라 확정 상태로 전이한다.
- Outbox는 주문 성공 이벤트 저장 시 `PENDING`이 되고, 전송 성공 시 `SENT`, 최대 재시도 초과 시 `FAILED`로 전이한다.

## 리스크와 선택지

- 주문 도메인이 재고까지 직접 알면 응집도는 높아질 수 있지만 도메인 간 결합이 커진다.
- `OrderFacade`가 재고와 결제를 조정하면 책임 경계는 명확하지만 애플리케이션 서비스가 커질 수 있다.
- 결제 결과를 내부 비동기로 처리하면 장애 격리는 좋아지지만 상태 조회, 1분 대기 만료, 중복 실행 방지 설계가 필요하다.
