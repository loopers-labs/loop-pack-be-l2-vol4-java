# 01. 유저 시나리오 기반 기능 정의 & 요구사항 명세

## 설계 범위

| 포함 | 제외 |
|------|------|
| 상품 목록 / 상세 / 브랜드 조회 | 회원가입, 내 정보 조회 |
| 상품 좋아요 등록 / 취소 | 결제 연동 (이후 단계에서 추가) |
| 주문 생성 (재고 차감) | |
| 브랜드 & 상품 어드민 CRUD | |
| 주문 어드민 조회 | |

---

## 합의된 정책 결정

| 항목        | 결정 | 이유 |
|-----------|------|------|
| 주문 실패 정책  | 재고 부족 상품이 하나라도 있으면 **전체 실패** | 원자적 처리, 구현 단순, UX 명확 |
| 좋아요 멱등    | **Silent Idempotent** — 중복 등록/없는 취소 모두 200 OK | 클라이언트 상태 관리 부담 없음, 네트워크 재시도 안전 |
| 재고가 없는 상품 | 목록 **노출 + 품절 표시** | 관심 상품 찜, 향후 재입고 기능 연결 가능 |
| 삭제 방식     | **Soft Delete** (`deleted_at` 컬럼) | 감사 추적, 주문 스냅샷 정합성, 복구 가능성 |
| 결제 연동     | **이번 범위 제외** | 요구사항 문서에 "과정 진행 중 추가 개발" 명시 |

---

## 개념 모델

### 액터

| 액터 | 설명 |
|------|------|
| **일반 사용자 (User)** | 상품 탐색, 좋아요, 주문 |
| **어드민 (Admin)** | 브랜드/상품 등록·수정·삭제, 주문 조회 |
| **시스템** | 재고 차감, 주문 상태 관리 |

### 핵심 도메인 구조

```
Brand (브랜드)
  └── Product (상품) ← 재고(stock) 포함
        └── Like (좋아요) ← User와 Product 연결

Order (주문)
  └── OrderItem (주문 상품) ← 상품 스냅샷 포함

User (사용자)
```

---

## 도메인별 기능 명세

### [도메인 1] 브랜드 & 상품 조회

**유저 시나리오**
> 사용자는 브랜드 정보를 확인하고, 해당 브랜드의 상품들을 원하는 정렬 기준으로 탐색한다.
> 마음에 드는 상품을 클릭해 상세 정보를 확인한다.

**API 명세**

| Method | URI | 인증 헤더 | 설명 |
|--------|-----|---------|------|
| GET | `/api/v1/brands/{brandId}` | 불필요 | 브랜드 단건 조회 |
| GET | `/api/v1/products` | 불필요 | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | 불필요 | 상품 단건 조회 |

**쿼리 파라미터 (상품 목록)**

| 파라미터 | 기본값 | 설명 |
|---------|-------|------|
| `brandId` | - | 특정 브랜드 필터 |
| `sort` | `latest` | `latest` / `price_asc` / `likes_desc` |
| `page` | `0` | 페이지 번호 |
| `size` | `20` | 페이지당 상품 수 |

**규칙**
- 삭제된 브랜드 조회 → `404 Not Found`
- 삭제된 상품 조회 → `404 Not Found`
- 상품 목록에서 삭제된 상품 제외
- 재고 0 상품은 목록에 포함, `stockStatus: SOLD_OUT` 으로 표시

---

### [도메인 2] 좋아요

**유저 시나리오**
> 사용자가 상품 상세 페이지에서 좋아요를 누른다.
> 이미 누른 상태에서 다시 눌러도, 없는 좋아요를 취소해도 오류 없이 처리된다.
> 내가 좋아요 한 상품 목록을 따로 확인할 수 있다.

**API 명세**

| Method | URI | 인증 헤더 | 설명 |
|--------|-----|---------|------|
| POST | `/api/v1/products/{productId}/likes` | `X-Loopers-LoginId` 필수 | 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | `X-Loopers-LoginId` 필수 | 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | `X-Loopers-LoginId` 필수 | 내 좋아요 상품 목록 |

**규칙**
- `POST` 시 이미 좋아요가 존재하면 → `200 OK` (무시, 중복 저장 없음)
- `DELETE` 시 좋아요가 없으면 → `200 OK` (무시)
- `GET /users/{userId}/likes` — 본인 외 접근 불가 (`403 Forbidden`)
- `User + Product` 조합은 DB unique constraint로 보장
- 좋아요 등록/취소 시 `Product.likeCount` 동기화
- 삭제된 상품은 내 좋아요 목록에서 제외
- **상품이 Soft Delete될 때, 해당 상품의 `likes` 데이터는 Hard Delete(벌크 삭제) 처리** — 유령 좋아요 데이터 방지

