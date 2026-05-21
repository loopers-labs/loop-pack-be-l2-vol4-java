# 02. 시퀀스 다이어그램

## 목적

각 API에서 다음을 검증한다:
- 호출 책임이 어느 레이어(Controller → Facade → Service → Repository)에 있는가
- 인증 흐름(사용자 헤더 인증 / LDAP 어드민 인증)이 어느 시점에 처리되는가
- 트랜잭션 경계가 어디에서 열리고 닫히는가
- 실패 시 어떤 보상 동작이 필요한가

---

## 1. 회원가입

**왜 필요한가**: 중복 ID 검증과 비밀번호 규칙 검증 책임이 어느 레이어에 위치하는지, 인증 없이 진입 가능한 흐름을 확인하기 위해 작성.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as UserV1Controller
    participant Facade as UserFacade
    participant MemberSvc as MemberService
    participant DB

    Client->>Controller: POST /api/v1/users
    Note over Client: { userId, password, username, birthDate, email }

    Controller->>Facade: register(userId, password, username, birthDate, email)
    Facade->>MemberSvc: register(...)

    MemberSvc->>DB: 로그인 ID로 활성 회원 조회
    DB-->>MemberSvc: null or MemberModel

    alt 이미 존재하는 userId
        MemberSvc-->>Controller: CoreException CONFLICT (409)
    else
        Note over MemberSvc: 형식 검증 (userId 영문+숫자, 이메일, 생년월일 YYYYMMDD)
        Note over MemberSvc: 비밀번호 규칙 검증 (8~16자, 생년월일 포함 불가)
        Note over MemberSvc: 비밀번호 단방향 암호화
        MemberSvc->>DB: 회원 정보 저장
        DB-->>MemberSvc: MemberModel
        MemberSvc-->>Facade: MemberModel
        Facade-->>Controller: UserInfo
        Controller-->>Client: 201 Created { userId, username, birthDate, email }
    end
```

> **읽는 포인트**: 형식 검증과 비밀번호 규칙 검증은 Service 레이어에서 수행한다. 중복 userId 확인은 INSERT 전에 SELECT로 선행 검증하며, 충돌 발생 시 CoreException으로 처리한다.

---

## 2. 내 정보 조회 / 비밀번호 변경

**왜 필요한가**: 헤더 기반 인증이 어떤 레이어에서 처리되고, 인증된 사용자 정보가 다음 단계로 어떻게 전달되는지 확인하기 위해 작성.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as UserV1Controller
    participant Auth as MemberResolver
    participant Facade as UserFacade
    participant MemberSvc as MemberService
    participant DB

    Client->>Controller: GET /api/v1/users/me
    Note over Controller: Header: X-Loopers-LoginId, X-Loopers-LoginPw

    Controller->>Auth: resolveMember(loginId, loginPw)
    Auth->>DB: 로그인 ID로 활성 회원 조회
    DB-->>Auth: MemberModel
    Note over Auth: 비밀번호 일치 검증 (불일치 시 401)
    Auth-->>Controller: MemberModel

    Controller->>Facade: getMyInfo(memberId)
    Facade->>MemberSvc: getMember(memberId)
    MemberSvc-->>Facade: MemberModel
    Note over Facade: 이름 마지막 글자 '*' 마스킹
    Facade-->>Controller: UserInfo
    Controller-->>Client: 200 OK { userId, maskedUsername, birthDate, email }
```

