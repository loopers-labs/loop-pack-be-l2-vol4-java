# PRD — 감성 이커머스 Vol.2 (Brand / Product / Like / Order)

> Tag: BE_L2 scenario
> 작성일: 2026-05-19

---

## 1. 제품 개요

브랜드 단위로 상품을 탐색하고, 좋아요로 취향을 표현하며, 여러 상품을 한 번에 주문하는 감성 이커머스 플랫폼.
유저 행동(좋아요·주문)은 데이터로 쌓여 이후 랭킹·추천 기능의 재료가 된다.

Vol.1(User 도메인) 위에 Brand → Product → Like → Order 도메인을 순차적으로 쌓는 구조이다.

---

## 2. Actors

| Actor | 식별 방법 | 설명 |
|-------|----------|------|
| 고객 (User) | `X-Loopers-LoginId` + `X-Loopers-LoginPw` 헤더 | 상품 탐색, 좋아요, 주문 |
| 어드민 (Admin) | `X-Loopers-Ldap: loopers.admin` 헤더 | 브랜드·상품 CRUD, 주문 조회 |
| 비인증 사용자 | 없음 | 브랜드·상품 읽기 전용 조회 |

---

## 3. API 구조 원칙

- 고객 기능: `/api/v1` prefix
- 어드민 기능: `/api-admin/v1` prefix
- 인증/인가 구현 없음 — 헤더 값으로 유저/어드민 식별만 수행
- 유저는 타 유저의 정보에 직접 접근 불가

---

## 4. 기능 요구사항

### 4-1. 브랜드 (Brand)

#### 고객 API

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/brands/{brandId}` | 불필요 | 브랜드 정보 조회 |

**반환 필드**: 브랜드 ID, 브랜드명

#### 어드민 API

| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/brands?page=0&size=20` | 브랜드 목록 조회 (페이지네이션) |
| GET | `/api-admin/v1/brands/{brandId}` | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | 브랜드 삭제 |

**삭제 정책**: Hard delete. 브랜드 삭제 시 해당 브랜드의 상품도 물리 삭제.
> 삭제된 브랜드에 연결된 좋아요·주문 데이터가 있는 경우의 처리는 추후 결제 기능 추가 시 재검토.

---

### 4-2. 상품 (Product)

#### 고객 API

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/products` | 불필요 | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | 불필요 | 상품 상세 조회 |

**상품 목록 쿼리 파라미터**

| 파라미터 | 기본값 | 설명 |
|---------|--------|------|
| `brandId` | 없음 | 특정 브랜드 필터링 |
| `sort` | `latest` | 정렬 기준 (`latest` / `price_asc` / `likes_desc`) |
| `page` | `0` | 페이지 번호 |
| `size` | `20` | 페이지당 상품 수 |

- `sort=latest` 필수 구현, `price_asc` / `likes_desc` 선택 구현
- `likes_desc` 정렬은 `product.like_count` 캐시 컬럼 기준

**반환 필드**: 상품 ID, 상품명, 가격, 재고, 좋아요 수, 브랜드 ID

#### 어드민 API

| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/products?page=0&size=20&brandId={brandId}` | 상품 목록 조회 |
| GET | `/api-admin/v1/products/{productId}` | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | 상품 등록 |
| PUT | `/api-admin/v1/products/{productId}` | 상품 정보 수정 |
| DELETE | `/api-admin/v1/products/{productId}` | 상품 삭제 |

**등록 조건**: 상품의 브랜드는 이미 등록된(삭제되지 않은) 브랜드여야 함.
**수정 제약**: 상품의 브랜드는 수정 불가.
**삭제 정책**: Hard delete.

**반환 필드**: 상품 ID, 상품명, 가격, 재고, 좋아요 수, 브랜드 ID, 상품 설명 (어드민은 설명 포함)

---

### 4-3. 좋아요 (Like)

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/products/{productId}/likes` | 필요 | 상품 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | 필요 | 상품 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | 필요 | 내가 좋아요 한 상품 목록 조회 |

**정책**:
- 이미 좋아요한 상품에 중복 등록 시 `CONFLICT` 예외
- 좋아요하지 않은 상품 취소 시 `NOT_FOUND` 예외
- `{userId}`는 DB PK (Long). 서비스 단에서 헤더의 loginId와 대조 → 불일치 시 `FORBIDDEN` 예외
- 좋아요 등록/취소 시 `product.like_count` 캐시 컬럼 동기화

---

### 4-4. 주문 (Order)

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/orders` | 필요 | 주문 요청 |
| GET | `/api/v1/orders?startAt=&endAt=` | 필요 | 유저의 주문 목록 조회 |
| GET | `/api/v1/orders/{orderId}` | 필요 | 단일 주문 상세 조회 |

**주문 요청 본문**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

**정책**:
- 주문 시 재고 확인 → 부족 시 `BAD_REQUEST` 예외
- 재고 확인 통과 시 즉시 재고 차감
- 주문 정보에는 당시 상품의 **스냅샷(상품명, 단가)** 저장
- 주문 상태: `ORDERED` → `PAID` → `CANCELLED` (결제 완료 시 `PAID`, 취소 시 `CANCELLED`)
- 주문 목록 조회 `startAt` / `endAt`: 주문 생성일(`createdAt`) 기준
- 본인의 주문만 조회 가능

#### 어드민 API

| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/orders?page=0&size=20` | 전체 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | 단일 주문 상세 조회 |

---

## 5. 비기능 요구사항

- 동시성·멱등성·일관성 문제는 전체 기능 구현 후 별도 단계에서 해결
- `like_count` 캐시 컬럼의 정합성 이슈도 이후 동시성 단계에서 해결

---

## 6. 미결 사항 (이후 단계)

| 항목 | 설명 |
|------|------|
| 결제 (Payment) | 주문 후 결제 흐름 추가 |
| 쿠폰 (Coupon) | 쿠폰 발급·적용 |
| 주문 취소 | 재고 복구 포함 |
| 동시 주문 재고 정합성 | 낙관적/비관적 락 또는 Redis 활용 |
| 랭킹·추천 | 좋아요·주문 데이터 활용 |
