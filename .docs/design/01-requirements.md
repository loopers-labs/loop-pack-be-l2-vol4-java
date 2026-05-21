# 01. 요구사항 명세

> 작성일: 2026-05-21
> 기준 문서: 루프팩 이커머스 시나리오 문서 (Round 1, Round 2 Quests)
> 아키텍처: DDD Lite (Controller → ApplicationService → Domain → Repository)

### 설계 범위

| 구분 | 도메인 |
|------|--------|
| ✅ 설계 범위 | 상품 목록/상세, 브랜드 조회, 좋아요 등록/취소, 주문 생성 및 결제 흐름 |
| ⚠️ 범위 외 (참고용 포함) | 회원가입, 내 정보 조회 |

---

## 1. 유비쿼터스 언어

| 한국어 | 영문 도메인 용어 | 설명 |
|--------|----------------|------|
| 회원 | Member | 서비스에 가입한 사용자 |
| 로그인 ID | LoginId | 회원 식별용 영문+숫자 ID |
| 관리자 | Admin | 상품/브랜드를 등록·관리하는 운영자 |
| 상품 | Product | 판매 단위 아이템 |
| 브랜드 | Brand | 상품을 분류하는 카테고리 주체 |
| 재고 | Stock | 상품과 1:1로 연결된 수량 관리 단위 |
| 좋아요 | Like | 회원이 상품에 관심을 표시하는 행위 |
| 주문 | Order | 상품 목록 기반으로 생성된 구매 의사 묶음 |
| 주문 항목 | OrderItem | 주문 안의 개별 상품+수량+가격 스냅샷 |
| 주문 상태 | OrderStatus | PENDING / CONFIRMED / CANCELLED |
| 가격 | Price | 금액을 나타내는 값 |

---

## 2. 사용자 역할 및 권한

