# Vol.2 소프트웨어 설계 문서

> 작성일: 2026-05-20
> 대상 요구사항: `docs/우리가 함께 만들어갈 단 하나의 감성 이커머스.md`

---

## 1. 개요

Vol.1 에서 구현된 User 도메인을 기반으로, 아래 4개 도메인을 추가 구현한다.

| 도메인 | 주요 기능 |
|---|---|
| Brand | 어드민 CRUD, 고객 단건 조회 |
| Product (확장) | Brand 연결, 좋아요 수 표시 |
| Like | 상품 좋아요 등록 / 취소 / 목록 조회 |
| Order | 주문 생성 / 조회 (재고 차감 + 스냅샷) |

---

## 2. 유저 플로우

### Customer

```
[인증 불필요]
  ├── 회원가입 (POST /api/v1/users)
  ├── 브랜드 단건 조회 (GET /api/v1/brands/{brandId})
  ├── 상품 목록 조회 (GET /api/v1/products?sort=latest)
  └── 상품 단건 조회 (GET /api/v1/products/{productId})

[인증 필요 — X-Loopers-LoginId / X-Loopers-LoginPw 헤더 포함]
  ├── 내 정보 조회 (GET /api/v1/users/me)
  ├── 비밀번호 수정 (PUT /api/v1/users/me/password)
  ├── 좋아요
  │     ├── 좋아요 등록 (POST /api/v1/products/{productId}/likes)
  │     ├── 좋아요 취소 (DELETE /api/v1/products/{productId}/likes)
  │     └── 내 좋아요 목록 (GET /api/v1/users/{userId}/likes)
  └── 주문
        ├── 주문 생성 (POST /api/v1/orders)
        ├── 내 주문 목록 (GET /api/v1/orders?startAt=&endAt=)
        └── 주문 단건 조회 (GET /api/v1/orders/{orderId})
```

### Admin

```
모든 요청에 X-Loopers-Ldap: loopers.admin 헤더 포함
  ├── 브랜드 관리
  │     ├── 목록 조회 (GET /api-admin/v1/brands)
  │     ├── 단건 조회 (GET /api-admin/v1/brands/{brandId})
  │     ├── 등록 (POST /api-admin/v1/brands)
  │     ├── 수정 (PUT /api-admin/v1/brands/{brandId})
  │     └── 삭제 (DELETE /api-admin/v1/brands/{brandId})
  ├── 상품 관리
  │     ├── 목록 조회 (GET /api-admin/v1/products)
  │     ├── 단건 조회 (GET /api-admin/v1/products/{productId})
  │     ├── 등록 (POST /api-admin/v1/products)
  │     ├── 수정 (PUT /api-admin/v1/products/{productId})
  │     └── 삭제 (DELETE /api-admin/v1/products/{productId})
  └── 주문 관리
        ├── 목록 조회 (GET /api-admin/v1/orders)
        └── 단건 조회 (GET /api-admin/v1/orders/{orderId})
```

---

## 3. 도메인 모델

### 핵심 설계 결정 요약

| 관계 | 방식 | 근거 |
|---|---|---|
| Product → Brand | `@ManyToOne` (NO_CONSTRAINT) | 상품 조회 시 브랜드명 JOIN 필요 |
| Product.likeCount | DB 비정규화 컬럼 | SQL 원자적 증감으로 COUNT 쿼리 제거 (ADR-003) |
| Product.quantity | PRODUCT_INVENTORY JOIN 필드 | 상품 조회 시 재고 포함 반환 — 별도 재고 조회 불필요, 품절 여부 노출 |
| Like → User / Product | `userId`, `productId` Long | 존재 여부 확인만 필요, JPA 관계 불필요 |
| OrderItem → Order | `@ManyToOne` | 동일 Aggregate, 생명주기 공유 |
| OrderItem → Product | `productId` + 스냅샷 컬럼 | 주문 시점 정보 보존 (요구사항 명시) |
| Order → User | `userId` Long | 유저 변경과 주문 이력 분리 |

