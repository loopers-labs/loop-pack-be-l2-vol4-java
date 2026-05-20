# 02. 시퀀스 다이어그램

각 유스케이스(`01-requirements.md` §6 참조)의 흐름을 레이어별 참여자 기준으로 시각화한다. 다이어그램은 Mermaid `sequenceDiagram` 문법으로 작성하며, 흐름 분기·조건은 `alt`/`opt` 블록으로 표현한다.

## 0. 표기 규칙

### 0.1 참여자 레이어

실제 Loopers 멀티모듈 패키지 구조에 맞춘 레이어를 사용한다.

| 레이어 | 패키지 | 책임 | 다이어그램 예시 |
| --- | --- | --- | --- |
| Client | — | 사용자/외부 호출자 (User, Guest, Admin) | `actor C as Client` |
| Interface | `interfaces.api.{domain}` | REST 엔드포인트, DTO 변환 | `XxxV1Controller` |
| Application | `application.{domain}` | 유스케이스 조립(Facade). 트랜잭션 경계 | `XxxFacade` |
| Domain Service | `domain.{domain}` | 도메인 로직, 여러 Aggregate 조정 | `XxxService` |
| Domain Aggregate | `domain.{domain}` | 상태와 불변식. 도메인 메서드를 외부에 노출 | `XxxModel` |
| Domain Repository | `domain.{domain}` | 영속화 인터페이스 | `XxxRepository` |
| External | — | 외부 시스템 어댑터 | `PaymentGateway` |

도메인 객체 간 메시지(예: `OrderModel.markPaid()`, `ProductModel.deductStock()`)는 명시적으로 표기한다. Service에서 Repository로 직행하지 않고 항상 Aggregate의 도메인 메서드를 거치는 흐름으로 그린다.

### 0.2 화살표·블록

- `->>` 동기 호출 (메서드 호출)
- `-->>` 응답·반환값
- `alt` / `else` — 분기 (예: 인증 여부)
- `opt` — 선택적 단계 (조건 만족 시만 실행)
- `Note over X: ...` — 트랜잭션 경계, 동시성 처리 등 메타 정보
- 비동기 이벤트(행동 로그)는 `--)` 또는 `Note`로 표현

### 0.3 생략 규칙

- 인증 헤더 검증(`AuthInterceptor` 류) 같은 횡단 관심사는 다이어그램에서 별도 표기하지 않고 Controller 진입을 인증 성공 전제로 시작한다. 인증 실패는 §0.4 공통 에러로 위임.
- 행동 로깅(01 §7.7 활동 기록)은 메인 흐름 마지막에 비동기 `--)` 한 줄로 표현하고 상세는 생략.
- 응답 변환(`Model → Info → Dto`) 단계는 다이어그램에서 한 줄로 압축한다.

### 0.4 공통 에러 (모든 UC 공통)

- `401 UNAUTHORIZED` — 인증 헤더 누락/불일치. Controller 진입 이전 단계에서 종결
- `400 BAD_REQUEST` — 입력 형식·필수값 위반
- 자세한 에러 분기는 `01-requirements.md` 각 UC `Exception Flow` 참조

---

## UC-01. 비밀번호 변경

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant Ctrl as UserV1Controller
    participant Fac as UserFacade
    participant Svc as UserService
    participant U as UserModel
    participant Repo as UserRepository

    C->>Ctrl: PUT /api/v1/users/password\n{newPassword}
    Ctrl->>Fac: changePassword(loginId, newPassword)
    Fac->>Svc: changePassword(loginId, newPassword)
    Svc->>Repo: findByLoginId(loginId)
    Repo-->>Svc: UserModel
    Svc->>U: matchesPassword(currentPw from header)
    U-->>Svc: true
    Svc->>U: changePassword(newPassword)
    Note over U: 정책 검증 + 해싱
    U-->>Svc: void
    Svc->>Repo: save(user)
    Repo-->>Svc: UserModel
    Svc--)Audit: password_changed (async)
    Svc-->>Fac: void
    Fac-->>Ctrl: void
    Ctrl-->>C: 204 No Content
