# 01. 요구사항 명세 — 상품 목록 / 상품 상세 / 브랜드 조회 / 상품 좋아요

## 개요

| 항목 | 내용 |
|------|------|
| 목적 | 비인증 사용자에게 상품 탐색 및 브랜드 조회 API 제공 |
| 대상 사용자 | 비인증 일반 사용자 (`user_required: X`) |
| 대상 모듈 | `apps/commerce-api` |
| 작성일 | 2026-05-18 |

## 유저 시나리오

### 시나리오 1: 상품 목록 탐색

> 사용자가 쇼핑몰에 접속하여 상품 목록을 둘러본다.

1. 사용자가 상품 목록 페이지에 접속한다
2. 최신순(기본)으로 정렬된 상품 목록이 20개씩 페이징되어 노출된다
3. 각 상품에는 이름, 설명, 가격, 재고, 좋아요 수, 브랜드명이 표시된다
4. 사용자가 다음 페이지로 이동하여 추가 상품을 확인한다

### 시나리오 2: 브랜드별 필터링

> 사용자가 특정 브랜드의 상품만 보고 싶다.

1. 사용자가 브랜드를 선택한다 (brandId 파라미터)
2. 해당 브랜드의 상품만 필터링되어 목록에 노출된다
3. 해당 브랜드의 상품이 없으면 빈 목록이 반환된다 (에러 아님)

### 시나리오 3: 정렬 변경

> 사용자가 가격순 또는 인기순으로 상품을 정렬한다.

1. 사용자가 정렬 기준을 선택한다 (`latest` / `price_asc` / `likes_desc`)
2. 선택한 기준으로 재정렬된 목록이 노출된다
3. 잘못된 정렬 값 입력 시 400 에러가 반환된다

### 시나리오 4: 상품 상세 조회

> 사용자가 관심 있는 상품의 상세 정보를 확인한다.

1. 사용자가 목록에서 특정 상품을 클릭한다
2. 상품의 상세 정보(이름, 설명, 가격, 재고, 좋아요 수, 브랜드 정보)가 노출된다
3. 존재하지 않거나 삭제된 상품 조회 시 404 에러가 반환된다

### 시나리오 5: 브랜드 정보 조회

> 사용자가 브랜드의 상세 정보를 확인한다.

1. 사용자가 브랜드 페이지에 접속한다
2. 브랜드 이름, 설명, 로고 URL이 노출된다
3. 존재하지 않거나 삭제된 브랜드 조회 시 404 에러가 반환된다

## API 명세

### API 1: 브랜드 정보 조회

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| Path | `/api/v1/brands/{brandId}` |
| 인증 | 불필요 |

**Path Variable:**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `brandId` | Long | 브랜드 ID |

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id": 1,
    "name": "나이키",
    "description": "스포츠 브랜드",
    "logoUrl": "https://example.com/logo.png"
  }
}
```

### API 2: 상품 목록 조회

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| Path | `/api/v1/products` |
| 인증 | 불필요 |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| `brandId` | Long | X | - | 특정 브랜드의 상품만 필터링 |
| `sort` | String | X | `latest` | 정렬 기준 |
| `page` | int | X | `0` | 페이지 번호 |
| `size` | int | X | `20` | 페이지당 상품 수 |

**정렬 옵션:**

| sort 값 | 정렬 기준 | 필수 여부 |
|----------|-----------|-----------|
| `latest` | `created_at DESC` | 필수 (기본값) |
| `price_asc` | `price ASC` | 선택 |
| `likes_desc` | `like_count DESC` | 선택 |

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "id": 1,
        "name": "에어맥스 90",
        "description": "클래식 러닝화",
        "price": 159000,
        "stock": 50,
        "likeCount": 120,
        "brandId": 1,
        "brandName": "나이키"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

### API 3: 상품 상세 조회

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| Path | `/api/v1/products/{productId}` |
| 인증 | 불필요 |

**Path Variable:**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `productId` | Long | 상품 ID |

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id": 1,
    "name": "에어맥스 90",
    "description": "클래식 러닝화",
    "price": 159000,
    "stock": 50,
    "likeCount": 120,
    "brandId": 1,
    "brandName": "나이키"
  }
}
```

## 비즈니스 규칙

