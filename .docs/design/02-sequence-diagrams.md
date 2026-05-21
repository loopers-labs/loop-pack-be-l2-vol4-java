# Loopers 이커머스 — 시퀀스 다이어그램

> **이 문서는 다른 팀원에게 설명이 필요한 복잡한 흐름만 다룬다.**  
> 단순 CRUD·단건 조회·어드민 운영 API는 의도적으로 제외했다.  
> 핵심은 **멱등 동작, 재고 차감, 외부 시스템 연동, 보상 트랜잭션** 5가지 흐름이다.

---

## 트랜잭션 경계 정책

```
interfaces (Controller)
   ↓ 호출
application (Facade)        ← 트랜잭션 없음. Service 조율만 담당
   ↓ 조율
domain (Service)            ← @Transactional. DB 변경의 원자성 보장 단위
   ↓ 사용
domain (Repository interface) ← 인터페이스
   ↑ 구현
infrastructure (RepositoryImpl) ← JPA 구현체
```

- **Facade**: 트랜잭션 없음. 여러 Service를 순서대로 호출
- **Service**: `@Transactional` 적용 — 단일 Service 메서드 내 DB 변경은 원자적
- **외부 시스템 호출(PG)**: 트랜잭션 외부에서 수행. 응답 수신 후 별도 짧은 트랜잭션으로 상태 업데이트

> 트랜잭션 경계를 Service 단위에 둔 이유: 외부 I/O(PG 호출)를 트랜잭션에 포함시키면 long-running transaction이 발생해 DB connection pool이 고갈된다.  
> 다만 Facade에 트랜잭션이 없으므로 **여러 Service 호출 간 원자성은 자체적으로 보장되지 않는다** — 이 한계는 `📡 나아가며`에서 다룬다.

---

## 다이어그램 표기 약속

**생략 대상** — 모든 시퀀스에 반복되는 보일러플레이트는 다이어그램에서 제거한다:

| 생략 항목                                                        | 이유                                                            |
|------------------------------------------------------------------|-----------------------------------------------------------------|
| `AuthFilter` 흐름 (`findByLoginId` → 비밀번호 검증 → `401` 분기) | 모든 인증 API에 동일. "(인증 통과 후, userId)" 노트로 대체      |
| `Controller` 단순 위임                                           | 위임 자체에 로직이 없을 때 — 단 응답 변환 책임이 있을 때는 유지 |

**유지 대상** — 책임 표현에 필요한 요소:

| 유지 항목                                           | 이유                                   |
|-----------------------------------------------------|----------------------------------------|
| `Controller → Facade → Service → Repository` 레이어 | 책임 분배 명확화                       |
| 응답 흐름 (`Service → Facade → Controller → User`)  | 정보 변환 책임 (Model → Info DTO) 표현 |
| 도메인 분기 (멱등 no-op, 재고 부족, PG 실패 등)     | 핵심 비즈니스 로직                     |

**색상 범례** — `rect` 박스 색상의 의미:

| 색상                          | 의미                                     | 사용 위치                   |
|-------------------------------|------------------------------------------|-----------------------------|
| 🔒 보라 (`rgb 245, 243, 255`) | Service 트랜잭션 (`@Transactional` 단위) | 모든 시퀀스                 |
| 🌐 빨강 (`rgb 254, 226, 226`) | 외부 시스템 호출 — **트랜잭션 외부**     | 결제 (PG 호출)              |
| 🔥 주황 (`rgb 254, 215, 170`) | 보상 트랜잭션 (실패 복구)                | 결제 (PG 실패 시 재고 복구) |
| 파랑 (`rgb 224, 242, 254`)    | 주문 Step 1 — 상품 확인                  | 주문 생성                   |
| 노랑 (`rgb 254, 243, 199`)    | 주문 Step 2 — 재고 차감                  | 주문 생성                   |
| 초록 (`rgb 220, 252, 231`)    | 주문 Step 3 / 결제 성공 — 상태 확정      | 주문 생성, 결제             |

**❗ 트랜잭션 경계 Note** — Service 호출 사이에 표시되는 위험 구간. Facade에 트랜잭션이 없어 두 Service 호출 간 원자성이 보장되지 않는 지점.

---

## 시퀀스 목록 (5개 핵심 흐름)