```

**에러 분기 (요약)**
- `newPassword`가 정책 위반 → `U.changePassword` 내부에서 `CoreException` → `400`
- `newPassword == currentPassword` → Service 선검사에서 `400 SAME_AS_CURRENT`
- 인증 헤더 검증은 진입 이전 단계 (§0.3)

---

## UC-02. 브랜드 조회

```mermaid
sequenceDiagram
    actor C as Client
    participant Ctrl as BrandV1Controller
    participant Fac as BrandFacade
    participant Svc as BrandService
    participant Repo as BrandRepository

    C->>Ctrl: GET /api/v1/brands/{brandId}
    Ctrl->>Fac: getBrand(brandId)
    Fac->>Svc: findActiveById(brandId)
    Svc->>Repo: findByIdAndDeletedAtIsNull(brandId)
    Repo-->>Svc: BrandModel | null
    alt 미존재
        Svc-->>Fac: throw CoreException(NOT_FOUND)
        Fac-->>Ctrl: 404 BRAND_NOT_FOUND
        Ctrl-->>C: 404
    else 존재
        Svc-->>Fac: BrandModel
        Fac-->>Ctrl: BrandInfo
        Ctrl-->>C: 200 OK + BrandDto
    end
```

---

## UC-03. 상품 목록 둘러보기

`likedByMe`는 페이지 내 productId 집합에 대해 **단일 IN 쿼리 1회**로 일괄 조회 (N+1 회피 — 04 §3 인덱스 전략).

```mermaid
sequenceDiagram
    actor C as Client
    participant Ctrl as ProductV1Controller
    participant Fac as ProductFacade
    participant PSvc as ProductService
    participant LSvc as LikeService
    participant PRepo as ProductRepository
    participant LRepo as LikeRepository

    C->>Ctrl: GET /api/v1/products?brandId=&sort=&page=&size=
    Ctrl->>Fac: getProducts(query, userId?)
    Fac->>PSvc: findPage(brandId, sort, page, size)
    PSvc->>PRepo: findActivePage(brandId, sort, pageable)/
    PRepo-->>PSvc: Page<ProductModel>
    PSvc-->>Fac: Page<ProductModel>
    alt userId 존재 (인증)
        Fac->>LSvc: findLikedProductIds(userId, productIds)
        LSvc->>LRepo: findActiveByUserIdAndProductIdIn(userId, productIds)
        LRepo-->>LSvc: List<LikeModel>
        LSvc-->>Fac: Set<Long> likedProductIds
    else userId 없음 (Guest)
        Note over Fac: likedProductIds = empty set
    end
    Fac->>Fac: assemble ProductInfo (likedByMe = ids.contains)
    Fac-->>Ctrl: Page<ProductInfo>
    Ctrl-->>C: 200 OK + ProductPageDto
```

**비고**
- `likes_desc` 정렬은 `products.likesCount` 비정규화 컬럼 기준 (실시간 COUNT 금지 — 01 §7.3 좋아요 수 표시, 04 §2.3)
- 정렬 안정성을 위해 모든 정렬에 `productId DESC` tiebreaker 적용

---

## UC-04. 상품 상세 조회

```mermaid
sequenceDiagram
    actor C as Client
    participant Ctrl as ProductV1Controller
    participant Fac as ProductFacade
    participant PSvc as ProductService
    participant LSvc as LikeService
    participant PRepo as ProductRepository

    C->>Ctrl: GET /api/v1/products/{productId}
    Ctrl->>Fac: getProductDetail(productId, userId?)
    Fac->>PSvc: findActiveById(productId)
    PSvc->>PRepo: findActiveByIdWithBrand(productId)
    Note over PRepo: Brand soft delete cascade 검사 포함
    PRepo-->>PSvc: ProductModel
    alt 미존재 또는 cascade로 deleted
        PSvc-->>Fac: throw CoreException(NOT_FOUND)
        Ctrl-->>C: 404 PRODUCT_NOT_FOUND
    else
        PSvc-->>Fac: ProductModel
        opt userId 존재
            Fac->>LSvc: existsActive(userId, productId)
            LSvc-->>Fac: boolean likedByMe
        end
        Fac-->>Ctrl: ProductInfo
        Ctrl-->>C: 200 OK + ProductDto
    end
