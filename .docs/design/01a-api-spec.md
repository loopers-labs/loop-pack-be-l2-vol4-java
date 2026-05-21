# Loopers 이커머스 — API 기술 명세

> 본 문서는 **개발자용 API 기술 명세**다. 엔드포인트, HTTP 메서드, 요청·응답 형식, 상태 코드를 정의한다.  
> 비즈니스·기능 요구사항은 [`01-requirements.md`](./01-requirements.md), 도메인 모델은 [`00-domain-spec.md`](./00-domain-spec.md)를 참조한다.

---

## API 공통 정책

| 항목             | 정책                                                 |
|------------------|------------------------------------------------------|
| 대고객 prefix    | `/api/v1`                                            |
| 어드민 prefix    | `/api-admin/v1`                                      |
| 유저 인증 헤더   | `X-Loopers-LoginId`, `X-Loopers-LoginPw`             |
| 어드민 인증 헤더 | `X-Loopers-Ldap: loopers.admin`                      |
| 접근 제어        | 유저는 타 유저 정보에 직접 접근 불가 (본인 리소스만) |
| 페이지네이션     | `page`(0-based, 기본 0), `size`(기본 20, 최대 100)   |
| 날짜 형식        | `YYYY-MM-DD` (KST). 종료일은 당일 23:59:59까지 포함  |

---

## 공통 에러 응답

모든 API는 실패 시 아래 형식으로 응답한다.

```json
{
  "code": "ERROR_CODE",
  "message": "사람이 읽을 수 있는 설명"
}
```

| HTTP Status        | 의미                         | 예시 상황                                      |
|--------------------|------------------------------|------------------------------------------------|
| `400 Bad Request`  | 요청 형식·비즈니스 규칙 위반 | 비밀번호 형식 오류, 재고 부족, 수량 범위 초과  |
| `401 Unauthorized` | 인증 헤더 누락 또는 불일치   | `X-Loopers-LoginId`/`Pw` 없음, 비밀번호 불일치 |
| `403 Forbidden`    | 타 유저 리소스 접근 시도     | 본인 것이 아닌 주문·좋아요 조회                |
| `404 Not Found`    | 존재하지 않는 리소스         | 없는 productId, orderId                        |
| `409 Conflict`     | 중복 요청 / 상태 충돌        | 중복 LoginId 가입 시도                         |

---

## 좋아요 멱등성 응답 코드

좋아요는 **완전 멱등** (REST PUT 시맨틱). 자원의 최종 상태가 동일하면 동일 응답.

| 상황                            | 응답 코드                          | 비고                        |
|---------------------------------|------------------------------------|-----------------------------|
| 신규 좋아요 등록 (`POST`)       | `201 Created`                      | 신규 자원 생성              |
| 이미 좋아요한 상품에 `POST`     | `200 OK` (already liked)           | `likeCount` 증분 없이 no-op |
| 좋아요 취소 (`DELETE`)          | `204 No Content`                   | 자원 제거 성공              |
| 좋아요하지 않은 상품에 `DELETE` | `204 No Content` (already removed) | `likeCount` 감소 없이 no-op |

---

## 👤 유저 (Users)

| METHOD | URI                      | 인증 | 설명          |
|--------|--------------------------|------|---------------|
| POST   | `/api/v1/users`          | X    | 회원가입      |
| GET    | `/api/v1/users/me`       | O    | 내 정보 조회  |
| PUT    | `/api/v1/users/password` | O    | 비밀번호 변경 |

---

## 🏷 브랜드 / 상품 — 대고객

| METHOD | URI                            | 인증 | 설명             |
|--------|--------------------------------|------|------------------|
| GET    | `/api/v1/brands/{brandId}`     | X    | 브랜드 정보 조회 |
| GET    | `/api/v1/products`             | X    | 상품 목록 조회   |
| GET    | `/api/v1/products/{productId}` | X    | 상품 정보 조회   |

### 상품 목록 조회 파라미터

| 파라미터   | 예시                                     | 설명                                     |
|------------|------------------------------------------|------------------------------------------|
| `brandId`  | `1`                                      | 특정 브랜드 필터                         |
| `category` | `BACKEND` / `SECURITY` / `NETWORK`       | 기술 카테고리 필터                       |
| `level`    | `BEGINNER` / `INTERMEDIATE` / `ADVANCED` | 난이도 필터                              |
| `sort`     | `latest` / `price_asc` / `likes_desc`    | 정렬 (`latest` 기본값, `createdAt DESC`) |
| `page`     | `0`                                      | 페이지 번호 (0-based, 기본 0)            |
| `size`     | `20`                                     | 페이지당 수 (기본 20, 최대 100)          |

---

## 🏷 브랜드 / 상품 — 어드민

| METHOD | URI                                                       | 설명             | 비고                          |
|--------|-----------------------------------------------------------|------------------|-------------------------------|
| GET    | `/api-admin/v1/brands?page=0&size=20`                     | 브랜드 목록 조회 |                               |
| GET    | `/api-admin/v1/brands/{brandId}`                          | 브랜드 상세 조회 |                               |
| POST   | `/api-admin/v1/brands`                                    | 브랜드 등록      |                               |
| PUT    | `/api-admin/v1/brands/{brandId}`                          | 브랜드 수정      |                               |
| DELETE | `/api-admin/v1/brands/{brandId}`                          | 브랜드 삭제      | 소속 상품 cascade 소프트 삭제 |
| GET    | `/api-admin/v1/products?page=0&size=20&brandId={brandId}` | 상품 목록 조회   |                               |
| GET    | `/api-admin/v1/products/{productId}`                      | 상품 상세 조회   |                               |
| POST   | `/api-admin/v1/products`                                  | 상품 등록        | 등록된 브랜드여야 함          |
| PUT    | `/api-admin/v1/products/{productId}`                      | 상품 수정        | 브랜드 수정 불가              |
| DELETE | `/api-admin/v1/products/{productId}`                      | 상품 삭제        |                               |