---

### [도메인 3] 주문

**유저 시나리오**
> 사용자가 여러 상품을 선택해 주문을 요청한다.
> 서버는 모든 상품의 재고를 확인하고 차감한 뒤 주문을 저장한다.
> 하나라도 재고가 부족하면 전체 주문이 취소된다.
> 사용자는 자신의 주문 목록과 상세를 날짜 기준으로 조회할 수 있다.

**API 명세**

| Method | URI | 인증 헤더 | 설명 |
|--------|-----|---------|------|
| POST | `/api/v1/orders` | `X-Loopers-LoginId` 필수 | 주문 생성 |
| GET | `/api/v1/orders?startAt=&endAt=` | `X-Loopers-LoginId` 필수 | 주문 목록 조회 |
| GET | `/api/v1/orders/{orderId}` | `X-Loopers-LoginId` 필수 | 주문 단건 조회 |

**요청 예시 (주문 생성)**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

**규칙**
- 재고 확인 + 재고 차감 + 주문 저장은 **하나의 트랜잭션** 으로 처리
- 재고 부족 상품이 하나라도 있으면 전체 실패 → `400 Bad Request` + 부족한 상품 정보 응답
- 주문 시 상품의 **이름, 가격을 스냅샷**으로 `OrderItem`에 저장 (이후 상품 수정과 무관)
- 본인 주문만 조회 가능 (타인 주문 조회 시 `403 Forbidden`)
- 결제는 이번 범위 제외 (이후 단계에서 `POST /orders/{orderId}/payments` 추가 예정)

---

### [도메인 4] 브랜드 & 상품 어드민

**유저 시나리오**
> 어드민은 브랜드와 상품을 등록·수정·삭제한다.
> 브랜드를 삭제하면 해당 브랜드의 모든 상품도 함께 삭제된다.

**API 명세**

| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/brands?page=0&size=20` | 브랜드 목록 조회 |
| GET | `/api-admin/v1/brands/{brandId}` | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | 브랜드 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | 브랜드 삭제 |
| GET | `/api-admin/v1/products?page=0&size=20&brandId=` | 상품 목록 조회 |
| GET | `/api-admin/v1/products/{productId}` | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | 상품 등록 |
| PUT | `/api-admin/v1/products/{productId}` | 상품 수정 |
| DELETE | `/api-admin/v1/products/{productId}` | 상품 삭제 |

**인증**: 모든 어드민 API는 `X-Loopers-Ldap: loopers.admin` 헤더 필요

**규칙**
- 브랜드 삭제 → 해당 브랜드의 모든 상품 Soft Delete 처리 (**벌크 쿼리**로 일괄 처리, `UPDATE products SET deleted_at = NOW() WHERE brand_id = :brandId`)
- 브랜드 삭제 → 해당 브랜드 상품들의 `likes` 데이터 Hard Delete (벌크 삭제)
- 브랜드/상품은 `Brand.delete()` 도메인 메서드 자체에서 자식을 지울 수 없음 (단방향 참조 구조) → **어드민 서비스 레이어에서 벌크 쿼리로 처리**
- 상품 등록 시 존재하는 브랜드여야 함 (없으면 `404`)
- 상품 수정 시 브랜드 변경 불가

---

### [도메인 5] 주문 어드민

**API 명세**

| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/orders?page=0&size=20` | 전체 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | 주문 단건 조회 |

**인증**: `X-Loopers-Ldap: loopers.admin` 헤더 필요

---

## 인증 방식 요약

| 구분 | 필수 헤더 | 누락 시 응답 |
|------|---------|------------|
| 일반 사용자 (인증 필요 API) | `X-Loopers-LoginId`, `X-Loopers-LoginPw` | `401 Unauthorized` |
| 어드민 | `X-Loopers-Ldap: loopers.admin` | `401 Unauthorized` |
| 인증 불필요 API | 없음 | — |

> 인증/인가는 주요 스코프가 아니므로 구현하지 않으며, 헤더 기반으로 사용자를 식별합니다.
> 유저는 타 유저의 정보에 직접 접근할 수 없습니다.
> 유저 식별 헤더(`X-Loopers-LoginId`)가 없거나 일치하는 유저가 없으면 `401 Unauthorized`를 반환합니다.