> 상세 결정 근거는 `docs/v3/adr/` 참고

---

## 4. ERD

→ [`docs/v3/erd.md`](./erd.md) 참고

---

## 5. 클래스 다이어그램

→ [`docs/v3/class.md`](./class.md) 참고

---

## 6. 레이어 구조 (도메인별)

기존 패턴(`interfaces → application → domain → infrastructure`)을 동일하게 따른다.

```
interfaces/api/
├── brand/
│   ├── BrandV1Controller         # Customer
│   ├── BrandAdminV1Controller    # Admin
│   └── BrandV1Dto
├── product/
│   ├── ProductV1Controller       # Customer
│   ├── ProductAdminV1Controller  # Admin
│   └── ProductV1Dto
├── like/
│   ├── LikeV1Controller
│   └── LikeV1Dto
└── order/
    ├── OrderV1Controller         # Customer
    ├── OrderAdminV1Controller    # Admin
    └── OrderV1Dto

application/
├── brand/   BrandFacade, BrandInfo
├── product/ ProductFacade, ProductInfo
├── like/    LikeFacade, LikeInfo
└── order/   OrderFacade, OrderInfo

domain/
├── brand/   BrandModel, BrandRepository, BrandService
├── product/ ProductModel, ProductInventoryModel,
│            ProductRepository, ProductInventoryRepository, ProductService
├── like/    LikeModel, LikeRepository, LikeService
└── order/   OrderModel, OrderItemModel, OrderRepository, OrderService

infrastructure/
├── brand/   BrandJpaRepository, BrandRepositoryImpl
├── product/ ProductJpaRepository, ProductRepositoryImpl,
│            ProductInventoryJpaRepository, ProductInventoryRepositoryImpl
├── like/    LikeJpaRepository, LikeRepositoryImpl
└── order/   OrderJpaRepository, OrderRepositoryImpl
```

---

## 7. API 엔드포인트

### Brand

**Customer**

| Method | URI | 설명 |
|---|---|---|
| GET | `/api/v1/brands/{brandId}` | 브랜드 단건 조회 |

**Admin**

| Method | URI | 설명 |
|---|---|---|
| GET | `/api-admin/v1/brands?page=0&size=20` | 브랜드 목록 |
| GET | `/api-admin/v1/brands/{brandId}` | 브랜드 단건 조회 |
| POST | `/api-admin/v1/brands` | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | 브랜드 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | 브랜드 삭제 (연관 상품 함께 soft delete) |

### Product

**Customer**

| Method | URI | 설명 |
|---|---|---|
| GET | `/api/v1/products?brandId=&sort=latest&page=0&size=20` | 상품 목록 (sort 생략 시 latest 기본값) |
| GET | `/api/v1/products/{productId}` | 상품 단건 조회 |

> **sort 파라미터 명세**
> | 값 | 정렬 기준 |
> |---|---|
> | `latest` (기본값) | 등록일시 내림차순 |
> | `price_asc` | 가격 오름차순 |
> | `price_desc` | 가격 내림차순 |
> | `like_asc` | 좋아요 수 오름차순 |
> | `like_desc` | 좋아요 수 내림차순 |
>
> sort 파라미터가 없는 경우 `latest`로 대체한다. 알 수 없는 값인 경우 `400 Bad Request`를 반환한다.

**Admin**

| Method | URI | 설명 |
|---|---|---|
| GET | `/api-admin/v1/products?brandId=&page=0&size=20` | 상품 목록 |
| GET | `/api-admin/v1/products/{productId}` | 상품 단건 조회 |
| POST | `/api-admin/v1/products` | 상품 등록 (브랜드 존재 검증) |
| PUT | `/api-admin/v1/products/{productId}` | 상품 수정 (브랜드 변경 불가) |
| DELETE | `/api-admin/v1/products/{productId}` | 상품 삭제 |

### Like

**Customer**

| Method | URI | 설명 |
|---|---|---|
| POST | `/api/v1/products/{productId}/likes` | 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | 내가 좋아요한 상품 목록 |

### Order

**Customer**