1. 모든 상품은 반드시 하나의 브랜드에 속한다 (N:1 필수)
2. 삭제된 상품/브랜드는 조회에서 완전히 제외한다 (`deleted_at IS NULL`)
3. 상품 목록의 기본 정렬은 `latest` (최신순)
4. `likeCount`는 ProductModel에 직접 필드로 관리한다 (기본값 0, 0 이상)
5. 페이징 기본값: page=0, size=20
6. brandId 필터 시 해당 브랜드의 상품이 없으면 빈 목록을 반환한다 (에러 아님)

## 예외 케이스

| 상황 | HTTP Status | ErrorType | 메시지 예시 |
|------|-------------|-----------|-------------|
| 존재하지 않는 productId 조회 | 404 | `NOT_FOUND` | `[id = {id}] 상품을 찾을 수 없습니다.` |
| 존재하지 않는 brandId 조회 | 404 | `NOT_FOUND` | `[id = {id}] 브랜드를 찾을 수 없습니다.` |
| 삭제된 상품/브랜드 조회 | 404 | `NOT_FOUND` | 삭제된 항목은 조회에서 제외되므로 동일 처리 |
| 잘못된 sort 값 | 400 | `BAD_REQUEST` | `지원하지 않는 정렬 기준입니다.` |
| brandId 필터 시 상품 없음 | 200 | - | 빈 content 배열 반환 |

## 영향 범위

### 신규 생성 (Brand 도메인 전체)
- `domain/brand/` — BrandModel, BrandRepository, BrandService
- `infrastructure/brand/` — BrandJpaRepository, BrandRepositoryImpl
- `application/brand/` — BrandFacade, BrandInfo
- `interfaces/api/brand/` — BrandV1Controller, BrandV1ApiSpec, BrandV1Dto

### 변경 (Product 도메인)
- `ProductModel` — brand 관계(ManyToOne), likeCount 필드 추가
- `ProductRepository` — 조건부 조회 메서드 추가
- `ProductService` — 목록 조회 로직 변경 (페이징, 필터, 정렬)
- `ProductRepositoryImpl` — 조건부 쿼리 구현
- `ProductFacade` — 목록 조회 Info 변환
- `ProductInfo` — brandId, brandName, likeCount 필드 추가
- `ProductV1Controller` — 목록 조회 파라미터 변경
- `ProductV1Dto` — 응답에 brand, likeCount 추가

---

# 상품 좋아요 등록 / 취소 / 내 좋아요 목록 조회

## 개요

| 항목 | 내용 |
|------|------|
| 목적 | 인증된 사용자가 상품에 좋아요를 등록/취소하고, 자신이 좋아요한 상품 목록을 조회 |
| 대상 사용자 | 인증된 사용자 (`user_required: O`) |
| 대상 모듈 | `apps/commerce-api` |
| 작성일 | 2026-05-18 |

## 유저 시나리오

### 시나리오 6: 상품 좋아요 등록

> 사용자가 마음에 드는 상품에 좋아요를 누른다.

1. 사용자가 상품 상세 페이지에서 좋아요 버튼을 클릭한다
2. 좋아요가 등록되고, 상품의 좋아요 수가 1 증가한다
3. 이미 좋아요한 상품에 다시 좋아요를 요청해도 중복 등록되지 않는다 (멱등)

### 시나리오 7: 상품 좋아요 취소

> 사용자가 좋아요를 취소한다.

1. 사용자가 이미 좋아요한 상품에서 좋아요 버튼을 다시 클릭한다
2. 좋아요가 취소되고, 상품의 좋아요 수가 1 감소한다
3. 좋아요하지 않은 상품에 취소를 요청해도 에러 없이 성공 응답한다 (멱등)

### 시나리오 8: 내가 좋아요한 상품 목록 조회

> 사용자가 자신이 좋아요한 상품 목록을 확인한다.

1. 사용자가 마이페이지에서 좋아요 목록을 조회한다
2. 좋아요한 시간 최신순으로 페이징된 상품 목록이 노출된다
3. 좋아요한 상품이 없으면 빈 목록이 반환된다

## API 명세

### API 4: 상품 좋아요 등록

| 항목 | 내용 |
|------|------|
| Method | `POST` |
| Path | `/api/v1/products/{productId}/likes` |
| 인증 | 필요 (`X-User-Id` 헤더) |

**Path Variable:**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `productId` | Long | 상품 ID |

