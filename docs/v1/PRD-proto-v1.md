# PRD — 감성 이커머스 플랫폼 (BE_L2)

## 1. 제품 개요

브랜드 상품을 탐색하고, 좋아요로 관심을 표현하고, 주문으로 구매 의사를 전달하는 커머스 플랫폼.
유저의 행동 데이터(좋아요, 주문)를 축적해 이후 랭킹·추천 기능으로 확장할 수 있는 기반을 만든다.

---

## 2. API 공통 규약

| 구분 | 규칙 |
|------|------|
| 대고객 prefix | `/api/v1` |
| 어드민 prefix | `/api-admin/v1` |
| 유저 인증 헤더 | `X-Loopers-LoginId`, `X-Loopers-LoginPw` |
| 어드민 인증 헤더 | `X-Loopers-Ldap: loopers.admin` |
| 인증/인가 구현 | 미구현 (헤더 값으로 유저 식별만 수행) |
| 타 유저 정보 접근 | 불가 (`FORBIDDEN`) |

---

## 3. 도메인별 기능 요구사항

### 3-1. 유저 (Users)

#### 기능 목록

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| PUT | `/api/v1/users/password` | O | 비밀번호 변경 |

#### 회원가입

- **입력:** `loginId`, `password`, `name`, `birthDate`, `email`
- **검증 규칙:**

| 필드 | 규칙 |
|------|------|
| loginId | 영문 + 숫자만 허용, 공백/특수문자 불가 |
| name | 한글만 허용, 특수문자·공백 불가 |
| email | `xx@yy.z` 형식 준수 |
| birthDate | `LocalDate` 파싱 가능 형식 (`1995-06-10`) |
| password | 8~16자, 영문 대소문자·숫자·특수문자 가능 |

- **비밀번호 추가 규칙:**
  - 생년월일 포함 불가 (`19950610`, `950610`, `0610` 모두 차단)
  - BCrypt 방식으로 암호화해 저장
- **중복 처리:** 동일 `loginId` 존재 시 `CONFLICT` 에러

#### 내 정보 조회

- **반환:** `loginId`, `name`(마지막 글자 `*` 마스킹), `birthDate`, `email`
- 헤더 `loginId` + `password` 검증 후 반환

#### 비밀번호 변경

- **입력:** `currentPassword`, `newPassword`
- 현재 비밀번호 불일치 시 `BAD_REQUEST`
- 새 비밀번호 = 현재 비밀번호 시 `BAD_REQUEST`
- 비밀번호 규칙(8~16자, 생년월일 포함 불가) 동일 적용

---

### 3-2. 브랜드 (Brands)

#### 기능 목록 — 대고객

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |

#### 기능 목록 — 어드민

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api-admin/v1/brands` | O | 브랜드 목록 조회 (페이징) |
| GET | `/api-admin/v1/brands/{brandId}` | O | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | O | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | O | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | O | 브랜드 삭제 |

#### 브랜드 모델 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| name | String | 브랜드명 |
| description | String | 브랜드 설명 |
| imageUrl | String | 브랜드 이미지 URL |

#### 정책

- 브랜드 삭제 시 해당 브랜드의 **상품도 함께 soft delete**
- 대고객과 어드민 응답 필드는 동일 (추후 차별화 여지 존재)
- 페이징: `page` (기본값 0), `size` (기본값 20)

---

### 3-3. 상품 (Products)

#### 기능 목록 — 대고객

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/products` | X | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | X | 상품 정보 조회 |