```mermaid
sequenceDiagram
    actor Client
    participant Controller as UserV1Controller
    participant Auth as MemberResolver
    participant Facade as UserFacade
    participant MemberSvc as MemberService
    participant DB

    Client->>Controller: PUT /api/v1/users/password
    Note over Client: { currentPassword, newPassword }
    Note over Controller: Header: X-Loopers-LoginId, X-Loopers-LoginPw

    Controller->>Auth: resolveMember(loginId, loginPw)
    Auth->>DB: 로그인 ID로 회원 조회
    DB-->>Auth: MemberModel (인증 실패 시 401)

    Controller->>Facade: changePassword(memberId, currentPassword, newPassword)
    Facade->>MemberSvc: changePassword(memberId, currentPassword, newPassword)
    Note over MemberSvc: 새 비밀번호 RULE 검증 (8~16자, 생년월일 포함 불가)
    Note over MemberSvc: 현재 비밀번호와 동일 여부 확인 (동일 시 400)
    MemberSvc->>DB: 회원 비밀번호 업데이트
    DB-->>MemberSvc: ok
    MemberSvc-->>Facade: void
    Facade-->>Controller: void
    Controller-->>Client: 200 OK
```

> **읽는 포인트**: `MemberResolver`는 모든 인증 필요 API에서 재사용된다. Controller는 인증 결과로 받은 `MemberModel`의 id를 Facade에 전달한다. 비밀번호 규칙 검증 책임은 Service에 위치한다.

---

## 3. 상품 목록 조회

**왜 필요한가**: 필터(brandId)·정렬(sort)·페이지네이션이 어느 레이어에서 처리되는지, 좋아요 수 집계 방식과 브랜드 조합 책임을 명확히 하기 위해 작성.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ProductV1Controller
    participant Facade as ProductFacade
    participant ProductSvc as ProductService
    participant BrandSvc as BrandService
    participant DB

    Client->>Controller: GET /api/v1/products?brandId=1&sort=latest&page=0&size=20
    Controller->>Facade: getProducts(brandId, sort, page, size)

    Facade->>ProductSvc: getProducts(brandId, sort, page, size)
    ProductSvc->>DB: 활성 상품 목록 조회 (계약 중지 브랜드 제외, 좋아요 수 집계, 필터·정렬·페이지네이션 적용)
    DB-->>ProductSvc: List<ProductRow(product + likeCount)>
    ProductSvc-->>Facade: List<ProductModel + likeCount>

    loop 각 상품의 브랜드 정보 조합
        Facade->>BrandSvc: getBrand(product.brandId)
        BrandSvc->>DB: 브랜드 정보 조회
        DB-->>BrandSvc: BrandModel
        BrandSvc-->>Facade: BrandModel
    end

    Facade-->>Controller: PageResult<ProductInfo>
    Controller-->>Client: 200 OK { content: [...], page, size, totalElements }
```

> **읽는 포인트**: 좋아요 수는 LEFT JOIN + COUNT로 집계한다. 계약 중지된 브랜드(`suspended_at IS NOT NULL`)의 상품은 쿼리 단계에서 제외한다. 브랜드 조합은 Facade에서 처리해 Product 도메인이 Brand를 직접 의존하지 않도록 한다. N+1 문제가 발생할 수 있으므로 브랜드 조회를 IN 조건 일괄 조회로 최적화하는 것을 검토한다.

---

## 4. 상품 좋아요 등록 / 취소

**왜 필요한가**: 멱등 처리 로직이 어느 레이어에서 분기하는지, soft delete 방식으로 취소를 표현하는 구조를 검증하기 위해 작성.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ProductV1Controller
    participant Auth as MemberResolver
    participant Facade as ProductFacade
    participant LikeSvc as ProductLikeService
    participant DB

    Client->>Controller: POST /api/v1/products/{productId}/likes
    Note over Controller: Header: X-Loopers-LoginId, X-Loopers-LoginPw

    Controller->>Auth: resolveMember(loginId, loginPw)
    Auth->>DB: 로그인 ID로 회원 조회
    DB-->>Auth: MemberModel (인증 실패 시 401)

    Controller->>Facade: addLike(memberId, productId)
    Facade->>LikeSvc: addLike(memberId, productId)

    LikeSvc->>DB: 활성 상품 존재 여부 확인
    DB-->>LikeSvc: ProductModel (없으면 404)

    LikeSvc->>DB: 해당 회원의 활성 좋아요 조회
    DB-->>LikeSvc: ProductLikeModel or null

    alt 이미 활성 좋아요 존재
        LikeSvc-->>Facade: 무시 (멱등)
    else 좋아요 없음
        LikeSvc->>DB: 좋아요 데이터 저장
        DB-->>LikeSvc: ok
    end

    Facade-->>Controller: void
    Controller-->>Client: 200 OK
```

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ProductV1Controller
    participant Auth as MemberResolver
    participant Facade as ProductFacade
    participant LikeSvc as ProductLikeService
    participant DB

    Client->>Controller: DELETE /api/v1/products/{productId}/likes
    Note over Controller: Header: X-Loopers-LoginId, X-Loopers-LoginPw

    Controller->>Auth: resolveMember(loginId, loginPw)
    Auth->>DB: 로그인 ID로 회원 조회
    DB-->>Auth: MemberModel (인증 실패 시 401)

    Controller->>Facade: removeLike(memberId, productId)
    Facade->>LikeSvc: removeLike(memberId, productId)

    LikeSvc->>DB: 해당 회원의 활성 좋아요 조회
    DB-->>LikeSvc: ProductLikeModel or null

    alt 활성 좋아요 없음
        LikeSvc-->>Facade: 무시 (멱등)
    else 활성 좋아요 존재
        LikeSvc->>DB: 좋아요 소프트 삭제
        DB-->>LikeSvc: ok
    end

    Facade-->>Controller: void
    Controller-->>Client: 200 OK
