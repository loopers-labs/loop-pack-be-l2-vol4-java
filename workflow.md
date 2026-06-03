# Week 3 Workflow

이 문서는 3주차 구현 흐름을 빠르게 파악하기 위한 작업자용 흐름도다.
현재 `apps/commerce-api` 범위는 RDB-only이며 Redis, Kafka, cache, message broker 연동은 사용하지 않는다.

## Boundary

```mermaid
flowchart LR
    Client[API Client]
    Admin[Admin Client]

    Client --> CatalogApi[catalog/interfaces]
    Client --> OrderApi[ordering/interfaces]
    Admin --> AdminApi[catalog, ordering admin interfaces]

    CatalogApi --> CatalogApp[catalog/application]
    OrderApi --> OrderingApp[ordering/application]
    AdminApi --> CatalogApp

    OrderingApp --> CatalogDomain[catalog/domain]
    OrderingApp --> OrderingDomain[ordering/domain]
    OrderingApp --> PaymentApp[payment/application]
    PaymentApp --> PaymentDomain[payment/domain]
    PaymentApp --> EventApp[event/application]

    CatalogDomain --> CatalogJpa[catalog/infrastructure JPA]
    OrderingDomain --> OrderingJpa[ordering/infrastructure JPA]
    PaymentDomain --> PaymentJpa[payment/infrastructure JPA]
    EventApp --> EventJpa[event/infrastructure JPA]

    CatalogJpa --> MySQL[(MySQL)]
    OrderingJpa --> MySQL
    PaymentJpa --> MySQL
    EventJpa --> MySQL
```

## Request Flow

```mermaid
flowchart TD
    Request[HTTP Request]
    Request --> Controller[interfaces/api controller]
    Controller --> ApiDto[API request DTO]
    ApiDto --> Command[application command/query DTO]
    Command --> AppService[application service/facade]
    AppService --> Domain[domain entity/service]
    Domain --> Port[domain repository port]
    Port --> Adapter[infrastructure repository adapter]
    Adapter --> JpaEntity[*JpaEntity]
    JpaEntity --> MySQL[(MySQL)]
    AppService --> Result[application result DTO]
    Result --> Response[API response DTO]
```

## Order And Payment

```mermaid
sequenceDiagram
    participant API as OrderController
    participant OrderApp as OrderFacade
    participant Stock as StockService
    participant OrderRepo as OrderRepository
    participant PayApp as PaymentCommandService
    participant MySQL as MySQL

    API->>OrderApp: placeOrder(command)
    OrderApp->>Stock: decrease(productId, quantity)
    Stock->>MySQL: lock products and update stock
    OrderApp->>OrderRepo: save(PAYMENT_PENDING order)
    OrderRepo->>MySQL: insert order/order_line
    OrderApp->>PayApp: createRequestedPayment(orderId, amount)
    PayApp->>MySQL: insert payment(REQUESTED)
    OrderApp-->>API: order detail + paymentStatus REQUESTED
```

## Payment Result

```mermaid
flowchart TD
    Requested[Payment REQUESTED]
    Requested --> Worker[PaymentWorker]
    Worker --> External[PaymentGateway authorize]
    External -->|SUCCESS| Success[Payment SUCCESS]
    External -->|FAILURE| Failure[Payment FAILED]
    External -->|CANCELED| Canceled[Payment CANCELED]
    External -->|timeout boundary| Timeout[Payment EXPIRED]

    Success --> Paid[Order PAID]
    Paid --> Outbox[OrderEventOutbox PENDING]
    Failure --> Restore1[Restore stock and fail order]
    Canceled --> Restore2[Restore stock and cancel order]
    Timeout --> Restore3[Restore stock and fail order]

    Outbox --> Relay[EventRelayWorker]
    Relay -->|RDB-only relay state| Sent[Outbox SENT]
    Relay -->|failure| Retry[retryCount + 1 or FAILED]
```

## Catalog

```mermaid
flowchart TD
    ProductList[GET /api/v1/products]
    ProductDetail[GET /api/v1/products/{id}]
    Like[POST /api/v1/products/{id}/likes]
    Unlike[DELETE /api/v1/products/{id}/likes]

    ProductList --> ProductQuery[ProductQueryService]
    ProductDetail --> ProductQuery
    Like --> LikeCommand[ProductLikeCommandService]
    Unlike --> LikeCommand

    ProductQuery --> ProductRepo[ProductRepository]
    ProductQuery --> BrandRepo[BrandRepository]
    ProductQuery --> LikeRepo[ProductLikeRepository]
    LikeCommand --> LikeRepo
    LikeCommand --> ProductRepo

    ProductRepo --> MySQL[(MySQL)]
    BrandRepo --> MySQL
    LikeRepo --> MySQL
```

## Verification

```powershell
$env:JAVA_HOME='C:\Users\woodo\.jdks\ms-21.0.9'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

docker compose -f docker\infra-compose.yml up -d mysql

$env:LOOPERS_TESTCONTAINERS_ENABLED='false'
$env:DATASOURCE_MYSQL_JPA_MAIN_JDBC_URL='jdbc:mysql://localhost:3306/loopers'
$env:DATASOURCE_MYSQL_JPA_MAIN_USERNAME='application'
$env:DATASOURCE_MYSQL_JPA_MAIN_PASSWORD='application'

.\gradlew.bat --no-daemon :apps:commerce-api:test
```

Expected boundary checks:

- `apps/commerce-api` has no Redis/Kafka runtime dependency.
- `apps/commerce-api` does not import `redis.yml` or `kafka.yml`.
- New week-3 domain packages do not depend on Spring, JPA, or HTTP types.
- RDB outbox is the event handoff mechanism for this week.
