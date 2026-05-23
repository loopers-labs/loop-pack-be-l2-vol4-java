# 서비스 요구사항

## 목차

1. [배경](#1-배경)
2. [서비스 흐름 예시](#2-서비스-흐름-예시)
3. [API 공통 규칙](#3-api-공통-규칙)
4. [기능 요구사항](#4-기능-요구사항)
5. [나아가며](#5-나아가며)

---

## 1. 배경

**좋아요**를 누르고, **쿠폰**을 쓰고, 카드로 **결제**하는 **감성 이커머스**입니다.

사용자는 좋아하는 브랜드의 상품들을 한 번에 담아 주문할 수 있습니다. 사용자의 행동 데이터는 이후 랭킹과 추천 기능으로 확장될 수 있습니다.

우리는 이 흐름을 하나씩 직접 만들어갑니다.

---

## 2. 서비스 흐름 예시

1. 사용자가 **회원가입**을 합니다.
2. 여러 브랜드의 상품을 둘러보고, 마음에 드는 상품에 **좋아요**를 누릅니다.
3. 사용자는 **쿠폰을 발급**받고, 여러 상품을 **한 번에 주문하고 결제**합니다.
4. 사용자의 행동은 모두 기록되며, 이 데이터는 이후 다양한 기능으로 확장될 수 있습니다.

---

## 3. API 공통 규칙

### 3.1 대고객 API

- Prefix: `/api/v1`
- 유저 로그인이 필요한 기능은 아래 헤더를 통해 유저를 식별해 제공합니다.
- 인증/인가는 주요 스코프가 아니므로 구현하지 않습니다.
- 유저는 타 유저의 정보에 직접 접근할 수 없습니다.

| Header | 설명 |
| --- | --- |
| `X-Loopers-LoginId` | 로그인 ID |
| `X-Loopers-LoginPw` | 비밀번호 |

### 3.2 어드민 API

- Prefix: `/api-admin/v1`
- 어드민 기능은 아래 헤더를 통해 어드민을 식별해 제공합니다.

| Header | 값 |
| --- | --- |
| `X-Loopers-Ldap` | `loopers.admin` |

---

## 4. 기능 요구사항

### 4.1 유저 (Users)

| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| PUT | `/api/v1/users/password` | O | 비밀번호 변경 |

---

### 4.2 브랜드 & 상품 (Brands / Products)

| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | X | 상품 정보 조회 |

#### 상품 목록 조회 쿼리 파라미터

| 파라미터 | 예시 | 설명 |
| --- | --- | --- |
| `brandId` | `1` | 특정 브랜드의 상품만 필터링 |
| `sort` | `latest` / `price_asc` / `likes_desc` | 정렬 기준 |
| `page` | `0` | 페이지 번호. 기본값은 `0` |
| `size` | `20` | 페이지당 상품 수. 기본값은 `20` |

> 정렬 기준은 선택 구현입니다. 필수는 `latest`이며, 그 외에는 `price_asc`, `likes_desc` 정도로 제한해도 충분합니다.

---

### 4.3 브랜드 & 상품 ADMIN

#### 브랜드 ADMIN API

| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/brands?page=0&size=20` | O | 등록된 브랜드 목록 조회 |
| GET | `/api-admin/v1/brands/{brandId}` | O | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | O | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | O | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | O | 브랜드 삭제 |

브랜드 ADMIN 규칙:

- 브랜드 제거 시 해당 브랜드의 상품들도 삭제되어야 합니다.

#### 상품 ADMIN API

| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/products?page=0&size=20&brandId={brandId}` | O | 등록된 상품 목록 조회 |
| GET | `/api-admin/v1/products/{productId}` | O | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | O | 상품 등록 |
| PUT | `/api-admin/v1/products/{productId}` | O | 상품 정보 수정 |
| DELETE | `/api-admin/v1/products/{productId}` | O | 상품 삭제 |

상품 ADMIN 규칙:

- 상품 등록 시 상품의 브랜드는 이미 등록된 브랜드여야 합니다.
- 상품 정보 수정 시 상품의 브랜드는 수정할 수 없습니다.

> 상품, 브랜드 정보 중 고객과 어드민에게 제공되어야 할 정보에 대해 고민해보세요.

---

### 4.4 좋아요 (Likes)

| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | O | 내가 좋아요 한 상품 목록 조회 |

---

### 4.5 주문 (Orders)

| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | O | 주문 요청 |
| GET | `/api/v1/orders?startAt=2026-01-31&endAt=2026-02-10` | O | 유저의 주문 목록 조회 |
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

주문 규칙:

- **결제**는 과정 진행 중 **추가로 개발**합니다.
- **주문 정보**에는 당시의 상품 정보가 스냅샷으로 저장되어야 합니다.
- **주문 시** 상품 재고 확인 및 차감이 보장되어야 합니다.

---

### 4.6 주문 ADMIN

| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/orders?page=0&size=20` | O | 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

---

## 5. 나아가며

모든 기능의 동작을 개발한 후, 실제 서비스에서 발생하는 다음 문제들을 해결합니다.

- 동시성
- 멱등성
- 일관성
- 느린 조회
- 동시 주문