```

> **읽는 포인트**: 멱등 분기는 Service에서 DB 조회 결과로 처리한다. 좋아요 취소는 하드 삭제가 아닌 soft delete로 표현하며, `deleted_at IS NULL`인 경우만 활성 좋아요로 간주한다.

---

## 5. 내가 좋아요한 상품 목록

**왜 필요한가**: 본인 데이터 접근 제어(403)가 어느 시점에 처리되는지, 좋아요-상품 JOIN 조회 책임을 확인하기 위해 작성.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as UserV1Controller
    participant Auth as MemberResolver
    participant Facade as ProductFacade
    participant LikeSvc as ProductLikeService
    participant DB

    Client->>Controller: GET /api/v1/users/{userId}/likes
    Note over Controller: Header: X-Loopers-LoginId, X-Loopers-LoginPw

    Controller->>Auth: resolveMember(loginId, loginPw)
    Auth->>DB: 로그인 ID로 회원 조회
    DB-->>Auth: MemberModel (인증 실패 시 401)

    Note over Controller: 인증된 member.userId vs path {userId} 비교
    alt 불일치
        Controller-->>Client: 403 Forbidden
    else 일치
        Controller->>Facade: getLikedProducts(memberId)
        Facade->>LikeSvc: getLikedProducts(memberId)
        LikeSvc->>DB: 회원의 활성 좋아요 상품 목록 조회
        DB-->>LikeSvc: List<ProductModel>
        LikeSvc-->>Facade: List<ProductModel>
        Facade-->>Controller: List<ProductInfo>
        Controller-->>Client: 200 OK [{ productId, name, price, brandName, imageUrl }]
    end
```

---

## 6. 주문 생성

**왜 필요한가**: 재고 차감과 주문 생성이 단일 트랜잭션으로 묶이는 경계를 확인하고, 재고 부족 실패 시 롤백 범위를 검증하기 위해 작성.

