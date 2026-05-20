# SDD — 감성 이커머스 Vol.2 (Brand / Product / Like / Order)

> Tag: BE_L2 scenario
> 작성일: 2026-05-20

---

## 1. Actors

| Actor | 식별 방법 | 설명 |
|-------|----------|------|
| 고객 (User) | `X-Loopers-LoginId` + `X-Loopers-LoginPw` 헤더 | 상품 탐색, 좋아요, 주문 |
| 어드민 (Admin) | `X-Loopers-Ldap: loopers.admin` 헤더 | 브랜드·상품 CRUD, 주문 조회 |
| 비인증 사용자 | 없음 | 브랜드·상품 읽기 전용 조회 |

---

## 2. API 구조 원칙

- 고객 기능: `/api/v1` prefix
- 어드민 기능: `/api-admin/v1` prefix
- 인증/인가 구현 없음 — 헤더 값으로 유저/어드민 식별만 수행
- 유저는 타 유저의 정보에 직접 접근 불가

---

## 3. 기능 요구사항

### 4-1. 브랜드 (Brand)

#### 고객 API

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/brands/{brandId}` | 불필요 | 브랜드 정보 조회 |

**반환 필드**: 브랜드 ID, 브랜드명

> 고객용 브랜드 목록 API는 제공하지 않음. 상품 목록의 `brandId` 필터로 간접 탐색.

#### 어드민 API

| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/brands?page=0&size=20` | 브랜드 목록 조회 (페이지네이션) |
| GET | `/api-admin/v1/brands/{brandId}` | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | 브랜드 삭제 |

**삭제 정책**: Soft Delete (`deletedAt` 갱신). 브랜드 삭제 시 해당 브랜드의 상품도 Soft Delete.
**조회 정책**: `deletedAt IS NULL` 조건으로 삭제된 항목 필터링.

---

### 4-2. 상품 (Product)

#### 상품 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `brandId` | Long | O | 소속 브랜드 |
| `name` | String | O | 상품명 |
| `price` | Long | O | 가격 (원 단위, 0 이상) |
| `stock` | Integer | O | 재고 수량 (0 이상) |
| `description` | String | X | 상품 설명 |

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
**수정 제약**: 상품의 브랜드(`brandId`)는 수정 불가.
**삭제 정책**: Soft Delete (`deletedAt` 갱신).
**반환 필드**: 상품 ID, 상품명, 가격, 재고, 좋아요 수, 브랜드 ID, 상품 설명

---

### 4-3. 좋아요 (Like)

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/products/{productId}/likes` | 필요 | 상품 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | 필요 | 상품 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | 필요 | 내가 좋아요 한 상품 목록 조회 |

**정책**:
- 이미 좋아요한 상품에 중복 등록 시 `409 Conflict`
- 좋아요하지 않은 상품 취소 시 `404 Not Found`
- `{userId}`는 DB PK (Long). 서비스 단에서 헤더의 `loginId`로 조회한 유저 ID와 대조 → 불일치 시 `403 Forbidden`
- 삭제된 상품에는 좋아요 불가 (`404 Not Found`)

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
- `items` 내 동일 `productId` 중복 시 `400 Bad Request`
- 아이템 중 하나라도 재고 부족 시 주문 전체 `400 Bad Request` (부분 성공 없음)
- 재고 확인 통과 시 즉시 재고 차감 (트랜잭션 내 원자적 처리)
- 주문 정보에는 당시 상품의 **스냅샷(상품명, 단가, 수량)** 저장
- 주문 상태: `ORDERED` (초기값) → 결제 기능 추가 시 확장
- 주문 목록 조회 `startAt` / `endAt`: **필수값**, 미입력 시 `400 Bad Request`. 주문 생성일(`createdAt`) 기준 필터링
- 본인의 주문만 조회 가능

#### 어드민 API

| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/orders?page=0&size=20` | 전체 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | 단일 주문 상세 조회 |

---

## 4. 도메인 의존 방향

```
User ──────────────────┐
                       ▼
Brand ──► Product ──► Like
                └────► Order ──► OrderItem (스냅샷)
```

- `Like`: User + Product 참조
- `Order`: User 참조, 여러 `OrderItem` 포함
- `OrderItem`: Product 스냅샷 값 복사 (Product 직접 FK 없음)

---

## 5. 비기능 요구사항

- 동시성·멱등성·일관성 문제는 전체 기능 구현 후 별도 단계에서 해결
- 재고 차감의 동시 주문 정합성(낙관적/비관적 락)은 이후 단계에서 처리

---

## 6. 의사결정 기록 (Decision Records)

### DR-01. 브랜드/상품 삭제 방식

- **대안 A: Soft Delete** — `deletedAt` 필드를 채워 논리 삭제. 데이터 보존, 복구 가능. `BaseEntity` 기존 패턴과 일치.
- **대안 B: Hard Delete** — DB에서 물리 삭제. 구현 단순하나, 좋아요·주문 스냅샷과의 참조 정합성 처리 필요.