| 역할 | 설명 | 식별 방법 |
|------|------|----------|
| GUEST | 로그인 없이 상품/브랜드 조회만 가능 | 헤더 없음 |
| USER | 로그인 후 좋아요·주문 가능 | `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 |
| ADMIN | 상품·브랜드 등록/수정/삭제 가능 | `X-Loopers-Ldap: loopers.admin` 헤더 |

| 기능 | GUEST | USER | ADMIN |
|------|:-----:|:----:|:-----:|
| 상품 목록 조회 | ✅ | ✅ | ✅ |
| 상품 상세 조회 | ✅ | ✅ | ✅ |
| 브랜드 조회 | ✅ | ✅ | ✅ |
| 회원가입 | ✅ | - | - |
| 내 정보 조회 | ❌ | ✅ | ❌ |
| 비밀번호 변경 | ❌ | ✅ | ❌ |
| 회원 탈퇴 | ❌ | ✅ | ❌ |
| 좋아요 등록/취소 | ❌ | ✅ | ❌ |
| 내 좋아요 목록 조회 | ❌ | ✅ | ❌ |
| 주문 생성 | ❌ | ✅ | ❌ |
| 주문 취소 | ❌ | ✅ | ❌ |
| 내 주문 목록/상세 조회 | ❌ | ✅ | ❌ |
| 상품 등록/수정/삭제 | ❌ | ❌ | ✅ |
| 브랜드 등록/수정/삭제 | ❌ | ❌ | ✅ |
| 재고 등록/수정 | ❌ | ❌ | ✅ |
| 전체 주문 목록/상세 조회 | ❌ | ❌ | ✅ |

---

## 3. 기능적 요구사항 (Functional Requirements)

| ID | 도메인 | 기능 | 대상 역할 |
|----|--------|------|----------|
| FR-01 | Member | 회원가입 (로그인 ID, 비밀번호, 이름, 생년월일, 이메일) ⚠️ 범위 외 | GUEST |
| FR-02 | Member | 내 정보 조회 (이름 마지막 글자 마스킹) ⚠️ 범위 외 | USER |
| FR-03 | Member | 비밀번호 변경 | USER |
| FR-04 | Member | 회원 탈퇴 (Soft Delete) | USER |
| FR-05 | Brand | 브랜드 등록 / 수정 / 삭제 | ADMIN |
| FR-06 | Brand | 브랜드 목록 / 상세 조회 | ALL |
| FR-07 | Product | 상품 등록 / 수정 / 삭제 (재고 함께 생성) | ADMIN |
| FR-08 | Product | 상품 목록 조회 (전체 / 브랜드 필터 / 정렬) | ALL |
| FR-09 | Product | 상품 상세 조회 | ALL |
| FR-10 | Stock | 재고 수정 / 조회 (상품 등록 시 자동 생성) | ADMIN |
| FR-11 | Like | 좋아요 등록 | USER |
| FR-12 | Like | 좋아요 취소 | USER |
| FR-13 | Like | 내 좋아요 목록 조회 (상품 기본 정보 포함) | USER |
| FR-14 | Order | 주문 생성 (재고 차감) | USER |
| FR-15 | Order | 주문 취소 (재고 복구) | USER |
| FR-16 | Order | 내 주문 목록 조회 (날짜 범위 필터) | USER |
| FR-17 | Order | 주문 상세 조회 | USER |
| FR-18 | Order | 전체 주문 목록 조회 | ADMIN |
| FR-19 | Order | 주문 상태 확정 (CONFIRMED) | USER |

---

## 4. 도메인별 상세 요구사항

### 회원 (Member)

**회원가입 필드**

| 필드 | 타입 | 제약 |
|------|------|------|
| loginId | String | 영문+숫자만, 중복 불가 |
| password | String | 8~16자, 영문 대소문자+숫자+특수문자, 생년월일 포함 불가 |
| name | String | 필수 |
| birthDate | LocalDate | 필수, 포맷 검증 |
| email | String | 이메일 포맷 검증, 중복 불가 (탈퇴 계정 포함) |

**내 정보 조회 응답**
- 반환: 로그인 ID, 이름, 생년월일, 이메일
- 이름 마지막 글자는 `*`로 마스킹 (예: "홍길동" → "홍길*")

**비밀번호 변경**
- 기존 비밀번호 확인 필수
- 현재 비밀번호와 동일한 비밀번호로 변경 불가
- 동일한 비밀번호 규칙 적용

**회원 탈퇴**
- Soft Delete (`deleted_at` 기록)
- 탈퇴한 이메일/로그인 ID로 재가입 영구 차단

---

### 브랜드 (Brand)

- 브랜드 삭제 시 연결된 모든 상품 연쇄 Soft Delete
- 브랜드 목록: 페이지네이션 적용 (기본 size: 20)

---

### 상품 (Product)

- 상품 등록 시 재고(Stock) 함께 생성
- 상품의 브랜드는 이미 등록된 브랜드여야 함
- 상품의 브랜드는 수정 불가
- 상품 목록 정렬 기준: `latest`(기본) / `price_asc` / `likes_desc`
- likeCount 컬럼 역정규화 (목록 조회 시 COUNT 쿼리 제거)

---

### 좋아요 (Like)

- 좋아요 등록 시 Like 저장 + Product.likeCount +1 (단일 트랜잭션)
- 좋아요 취소 시 Like 삭제 + Product.likeCount -1 (단일 트랜잭션)
- 이미 좋아요한 상품에 재등록 시 200 OK 반환 (멱등 처리 — 네트워크 재시도 안전)
- 좋아요 목록 조회 시 상품 기본 정보(상품명, 가격, 브랜드명) JOIN하여 반환

---

### 주문 (Order)

- 주문 생성 시 items 배열 직접 전송 (장바구니 없음)
- 전체 재고 검증 후 일괄 차감 (부분 실패 방지)
- 재고 차감: 비관적 락 (`SELECT FOR UPDATE`)
- 주문 생성 + 재고 차감: 단일 트랜잭션
- 주문 취소 + 재고 복구: 단일 트랜잭션
- OrderItem에 주문 시점 가격 + 상품명 스냅샷 저장
- 주문 목록: 날짜 범위 필터 (`startAt`, `endAt`)

---

## 5. 비기능적 요구사항 (Non-Functional Requirements)

### 보안 (Security)
- 비밀번호 BCrypt 암호화 저장
- `X-Loopers-LoginId/Pw` 헤더로 사용자 식별
- `X-Loopers-Ldap` 헤더로 어드민 식별
- 본인 리소스 외 접근 시 403 반환
- ADMIN 계정은 `data.sql`로 초기 데이터 INSERT (별도 가입 API 없음)

### 데이터 정합성 (Consistency)
- 주문 생성 시 재고 차감은 단일 트랜잭션으로 처리 (비관적 락)
- 재고 부족 시 주문 전체 롤백
- 주문 취소 시 재고 복구와 상태 변경 단일 트랜잭션
- OrderItem에 주문 시점 가격 + 상품명 스냅샷 저장
- Soft Delete 적용 (전체 도메인, `deleted_at`)
- DB FK 제약조건 없음, 애플리케이션 레벨에서 관계 관리
- `@SQLRestriction`으로 삭제된 데이터 자동 필터링

### 응답성 (Performance)
- 상품 목록 조회: Offset 기반 페이지네이션 (기본 size: 20)
- Product 테이블에 likeCount 역정규화 (목록 조회 시 COUNT 쿼리 제거)

### 에러 처리 (Error Handling)
- 표준 HTTP 상태코드 사용 (400 / 401 / 403 / 404 / 409)
- 비즈니스 에러: `{ "code": "ERROR_CODE", "message": "설명" }`
- 유효성 검증 에러: `{ "code": "VALIDATION_ERROR", "message": "...", "errors": [{ "field": "...", "message": "..." }] }`

---

## 6. Soft Delete 정책

| 도메인 | 정책 | 비고 |
|--------|------|------|
| Member | Soft Delete (`deleted_at`) | 탈퇴 후 주문 이력 보존. 탈퇴 이메일/로그인 ID 재가입 영구 차단 |
| Order | Soft Delete (`deleted_at`) | 취소 주문 이력 보존 |
| Product | Soft Delete (`deleted_at`) | 판매 중단 후 주문 내역 조회 가능 |
| Brand | Soft Delete (`deleted_at`) | 브랜드 삭제 시 연결 상품 연쇄 Soft Delete |
| Like | Hard Delete | 취소 시 레코드 삭제 |
| Stock | Soft Delete (`deleted_at`) | 상품 삭제 시 재고 이력 보존 |

---

## 7. API 목록

### 회원 (Members)

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/users` | ❌ | 회원가입 ⚠️ 범위 외 |
| GET | `/api/v1/users/me` | USER | 내 정보 조회 ⚠️ 범위 외 |
| PUT | `/api/v1/users/password` | USER | 비밀번호 변경 |
| DELETE | `/api/v1/users/me` | USER | 회원 탈퇴 (Soft Delete) |