```

---

## UC-05. 상품 좋아요 등록

기존 행이 있고 `deleted=true`면 INSERT 대신 **reactivate**(UPDATE). MariaDB 부분 인덱스 미지원 회피 — `UNIQUE(userId, productId)` 제약과 양립 (01 §7.5 비활성 처리·전파, 04 §4.4).

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant Ctrl as LikeV1Controller
    participant Fac as LikeFacade
    participant PSvc as ProductService
    participant LSvc as LikeService
    participant L as LikeModel
    participant LRepo as LikeRepository
    participant P as ProductModel
    participant PRepo as ProductRepository

    C->>Ctrl: POST /api/v1/products/{productId}/likes
    Ctrl->>Fac: like(userId, productId)
    Note over Fac: @Transactional 시작
    Fac->>PSvc: assertActive(productId)
    PSvc->>PRepo: findActiveById(productId)
    PRepo-->>PSvc: ProductModel | throw NOT_FOUND
    Fac->>LSvc: like(userId, productId)
    LSvc->>LRepo: findByUserIdAndProductId(userId, productId)
    LRepo-->>LSvc: LikeModel | null
    alt 행 없음
        LSvc->>L: new LikeModel(userId, productId)
        LSvc->>LRepo: save(like)
        LRepo-->>LSvc: LikeModel (신규)
        LSvc-->>Fac: CHANGED
    else 행 있음, deleted=true
        LSvc->>L: reactivate()
        Note over L: deleted=false, likedAt=now()
        LSvc->>LRepo: save(like)
        LSvc-->>Fac: CHANGED
    else 행 있음, deleted=false (이미 활성)
        LSvc-->>Fac: NO_OP
    end
    opt CHANGED
        Fac->>PSvc: incrementLikesCount(productId)
        PSvc->>P: incrementLikesCount()
        Note over P: 원자 UPDATE\nSET likesCount = likesCount + 1
        PSvc->>PRepo: save(product)
        Fac--)Audit: product_liked (async)
    end
    Note over Fac: 커밋
    Fac-->>Ctrl: void
    Ctrl-->>C: 204 No Content
```

**비고**
- `UNIQUE(userId, productId)` race 발생 시 `LRepo.save`에서 예외 → 1회 재시도. 재시도 후에도 실패 시 `409 CONFLICT`

---

## UC-06. 상품 좋아요 취소

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant Ctrl as LikeV1Controller
    participant Fac as LikeFacade
    participant PSvc as ProductService
    participant LSvc as LikeService
    participant L as LikeModel
    participant LRepo as LikeRepository
    participant P as ProductModel
    participant PRepo as ProductRepository

    C->>Ctrl: DELETE /api/v1/products/{productId}/likes
    Ctrl->>Fac: unlike(userId, productId)
    Note over Fac: @Transactional 시작
    Fac->>PSvc: assertActive(productId)
    PSvc-->>Fac: ok | 404
    Fac->>LSvc: unlike(userId, productId)
    LSvc->>LRepo: findActiveByUserIdAndProductId(userId, productId)
    LRepo-->>LSvc: LikeModel | null
    alt 활성 Like 있음
        LSvc->>L: softDelete()
        Note over L: deleted=true,\ndeletedAt=now()
        LSvc->>LRepo: save(like)
        LSvc-->>Fac: CHANGED
    else 활성 Like 없음
        LSvc-->>Fac: NO_OP
    end
    opt CHANGED
        Fac->>PSvc: decrementLikesCount(productId)
        PSvc->>P: decrementLikesCount()
        Note over P: SET likesCount = GREATEST(likesCount - 1, 0)
        PSvc->>PRepo: save(product)
        Fac--)Audit: product_unliked (async)
    end
    Note over Fac: 커밋
    Fac-->>Ctrl: void
    Ctrl-->>C: 204 No Content