| #   | 시퀀스                  | 왜 복잡한가 (강조 포인트)                                 |
|-----|-------------------------|-----------------------------------------------------------|
| 1   | 상품 목록 조회          | 필터·정렬·페이징 — 인덱스 설계가 성능을 결정              |
| 2   | 좋아요 등록 (완전 멱등) | 중복 시 `likeCount` 증분 없는 no-op — 상태 표현 도메인    |
| 3   | 좋아요 취소 (완전 멱등) | 미존재 시 no-op — REST PUT 시맨틱                         |
| 4   | 주문 생성               | 다중 항목 재고 차감 + 스냅샷 + 한 건 실패 시 전체 실패    |
| 5   | 결제 요청               | 외부 PG 연동 + 보상 트랜잭션(재고 복구) + Order 상태 전이 |

---

## 1. 상품 목록 조회

`GET /api/v1/products` — 인증 불필요, 필터·정렬·페이징

### 강조 포인트

- **필터 조합**: `category` × `level` × `brandId` — 복합 인덱스 `(category, level, status)` 활용
- **정렬**: `latest`(기본 `createdAt DESC`) / `price_asc` / `likes_desc`
- **`likes_desc` 정렬은 비정규화 `like_count` 컬럼 인덱스**가 필수 — COUNT 집계 회피
- **대고객 노출 제한**: `stock`·`isbn`·`status` 컬럼은 응답에서 제외 (Facade DTO 변환 책임)

```mermaid
sequenceDiagram
    actor Client
    participant PC as ProductController
    participant PF as ProductFacade
    participant PS as ProductService
    participant PR as ProductRepository

    Client->>PC: GET /api/v1/products<br/>?category=BACKEND&level=INTERMEDIATE<br/>&brandId=1&sort=latest&page=0&size=20
    activate PC

    PC->>PF: findProducts(category?, level?, brandId?, sort, pageable)
    activate PF
    PF->>PS: findProducts(category?, level?, brandId?, sort, pageable)

    rect rgb(245, 243, 255)
        Note over PS,PR: 🔒 @Transactional(readOnly=true) — ProductService.findProducts
        activate PS
        Note over PS: sort 기본값: latest<br/>선택: price_asc / likes_desc
        PS->>PR: findAllByFilter(category?, level?, brandId?, sort, pageable)
        activate PR
        PR-->>PS: Page<ProductModel>
        deactivate PR
        PS-->>PF: Page<ProductModel>
        deactivate PS
    end

    Note over PF: 대고객 DTO 변환<br/>stock · isbn · status 제외

    PF-->>PC: Page<ProductInfo>
    deactivate PF
    PC-->>Client: 200 OK {content[], totalElements, totalPages}
    deactivate PC
```

---

## 2. 좋아요 등록 (완전 멱등)

`POST /api/v1/products/{productId}/likes` — 유저 인증

### 강조 포인트

- **완전 멱등 정책**: 좋아요는 상태 표현(Binary State Toggle) → REST PUT 시맨틱
  - 신규 등록 → `201 Created` + `likeCount + 1`
  - **중복 시 → `200 OK` + likeCount 증분 없음 (no-op)** — 모바일 재시도/네트워크 재전송 강건성
- **트랜잭션 경계**: Facade는 트랜잭션 없음. `LikeService.createLike`, `ProductService.incrementLikeCount` 각각 별도 Service 트랜잭션
- **잠재 리스크**: 두 Service 호출 사이에 실패 발생 시 Like는 저장됐는데 `likeCount`는 증가 안 한 불일치 가능 — `📡 나아가며` §일관성에서 다룸
- **DB 안전망**: 복합 PK `(user_id, product_id)`가 동시 INSERT race condition을 차단

