# 시퀀스 다이어그램

## 1. 주문 생성 (POST /api/v1/orders)

**목적**: 여러 상품을 한 번에 주문할 때 재고 예약 → PG 초기화까지의 책임 경계와 호출 순서를 검증한다.
**검증 포인트**: 모든 상품 재고 예약이 원자적으로 처리되는 것(하나라도 부족하면 전체 롤백), 재고 검증 실패 시 PG를 호출하지 않는 것.

```mermaid
sequenceDiagram
    actor User
    participant API
    participant OrderFacade
    participant StockService
    participant OrderService
    participant PGClient
    participant ExternalPG

    User->>API: POST /api/v1/orders (items[], 배송지)
    API->>OrderFacade: createOrder(userId, items, 배송지)

    Note over OrderFacade, StockService: 재고 예약 단일 트랜잭션 시작
    loop 주문 상품(item)별
        OrderFacade->>StockService: reserveStock(productId, qty)
        Note over StockService: 상품당 재고 1건 (옵션 없음)
        StockService->>StockService: available = total_quantity - reserved_quantity 검증

        alt 재고 부족
            StockService-->>OrderFacade: CoreException(OUT_OF_STOCK)
            Note over OrderFacade, StockService: 트랜잭션 롤백<br/>(이미 예약한 항목 모두 해제)
            OrderFacade-->>API: 409 Conflict
            API-->>User: 재고 부족 (주문 전체 거부)
        end

        StockService->>StockService: reserved_quantity += qty
    end
    Note over OrderFacade, StockService: 재고 예약 트랜잭션 커밋

    OrderFacade->>OrderService: createOrder(userId, PENDING, items, pgAmount, 배송지)
    OrderService-->>OrderFacade: orderId (order_items 함께 INSERT)

    OrderFacade->>PGClient: initializePayment(orderId, pgAmount)
    PGClient->>ExternalPG: 결제 초기화 요청
    ExternalPG-->>PGClient: pgTransactionId, pgRedirectUrl
    PGClient-->>OrderFacade: pgRedirectUrl

    OrderFacade-->>API: { orderId, pgRedirectUrl }
    API-->>User: 200 OK { orderId, pgRedirectUrl }
```

**읽는 포인트**
- 주문에 포함된 모든 상품 재고 예약이 **단일 트랜잭션**으로 묶인다. 하나라도 부족하면 이미 예약한 항목까지 전부 롤백하고 주문 전체를 거부한다. (부분 주문 없음)
- 예약은 원자적 조건부 UPDATE(`SET reserved += qty WHERE (total - reserved) >= qty`, affected rows=0이면 부족)로 동시성하의 오버셀을 방지한다.
- 재고가 모두 확보된 뒤에야 PG 초기화로 넘어간다. 재고 실패 시 PG를 호출하지 않는다.
- `pgAmount`는 주문 라인들의 (단가 × 수량) 합계. 포인트가 없으므로 주문 총액 = PG 결제 금액.

---

## 2. 결제 확정 (POST /api/v1/payments/confirm)

**목적**: PG 콜백 수신 후 재고 확정 / 주문 상태 전이의 트랜잭션 경계와 실패 분기를 검증한다.
**검증 포인트**: 재고 확정과 주문 확정이 단일 트랜잭션에 묶이는 것, 실패 시 모든 상품 예약이 즉시 해제되는 것.