**Request Header:**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `X-User-Id` | Long | O | 사용자 ID |

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

### API 5: 상품 좋아요 취소

| 항목 | 내용 |
|------|------|
| Method | `DELETE` |
| Path | `/api/v1/products/{productId}/likes` |
| 인증 | 필요 (`X-User-Id` 헤더) |

**Path Variable:**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `productId` | Long | 상품 ID |

**Request Header:**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `X-User-Id` | Long | O | 사용자 ID |

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

### API 6: 내가 좋아요한 상품 목록 조회

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| Path | `/api/v1/users/{userId}/likes` |
| 인증 | 필요 (`X-User-Id` 헤더) |

**Path Variable:**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `userId` | Long | 사용자 ID |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| `page` | int | X | `0` | 페이지 번호 |
| `size` | int | X | `20` | 페이지당 상품 수 |

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "id": 1,
        "name": "에어맥스 90",
        "description": "클래식 러닝화",
        "price": 159000,
        "stock": 50,
        "likeCount": 120,
        "brandId": 1,
        "brandName": "나이키"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1
  }
}
```

## 비즈니스 규칙

1. 좋아요 등록/취소는 **멱등** 동작이다
   - 이미 좋아요한 상품에 다시 등록 → 기존 상태 유지, 성공 응답
   - 좋아요하지 않은 상품에 취소 → 아무 일도 일어나지 않음, 성공 응답
2. 좋아요 등록 시 `product.like_count`를 +1, 취소 시 -1 (동기 반영)
3. 좋아요 이력은 별도 테이블(`product_like`)로 관리한다
4. 좋아요 취소 시 이력을 **물리 삭제(hard delete)** 한다
5. 내 좋아요 목록은 좋아요한 시간 최신순으로 정렬한다
6. 사용자 식별은 `X-User-Id` 요청 헤더로 처리한다

## 예외 케이스

| 상황 | HTTP Status | ErrorType | 메시지 예시 |
|------|-------------|-----------|-------------|
| 존재하지 않는 productId로 좋아요 등록/취소 | 404 | `NOT_FOUND` | `[id = {id}] 상품을 찾을 수 없습니다.` |
| 존재하지 않는 userId | 404 | `NOT_FOUND` | `[id = {id}] 사용자를 찾을 수 없습니다.` |
| `X-User-Id` 헤더 누락 | 400 | `BAD_REQUEST` | `사용자 정보가 필요합니다.` |
| 이미 좋아요한 상품에 등록 | 200 | - | 멱등 — 기존 상태 유지, 성공 응답 |
| 좋아요하지 않은 상품에 취소 | 200 | - | 멱등 — 아무 동작 없이 성공 응답 |

## 영향 범위

### 신규 생성 (User 도메인)
- `domain/user/` — UserModel, UserRepository, UserService
- `infrastructure/user/` — UserJpaRepository, UserRepositoryImpl

### 신규 생성 (ProductLike 도메인)
- `domain/productlike/` — ProductLikeModel, ProductLikeRepository, ProductLikeService
- `infrastructure/productlike/` — ProductLikeJpaRepository, ProductLikeRepositoryImpl
- `application/productlike/` — ProductLikeFacade
- `interfaces/api/productlike/` — ProductLikeV1Controller, ProductLikeV1ApiSpec, ProductLikeV1Dto

### 변경 (Product 도메인)
- `ProductModel` — `incrementLikeCount()`, `decrementLikeCount()` 메서드 추가

## 미결정 사항

| 항목 | 현재 상태 | 결정 필요 시점 |
|------|-----------|---------------|
| likeCount 동시성 제어 방식 | 미정 (낙관적 락 vs 비관적 락) | 구현 시 |
| size 최대값 제한 | 미정 | 구현 시 |
| User 인증 체계 전환 | 현재 `X-User-Id` 헤더 → 추후 토큰 기반 | 인증 시스템 도입 시 |

---

# 주문 생성 및 결제 흐름

## 개요

| 항목 | 내용 |
|------|------|
| 목적 | 인증된 사용자가 여러 상품을 담아 주문을 생성하고, 결제까지 완료하는 흐름 제공 |
| 대상 사용자 | 인증 유저 (`X-Loopers-LoginId` + `X-Loopers-LoginPw`), Admin (`X-Loopers-Ldap: loopers.admin`) |
| 대상 모듈 | `apps/commerce-api` |
| 작성일 | 2026-05-20 |

## 유저 시나리오

### 시나리오 9: 주문 생성

> 사용자가 원하는 상품과 수량을 담아 주문을 생성한다.

1. 사용자가 상품 목록에서 원하는 상품과 수량을 선택한다
2. 주문 생성 요청 시 각 상품의 재고를 확인한다
3. 재고가 충분하면 재고를 차감하고 주문을 생성한다
4. 주문 아이템에는 당시 상품명·단가가 스냅샷으로 저장된다
5. 주문은 PENDING 상태로 생성된다

### 시나리오 10: 결제 요청 (stub)

> 사용자가 생성된 주문에 대해 결제를 완료한다.

1. 사용자가 PENDING 상태의 주문에 결제를 요청한다
2. 결제 stub이 실행되어 무조건 성공 응답을 반환한다
3. 주문 상태가 PENDING → PAID로 변경된다
4. PaymentModel이 생성된다

### 시나리오 11: 주문 목록 조회

> 사용자가 기간 조건으로 본인의 주문 내역을 조회한다.

1. 사용자가 startAt, endAt을 지정하여 주문 목록을 요청한다
2. 해당 기간 내 본인의 주문만 최신순으로 반환된다

### 시나리오 12: 주문 상세 조회

> 사용자가 특정 주문의 아이템 상세를 확인한다.

1. 사용자가 orderId로 주문 상세를 요청한다
2. 본인의 주문이면 아이템 목록(스냅샷)이 반환된다
3. 타인의 주문 조회 시 404 에러가 반환된다

### 시나리오 13: [Admin] 주문 목록 / 상세 조회

> Admin이 전체 주문을 관리 목적으로 조회한다.

1. Admin이 `X-Loopers-Ldap: loopers.admin` 헤더를 포함하여 `/api-admin/v1/orders`에 접근한다
2. 전체 회원의 주문을 페이징으로 조회한다

## API 명세

### API 7: 주문 생성

| 항목 | 내용 |
|------|------|
| Method | `POST` |
| Path | `/api/v1/orders` |
| 인증 | 필요 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`) |