| Method | URI | 설명 |
|---|---|---|
| POST | `/api/v1/orders` | 주문 생성 |
| GET | `/api/v1/orders?startAt=&endAt=&page=0&size=20` | 내 주문 목록 (page 기본값 0, size 기본값 20) |
| GET | `/api/v1/orders/{orderId}` | 주문 단건 조회 |

**Admin**

| Method | URI | 설명 |
|---|---|---|
| GET | `/api-admin/v1/orders?page=0&size=20` | 주문 목록 |
| GET | `/api-admin/v1/orders/{orderId}` | 주문 단건 조회 |

---

## 8. 응답 DTO 스펙

> **HTTP 상태 코드 기준**
> - 단건/목록 조회 (GET): `200 OK`
> - 생성 (POST): `201 Created`
> - 수정 (PUT): `200 OK`
> - 삭제 (DELETE): `204 No Content`
> - 좋아요 등록 (POST): `204 No Content` (body 없음)

---

### Brand

```json
// GET /api/v1/brands/{brandId}  →  200
{ "id": 1, "name": "Nike", "description": "나이키입니다" }

// GET /api-admin/v1/brands  →  200
{
  "content": [
    { "id": 1, "name": "Nike", "description": "나이키입니다", "createdAt": "2026-05-20T10:00:00" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}

// GET /api-admin/v1/brands/{brandId}  →  200
{ "id": 1, "name": "Nike", "description": "나이키입니다", "createdAt": "2026-05-20T10:00:00" }

// POST /api-admin/v1/brands  →  201
{ "id": 1, "name": "Nike", "description": "나이키입니다", "createdAt": "2026-05-20T10:00:00" }

// PUT /api-admin/v1/brands/{brandId}  →  200
{ "id": 1, "name": "Nike", "description": "나이키입니다", "createdAt": "2026-05-20T10:00:00" }

// DELETE /api-admin/v1/brands/{brandId}  →  204  (body 없음)
```

---

### Product

```json
// GET /api/v1/products  →  200
{
  "content": [
    {
      "id": 1,
      "brandId": 2,
      "brandName": "Nike",
      "name": "에어맥스",
      "price": 150000,
      "likeCount": 42
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100
}

// GET /api/v1/products/{productId}  →  200
{
  "id": 1,
  "brandId": 2,
  "brandName": "Nike",
  "name": "에어맥스",
  "description": "편안한 러닝화",
  "price": 150000,
  "quantity": 10,
  "likeCount": 42
}

// GET /api-admin/v1/products  →  200
{
  "content": [
    {
      "id": 1,
      "brandId": 2,
      "brandName": "Nike",
      "name": "에어맥스",
      "price": 150000,
      "quantity": 10,
      "likeCount": 42,
      "createdAt": "2026-05-20T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100
}

// GET /api-admin/v1/products/{productId}  →  200
{
  "id": 1,
  "brandId": 2,
  "brandName": "Nike",
  "name": "에어맥스",
  "description": "편안한 러닝화",
  "price": 150000,
  "quantity": 10,
  "likeCount": 42,
  "createdAt": "2026-05-20T10:00:00"
}

// POST /api-admin/v1/products  →  201
{
  "id": 1,
  "brandId": 2,
  "brandName": "Nike",
  "name": "에어맥스",
  "description": "편안한 러닝화",
  "price": 150000,
  "quantity": 10,
  "likeCount": 0,
  "createdAt": "2026-05-20T10:00:00"
}

// PUT /api-admin/v1/products/{productId}  →  200  (POST와 동일 구조)
// DELETE /api-admin/v1/products/{productId}  →  204  (body 없음)
```

> **Customer vs Admin 응답 차이**
> - Customer 목록: `description`, `quantity`, `createdAt` 제외 (탐색용 요약 정보)
> - Customer 단건: `createdAt` 제외
> - Admin: 전체 필드 제공

---

### Like