```mermaid
sequenceDiagram
    actor User
    participant LC as LikeController
    participant LF as LikeFacade
    participant LS as LikeService
    participant PS as ProductService
    participant LR as LikeRepository
    participant PR as ProductRepository

    User->>LC: POST /api/v1/products/{productId}/likes<br/>(인증 통과 후, userId)
    activate LC

    LC->>LF: addLike(userId, productId)
    activate LF

    rect rgb(245, 243, 255)
        Note over PS,PR: 🔒 @Transactional(readOnly=true) — ProductService.getProductModel
        LF->>PS: getProductModel(productId)
        activate PS
        PS->>PR: findById(productId)
        activate PR
        alt 상품 미존재
            PR-->>PS: empty
            PS-->>LF: CoreException (ProductNotFound)
            LF-->>LC: 404 Not Found
            LC-->>User: 404 Not Found
        end
        PR-->>PS: ProductModel
        deactivate PR
        PS-->>LF: ProductModel
        deactivate PS
    end

    Note over LF,LS: [멱등성 정책] POST는 완전 멱등<br/>중복 시 likeCount 증분 없이 200 OK 반환 (no-op)
    rect rgb(245, 243, 255)
        Note over LS,LR: 🔒 @Transactional(readOnly=true) — LikeService.checkLikeExists
        LF->>LS: checkLikeExists(userId, productId)
        activate LS
        LS->>LR: existsByUserIdAndProductId(userId, productId)
        activate LR
        alt 이미 좋아요 등록됨 (멱등 no-op)
            LR-->>LS: true
            LS-->>LF: exists=true
            LF-->>LC: 200 OK (already liked)
            LC-->>User: 200 OK
        end
        LR-->>LS: false
        deactivate LR
        LS-->>LF: 신규
        deactivate LS
    end

    rect rgb(245, 243, 255)
        Note over LS,LR: 🔒 @Transactional — LikeService.createLike
        LF->>LS: createLike(userId, productId)
        activate LS
        LS->>LR: save(LikeModel {userId, productId, likedAt})
        activate LR
        LR-->>LS: LikeModel
        deactivate LR
        LS-->>LF: LikeModel
        deactivate LS
    end

    Note over LF: ❗ 트랜잭션 경계 — Like 저장과 likeCount 증분 사이<br/>이 사이에 실패 발생 시 불일치 가능 (📡 나아가며 §일관성)

    rect rgb(245, 243, 255)
        Note over PS,PR: 🔒 @Transactional — ProductService.incrementLikeCount
        LF->>PS: incrementLikeCount(productModel)
        activate PS
        Note over PS: productModel.incrementLikeCount() → likeCount + 1
        PS->>PR: save(ProductModel)
        activate PR
        PR-->>PS: ok
        deactivate PR
        PS-->>LF: ok
        deactivate PS
    end

    LF-->>LC: ok
    deactivate LF
    LC-->>User: 201 Created
    deactivate LC
```

---

## 3. 좋아요 취소 (완전 멱등)

`DELETE /api/v1/products/{productId}/likes` — 유저 인증

### 강조 포인트

- **완전 멱등 정책**: 자원 최종 상태(좋아요 없음)가 동일하면 동일 응답
  - 좋아요 있음 → `204 No Content` + `likeCount - 1`
  - **좋아요 없음 → `204 No Content` + likeCount 감소 없음 (no-op)** — DELETE 멱등성 보장
- **최솟값 0 보호**: `likeCount`가 0 미만으로 떨어지지 않도록 도메인 모델이 보장 (`productModel.decrementLikeCount()`)
- **트랜잭션 경계**: 등록과 동일하게 두 Service에 분리됨

```mermaid
sequenceDiagram
    actor User
    participant LC as LikeController
    participant LF as LikeFacade
    participant LS as LikeService
    participant PS as ProductService
    participant LR as LikeRepository
    participant PR as ProductRepository

    User->>LC: DELETE /api/v1/products/{productId}/likes<br/>(인증 통과 후, userId)
    activate LC

    LC->>LF: removeLike(userId, productId)
    activate LF

    Note over LF,LS: [멱등성 정책] DELETE는 완전 멱등<br/>미존재 시 likeCount 감소 없이 204 No Content 반환 (no-op)
    rect rgb(245, 243, 255)
        Note over LS,LR: 🔒 @Transactional(readOnly=true) — LikeService.findLikeModel
        LF->>LS: findLikeModel(userId, productId)
        activate LS
        LS->>LR: findByUserIdAndProductId(userId, productId)
        activate LR
        alt 좋아요 없음 (멱등 no-op)
            LR-->>LS: empty
            LS-->>LF: Optional.empty()
            LF-->>LC: 204 No Content (already removed)
            LC-->>User: 204 No Content
        end
        LR-->>LS: LikeModel
        deactivate LR
        LS-->>LF: LikeModel
        deactivate LS
    end

    rect rgb(245, 243, 255)
        Note over LS,LR: 🔒 @Transactional — LikeService.deleteLike
        LF->>LS: deleteLike(likeModel)
        activate LS
        LS->>LR: delete(likeModel)
        activate LR
        LR-->>LS: ok
        deactivate LR
        LS-->>LF: ok
        deactivate LS
    end

    Note over LF: ❗ 트랜잭션 경계 — Like 삭제와 likeCount 감소 사이<br/>이 사이에 실패 발생 시 불일치 가능 (📡 나아가며 §일관성)

    rect rgb(245, 243, 255)
        Note over PS,PR: 🔒 @Transactional — ProductService.decrementLikeCount
        LF->>PS: decrementLikeCount(productId)
        activate PS
        PS->>PR: findById(productId)
        activate PR
        PR-->>PS: ProductModel
        deactivate PR
        Note over PS: productModel.decrementLikeCount()<br/>likeCount - 1 (최솟값 0)
        PS->>PR: save(ProductModel)
        activate PR
        PR-->>PS: ok
        deactivate PR
        PS-->>LF: ok
        deactivate PS
    end

    LF-->>LC: ok
    deactivate LF
    LC-->>User: 204 No Content
    deactivate LC
```

