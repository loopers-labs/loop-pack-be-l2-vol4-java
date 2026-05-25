# 루프팩 이커머스 서비스 시나리오

## 🎯 배경

**좋아요** 누르고, **쿠폰** 쓰고, 카드로 **결제**하는 **감성 이커머스**.
내가 좋아하는 브랜드의 상품들을 한 번에 담아 주문하고, 유저 행동은 랭킹과 추천으로 연결돼요.

---

## 🧭 서비스 흐름

1. 사용자가 **회원가입**을 하고
2. 여러 브랜드의 상품을 둘러보고, 마음에 드는 상품엔 **좋아요**를 누르죠.
3. 사용자는 **쿠폰을 발급**받고, 여러 상품을 **한 번에 주문하고 결제**합니다.
4. 유저의 행동은 모두 기록되고, 그 데이터는 이후 다양한 기능으로 확장될 수 있어요.

---

## ✅ API 공통 규칙

### 대고객 API
- Prefix: `/api/v1`
- 로그인 유저 식별 헤더:
  - `X-Loopers-LoginId` : 로그인 ID
  - `X-Loopers-LoginPw` : 비밀번호
- 인증/인가는 구현하지 않으며, 헤더로 유저를 식별만 함
- 유저는 타 유저의 정보에 직접 접근할 수 없음

### 어드민 API
- Prefix: `/api-admin/v1`
- 어드민 식별 헤더:
  - `X-Loopers-Ldap` : `loopers.admin`

---

## ✅ 요구사항

### 👤 유저 (Users)

| METHOD | URI | 인증 필요 | 설명 |
|--------|-----|-----------|------|
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| PUT | `/api/v1/users/password` | O | 비밀번호 변경 |

---

### 🏷 브랜드 & 상품 (대고객)

| METHOD | URI | 인증 필요 | 설명 |
|--------|-----|-----------|------|
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | X | 상품 정보 조회 |

#### 상품 목록 조회 쿼리 파라미터

| 파라미터 | 예시 | 설명 |
|----------|------|------|
| `brandId` | `1` | 특정 브랜드의 상품만 필터링 |
| `sort` | `latest` / `price_asc` / `likes_desc` | 정렬 기준 (필수: `latest`) |
| `page` | `0` | 페이지 번호 (기본값 0) |
| `size` | `20` | 페이지당 상품 수 (기본값 20) |

---

### 🏷 브랜드 & 상품 (어드민)

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/brands?page=0&size=20` | 브랜드 목록 조회 |
| GET | `/api-admin/v1/brands/{brandId}` | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | 브랜드 삭제 (해당 브랜드 상품도 함께 삭제) |
| GET | `/api-admin/v1/products?page=0&size=20&brandId={brandId}` | 상품 목록 조회 |
| GET | `/api-admin/v1/products/{productId}` | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | 상품 등록 (이미 등록된 브랜드여야 함) |
| PUT | `/api-admin/v1/products/{productId}` | 상품 정보 수정 (브랜드 수정 불가) |
| DELETE | `/api-admin/v1/products/{productId}` | 상품 삭제 |

> 상품·브랜드 정보 중 고객과 어드민에게 각각 제공해야 할 정보 범위를 고민해보세요.

---

### ❤️ 좋아요 (Likes)

| METHOD | URI | 인증 필요 | 설명 |
|--------|-----|-----------|------|
| POST | `/api/v1/products/{productId}/likes` | O | 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | O | 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | O | 내가 좋아요한 상품 목록 조회 |

---

### 🧾 주문 (Orders)

| METHOD | URI | 인증 필요 | 설명 |
|--------|-----|-----------|------|
| POST | `/api/v1/orders` | O | 주문 요청 |
| GET | `/api/v1/orders?startAt=2026-01-31&endAt=2026-02-10` | O | 유저 주문 목록 조회 |
| GET | `/api/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

#### 주문 요청 예시

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

#### 주문 시 보장사항
- 상품 재고 확인 및 차감
- 주문 정보에 당시 상품 정보(가격, 이름 등)를 **스냅샷**으로 저장

> 결제는 과정 진행 중 추가로 개발됩니다.

---

### 🧾 주문 (어드민)

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/orders?page=0&size=20` | 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | 단일 주문 상세 조회 |

---

## 📡 이후 과제 방향

> ⚙️ 모든 기능의 동작을 개발한 후에 **동시성, 멱등성, 일관성, 느린 조회, 동시 주문** 등 실제 서비스에서 발생하는 문제들을 해결하게 됩니다.