```json
// POST /api/v1/products/{productId}/likes  →  204  (body 없음)
// DELETE /api/v1/products/{productId}/likes  →  204  (body 없음)

// GET /api/v1/users/{userId}/likes  →  200
{
  "content": [
    {
      "id": 1,
      "brandId": 2,
      "brandName": "Nike",
      "name": "에어맥스",
      "price": 150000,
      "likeCount": 42
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5
}
```

---

### Order

```json
// POST /api/v1/orders 요청
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}

// POST /api/v1/orders  →  201
{
  "orderId": 10,
  "status": "COMPLETED",
  "items": [
    { "productId": 1, "productName": "에어맥스", "productPrice": 150000, "quantity": 2 },
    { "productId": 3, "productName": "런닝화", "productPrice": 80000, "quantity": 1 }
  ],
  "totalAmount": 380000,
  "createdAt": "2026-05-20T10:00:00"
}

// GET /api/v1/orders?startAt=2026-05-01&endAt=2026-05-31&page=0&size=20  →  200
{
  "content": [
    {
      "orderId": 10,
      "status": "COMPLETED",
      "totalAmount": 380000,
      "createdAt": "2026-05-20T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3
}

// GET /api/v1/orders/{orderId}  →  200
{
  "orderId": 10,
  "status": "COMPLETED",
  "items": [
    { "productId": 1, "productName": "에어맥스", "productPrice": 150000, "quantity": 2 },
    { "productId": 3, "productName": "런닝화", "productPrice": 80000, "quantity": 1 }
  ],
  "totalAmount": 380000,
  "createdAt": "2026-05-20T10:00:00"
}

// GET /api-admin/v1/orders  →  200
{
  "content": [
    {
      "orderId": 10,
      "userId": 5,
      "status": "COMPLETED",
      "totalAmount": 380000,
      "createdAt": "2026-05-20T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50
}

// GET /api-admin/v1/orders/{orderId}  →  200
{
  "orderId": 10,
  "userId": 5,
  "status": "COMPLETED",
  "items": [
    { "productId": 1, "productName": "에어맥스", "productPrice": 150000, "quantity": 2 },
    { "productId": 3, "productName": "런닝화", "productPrice": 80000, "quantity": 1 }
  ],
  "totalAmount": 380000,
  "createdAt": "2026-05-20T10:00:00"
}
```

> **Customer vs Admin 주문 응답 차이**
> - Customer: `userId` 미노출 (자신의 주문만 조회 가능)
> - Admin 목록: `userId` 포함 (전체 주문 관리)
> - Admin 단건: `userId` 포함 + 전체 items

---

## 9. 핵심 비즈니스 로직

### Brand 삭제

도메인 서비스 간 직접 호출은 금지한다. `BrandFacade`가 오케스트레이션을 담당한다.

```
BrandFacade.deleteBrand(brandId)
  ├── BrandService.delete(brandId) → brand 조회 후 brand.delete()
  └── ProductService.deleteAllByBrand(brandId) → 연관 상품 각각 product.delete()
```

### 상품 삭제

`ProductFacade`가 오케스트레이션을 담당한다. 상품이 soft delete될 때 연관된 재고 행도 함께 soft delete한다.

```
ProductFacade.deleteProduct(productId)
  ├── ProductService.delete(productId)          → product.delete()
  └── ProductInventoryService.deleteByProduct(productId) → inventory.delete()
```

> 상품이 soft delete되면 ProductInventory도 동일하게 soft delete한다. 이후 재고 조회 시 `deleted_at IS NULL` 필터로 제외된다.

### 상품 등록 / 수정

- 등록: `brandId`로 Brand 존재 여부 검증 후 ProductModel 생성, ProductInventoryModel도 함께 생성
- 수정: 브랜드 변경 불가 — `brand` 필드는 update 메서드에서 제외

### 좋아요 등록 / 취소

```
POST  → findByUserIdAndProductId (deleted_at 포함 전체 조회)
        → active 존재: 409 Conflict
        → soft-deleted 존재: restore() [deleted_at = null]
        → 없음: save(new LikeModel)

DELETE → findByUserIdAndProductId (deleted_at IS NULL, active만)
        → 없으면 404 Not Found
        → 존재: like.delete() [deleted_at = now()]
```