---

## 4. 주문 생성

`POST /api/v1/orders` — 유저 인증

### 강조 포인트

- **3단계 조율** (`OrderFacade`가 순차 진행):
  1. **Step 1 — 상품 존재 확인**: 모든 항목의 상품을 먼저 조회 (한 건이라도 없으면 전체 실패)
  2. **Step 2 — 재고 확인 및 차감**: `productModel.reduceStock(quantity)` — 한 건이라도 부족하면 전체 실패
  3. **Step 3 — 주문 생성 + 스냅샷 저장**: `productNameSnapshot`, `unitPriceSnapshot` 영구 보존
- **재고 동시성**: 동시 주문 시 재고 음수 방지 — 현재는 `UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?` 원자적 SQL. 분산 환경에서는 `📡 나아가며` §동시 주문 참조
- **트랜잭션 경계 한계**: Facade에 트랜잭션 없으므로 Step 2 도중 실패 시 일부 상품만 재고 차감된 상태로 끝날 수 있음 — Step별 Service 트랜잭션은 원자적이지만 Step 간은 아님

```mermaid
sequenceDiagram
    actor User
    participant OC as OrderController
    participant OF as OrderFacade
    participant PS as ProductService
    participant OS as OrderService
    participant PR as ProductRepository
    participant OR as OrderRepository

    User->>OC: POST /api/v1/orders<br/>(인증 통과 후, userId)<br/>{items: [{productId, quantity}, ...]}
    activate OC

    OC->>OF: placeOrder(userId, items)
    activate OF

    Note over OF: R1: items 1개 이상 검증
    alt items 비어있음
        OF-->>OC: CoreException (EmptyOrderItems)
        OC-->>User: 400 Bad Request
    end

    rect rgb(224, 242, 254)
        Note over OF,PR: Step 1 — 상품 존재 확인<br/>🔒 @Transactional(readOnly=true) per call — 항목마다 별도 트랜잭션
        loop 각 OrderItem
            OF->>PS: getProductModel(productId)
            activate PS
            PS->>PR: findById(productId)
            activate PR
            alt 상품 미존재
                PR-->>PS: empty
                PS-->>OF: CoreException (ProductNotFound)
                OF-->>OC: 404 Not Found
                OC-->>User: 404 Not Found
            end
            PR-->>PS: ProductModel {name, price, stock}
            PS-->>OF: ProductModel
        end
    end

    Note over OF: ❗ Step 1 ↔ Step 2 트랜잭션 경계 — 사이에 외부 변경이 끼어들 수 있음

    rect rgb(254, 243, 199)
        Note over OF,PR: Step 2 — 재고 확인 및 차감 (R2, R3)<br/>🔒 @Transactional per call — 한 건이라도 실패 시 이미 차감된 재고는 롤백 안 됨
        loop 각 ProductModel
            OF->>PS: validateAndReduceStock(productModel, quantity)
            activate PS
            alt 재고 부족
                PS-->>OF: CoreException (InsufficientStock)
                OF-->>OC: 400 Bad Request
                OC-->>User: 400 Bad Request
            end
            Note over PS: productModel.reduceStock(quantity)
            PS->>PR: save(ProductModel)
            activate PR
            PR-->>PS: ok
            deactivate PR
            PS-->>OF: ok
            deactivate PS
        end
    end

    Note over OF: ❗ Step 2 ↔ Step 3 트랜잭션 경계 — 재고 차감 후 주문 생성 실패 시 재고만 빠진 상태

    rect rgb(220, 252, 231)
        Note over OF,OR: Step 3 — 주문 생성 + 스냅샷 저장 (R4)<br/>🔒 @Transactional — OrderService.createOrder
        OF->>OS: createOrder(userId, itemsWithSnapshots)
        activate OS
        Note over OS: OrderModel.of(userId,<br/>  [{productId, productNameSnapshot,<br/>    unitPriceSnapshot, quantity}])<br/>totalAmount = Σ(unitPriceSnapshot × quantity)<br/>status = PENDING
        OS->>OR: save(OrderModel)
        activate OR
        OR-->>OS: OrderModel {orderId}
        deactivate OR
        OS-->>OF: OrderModel
        deactivate OS
    end

    OF-->>OC: OrderInfo {orderId}
    deactivate OF
    OC-->>User: 201 Created {orderId}
    deactivate OC
```