```mermaid
sequenceDiagram
    participant ExternalPG
    participant API
    participant PaymentFacade
    participant PGClient
    participant StockService
    participant OrderService

    ExternalPG->>API: POST /api/v1/payments/confirm (pgTransactionId, orderId, status, signature)
    API->>PaymentFacade: confirmPayment(payload, signature)

    PaymentFacade->>PaymentFacade: 콜백 서명 검증 (공유 시크릿/HMAC)
    alt 서명 불일치 (위조 콜백)
        PaymentFacade-->>API: CoreException(UNAUTHORIZED)
        API-->>ExternalPG: 401 Unauthorized
    end

    PaymentFacade->>PGClient: verifyPayment(pgTransactionId)
    PGClient->>ExternalPG: 결제 검증 요청
    ExternalPG-->>PGClient: 결제 금액(approvedAmount), 상태
    PGClient-->>PaymentFacade: verified (approvedAmount, status)

    PaymentFacade->>OrderService: findOrder(orderId)
    OrderService-->>PaymentFacade: Order (status, pg_amount)

    alt 주문이 PENDING 아님 (이미 처리된 콜백)
        Note over PaymentFacade: 멱등 처리 — 재고/상태 변경 없이 종료
        PaymentFacade-->>API: ok
        API-->>ExternalPG: 200 OK
    end

    alt 결제 성공 AND approvedAmount == orders.pg_amount
        Note over PaymentFacade, OrderService: 단일 트랜잭션 시작
        loop 주문 상품별
            PaymentFacade->>StockService: confirmStock(productId, qty)
            Note over StockService: total_quantity -= qty<br/>reserved_quantity -= qty
        end

        PaymentFacade->>OrderService: confirmOrder(orderId, CONFIRMED)
        Note over PaymentFacade, OrderService: 트랜잭션 커밋

        PaymentFacade-->>API: ok
        API-->>ExternalPG: 200 OK

    else 결제 실패 OR 금액 불일치
        loop 주문 상품별
            PaymentFacade->>StockService: releaseReservation(productId, qty)
            Note over StockService: reserved_quantity -= qty<br/>(total_quantity 유지)
        end

        PaymentFacade->>OrderService: updateStatus(orderId, FAILED)
        PaymentFacade-->>API: ok
        API-->>ExternalPG: 200 OK
    end
```

**읽는 포인트**
- **콜백 서명 검증**: 외부에서 호출되는 웹훅이므로, 진짜 PG가 보낸 콜백인지 공유 시크릿 기반 서명(HMAC 등)으로 먼저 검증한다. 위조 콜백은 PG 재조회 전에 차단된다. (PG 재조회 `verifyPayment`가 2차 방어)
- **멱등 처리**: PG가 콜백을 중복 전송해도 주문이 이미 PENDING이 아니면 재고/상태를 건드리지 않고 종료한다. 재고 이중 차감을 방지한다.
- PG 승인 금액(`approvedAmount`)이 우리 `orders.pg_amount`와 일치하는지 검증한 뒤에야 확정으로 넘어간다. 금액 위·변조나 계산 버그를 결제 확정 직전에 차단한다.
- 금액 불일치는 결제 실패와 동일하게 처리한다(재고 예약 해제 + 주문 FAILED). "5만원 주문이 5천원에 확정"되는 사고를 막는다.
- 결제 성공 시 모든 상품의 재고 확정 → 주문 확정이 단일 트랜잭션으로 묶인다. 하나라도 실패하면 전체 롤백된다.
- 포인트 차감 단계가 제거되어 트랜잭션이 재고 확정 + 주문 확정 2단계로 단순해졌다.
- 결제 실패 시 모든 상품의 reserved_quantity만 되돌린다. total_quantity는 건드리지 않는다.
- **쿠폰 복구**: 결제 실패/금액 불일치로 주문을 FAILED 처리할 때, 해당 주문에 적용된 쿠폰이 있으면 `releaseCoupon(orderId)`로 `USED → AVAILABLE` 복구한다. (재고 예약 해제와 동일 트랜잭션·동일 시점)

---

## 3. 좋아요 등록 / 취소 (POST · DELETE /api/v1/products/{productId}/likes)

**목적**: 등록(POST)·취소(DELETE)가 분리된 엔드포인트에서 각각 멱등하게 동작하는 흐름을 명시한다.
**검증 포인트**: 이미 등록/취소된 상태에서 같은 요청을 반복해도 결과가 수렴하는 것, 소프트 딜리트 상품엔 등록이 거부되는 것.

```mermaid
sequenceDiagram
    actor User
    participant API
    participant LikeFacade
    participant ProductService
    participant LikeRepository

    Note over User, LikeRepository: 등록 (POST)
    User->>API: POST /api/v1/products/{productId}/likes
    API->>LikeFacade: like(userId, productId)
    LikeFacade->>ProductService: 활성 상품 검증 (deleted_at IS NULL)
    alt 상품 없음/삭제됨
        ProductService-->>LikeFacade: CoreException(NOT_FOUND)
        LikeFacade-->>API: 404 Not Found
    end
    LikeFacade->>LikeRepository: findByUserIdAndProductId(userId, productId)
    alt 이미 등록됨
        LikeRepository-->>LikeFacade: Like (그대로 유지, 멱등)
    else 미등록
        LikeFacade->>LikeRepository: save(Like)
    end
    API-->>User: 200 OK

    Note over User, LikeRepository: 취소 (DELETE)
    User->>API: DELETE /api/v1/products/{productId}/likes
    API->>LikeFacade: unlike(userId, productId)
    LikeFacade->>LikeRepository: deleteByUserIdAndProductId(userId, productId)
    Note over LikeRepository: 없으면 no-op (멱등)
    API-->>User: 200 OK
```