#### 기능 목록 — 어드민

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api-admin/v1/products` | O | 상품 목록 조회 (페이징) |
| GET | `/api-admin/v1/products/{productId}` | O | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | O | 상품 등록 |
| PUT | `/api-admin/v1/products/{productId}` | O | 상품 정보 수정 |
| DELETE | `/api-admin/v1/products/{productId}` | O | 상품 삭제 |

#### 상품 모델 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| name | String | 상품명 |
| price | Long | 가격 |
| stock | Integer | 재고 수량 (`product_stocks` 테이블로 분리 관리) |
| description | String | 상품 설명 |
| imageUrl | String | 상품 이미지 URL |
| brandId | Long | 브랜드 FK |

#### 상품 목록 조회 쿼리 파라미터

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| brandId | — | 특정 브랜드 필터링 |
| sort | `latest` | `latest` (필수), `price_asc`, `likes_desc` (선택) |
| page | 0 | 페이지 번호 |
| size | 20 | 페이지당 상품 수 |

#### 정책

- 상품 등록 시 존재하는 브랜드여야 함
- 상품의 브랜드는 수정 불가
- 삭제는 soft delete
- 브랜드 삭제 시 해당 브랜드 상품 일괄 soft delete

---

### 3-4. 좋아요 (Likes)

#### 기능 목록

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | O | 내가 좋아요한 상품 목록 |

#### 정책

- 좋아요 목록 조회(`GET /api/v1/users/{userId}/likes`)는 **본인만 허용** — 헤더 `loginId`와 `{userId}` 불일치 시 `FORBIDDEN`
- 중복 좋아요 / 없는 좋아요 취소에 대한 예외 처리는 이번 범위 제외 (발생하지 않는 것으로 간주)

---

### 3-5. 주문 (Orders)

#### 기능 목록 — 대고객

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/orders` | O | 주문 요청 |
| GET | `/api/v1/orders` | O | 주문 목록 조회 (날짜 필터) |
| GET | `/api/v1/orders/{orderId}` | O | 주문 상세 조회 |

#### 기능 목록 — 어드민

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api-admin/v1/orders` | O | 전체 주문 목록 조회 (페이징) |
| GET | `/api-admin/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

#### 주문 요청 Request Body

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

#### 주문 모델

| 필드 | 설명 |
|------|------|
| userId | 주문한 유저 |
| status | `ORDERED` / `PAID` / `CANCELLED` |
| items | 주문 아이템 목록 (스냅샷 포함) |

#### 주문 아이템 스냅샷 필드

| 필드 | 설명 |
|------|------|
| productId | 상품 참조 ID |
| productName | 주문 당시 상품명 (스냅샷) |
| price | 주문 당시 가격 (스냅샷) |
| quantity | 주문 수량 |

#### 정책

- 주문 시 **재고 확인 후 차감** 보장
- 재고 부족 시 에러 반환
- 주문 목록 조회: `startAt` / `endAt` 파라미터로 **주문 생성일시** 기준 필터링
- 결제 기능은 이번 범위 제외 (추후 개발)
- 주문 취소 기능은 이번 범위 제외

---

## 4. 비기능 요구사항

| 항목 | 내용 |
|------|------|
| 삭제 방식 | 브랜드·상품 모두 Soft delete (`deletedAt` 기반) |
| 동시성 처리 | 이번 범위 제외 (모든 기능 구현 후 별도 단계에서 해결) |
| 인증 방식 | 헤더 기반 식별, JWT/세션 미사용 |
| 오버엔지니어링 금지 | 현재 요구사항 외 사전 설계 최소화 |

---

## 5. 유저 시나리오

1. 사용자가 `POST /api/v1/users`로 회원가입
2. 어드민이 브랜드 등록 (`POST /api-admin/v1/brands`)
3. 어드민이 해당 브랜드에 상품 등록 (`POST /api-admin/v1/products`)
4. 사용자가 상품 목록 탐색 (`GET /api/v1/products`)
5. 마음에 드는 상품에 좋아요 (`POST /api/v1/products/{productId}/likes`)
6. 상품 여러 개를 한 번에 주문 (`POST /api/v1/orders`)
7. 주문 내역 확인 (`GET /api/v1/orders`)

---

## 6. 마일스톤

| 단계 | 도메인 | 내용 |
|------|--------|------|
| Vol.1 | User | 회원가입 / 내 정보 조회 / 비밀번호 변경 |
| Vol.2 | Brand + Product | 브랜드·상품 CRUD (대고객 + 어드민) |
| Vol.3 | Likes | 좋아요 등록·취소·목록 조회 |
| Vol.4 | Order | 주문 생성·조회 + 재고 차감 |
| Vol.5 | 고도화 | 동시성 / 멱등성 / 느린 조회 / 동시 주문 해결 |

---

## 7. 의존성

```
Order → Product (재고 차감, 스냅샷)
Order → User (주문자 식별)
Like  → Product
Like  → User
Product → Brand (브랜드 삭제 시 상품 연쇄 soft delete)
```