좋아요 수:
- `product` 테이블의 `like_count` 컬럼으로 관리 (DB 비정규화, ADR-003)
- **등록**: `UPDATE product SET like_count = like_count + 1 WHERE id = ?` (SQL 원자적 처리)
- **취소**: `UPDATE product SET like_count = like_count - 1 WHERE id = ?` (SQL 원자적 처리)
- **조회**: `ProductModel.likeCount` 필드를 그대로 반환 — 별도 COUNT 쿼리 없음

### 주문 생성

```
1. 상품 조회 — PRODUCT JOIN PRODUCT_INVENTORY → ProductModel (quantity 포함, 스냅샷 데이터 수집)
2. 재고 확인 (fast fail, 락 없음) — product.quantity < 요청수량이면 400 Bad Request
3. 주문 생성 — OrderModel + OrderItemModel INSERT (스냅샷 포함)
4. 재고 차감 — SELECT ... FOR UPDATE → productInventory.deduct(quantity)
   → 실패 시 @Transactional 전체 롤백 (주문 생성 포함)
```

> - 상품 조회 시 PRODUCT_INVENTORY JOIN으로 quantity를 함께 가져오므로 별도 재고 조회 불필요
> - 2번 재고 확인은 명백한 재고 부족을 주문 INSERT 이전에 조기 차단하는 역할 (fast fail)
> - 실제 동시성 보장은 4번의 FOR UPDATE 락이 담당
> - product_inventory 테이블에만 락이 걸리므로 상품 조회 성능에 영향 없음 (ADR-006 참고)

### 어드민 인증

`X-Loopers-Ldap` 헤더 값 == `"loopers.admin"` 검증. 불일치 시 `403 Forbidden`.

---

## 10. 시퀀스 다이어그램

→ [`docs/v3/sequence.md`](./sequence.md) 참고 (전체 API 시퀀스 다이어그램)

---

## 11. 에러 처리

| 상황 | ErrorType | HTTP |
|---|---|---|
| 브랜드/상품/주문 없음 | `NOT_FOUND` | 404 |
| 이미 좋아요한 상품 재등록 | `CONFLICT` | 409 |
| 재고 부족 | `BAD_REQUEST` | 400 |
| 브랜드 변경 시도 (상품 수정) | `BAD_REQUEST` | 400 |
| 어드민 헤더 불일치 | `FORBIDDEN` | 403 |
| 타인의 좋아요 목록 조회 시도 | `FORBIDDEN` | 403 |
| 알 수 없는 sort 파라미터 값 | `BAD_REQUEST` | 400 |

> `FORBIDDEN` ErrorType 추가 필요

---

## 12. ADR 목록

| 번호 | 제목 | 파일 |
|---|---|---|
| ADR-001 | OrderItem 스냅샷 패턴 | `adr/001-order-item-snapshot.md` |
| ADR-002 | 어드민 인증 헤더 검증 | `adr/002-admin-auth-header.md` |
| ADR-003 | 좋아요 수 COUNT 쿼리 | `adr/003-like-count-query.md` |
| ADR-004 | 상품 응답에 브랜드명 포함 | `adr/004-product-brand-response.md` |
| ADR-005 | @ManyToOne FK 제약조건 제거 | `adr/005-jpa-no-fk-constraint.md` |
| ADR-006 | 재고 별도 테이블 분리 | `adr/006-product-inventory-table.md` |
| ADR-007 | 주문 생성 흐름 설계 | `adr/007-order-creation-flow.md` |
| ADR-008 | likes 테이블 UNIQUE 제약 | `adr/008-likes-unique-constraint.md` |
| ADR-009 | 좋아요 목록 소유권 검증 | `adr/009-likes-ownership-check.md` |
| ADR-010 | 내 주문 목록 조회 — 날짜 필터 + 페이지네이션 | `adr/010-order-list-query-spec.md` |
| ADR-011 | 인증 인터셉터 위치 — support/auth | `adr/011-auth-interceptor-location.md` |