**읽는 포인트**
- 등록과 취소가 별도 엔드포인트(POST/DELETE)로 분리된다. 각각 멱등 — 이미 등록이면 등록 유지, 이미 취소면 no-op.
- `likes` 테이블의 `(user_id, product_id)` 유니크 제약이 동시 요청 시 중복 등록을 DB 레벨에서 방어한다.
- 등록 시 소프트 딜리트된 상품인지 검증해 거부(404)한다. 취소는 존재하지 않아도 무해하게 수렴한다.
- 비로그인 사용자는 API 인증 단계에서 401로 차단된다.

---

## 4. PENDING 주문 만료 처리 (스케줄러)

**목적**: PG 콜백 미수신으로 PENDING 상태에 머문 주문의 재고 예약을 자동 해제하는 흐름을 정의한다.
**검증 포인트**: 스케줄러가 만료 기준을 어떻게 판단하고, 주문에 포함된 모든 상품의 예약을 해제하는지.

```mermaid
sequenceDiagram
    participant Scheduler
    participant OrderService
    participant StockService

    Scheduler->>OrderService: findExpiredPendingOrders(expiredBefore)
    OrderService-->>Scheduler: List~Order~ (PENDING 상태 + 생성시간 초과)

    loop 만료 주문별
        loop 주문 상품별
            Scheduler->>StockService: releaseReservation(productId, qty)
            Note over StockService: reserved_quantity -= qty
        end
        Scheduler->>OrderService: updateStatus(orderId, FAILED)
    end
```

**읽는 포인트**
- 스케줄러는 조회와 위임만 한다. 실제 재고 해제는 StockService, 상태 전이는 OrderService가 책임진다.
- 복수 상품 주문이므로 주문 1건당 포함된 모든 상품 라인의 예약을 해제한다.
- 만료 기준은 주문 생성 시간 기준 **15분**으로 한다. (생성 후 15분 초과 PENDING → 만료)
- 만료 주문에 적용된 쿠폰이 있으면 재고 예약 해제와 함께 `releaseCoupon(orderId)`로 `USED → AVAILABLE` 복구한다.

---

## 5. 주문 취소 (DELETE /api/v1/orders/{orderId})

**목적**: CONFIRMED 상태 주문 취소 시 PG 결제 취소 → 재고 복구의 처리 순서와 각 서비스의 책임을 검증한다.
**검증 포인트**: PG 취소와 재고 복구가 단일 트랜잭션으로 묶이는 것, 외부 PG 취소 실패 시 전체 롤백되는 것.

```mermaid
sequenceDiagram
    actor User
    participant API
    participant OrderFacade
    participant OrderService
    participant PGClient
    participant ExternalPG
    participant StockService

    User->>API: DELETE /api/v1/orders/{orderId}
    API->>OrderFacade: cancelOrder(userId, orderId)

    OrderFacade->>OrderService: findOrder(orderId)
    OrderService-->>OrderFacade: Order

    alt CONFIRMED 상태가 아님
        OrderFacade-->>API: CoreException(INVALID_ORDER_STATUS)
        API-->>User: 400 Bad Request
    end

    Note over OrderFacade, StockService: 단일 트랜잭션 시작

    OrderFacade->>PGClient: cancelPayment(pgTransactionId)
    PGClient->>ExternalPG: 결제 취소 요청
    ExternalPG-->>PGClient: 취소 완료
    PGClient-->>OrderFacade: ok

    loop 주문 상품별
        OrderFacade->>StockService: restoreStock(productId, qty)
        Note over StockService: total_quantity += qty
    end

    OrderFacade->>OrderService: updateStatus(orderId, CANCELLED)

    Note over OrderFacade, StockService: 트랜잭션 커밋

    OrderFacade-->>API: ok
    API-->>User: 200 OK
```

**읽는 포인트**
- PG 취소를 가장 먼저 호출한다. 외부 시스템 취소가 실패하면 재고를 건드리지 않고 전체 롤백한다.
- 포인트 환불 단계가 제거되어 취소 트랜잭션은 PG 취소 + 재고 복구 + 상태 전이로 단순해졌다.
- 복수 상품 주문이므로 모든 상품 라인의 재고를 복구한다.
- 취소 가능 상태는 CONFIRMED만 해당한다. PENDING/FAILED/CANCELLED 주문은 취소 요청 시 400을 반환한다.