---

## 5. 결제 요청

`POST /api/v1/orders/{orderId}/payments` — 유저 인증

### 강조 포인트

- **외부 시스템(PG) 연동**: PG 호출은 `@Transactional` 외부에서 수행
  - DB 트랜잭션 안에서 외부 I/O를 하면 connection이 길게 잡혀 connection pool 고갈
  - PG 응답 수신 후 별도 짧은 트랜잭션으로 상태 업데이트
- **주문 상태 전이**:
  - 결제 성공 → `OrderStatus.CONFIRMED`
  - 결제 실패 → `OrderStatus.CANCELLED` + **재고 복구(보상 트랜잭션)**
- **보상 트랜잭션**: 결제 실패 시 차감했던 재고를 복구. 현재는 동일 요청 컨텍스트 내 처리 — 분산 환경에서는 Saga 패턴 검토 (`📡 나아가며` §일관성)
- **선제 검증**: PG 호출 전에 본인 주문/PENDING 상태/금액 일치 모두 확인 → 외부 호출 실패율을 낮춤
- **현재 미해결**:
  - PG 응답 타임아웃·네트워크 단절 시 처리 미정의 (성공/실패 이분법만)
  - 결제 멱등 키(`Idempotency-Key`) 없음 → 클라이언트 재시도 시 중복 결제 위험