```

---

## UC-07. 내가 좋아요한 상품 조회

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant Ctrl as LikeV1Controller
    participant Fac as LikeFacade
    participant LSvc as LikeService
    participant PSvc as ProductService
    participant LRepo as LikeRepository
    participant PRepo as ProductRepository

    C->>Ctrl: GET /api/v1/users/me/likes?page=&size=
    Ctrl->>Fac: getMyLikes(userId, page, size)
    Fac->>LSvc: findMyActiveLikes(userId, pageable)
    LSvc->>LRepo: findActiveByUserIdOrderByLikedAtDesc(userId, pageable)
    LRepo-->>LSvc: Page<LikeModel>
    LSvc-->>Fac: Page<LikeModel>
    Fac->>PSvc: findActiveByIds(productIds)
    PSvc->>PRepo: findActiveByIdInWithBrand(productIds)
    Note over PRepo: 상품·브랜드 cascade 검사
    PRepo-->>PSvc: List<ProductModel>
    PSvc-->>Fac: List<ProductModel>
    Fac->>Fac: filter cascade-deleted + assemble (likedByMe=true)
    Fac-->>Ctrl: Page<ProductInfo>
    Ctrl-->>C: 200 OK
```

**비고**
- 좋아요한 상품 중 일부가 cascade로 내려간 경우 결과에서 자동 제외 — `totalElements`는 활성 항목 수

---

## UC-08a. 상품 주문·결제 — 정상 흐름 (Main Flow)

PG 호출은 **트랜잭션 커밋 후** 별도 단계에서 수행 (§7.6). 트랜잭션 내에 외부 시스템 호출을 두지 않는다.

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant Ctrl as OrderV1Controller
    participant Fac as OrderFacade
    participant PSvc as ProductService
    participant OSvc as OrderService
    participant P as ProductModel
    participant O as OrderModel
    participant PRepo as ProductRepository
    participant ORepo as OrderRepository
    participant PG as PaymentGateway

    C->>Ctrl: POST /api/v1/orders {items, paymentMethod}
    Ctrl->>Fac: placeOrder(userId, command)
    Fac->>Fac: 입력 검증 (items 1+, qty 1+, productId 중복)

    Note over Fac,ORepo: @Transactional 시작
    Fac->>PSvc: deductStocks(items)
    loop 각 item
        PSvc->>P: deductStock(qty)
        Note over P: 원자 UPDATE\nWHERE stock >= qty
        P-->>PSvc: ok | INSUFFICIENT_STOCK
    end
    PSvc-->>Fac: 단가 포함 priced items

    Fac->>OSvc: create(userId, pricedItems, paymentMethod)
    OSvc->>O: new OrderModel(... status=PENDING)
    OSvc->>ORepo: save(order)
    ORepo-->>OSvc: OrderModel (orderId)
    OSvc-->>Fac: OrderModel
    Note over Fac,ORepo: 커밋

    Fac->>PG: pay(orderId, totalAmount, paymentMethod)
    PG-->>Fac: PaymentResult(SUCCESS)

    Note over Fac: @Transactional (보조)
    Fac->>OSvc: markPaid(orderId)
    OSvc->>O: markPaid()
    Note over O: status=PAID, paidAt=now()
    OSvc->>ORepo: save(order)
    Fac--)Audit: order_paid (async)

    Fac-->>Ctrl: OrderInfo
    Ctrl-->>C: 200 OK + OrderDto (status=PAID)
```

---

## UC-08b. 상품 주문·결제 — PG 실패 (보상 트랜잭션)

PG 호출 결과가 실패면 **보상 트랜잭션**으로 재고 원복 + `Order.status=FAILED`.

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant Ctrl as OrderV1Controller
    participant Fac as OrderFacade
    participant PSvc as ProductService
    participant OSvc as OrderService
    participant P as ProductModel
    participant O as OrderModel
    participant PG as PaymentGateway

    Note over C,O: UC-08a Main Flow와 동일하게 재고 차감 + Order(PENDING) 커밋까지 진행
    Fac->>PG: pay(orderId, totalAmount, paymentMethod)
    PG-->>Fac: PaymentResult(FAILED, reason)

    Note over Fac: 보상 @Transactional 시작
    Fac->>PSvc: restoreStocks(orderItems)
    loop 각 item
        PSvc->>P: restoreStock(qty)
        Note over P: SET stock = stock + qty
    end
    Fac->>OSvc: markFailed(orderId, reason)
    OSvc->>O: markFailed(reason)
    Note over O: status=FAILED, failureReason=reason
    Fac--)Audit: order_failed (async)
    Note over Fac: 커밋

    Fac-->>Ctrl: OrderInfo (status=FAILED)
    Ctrl-->>C: 200 OK + OrderDto (status=FAILED)
```