```mermaid
sequenceDiagram
    actor Client
    participant Controller as OrderV1Controller
    participant Auth as MemberResolver
    participant Facade as OrderFacade
    participant OrderSvc as OrderService
    participant DB

    Client->>Controller: POST /api/v1/orders
    Note over Client: { items: [{ productId, quantity }] }
    Note over Controller: Header: X-Loopers-LoginId, X-Loopers-LoginPw

    Controller->>Auth: resolveMember(loginId, loginPw)
    Auth->>DB: 로그인 ID로 회원 조회
    DB-->>Auth: MemberModel (인증 실패 시 401)

    Controller->>Facade: createOrder(memberId, items)

    Note over Facade: 트랜잭션 시작

    Facade->>OrderSvc: createOrder(memberId, items)

    loop 각 주문 항목별
        OrderSvc->>DB: 활성 상품 조회 (비관적 락)
        DB-->>OrderSvc: ProductModel (없으면 404)
        Note over OrderSvc: product.deductStock(quantity)
        Note over OrderSvc: 재고 부족 시 CoreException → 트랜잭션 롤백
        OrderSvc->>DB: 상품 재고 차감
        Note over OrderSvc: stock = 0이면 sold_out_at 갱신 (품절 soft-delete)
    end

    Note over OrderSvc: totalAmount = Σ(price × quantity)
    OrderSvc->>DB: 주문 생성 (상태: PENDING)
    OrderSvc->>DB: 주문 항목 저장 (N건)

    Note over Facade: 트랜잭션 커밋

    Facade-->>Controller: OrderInfo
    Controller-->>Client: 201 Created { orderId, status, totalAmount, items }
```

```mermaid
sequenceDiagram
    participant Facade as OrderFacade
    participant OrderSvc as OrderService
    participant DB

    Note over Facade: [실패 시나리오] 재고 부족

    Facade->>OrderSvc: createOrder(...)
    OrderSvc->>DB: 상품 비관적 락 조회
    DB-->>OrderSvc: ProductModel (예: stock = 0, sold_out_at IS NOT NULL)
    Note over OrderSvc: deductStock(quantity) → CoreException INSUFFICIENT_STOCK

    Note over Facade: 트랜잭션 롤백
    Note over DB: 이전 항목의 재고 차감 전부 롤백 (자동)
    Note over DB: 주문 / 주문 항목 저장 없음

    Facade-->>Client: 400 재고 부족 에러
```

> **읽는 포인트**: 재고 차감과 주문 생성이 **단일 트랜잭션** 내에 묶인다. `FOR UPDATE`를 통한 비관적 락으로 동시 주문의 재고 경합을 방지한다. 재고 차감 후 `stock = 0`이면 `sold_out_at`을 갱신해 품절 상태를 soft-delete 방식으로 표현한다. 어느 항목에서든 재고 부족이 발생하면 이전 차감 결과까지 포함해 전체 롤백된다.

---

## 7. 어드민 - 브랜드 삭제 (상품 cascade)

**왜 필요한가**: 브랜드 삭제 시 상품 cascade soft delete가 단일 트랜잭션으로 처리되는지, LDAP 인증 흐름이 어느 레이어에서 처리되는지 확인하기 위해 작성.

```mermaid
sequenceDiagram
    actor Admin
    participant Interceptor as LdapInterceptor
    participant Controller as BrandAdminV1Controller
    participant Facade as BrandFacade
    participant BrandSvc as BrandService
    participant ProductSvc as ProductService
    participant DB

    Admin->>Interceptor: DELETE /api-admin/v1/brands/{brandId}
    Note over Interceptor: LDAP 인증 검증 (실패 시 401)
    Interceptor->>Controller: 인증 통과

    Controller->>Facade: deleteBrand(brandId)

    Note over Facade: 트랜잭션 시작

    Facade->>BrandSvc: deleteBrand(brandId)
    BrandSvc->>DB: 브랜드 존재 여부 확인
    DB-->>BrandSvc: BrandModel (없으면 404)
    BrandSvc->>DB: 브랜드 소프트 삭제

    Facade->>ProductSvc: deleteAllByBrandId(brandId)
    ProductSvc->>DB: 해당 브랜드의 활성 상품 일괄 소프트 삭제

    Note over Facade: 트랜잭션 커밋

    Facade-->>Controller: void
    Controller-->>Admin: 200 OK
```

> **읽는 포인트**: LDAP 인증은 Spring Interceptor 레벨에서 처리되어 Controller에 도달하기 전에 완료된다. 브랜드 soft delete와 상품 bulk soft delete는 단일 트랜잭션으로 묶여 부분 삭제가 발생하지 않는다.