---

## 6. 관리자 — 브랜드 삭제 (DELETE /api-admin/v1/brands/{brandId})

**목적**: 브랜드 삭제 시 소속 상품의 소프트 딜리트(즉시 노출 차단)와, 좋아요의 비동기 물리 정리로 분리되는 흐름을 검증한다.
**검증 포인트**: `X-Loopers-Ldap` 헤더로 식별돼야만 진입하는 것, 동기 트랜잭션은 brands/products의 deleted_at만 기록하는 것, 고용량 좋아요 삭제는 비동기 배치로 분리되는 것.

```mermaid
sequenceDiagram
    actor Admin
    participant API
    participant AdminInterceptor
    participant BrandFacade
    participant BrandService
    participant ProductService
    participant LikeCleanupBatch

    Admin->>API: DELETE /api-admin/v1/brands/{brandId}<br/>(X-Loopers-Ldap: loopers.admin)
    API->>AdminInterceptor: 헤더 값 검증 (== loopers.admin)

    alt 헤더 누락/불일치
        AdminInterceptor-->>API: 식별 거부
        API-->>Admin: 401 Unauthorized
    end

    API->>BrandFacade: deleteBrand(brandId)

    BrandFacade->>BrandService: findBrand(brandId)
    BrandService-->>BrandFacade: Brand

    alt 브랜드 없음
        BrandFacade-->>API: CoreException(BRAND_NOT_FOUND)
        API-->>Admin: 404 Not Found
    end

    Note over BrandFacade, ProductService: 단일 트랜잭션 시작 (즉시 노출 차단)
    BrandFacade->>ProductService: softDeleteByBrandId(brandId)
    Note over ProductService: 소속 상품 deleted_at 기록<br/>likes_purged=false<br/>(재고는 상품 통해 조회 차단, 손대지 않음)
    BrandFacade->>BrandService: softDelete(brandId)
    Note over BrandService: brand deleted_at 기록
    Note over BrandFacade, ProductService: 트랜잭션 커밋

    BrandFacade-->>API: ok
    API-->>Admin: 200 OK

    Note over LikeCleanupBatch, ProductService: --- 이후 비동기 배치 (별도 트랜잭션) ---
    LikeCleanupBatch->>ProductService: findDeletedNotPurged()
    Note over ProductService: deleted_at IS NOT NULL<br/>AND likes_purged = false
    loop 대상 상품별
        loop 남은 좋아요 (청크)
            LikeCleanupBatch->>LikeCleanupBatch: DELETE FROM likes WHERE product_id = ? LIMIT N
        end
        LikeCleanupBatch->>ProductService: markLikesPurged(productId)
    end
```

**읽는 포인트**
- 관리자 식별 게이트(Interceptor)가 비즈니스 로직 진입 전에 `X-Loopers-Ldap` 헤더 값을 확인한다. 인증/인가는 구현 범위가 아니며 헤더 값 일치 여부만 본다. 불일치 시 Facade를 호출하지 않는다.
- 동기 트랜잭션은 brands/products의 `deleted_at`만 기록한다(저용량, 즉시 노출 차단). 과거 주문의 order_items는 스냅샷이라 영향받지 않는다.
- 재고는 상품을 통해서만 도달하므로 손대지 않는다. 상품 복구 시 자동으로 함께 복구된다.
- 좋아요는 고용량(수천만~수억)이고 복구 가치가 없으므로, 동기 삭제 대신 **비동기 배치가 청크 단위로 물리 삭제**한다. `likes_purged` 플래그로 정리 완료를 마킹해 반복 스캔을 막는다.

---

## 7. 관리자 — 상품 등록 (POST /api-admin/v1/products)

**목적**: 상품 + 재고를 한 번에 등록하는 흐름과 브랜드 사전 등록 검증을 확인한다.
**검증 포인트**: 사전 등록되지 않은 브랜드면 거부하는 것, 상품·재고가 단일 트랜잭션으로 함께 생성되는 것.