**비고**
- 보상 트랜잭션 자체가 실패하면 `Order.status=PENDING` 잔존 → reconcile job이 후처리 (UC-08c와 동일 경로)

---

## UC-08c. 상품 주문·결제 — PG 타임아웃 (PENDING + Reconcile)

PG 응답이 없거나 타임아웃이면 **재고 차감 상태 유지** + `Order=PENDING`. 비동기 reconcile job이 최종 상태 확정.

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant Fac as OrderFacade
    participant OSvc as OrderService
    participant O as OrderModel
    participant PG as PaymentGateway
    participant Recon as ReconcileJob

    Note over C,Fac: UC-08a Main Flow와 동일하게 재고 차감 + Order(PENDING) 커밋

    Fac->>PG: pay(orderId, totalAmount, paymentMethod)
    Note over PG: 타임아웃 (응답 없음)
    PG--xFac: TimeoutException

    Note over Fac: 추가 보상 없이 PENDING 유지\n(재고는 차감 상태)
    Fac-->>C: 202 ACCEPTED + OrderDto (status=PENDING)

    rect rgb(245, 245, 245)
        Note over Recon,PG: 비동기 reconcile 사이클
        Recon->>PG: queryStatus(orderId)
        alt PG 결과 SUCCESS
            PG-->>Recon: SUCCESS
            Recon->>OSvc: markPaid(orderId)
            OSvc->>O: markPaid()
        else PG 결과 FAILED
            PG-->>Recon: FAILED
            Recon->>OSvc: markFailedAndCompensate(orderId)
            Note over OSvc: 재고 복원 + status=FAILED
        else PG 여전히 미확정 또는 미응답
            Recon-->>Recon: 다음 사이클까지 보류
        end
    end
```

---

## UC-09. 내 주문 조회

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant Ctrl as OrderV1Controller
    participant Fac as OrderFacade
    participant OSvc as OrderService
    participant ORepo as OrderRepository

    C->>Ctrl: GET /api/v1/orders/{orderId}
    Ctrl->>Fac: getMyOrder(userId, orderId)
    Fac->>OSvc: findByIdForUser(orderId, userId)
    OSvc->>ORepo: findByIdAndUserIdWithItems(orderId, userId)
    ORepo-->>OSvc: OrderModel | null
    alt 미존재 또는 본인 주문 아님
        OSvc-->>Fac: throw CoreException(NOT_FOUND)
        Ctrl-->>C: 404 ORDER_NOT_FOUND
    else
        OSvc-->>Fac: OrderModel + OrderItems (스냅샷)
        Fac-->>Ctrl: OrderInfo
        Ctrl-->>C: 200 OK + OrderDto
    end
```

**비고**
- 타인 주문 요청도 동일하게 `404` 반환 — orderId 존재 여부 누설 방지 (01 §7.4 본인 자원 접근 정책)

---

## UC-10. (Admin) 브랜드 삭제 — Cascade

Brand → Product → Like cascade soft delete. Order/OrderItem은 영향 없음 (01 §7.5).

```mermaid
sequenceDiagram
    actor A as Client(Admin)
    participant Ctrl as AdminBrandController
    participant Fac as BrandFacade
    participant BSvc as BrandService
    participant PSvc as ProductService
    participant LSvc as LikeService
    participant B as BrandModel
    participant BRepo as BrandRepository

    A->>Ctrl: DELETE /api-admin/v1/brands/{brandId}
    Ctrl->>Fac: deleteBrand(brandId)
    Note over Fac: @Transactional 시작
    Fac->>BSvc: softDelete(brandId)
    BSvc->>BRepo: findActiveById(brandId)
    BRepo-->>BSvc: BrandModel | 404
    BSvc->>B: softDelete()
    Note over B: deletedAt = now()
    BSvc->>BRepo: save(brand)
    BSvc-->>Fac: ok

    Fac->>PSvc: softDeleteByBrandId(brandId)
    Note over PSvc: 소속 Product 전부\ndeletedAt 일괄 UPDATE
    PSvc-->>Fac: List<Long> productIds

    Fac->>LSvc: softDeleteByProductIds(productIds)
    Note over LSvc: 해당 Product의 활성 Like 전부\ndeletedAt 일괄 UPDATE
    LSvc-->>Fac: ok

    Fac--)Audit: admin_brand_deleted (async)
    Note over Fac: 커밋
    Fac-->>Ctrl: void
    Ctrl-->>A: 204 No Content
```