```mermaid
sequenceDiagram
    actor Client
    participant PC as PaymentController
    participant PF as PaymentFacade
    participant OS as OrderService
    participant PayS as PaymentService
    participant PR as PaymentRepository
    participant PG as PaymentGateway
    participant ProdS as ProductService
    participant OR as OrderRepository
    participant ProdR as ProductRepository

    Client->>PC: POST /api/v1/orders/{orderId}/payments<br/>(인증 통과 후, userId)<br/>{paymentMethod, amount}
    activate PC

    PC->>PF: pay(userId, orderId, paymentMethod, amount)
    activate PF

    rect rgb(245, 243, 255)
        Note over OS,OR: 🔒 @Transactional(readOnly=true) — OrderService.getOrderModel
        PF->>OS: getOrderModel(orderId)
        activate OS
        OS->>OR: findById(orderId)
        activate OR
        alt 주문 미존재
            OR-->>OS: empty
            OS-->>PF: CoreException (OrderNotFound)
            PF-->>PC: 예외 전파
            PC-->>Client: 404 Not Found
        end
        OR-->>OS: OrderModel
        deactivate OR
        OS-->>PF: OrderModel
        deactivate OS
    end

    Note over PF: 선제 검증 (Facade 레벨, 트랜잭션 없음)
    alt 타인 주문 접근 (orderModel.userId ≠ userId)
        PF-->>PC: CoreException (Forbidden)
        PC-->>Client: 403 Forbidden
    else 주문 상태가 PENDING 아님
        PF-->>PC: CoreException (InvalidOrderStatus)
        PC-->>Client: 400 Bad Request
    else amount ≠ orderModel.totalAmount
        PF-->>PC: CoreException (AmountMismatch)
        PC-->>Client: 400 Bad Request
    end

    rect rgb(245, 243, 255)
        Note over PF,PR: 🔒 @Transactional — PaymentService.createPendingPayment<br/>(짧은 쓰기 트랜잭션, PG 호출 전에 PENDING 행 확보)
        PF->>PayS: createPendingPayment(orderId, paymentMethod, amount)
        activate PayS
        PayS->>PR: save — status=PENDING, pgTransactionId=NULL
        activate PR
        PR-->>PayS: PaymentModel (PENDING)
        deactivate PR
        PayS-->>PF: PaymentModel (PENDING)
        deactivate PayS
    end

    rect rgb(254, 226, 226)
        Note over PF,PG: 🌐 외부 시스템 연동 — ⚠️ 트랜잭션 외부에서 수행<br/>(DB connection 점유 방지, 헥사고날 포트 호출)
        PF->>PG: request(paymentMethod, amount)
        activate PG
        PG-->>PF: PaymentResult {success, pgTransactionId, failureReason}
        deactivate PG
    end

    alt PG 결제 실패
        rect rgb(254, 215, 170)
            Note over PF,ProdR: 보상 트랜잭션 — Payment FAILED + Order CANCELLED + 재고 복구<br/>🔒 3개 Service 트랜잭션 분리 (원자성 비보장 — 운영 로그 + Saga 차기 검토)

            Note over PayS,PR: 🔒 @Transactional — PaymentService.markFailed
            PF->>PayS: markFailed(paymentModel, failureReason)
            activate PayS
            PayS->>PR: save — status=FAILED, failureReason
            PR-->>PayS: ok
            PayS-->>PF: PaymentModel (FAILED)
            deactivate PayS

            Note over OS,OR: 🔒 @Transactional — OrderService.cancelOrder
            PF->>OS: cancelOrder(orderModel)
            activate OS
            OS->>OR: save — status=CANCELLED
            OR-->>OS: ok
            OS-->>PF: OrderModel (CANCELLED)
            deactivate OS

            Note over ProdS,ProdR: 🔒 @Transactional — ProductService.restoreStock (항목별)
            PF->>ProdS: restoreStock(orderModel.items)
            activate ProdS
            ProdS->>ProdR: save — stock += quantity (항목별)
            ProdR-->>ProdS: ok
            ProdS-->>PF: 재고 복구 완료
            deactivate ProdS

            PF-->>PC: CoreException (PaymentFailed)
            PC-->>Client: 400 Bad Request
        end
    end

    rect rgb(220, 252, 231)
        Note over PF,OR: PG 성공 경로 — Payment SUCCEEDED + Order CONFIRMED<br/>🔒 2개 Service 트랜잭션 분리

        Note over PayS,PR: 🔒 @Transactional — PaymentService.markSucceeded
        PF->>PayS: markSucceeded(paymentModel, pgTransactionId)
        activate PayS
        PayS->>PR: save — status=SUCCEEDED, pgTransactionId, completedAt
        PR-->>PayS: ok
        PayS-->>PF: PaymentModel (SUCCEEDED)
        deactivate PayS

        Note over OS,OR: 🔒 @Transactional — OrderService.confirmOrder
        PF->>OS: confirmOrder(orderModel)
        activate OS
        OS->>OR: save — status=CONFIRMED, orderedAt
        activate OR
        OR-->>OS: OrderModel
        deactivate OR
        OS-->>PF: OrderModel
        deactivate OS
    end

    PF-->>PC: PaymentResultInfo {orderId, paymentId, status, paidAmount, pgTransactionId}
    deactivate PF
    PC-->>Client: 200 OK {orderId, paymentId, status: CONFIRMED, paidAmount, pgTransactionId}
    deactivate PC
```

> **명명·구조 정합성 (결정 2 안 B):**
> - `PaymentService`는 **영속화만** 책임 (`createPendingPayment`/`markSucceeded`/`markFailed`). PG와 직접 통신하지 않음
> - `PaymentGateway`는 헥사고날 **포트**. `PaymentFacade`가 트랜잭션 외부에서 직접 호출. PG 종류 교체는 어댑터 교체로만 처리
> - `ProductService.restoreStock`은 **PaymentFacade가 직접 호출** (OrderService 경유 ❌). Facade가 결제 실패 보상 순서를 조율
> - Payment 라이프사이클: `PENDING` → (`SUCCEEDED` | `FAILED`) **단방향**. 재시도는 새 행 생성 — 추후 결정

---

## 📡 나아가며 — 현재 미해결 & 차기 대응