**Request:**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "orderId": 1,
    "status": "PENDING",
    "totalPrice": 318000,
    "items": [
      { "productId": 1, "productName": "에어맥스 90", "unitPrice": 159000, "quantity": 2 }
    ],
    "createdAt": "2026-05-20T10:00:00"
  }
}
```

### API 8: 결제 요청 (stub)

| 항목 | 내용 |
|------|------|
| Method | `POST` |
| Path | `/api/v1/payments` |
| 인증 | 필요 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`) |

**Request:**
```json
{ "orderId": 1 }
```

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "paymentId": 1,
    "orderId": 1,
    "status": "PAID",
    "amount": 318000,
    "paidAt": "2026-05-20T10:01:00"
  }
}
```

### API 9: 주문 목록 조회

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| Path | `/api/v1/orders?startAt=2026-01-31&endAt=2026-02-10` |
| 인증 | 필요 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`) |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `startAt` | LocalDate | O | 조회 시작일 (inclusive) |
| `endAt` | LocalDate | O | 조회 종료일 (inclusive) |

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": [
    {
      "orderId": 1,
      "status": "PAID",
      "totalPrice": 318000,
      "createdAt": "2026-01-31T10:00:00"
    }
  ]
}
```

### API 10: 단일 주문 상세 조회

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| Path | `/api/v1/orders/{orderId}` |
| 인증 | 필요 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`) |