**비고**
- 대량 cascade(상품·좋아요 만 건)는 동기 처리 시 응답 지연 가능 → 비동기 job 분리 검토 (현재는 동기)
- `likesCount`는 cascade 대상 아님 (어차피 비노출 상품의 likesCount는 영향 없음)

---

## UC-11. (Admin) 상품 삭제 — Cascade

```mermaid
sequenceDiagram
    actor A as Client(Admin)
    participant Ctrl as AdminProductController
    participant Fac as ProductFacade
    participant PSvc as ProductService
    participant LSvc as LikeService
    participant P as ProductModel
    participant PRepo as ProductRepository

    A->>Ctrl: DELETE /api-admin/v1/products/{productId}
    Ctrl->>Fac: deleteProduct(productId)
    Note over Fac: @Transactional 시작
    Fac->>PSvc: softDelete(productId)
    PSvc->>PRepo: findActiveById(productId)
    PRepo-->>PSvc: ProductModel | 404
    PSvc->>P: softDelete()
    Note over P: deletedAt = now()
    PSvc->>PRepo: save(product)
    PSvc-->>Fac: ok

    Fac->>LSvc: softDeleteByProductIds([productId])
    LSvc-->>Fac: ok

    Fac--)Audit: admin_product_deleted (async)
    Note over Fac: 커밋
    Fac-->>Ctrl: void
    Ctrl-->>A: 204 No Content
```

---

## UC-12. (Admin) 주문 모니터링

본인 격리 규칙 미적용. 어드민은 전체 접근.

```mermaid
sequenceDiagram
    actor A as Client(Admin)
    participant Ctrl as AdminOrderController
    participant Fac as OrderFacade
    participant OSvc as OrderService
    participant ORepo as OrderRepository

    A->>Ctrl: GET /api-admin/v1/orders?status=&userId=&from=&to=&page=&size=
    Ctrl->>Fac: searchAllOrders(filter, pageable)
    Fac->>OSvc: searchAll(filter, pageable)
    OSvc->>ORepo: searchWithFilter(filter, pageable)
    Note over ORepo: 인덱스(status, createdAt),\n(userId, createdAt) 활용
    ORepo-->>OSvc: Page<OrderModel>
    OSvc-->>Fac: Page<OrderModel>
    Fac->>Fac: assemble AdminOrderInfo (userId 등 포함)
    Fac--)Audit: admin_order_viewed (async)
    Fac-->>Ctrl: Page<AdminOrderInfo>
    Ctrl-->>A: 200 OK + AdminOrderPageDto
```

---

## 부록: 다이어그램 ↔ 요구사항 매핑

| 다이어그램 | 요구사항 UC | 핵심 분기·메모 |
| --- | --- | --- |
| UC-01 | §6 UC-01 | 인증 헤더가 현재 비번 확인 매개체 |
| UC-02 | §6 UC-02 | soft delete cascade 검사 |
| UC-03 | §6 UC-03 | likedByMe IN 쿼리 일괄 조회 (N+1 회피) |
| UC-04 | §6 UC-04 | 인증 시에만 like 조회 |
| UC-05 | §6 UC-05 | reactivate 분기, likesCount 원자 UPDATE |
| UC-06 | §6 UC-06 | softDelete 분기, likesCount 음수 방지 |
| UC-07 | §6 UC-07 | 좋아요 시점순, cascade 자동 제외 |
| UC-08a | §6 UC-08 정상 흐름 | PG는 트랜잭션 커밋 후 |
| UC-08b | §6 UC-08 결제 실패 | 보상 트랜잭션 |
| UC-08c | §6 UC-08 응답 지연 | PENDING + reconcile job |
| UC-09 | §6 UC-09 | 본인 외 = 404 |
| UC-10 | §6 UC-10 | Brand→Product→Like cascade |
| UC-11 | §6 UC-11 | Product→Like cascade |
| UC-12 | §6 UC-12 | 본인 격리 미적용, audit 필수 |