```mermaid
sequenceDiagram
    actor Admin
    participant API
    participant AdminInterceptor
    participant ProductFacade
    participant BrandService
    participant ProductService
    participant StockService

    Admin->>API: POST /api-admin/v1/products (brandId, name, price, quantity)<br/>(X-Loopers-Ldap: loopers.admin)
    API->>AdminInterceptor: 헤더 값 검증 (== loopers.admin)

    alt 헤더 누락/불일치
        AdminInterceptor-->>API: 식별 거부
        API-->>Admin: 401 Unauthorized
    end

    API->>ProductFacade: createProduct(brandId, name, price, quantity)

    ProductFacade->>BrandService: findBrand(brandId)
    alt 브랜드 미등록
        BrandService-->>ProductFacade: CoreException(BRAND_NOT_FOUND)
        ProductFacade-->>API: 400 Bad Request
        API-->>Admin: 브랜드 사전 등록 필요
    end
    BrandService-->>ProductFacade: Brand

    Note over ProductFacade, StockService: 단일 트랜잭션 시작
    ProductFacade->>ProductService: createProduct(brandId, name, price)
    ProductService-->>ProductFacade: productId

    ProductFacade->>StockService: createStock(productId, quantity)
    Note over StockService: 상품당 재고 1건 생성 (1:1)
    Note over ProductFacade, StockService: 트랜잭션 커밋

    ProductFacade-->>API: { productId }
    API-->>Admin: 201 Created
```

**읽는 포인트**
- 브랜드 사전 등록 여부를 가장 먼저 검증한다. 미등록이면 상품·재고를 만들지 않고 거부한다.
- 상품·재고가 단일 트랜잭션으로 함께 생성되어 재고가 없는 불완전 상태가 생기지 않는다.
- 옵션 개념이 없으므로 상품 1개당 재고(stocks) 1건이 직접 매핑된다.

**상품 수정(PUT) 시 재고 가드 (별도 시퀀스 생략, 정책 노트)**
- 관리자는 `total_quantity`만 수정하며 `reserved_quantity`는 건드리지 않는다.
- 재고 수정은 조건부 UPDATE로 처리: `UPDATE stocks SET total_quantity = :newTotal WHERE id = :id AND :newTotal >= reserved_quantity`.
- affected rows = 0이면 새 보유량이 현재 예약량보다 작은 것이므로 **409로 거부**한다. (불변식 `total >= reserved` 보장)
- 브랜드(brand_id)는 수정 대상에서 제외한다. (변경 불가)

---

## 8. 쿠폰 발급 (POST /api/v1/coupons/{couponId}/issue)

**목적**: 쿠폰 템플릿을 유저에게 발급할 때 중복 발급/만료 검증 흐름을 검증한다.
**검증 포인트**: 동일 템플릿 중복 발급이 `(user_id, template_id)` 유니크로 차단되는 것, 발급 시점에 만료된 템플릿은 거부되는 것.

```mermaid
sequenceDiagram
    actor User
    participant API
    participant CouponFacade
    participant CouponTemplateService
    participant UserCouponService

    User->>API: POST /api/v1/coupons/{couponId}/issue
    API->>CouponFacade: issue(userId, templateId)

    CouponFacade->>CouponTemplateService: findActive(templateId)
    alt 템플릿 없음/삭제됨
        CouponTemplateService-->>CouponFacade: CoreException(COUPON_NOT_FOUND)
        CouponFacade-->>API: 404 Not Found
    end
    CouponTemplateService-->>CouponFacade: template (type, value, minOrderAmount, expiredAt)

    alt 템플릿 이미 만료 (expiredAt < now)
        CouponFacade-->>API: CoreException(COUPON_EXPIRED)
        API-->>User: 409 Conflict
    end

    CouponFacade->>UserCouponService: issue(userId, template)
    Note over UserCouponService: 발급 시점 스냅샷(type/value/minOrderAmount/expiredAt) 저장<br/>status = AVAILABLE
    alt 이미 발급받음 (user_id, template_id 유니크 위반)
        UserCouponService-->>CouponFacade: CoreException(COUPON_ALREADY_ISSUED)
        CouponFacade-->>API: 409 Conflict
    end
    UserCouponService-->>CouponFacade: userCoupon
    CouponFacade-->>API: { userCouponId, status }
    API-->>User: 201 Created
```

**읽는 포인트**
- `{couponId}`는 **템플릿 ID**다. 발급 결과로 `user_coupons` 한 건이 생성된다.
- 동일 템플릿 중복 발급은 `(user_id, template_id)` 유니크 제약이 DB 레벨에서 막는다. 위반 시 409.
- 발급 쿠폰은 템플릿의 할인 규칙을 **발급 시점 스냅샷**으로 보관한다. 이후 템플릿이 수정/삭제돼도 이미 발급된 쿠폰엔 영향이 없다.