**Response:**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "orderId": 1,
    "status": "PAID",
    "totalPrice": 318000,
    "items": [
      { "productId": 1, "productName": "에어맥스 90", "unitPrice": 159000, "quantity": 2 }
    ],
    "createdAt": "2026-05-20T10:00:00"
  }
}
```

### API 11: [Admin] 주문 목록 조회

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| Path | `/api-admin/v1/orders?page=0&size=20` |
| 인증 | `X-Loopers-Ldap: loopers.admin` |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| `page` | int | X | 0 | 페이지 번호 |
| `size` | int | X | 20 | 페이지당 주문 수 |

### API 12: [Admin] 단일 주문 상세 조회

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| Path | `/api-admin/v1/orders/{orderId}` |
| 인증 | `X-Loopers-Ldap: loopers.admin` |

## 비즈니스 규칙

1. 주문 생성 시 items는 1개 이상이어야 한다
2. 각 아이템의 quantity는 1 이상이어야 한다
3. 상품 재고 < 요청 quantity이면 주문 전체가 실패한다 (부분 성공 없음)
4. 재고는 모든 상품을 순차 확인 후, 전부 가능할 때 일괄 차감한다 (락 없음 — 추후 도입 예정)
5. `totalPrice = SUM(unitPrice × quantity)` — 주문 생성 시 스냅샷 단가 기준으로 계산
6. 결제는 PENDING 상태의 주문에만 가능하다
7. 주문 상세/목록은 본인의 주문만 조회 가능하다 (타인 orderId → NOT_FOUND)
8. Admin은 모든 주문을 조회 가능하다
9. 주문 목록 정렬: `createdAt DESC`

**주문 상태 생명주기:**

| 상태 | 설명 | 전이 조건 |
|------|------|-----------|
| `PENDING` | 주문 생성됨 | 주문 생성 시 |
| `PAID` | 결제 완료 | 결제 stub 성공 시 |
| `SHIPPED` | 배송 중 | 추후 구현 |
| `DELIVERED` | 배송 완료 | 추후 구현 |

## 예외 케이스

| 상황 | HTTP Status | ErrorType | 메시지 예시 |
|------|-------------|-----------|-------------|
| items 비어있음 | 400 | `BAD_REQUEST` | `주문 항목은 1개 이상이어야 합니다.` |
| quantity < 1 | 400 | `BAD_REQUEST` | `수량은 1 이상이어야 합니다.` |
| 존재하지 않는 productId | 404 | `NOT_FOUND` | `[id = {id}] 상품을 찾을 수 없습니다.` |
| 재고 부족 | 400 | `BAD_REQUEST` | `[id = {id}] 상품의 재고가 부족합니다.` |
| 존재하지 않는 orderId 조회 | 404 | `NOT_FOUND` | `[id = {id}] 주문을 찾을 수 없습니다.` |
| 타인의 주문 조회 | 404 | `NOT_FOUND` | 존재하지 않는 것처럼 처리 |
| PENDING이 아닌 주문에 결제 | 409 | `CONFLICT` | `이미 처리된 주문입니다.` |
| Admin 헤더 없이 `/api-admin/**` 접근 | 401 | `UNAUTHORIZED` (신규 추가 필요) | `인증 정보가 없습니다.` |

## 영향 범위

### 신규 생성 (Order 도메인)
- `domain/order/` — OrderModel, OrderItemModel, OrderStatus, OrderRepository, OrderService
- `infrastructure/order/` — OrderJpaRepository, OrderItemJpaRepository, OrderRepositoryImpl
- `application/order/` — OrderFacade, OrderInfo
- `interfaces/api/order/` — OrderV1Controller, OrderV1ApiSpec, OrderV1Dto
- `interfaces/api/order/admin/` — OrderAdminV1Controller, OrderAdminV1ApiSpec, OrderAdminV1Dto

### 신규 생성 (Payment 도메인)
- `domain/payment/` — PaymentModel, PaymentStatus, PaymentRepository, PaymentService
- `infrastructure/payment/` — PaymentJpaRepository, PaymentRepositoryImpl
- `application/payment/` — PaymentFacade, PaymentInfo
- `interfaces/api/payment/` — PaymentV1Controller, PaymentV1ApiSpec, PaymentV1Dto

### 변경 (Product 도메인)
- `ProductModel` — `deductStock(int quantity)` 메서드 추가
- `ProductService` — 재고 차감 메서드 추가

### 변경 (공통)
- `support/error/ErrorType.java` — `UNAUTHORIZED(HttpStatus.UNAUTHORIZED, ...)` 추가 검토

## 미결정 사항

| 항목 | 현재 상태 | 결정 필요 시점 |
|------|-----------|---------------|
| 재고 차감 동시성 제어 | 미적용 (락 없음) | 트래픽 증가 시점 |
| 주문 취소 기능 | 미포함 | 추후 요구사항 |
| 실제 PG 연동 | stub 수준 | 결제 고도화 시점 |
| `UNAUTHORIZED` ErrorType 추가 여부 | 미정 | Admin 인증 필터 구현 시 |
| Admin 응답에 사용자 정보 포함 범위 | 미정 | 구현 시 |