### 브랜드 (Brands)

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/brands/{brandId}` | ❌ | 브랜드 상세 조회 |
| GET | `/api-admin/v1/brands` | ADMIN | 브랜드 목록 조회 |
| GET | `/api-admin/v1/brands/{brandId}` | ADMIN | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | ADMIN | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | ADMIN | 브랜드 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | ADMIN | 브랜드 삭제 (연결 상품 연쇄 삭제) |

### 상품 (Products)

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/products` | ❌ | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | ❌ | 상품 상세 조회 |
| GET | `/api-admin/v1/products` | ADMIN | 상품 목록 조회 |
| GET | `/api-admin/v1/products/{productId}` | ADMIN | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | ADMIN | 상품 등록 (재고 함께 생성) |
| PUT | `/api-admin/v1/products/{productId}` | ADMIN | 상품 수정 (브랜드 변경 불가) |
| DELETE | `/api-admin/v1/products/{productId}` | ADMIN | 상품 삭제 |
| PUT | `/api-admin/v1/products/{productId}/stock` | ADMIN | 재고 수정 |

**상품 목록 쿼리 파라미터:**

| 파라미터 | 예시 | 설명 |
|---------|------|------|
| `brandId` | `1` | 브랜드 필터링 |
| `sort` | `latest` / `price_asc` / `likes_desc` | 정렬 기준 (기본: `latest`) |
| `page` | `0` | 페이지 번호 (기본값 0) |
| `size` | `20` | 페이지당 수 (기본값 20) |