---

## ❤️ 좋아요 (Likes)

| METHOD | URI                                  | 인증 | 설명                     |
|--------|--------------------------------------|------|--------------------------|
| POST   | `/api/v1/products/{productId}/likes` | O    | 좋아요 등록 (멱등)       |
| DELETE | `/api/v1/products/{productId}/likes` | O    | 좋아요 취소 (멱등)       |
| GET    | `/api/v1/users/{userId}/likes`       | O    | 내가 좋아요 한 상품 목록 |

> 멱등성 정책은 본 문서 상단 **§좋아요 멱등성 응답 코드** 참조.

---

## 🧾 주문 (Orders) — 대고객

| METHOD | URI                                                  | 인증 | 설명                        |
|--------|------------------------------------------------------|------|-----------------------------|
| POST   | `/api/v1/orders`                                     | O    | 주문 요청                   |
| GET    | `/api/v1/orders?startAt=2026-01-31&endAt=2026-02-10` | O    | 주문 목록 조회 (최대 365일) |
| GET    | `/api/v1/orders/{orderId}`                           | O    | 주문 상세 조회              |

### 주문 요청 예시

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

---

## 🧾 주문 (Orders) — 어드민

| METHOD | URI                                   | 설명                             |
|--------|---------------------------------------|----------------------------------|
| GET    | `/api-admin/v1/orders?page=0&size=20` | 전체 주문 목록 (`size` 최대 100) |
| GET    | `/api-admin/v1/orders/{orderId}`      | 주문 상세 조회                   |

---

## 💳 결제 (Payments)

> 주문 생성 후 별도 결제 요청으로 주문이 확정된다.

| METHOD | URI                                 | 인증 | 설명      |
|--------|-------------------------------------|------|-----------|
| POST   | `/api/v1/orders/{orderId}/payments` | O    | 결제 요청 |

### 결제 요청 예시

```json
{
  "paymentMethod": "CARD",
  "amount": 45000
}
```

### 결제 응답 HTTP 상태 코드

| 상황              | 코드              |
|-------------------|-------------------|
| 결제 성공         | `200 OK`          |
| 주문 미존재       | `404 Not Found`   |
| 타인 주문 접근    | `403 Forbidden`   |
| PENDING 아닌 주문 | `400 Bad Request` |
| 금액 불일치       | `400 Bad Request` |
| PG 결제 실패      | `400 Bad Request` |

---

## API 전체 목록 (25개)

`02-sequence-diagrams.md`의 시퀀스 다이어그램과 1:1 매핑된다.

| #   | Method | URI                                  | 인증  | 단위       |
|-----|--------|--------------------------------------|-------|------------|
| 1   | POST   | `/api/v1/users`                      | X     | 로직       |
| 2   | GET    | `/api/v1/users/me`                   | O     | 로직       |
| 3   | PUT    | `/api/v1/users/password`             | O     | 로직       |
| 4   | GET    | `/api/v1/brands/{brandId}`           | X     | 로직       |
| 5   | GET    | `/api/v1/products`                   | X     | 로직       |
| 6   | GET    | `/api/v1/products/{productId}`       | X     | 로직       |
| 7   | GET    | `/api-admin/v1/brands`               | Admin | 로직       |
| 8   | GET    | `/api-admin/v1/brands/{brandId}`     | Admin | 로직       |
| 9   | POST   | `/api-admin/v1/brands`               | Admin | 로직       |
| 10  | PUT    | `/api-admin/v1/brands/{brandId}`     | Admin | 로직       |
| 11  | DELETE | `/api-admin/v1/brands/{brandId}`     | Admin | 로직       |
| 12  | GET    | `/api-admin/v1/products`             | Admin | 로직       |
| 13  | GET    | `/api-admin/v1/products/{productId}` | Admin | 로직       |
| 14  | POST   | `/api-admin/v1/products`             | Admin | 로직       |
| 15  | PUT    | `/api-admin/v1/products/{productId}` | Admin | 로직       |
| 16  | DELETE | `/api-admin/v1/products/{productId}` | Admin | 로직       |
| 17  | POST   | `/api/v1/products/{productId}/likes` | O     | 어그리거트 |
| 18  | DELETE | `/api/v1/products/{productId}/likes` | O     | 어그리거트 |
| 19  | GET    | `/api/v1/users/{userId}/likes`       | O     | 로직       |
| 20  | POST   | `/api/v1/orders`                     | O     | 어그리거트 |
| 21  | GET    | `/api/v1/orders`                     | O     | 로직       |
| 22  | GET    | `/api/v1/orders/{orderId}`           | O     | 로직       |
| 23  | GET    | `/api-admin/v1/orders`               | Admin | 로직       |
| 24  | GET    | `/api-admin/v1/orders/{orderId}`     | Admin | 로직       |
| 25  | POST   | `/api/v1/orders/{orderId}/payments`  | O     | 어그리거트 |