---

## 9. 주문 생성 시 쿠폰 적용 (POST /api/v1/orders, couponId 포함)

**목적**: 주문 생성 흐름(섹션 1)에 쿠폰 검증·할인·선점(USED)이 끼어드는 위치와 실패 시 롤백을 검증한다.
**검증 포인트**: 쿠폰 사용이 조건부 UPDATE로 재사용을 막는 것, 재고 예약과 쿠폰 사용이 하나의 트랜잭션으로 묶여 한쪽 실패 시 함께 롤백되는 것.

```mermaid
sequenceDiagram
    actor User
    participant API
    participant OrderFacade
    participant CouponService
    participant StockService
    participant OrderService
    participant PGClient

    User->>API: POST /api/v1/orders (items[], couponId?, 배송지)
    API->>OrderFacade: createOrder(userId, items, couponId, 배송지)

    Note over OrderFacade, OrderService: 주문 생성 단일 트랜잭션 시작
    loop 주문 상품(item)별
        OrderFacade->>StockService: reserveStock(productId, qty)
        alt 재고 부족
            StockService-->>OrderFacade: CoreException(OUT_OF_STOCK)
            Note over OrderFacade: 트랜잭션 롤백 (재고 예약 + 쿠폰 사용 모두 해제)
            OrderFacade-->>API: 409 Conflict
        end
    end

    OrderFacade->>OrderFacade: originalAmount = Σ(price × qty)

    alt couponId 있음
        OrderFacade->>CouponService: findOwned(couponId, userId)
        alt 쿠폰 없음 / 타 유저 소유
            CouponService-->>OrderFacade: CoreException(COUPON_NOT_FOUND)
            OrderFacade-->>API: 404 (트랜잭션 롤백)
        end
        CouponService->>CouponService: 만료/최소주문금액 검증 (expiredAt, minOrderAmount)
        alt 만료 / 최소금액 미달
            CouponService-->>OrderFacade: CoreException(COUPON_EXPIRED / COUPON_MIN_ORDER_NOT_MET)
            OrderFacade-->>API: 409 (트랜잭션 롤백)
        end
        OrderFacade->>CouponService: use(couponId, orderId)
        Note over CouponService: UPDATE user_coupons SET status=USED, order_id=?<br/>WHERE id=? AND status=AVAILABLE
        alt affected rows = 0 (이미 사용됨, 동시 요청)
            CouponService-->>OrderFacade: CoreException(COUPON_ALREADY_USED)
            OrderFacade-->>API: 409 (트랜잭션 롤백)
        end
        CouponService->>CouponService: discount = calculate(type, value, originalAmount) (RATE=버림, cap=원금)
        CouponService-->>OrderFacade: discountAmount
    end

    OrderFacade->>OrderFacade: pgAmount = originalAmount - discountAmount
    OrderFacade->>OrderService: createOrder(userId, PENDING, items, originalAmount, discountAmount, pgAmount, couponId, 배송지)
    OrderService-->>OrderFacade: orderId
    Note over OrderFacade, OrderService: 트랜잭션 커밋

    OrderFacade->>PGClient: initializePayment(orderId, pgAmount)
    PGClient-->>OrderFacade: pgRedirectUrl
    OrderFacade-->>API: { orderId, pgRedirectUrl }
    API-->>User: 200 OK
```

**읽는 포인트**
- 쿠폰 사용은 **조건부 UPDATE**(`status=USED WHERE status=AVAILABLE`)로 처리한다. 동시 요청으로 같은 쿠폰을 두 주문에 쓰려 하면 한쪽은 affected rows=0 → 409로 거부된다. (Lost Update / 이중 사용 방지)
- 재고 예약과 쿠폰 사용이 **하나의 트랜잭션**에 묶인다. 재고 부족·쿠폰 실패 어느 쪽이든 둘 다 롤백돼 정합성이 유지된다.
- 할인액은 원금 기준으로 계산하며, 정률은 버림·할인액 상한은 원금이다. 최종 `pgAmount = originalAmount - discountAmount`가 PG 결제 금액이 된다.
- 결제 실패 / 금액 불일치(섹션 2) 또는 PENDING 만료(섹션 4) 시, 재고 예약 해제와 함께 쿠폰도 `AVAILABLE`로 복구한다(`releaseCoupon(orderId)`).