---

## 8. 어드민 - 브랜드 계약 중지 / 재개

**왜 필요한가**: 계약 중지가 영구 삭제(`deleted_at`)와 다른 흐름인지, 상품에 cascade가 발생하지 않음을 확인하기 위해 작성.

```mermaid
sequenceDiagram
    actor Admin
    participant Interceptor as LdapInterceptor
    participant Controller as BrandAdminV1Controller
    participant Facade as BrandFacade
    participant BrandSvc as BrandService
    participant DB

    Admin->>Interceptor: PUT /api-admin/v1/brands/{brandId}/suspend
    Note over Interceptor: LDAP 인증 검증 (실패 시 401)
    Interceptor->>Controller: 인증 통과

    Controller->>Facade: suspendBrand(brandId)
    Facade->>BrandSvc: suspendBrand(brandId)
    BrandSvc->>DB: 브랜드 존재 여부 확인
    DB-->>BrandSvc: BrandModel (없으면 404)
    Note over BrandSvc: brand.suspend() → suspendedAt = now()
    BrandSvc->>DB: 브랜드 suspended_at 갱신
    DB-->>BrandSvc: ok
    BrandSvc-->>Facade: void
    Facade-->>Controller: void
    Controller-->>Admin: 200 OK
```

```mermaid
sequenceDiagram
    actor Admin
    participant Interceptor as LdapInterceptor
    participant Controller as BrandAdminV1Controller
    participant Facade as BrandFacade
    participant BrandSvc as BrandService
    participant DB

    Admin->>Interceptor: PUT /api-admin/v1/brands/{brandId}/reinstate
    Note over Interceptor: LDAP 인증 검증 (실패 시 401)
    Interceptor->>Controller: 인증 통과

    Controller->>Facade: reinstateBrand(brandId)
    Facade->>BrandSvc: reinstateBrand(brandId)
    BrandSvc->>DB: 브랜드 존재 여부 확인
    DB-->>BrandSvc: BrandModel (없으면 404)
    Note over BrandSvc: brand.reinstate() → suspendedAt = null
    BrandSvc->>DB: 브랜드 suspended_at 초기화
    DB-->>BrandSvc: ok
    BrandSvc-->>Facade: void
    Facade-->>Controller: void
    Controller-->>Admin: 200 OK
```

> **읽는 포인트**: 계약 중지는 `suspended_at`만 갱신하며 상품에 cascade가 발생하지 않는다. 공개 API 조회 시 브랜드 `suspended_at IS NULL` 조건으로 계약 중지 브랜드와 그 소속 상품이 자동으로 제외된다. 영구 삭제(`deleted_at`)와 달리 가역적이며, 재개 즉시 별도 상품 처리 없이 노출된다.

---

## 다이어그램 요약

| 다이어그램 | 핵심 검증 포인트 |
|-----------|----------------|
| 회원가입 | 중복 ID 검증 / 비밀번호 규칙 검증 위치 (Service) |
| 내 정보 / 비밀번호 변경 | MemberResolver 재사용 / 인증 흐름 |
| 상품 목록 조회 | 필터+정렬+페이지네이션 / 좋아요 수 JOIN 집계 / 계약 중지 브랜드 제외 / Brand 조합 위치 (Facade) |
| 좋아요 등록/취소 | 멱등 처리 위치 (Service) / soft delete 활용 |
| 내가 좋아요한 목록 | 본인 확인 403 분기 위치 (Controller) |
| 주문 생성 | 재고 차감 + sold_out_at 갱신 단일 트랜잭션 / FOR UPDATE 비관적 락 |
| 어드민 브랜드 삭제 | LDAP 인증 위치 (Interceptor) / cascade soft delete 트랜잭션 |
| 어드민 브랜드 계약 중지/재개 | suspended_at soft-delete / 상품 cascade 없음 / 가역성 확인 |