> 모든 기능을 개발한 후 실제 서비스에서 마주칠 **동시성·멱등성·일관성·느린 조회·동시 주문** 이슈를 정리한다.  
> 본 MVP는 핵심 동작만 검증하고, 아래 항목은 운영 데이터 수집 후 단계적으로 도입한다.

### A. 동시성 — 재고 음수 방지

**문제 상황**: 동시 주문 N건이 동일 상품을 동시에 차감 시도하면 재고가 음수가 될 수 있다.

| 단계   | 현재 설계                                                                           | 차기 대응                                                               |
|--------|-------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| MVP    | 원자적 UPDATE — `UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?` | —                                                                       |
| 운영   | —                                                                                   | `SELECT ... FOR UPDATE` 비관적 락 또는 Redis 분산락 도입 검토           |
| 고부하 | —                                                                                   | 재고 예약(Reservation) 패턴 — 주문 시 예약, 결제 성공 시 확정 (2-Phase) |

### B. 멱등성 — 중복 요청 방지

**문제 상황**: 모바일 재시도·네트워크 재전송으로 동일 요청이 여러 번 도착.

| 자원      | 현재 설계                                                        | 차기 대응                                             |
|-----------|------------------------------------------------------------------|-------------------------------------------------------|
| 좋아요    | **완전 멱등** — 자원 최종 상태 동일 시 동일 응답 (200/204 no-op) | —                                                     |
| 주문 생성 | 멱등성 없음 — 동일 요청 2회 시 주문 2건 생성                     | 클라이언트 `Idempotency-Key` 헤더 도입 검토           |
| **결제**  | 멱등성 없음 — 동일 결제 2회 시 PG 중복 호출 위험                 | **`Idempotency-Key` 또는 PG 거래번호 중복 차단 필수** |

### C. 일관성 — 트랜잭션 경계와 Saga

**문제 상황**: Facade에 트랜잭션이 없어 여러 Service 호출 간 원자성이 보장되지 않음.

| 흐름             | 현재 한계                                                                    | 차기 대응                                                                            |
|------------------|------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| 좋아요 등록/취소 | `Like` 저장과 `likeCount` 증감이 별도 Service 트랜잭션 → 중간 실패 시 불일치 | DB 이벤트(`@TransactionalEventListener`) 또는 도메인 이벤트로 후처리 보장            |
| 주문 생성        | Step 1·2·3이 별도 트랜잭션 → Step 2 도중 실패 시 일부 상품만 재고 차감       | 주문 생성을 단일 Service 트랜잭션으로 통합 또는 Saga                                 |
| **결제**         | PG 호출 후 보상 트랜잭션 실패 시 재고/주문 상태 불일치                       | **Saga 패턴 + Outbox 이벤트** — `PaymentFailed` 이벤트 발행 후 재고 복구 비동기 처리 |

### D. 느린 조회 — 인덱스 설계

**문제 상황**: 카테고리·난이도 필터 + 좋아요 많은 순 정렬은 인덱스 없이는 전체 스캔 수준.

| 쿼리 패턴                          | 인덱스                                          |
|------------------------------------|-------------------------------------------------|
| 카테고리 + 난이도 + 활성 상태 필터 | `idx_products_filter (category, level, status)` |
| 좋아요 많은 순 정렬                | `idx_products_like_count (like_count DESC)`     |
| 브랜드별 상품 목록                 | `idx_products_brand_id (brand_id)`              |
| 주문 날짜 범위 조회                | `idx_orders_user_date (user_id, ordered_at)`    |
| 좋아요 중복 체크                   | (복합 PK) `(user_id, product_id)`               |

향후 검토: `likeCount` 정확도 트레이드오프 — 캐시(Redis)로 빼고 비동기 동기화

### E. 동시 주문 — 동일 상품 동시 차감

**문제 상황**: 인기 상품 동시 주문 시 재고 race condition.

| 단계             | 전략                                                              |
|------------------|-------------------------------------------------------------------|
| MVP              | 원자적 UPDATE (A 참조) — 단일 인스턴스 환경에서 충분              |
| 운영 (단일 DB)   | `SELECT ... FOR UPDATE` 또는 낙관적 락(`@Version`)                |
| 운영 (분산 환경) | Redis `SETNX` 기반 상품별 분산락, 또는 재고 예약 패턴             |
| 트래픽 폭증      | 주문 큐 도입 — Kafka/SQS로 비동기 처리, 동시성을 컨슈머 수로 제어 |