### 좋아요 (Likes)

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/products/{productId}/likes` | USER | 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | USER | 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | USER | 내 좋아요 목록 조회 |

### 주문 (Orders)

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/orders` | USER | 주문 생성 |
| GET | `/api/v1/orders` | USER | 내 주문 목록 조회 |
| GET | `/api/v1/orders/{orderId}` | USER | 주문 상세 조회 |
| DELETE | `/api/v1/orders/{orderId}` | USER | 주문 취소 (재고 복구) |
| GET | `/api-admin/v1/orders` | ADMIN | 전체 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | ADMIN | 주문 상세 조회 |
| PATCH | `/api/v1/orders/{orderId}/confirm` | USER | 주문 확정 (PENDING → CONFIRMED, PG 결제 성공 후 클라이언트 호출) |

**주문 생성 요청 예시:**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

**주문 목록 쿼리 파라미터:**

| 파라미터 | 예시 | 설명 |
|---------|------|------|
| `startAt` | `2026-01-31` | 조회 시작일 |
| `endAt` | `2026-02-10` | 조회 종료일 |

---

## 8. 주요 유스케이스 흐름

### UC-01. 회원가입 ⚠️ 범위 외

**Main Flow**
1. 사용자가 로그인 ID, 비밀번호, 이름, 생년월일, 이메일을 입력한다
2. 시스템이 각 필드 포맷을 검증한다
3. 로그인 ID / 이메일 중복 여부를 확인한다 (탈퇴 계정 포함)
4. 비밀번호에 생년월일이 포함되어 있는지 확인한다
5. 비밀번호를 BCrypt로 암호화하여 Member를 저장한다

**Exception Flow**
- 포맷 오류 → 400 Bad Request (errors 배열)
- 로그인 ID / 이메일 중복 → 409 Conflict

---

### UC-02. 주문 생성

**Main Flow**
1. 로그인 회원이 상품 목록과 수량을 담아 주문 생성을 요청한다
2. 시스템이 각 상품의 존재 여부를 확인한다
3. 모든 상품의 재고가 충분한지 전체 검증한다
4. Order 및 OrderItem을 생성한다 (주문 시점 가격 + 상품명 스냅샷 저장)
5. 각 상품의 재고를 일괄 차감한다 (비관적 락, 단일 트랜잭션)
6. 주문 상태 PENDING으로 반환한다

**Exception Flow**
- 비로그인 상태 → 401 Unauthorized
- 존재하지 않는 상품 → 404 Not Found
- 재고 부족 → 400 Bad Request (부족한 상품 명시)

---

### UC-03. 주문 취소

**Main Flow**
1. 로그인 회원이 주문 취소를 요청한다
2. 본인 주문인지 확인한다
3. 주문 상태가 PENDING 또는 CONFIRMED인지 확인한다
4. OrderItem별 재고를 복구하고 주문 상태를 CANCELLED로 변경한다 (단일 트랜잭션)

**Exception Flow**
- 본인 주문 아님 → 403 Forbidden
- 이미 CANCELLED 상태 → 400 Bad Request

---

### UC-04. 좋아요 등록

**Main Flow**
1. 로그인 회원이 상품에 좋아요를 요청한다
2. 상품 존재 여부를 확인한다
3. 이미 좋아요했는지 확인한다
4. Like를 저장하고 Product.likeCount를 +1한다 (단일 트랜잭션)

**Exception Flow**
- 비로그인 상태 → 401 Unauthorized
- 존재하지 않는 상품 → 404 Not Found
- 이미 좋아요한 상품 재등록 → 200 OK (멱등 처리)

---

### UC-05. 브랜드 삭제

**Main Flow**
1. 관리자가 브랜드 삭제를 요청한다
2. 브랜드 존재 여부를 확인한다
3. 연결된 상품을 모두 Soft Delete한다
4. 브랜드를 Soft Delete한다

**Exception Flow**
- 존재하지 않는 브랜드 → 404 Not Found

---

## 9. 주문 상태 전이

```
주문 생성
    ↓
[PENDING] ──────────────→ [CANCELLED]
    ↓ ADMIN 확정
[CONFIRMED] ────────────→ [CANCELLED]
```

- `PENDING`: 주문 생성 직후
- `CONFIRMED`: 클라이언트가 PG 결제 성공 후 confirm 호출 (추후 Webhook으로 교체 예정)
- `CANCELLED`: PENDING / CONFIRMED 모두 취소 가능