**선택: 대안 A** — 기존 `BaseEntity`의 `deletedAt` 패턴을 그대로 활용하고, 이후 좋아요·주문 이력과의 관계에서 참조 무결성을 안전하게 유지하기 위해 Soft Delete 채택.

---

### DR-02. 좋아요 중복 등록 / 없는 좋아요 취소 처리

- **대안 A: 에러 반환** — 중복 등록 `409 Conflict`, 없는 취소 `404 Not Found`. 클라이언트가 상태를 명확히 인지해야 함.
- **대안 B: 멱등 처리** — 두 경우 모두 `200 OK`. 클라이언트가 상태를 신경 쓰지 않아도 됨.

**선택: 대안 A** — 좋아요 상태는 UI에서 명확히 구분되어야 하므로, 상태 불일치를 서버가 명시적으로 알려주는 에러 반환 방식이 적합.

---

### DR-03. 재고 부족 시 주문 처리

- **대안 A: 전체 실패** — 아이템 중 하나라도 재고 부족이면 주문 전체 거부. 트랜잭션 원자성 유지.
- **대안 B: 부분 성공** — 재고 있는 상품만 주문 처리. 유연하지만 응답 구조 복잡.

**선택: 대안 A** — 주문은 요청한 수량 전체가 보장되어야 한다는 비즈니스 원칙을 준수. 부분 성공은 결제 연동 시 환불 정책과 충돌할 수 있어 제외.

---

### DR-04. 주문 상품 스냅샷 범위

- **대안 A: 최소 스냅샷** — 상품명, 단가, 수량만 저장. 구현 단순.
- **대안 B: 확장 스냅샷** — 상품명, 단가, 수량 + 브랜드명 포함.
- **대안 C: 전체 스냅샷** — 모든 필드를 JSON 직렬화 저장. 유연하나 스키마 관리 어려움.

**선택: 대안 A** — 현재 주문 상세 조회 요구사항에서 브랜드 정보까지 스냅샷이 필요한 근거가 없음. 이후 필요 시 확장.

---

### DR-05. 주문 목록 날짜 필터 (`startAt` / `endAt`)

- **대안 A: 필수값** — 미입력 시 `400 Bad Request`. API 스펙 표기와 일치.
- **대안 B: 기본값 적용** — 미입력 시 최근 30일 자동 적용.
- **대안 C: 전체 조회** — 미입력 시 전체 주문 반환.

**선택: 대안 A** — 전체 주문 반환은 데이터 증가 시 성능 문제가 발생할 수 있으며, API 스펙에 명시된 파라미터 형태(`?startAt=&endAt=`)가 입력을 전제하고 있음.

---

### DR-06. 좋아요 목록 접근 제어

- **대안 A: 본인만 조회 가능** — 헤더 `loginId`로 조회한 유저 ID와 `{userId}` 불일치 시 `403 Forbidden`.
- **대안 B: 누구나 조회 가능** — 인증만 필요, 권한 제한 없음.

**선택: 대안 A** — 요구사항에 "유저는 타 유저의 정보에 직접 접근할 수 없다"는 원칙이 명시되어 있음. 좋아요 목록도 개인 정보의 일부로 간주.

---

### DR-07. 동일 `productId` 중복 주문 요청

- **대안 A: 에러 반환** — `400 Bad Request`. 클라이언트가 중복 없이 요청해야 함.
- **대안 B: 수량 합산** — 같은 `productId`가 여러 번 오면 합쳐서 처리.

**선택: 대안 A** — 클라이언트가 명확한 의도로 요청해야 한다는 원칙을 적용. 수량 합산은 암묵적 동작으로 버그를 숨길 위험이 있음.

---

### DR-08. 재고 차감 동시성 제어

- **대안 A: 비관적 락** — DB 행 락으로 동시성 완벽 보장.
- **대안 B: 낙관적 락** — `@Version` 필드로 충돌 감지 후 재시도.
- **대안 C: 이후 단계에서 처리** — 현재 기능 구현에 집중, 동시성은 별도 단계에서 해결.

**선택: 대안 C** — 요구사항 문서에 "모든 기능 구현 후 동시성·멱등성·일관성 문제를 해결한다"고 명시되어 있음. 현재 단계에서 선행 복잡도를 줄이고 기능 완성에 집중.

---

## 7. 미결 사항 (이후 단계)

| 항목 | 설명 |
|------|------|
| 결제 (Payment) | 주문 후 결제 흐름 추가, 주문 상태 `PAID`/`CANCELLED` 전환 |
| 쿠폰 (Coupon) | 쿠폰 발급·적용 |
| 주문 취소 | 취소 시 재고 복구 포함 |
| 동시 주문 재고 정합성 | 낙관적/비관적 락 또는 Redis 활용 |
| 랭킹·추천 | 좋아요·주문 데이터 활용 |
