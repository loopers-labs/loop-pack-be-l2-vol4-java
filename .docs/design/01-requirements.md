# 01. 요구사항 명세

## 1. 문제 상황 재해석

### 사용자 관점
- 회원으로 가입한 뒤 상품을 탐색하고, 마음에 드는 상품을 좋아요로 저장해 나중에 다시 찾을 수 있어야 한다.
- 상품 목록을 브랜드·정렬 기준으로 필터링해 원하는 상품을 빠르게 발견하고 주문할 수 있어야 한다.
- 내 좋아요 목록과 주문 이력을 기간별로 조회할 수 있어야 한다.

### 비즈니스 관점
- 어드민이 브랜드·상품 카탈로그를 직접 관리한다. 브랜드 삭제 시 해당 브랜드의 상품도 함께 숨겨져야 한다.
- 브랜드 계약이 중지된 경우 해당 브랜드와 그 소속 상품은 공개 API에서 노출되지 않아야 한다.
- 좋아요 수는 인기도 정렬 기준으로 활용되므로 데이터 정합성이 중요하다.
- 재고 oversell은 배송 불가로 이어지므로 주문 시점 재고 차감이 원자적으로 보장되어야 한다.
- 결제 기능은 추후 별도 개발 예정이며, 현재 주문은 재고 차감까지만 처리한다.

### 시스템 관점
- 사용자 인증: 쓰기 요청 및 인증이 필요한 조회는 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더로 처리.
- 어드민 인증: `/api-admin/**` 경로는 LDAP 인증을 통해 별도 접근 제어.
- 좋아요는 동일 유저가 중복 요청해도 데이터 불일치 없이 멱등 동작해야 한다.
- 주문 생성은 재고 차감과 함께 단일 트랜잭션 내 원자성이 보장되어야 한다.

---

## 2. 기능 정의

### 2.1 회원 관리

#### 회원가입 (POST /api/v1/users)
| 항목 | 내용 |
|------|------|
| 인증 | 불필요 |
| 입력 | 로그인 ID, 비밀번호, 이름, 생년월일, 이메일 |
| 검증 | 로그인 ID (영문+숫자만 허용), 이메일 형식, 생년월일 (YYYYMMDD) |
| 중복 | 이미 가입된 로그인 ID → 409 Conflict |
| 비밀번호 규칙 | 8~16자 영문 대소문자·숫자·특수문자; 생년월일 포함 불가; 단방향 암호화 저장 |

#### 내 정보 조회 (GET /api/v1/users/me)
| 항목 | 내용 |
|------|------|
| 인증 | 필요 |
| 반환 | 로그인 ID, 이름 (마지막 글자 `*` 마스킹), 생년월일, 이메일 |

#### 비밀번호 변경 (PUT /api/v1/users/password)
| 항목 | 내용 |
|------|------|
| 인증 | 필요 |
| 입력 | 기존 비밀번호, 새 비밀번호 |
| 제약 | 새 비밀번호는 비밀번호 RULE 준수; 현재 비밀번호와 동일한 값으로 변경 불가 |

---

### 2.2 브랜드 조회 (공개)

#### 브랜드 상세 조회 (GET /api/v1/brands/{brandId})
| 항목 | 내용 |
|------|------|
| 인증 | 불필요 |
| 반환 | brandId, 브랜드명, 이미지 URL |
| 제약 | 삭제되거나 계약 중지된 브랜드 조회 불가 (404) |

> 설명(description) 등 내부 정보는 고객에게 노출하지 않는다.

---

### 2.3 상품 조회 (공개)

#### 상품 목록 조회 (GET /api/v1/products)
| 항목 | 내용 |
|------|------|
| 인증 | 불필요 |
| 쿼리 파라미터 | brandId (선택), sort (latest / price_asc / likes_desc), page (기본 0), size (기본 20) |
| 반환 | productId, 상품명, 가격, 품절 여부, 이미지, 브랜드명, 좋아요 수 |
| 제약 | 삭제된 상품·계약 중지 브랜드 상품 제외; 재고 0(`sold_out_at IS NOT NULL`)인 상품은 품절 상태로 포함 |
| 정렬 | latest (필수 구현), price_asc / likes_desc (선택 구현) |

#### 상품 상세 조회 (GET /api/v1/products/{productId})
| 항목 | 내용 |
|------|------|
| 인증 | 불필요 |
| 반환 | productId, 상품명, 설명, 가격, 품절 여부, 이미지, 브랜드 정보 (id + name), 좋아요 수 |
| 제약 | 삭제된 상품·계약 중지 브랜드 상품 조회 불가 (404) |

---

### 2.4 상품 좋아요

#### 좋아요 등록 (POST /api/v1/products/{productId}/likes)
| 항목 | 내용 |
|------|------|
| 인증 | 필요 |
| 멱등 | 이미 좋아요한 상품 → 무시 후 200 OK |
| 제약 | 존재하지 않거나 삭제된 상품 → 404 |

#### 좋아요 취소 (DELETE /api/v1/products/{productId}/likes)
| 항목 | 내용 |
|------|------|
| 인증 | 필요 |
| 멱등 | 좋아요가 없는 상품 취소 요청 → 무시 후 200 OK |

#### 내가 좋아요한 상품 목록 (GET /api/v1/users/{userId}/likes)
| 항목 | 내용 |
|------|------|
| 인증 | 필요 |
| 접근 제어 | 인증된 사용자 본인 데이터만 조회 가능 (불일치 시 403) |
| 반환 | 좋아요한 상품 목록 (productId, 상품명, 가격, 브랜드명, 이미지) |

---

### 2.5 주문

#### 주문 생성 (POST /api/v1/orders)
| 항목 | 내용 |
|------|------|
| 인증 | 필요 |
| 입력 | items: [{ productId, quantity }] |
| 재고 | 주문 시점 재고 확인 및 즉시 차감; 재고 부족 시 400 |
| 품절 처리 | 차감 후 stock = 0이면 `sold_out_at` 갱신 (soft-delete 방식) |
| 스냅샷 | 주문 시점의 상품명·단가를 OrderItem에 저장 |
| 결제 | 현재 미구현 (추후 개발 예정); 주문 생성 후 상태는 PENDING |
| 반환 | orderId, 주문 상태, 총금액, 주문 상품 목록 |

#### 주문 목록 조회 (GET /api/v1/orders)
| 항목 | 내용 |
|------|------|
| 인증 | 필요 |
| 파라미터 | startAt, endAt (ISO 8601 날짜 형식, 생략 시 전체) |
| 반환 | 본인의 주문 목록 (orderId, 주문일시, 상태, 총금액) |

#### 주문 단건 상세 조회 (GET /api/v1/orders/{orderId})
| 항목 | 내용 |
|------|------|
| 인증 | 필요 |
| 접근 제어 | 본인 주문만 조회 가능 (타인 주문 접근 시 403) |
| 반환 | orderId, 주문일시, 상태, 총금액, 주문 상품 목록 |

---

### 2.6 어드민 - 브랜드 관리 (LDAP 인증)

| Method | URI | 설명 | 제약 |
|--------|-----|------|------|
| GET | /api-admin/v1/brands | 브랜드 목록 조회 (페이지네이션) | |
| GET | /api-admin/v1/brands/{brandId} | 브랜드 상세 조회 | |
| POST | /api-admin/v1/brands | 브랜드 등록 | |
| PUT | /api-admin/v1/brands/{brandId} | 브랜드 수정 | |
| DELETE | /api-admin/v1/brands/{brandId} | 브랜드 삭제 | 해당 브랜드 상품 전체 soft delete |
| PUT | /api-admin/v1/brands/{brandId}/suspend | 브랜드 계약 중지 | `suspended_at` 설정 (soft-delete 방식) |
| PUT | /api-admin/v1/brands/{brandId}/reinstate | 브랜드 계약 재개 | `suspended_at` 초기화 |

> 어드민 응답에는 description, createdAt 등 내부 정보 포함.

---

### 2.7 어드민 - 상품 관리 (LDAP 인증)

| Method | URI | 설명 | 제약 |
|--------|-----|------|------|
| GET | /api-admin/v1/products | 상품 목록 조회 (brandId 필터, 페이지네이션) | |
| GET | /api-admin/v1/products/{productId} | 상품 상세 조회 | |
| POST | /api-admin/v1/products | 상품 등록 | 이미 등록된 브랜드여야 함 |
| PUT | /api-admin/v1/products/{productId} | 상품 수정 | 브랜드 변경 불가 |
| DELETE | /api-admin/v1/products/{productId} | 상품 삭제 | |

---

### 2.8 어드민 - 주문 조회 (LDAP 인증)

| Method | URI | 설명 |
|--------|-----|------|
| GET | /api-admin/v1/orders | 전체 주문 목록 (페이지네이션) |
| GET | /api-admin/v1/orders/{orderId} | 단일 주문 상세 조회 |

---

## 3. 비즈니스 규칙 및 제약사항

### 3.1 비밀번호 규칙
- 8~16자의 영문 대소문자, 숫자, 특수문자만 허용.
- 생년월일(YYYYMMDD)이 비밀번호 내에 포함될 수 없다.
- 비밀번호 변경 시 현재 비밀번호와 동일한 값으로 변경 불가.

### 3.2 재고 관리
- 재고는 0 이상이어야 하며 음수 재고 허용 불가.
- 주문 수량이 현재 재고를 초과하면 주문 불가 (400).
- 주문 취소(또는 추후 결제 실패) 시 차감한 재고를 원복.
- 재고 차감 후 `stock = 0`이 되면 `sold_out_at`을 현재 시각으로 설정한다 (soft-delete 방식).
- 재고 복구 후 `stock > 0`이 되면 `sold_out_at`을 NULL로 초기화한다.

### 3.3 브랜드 삭제 연쇄 처리
- 브랜드를 삭제(soft delete)하면 해당 브랜드의 모든 상품도 soft delete.
- 삭제된 상품은 공개 API에서 조회 불가.

### 3.4 좋아요 정합성
- 동일 회원이 동일 상품에 좋아요는 1개만 유지.
- (memberId, productId) 조합으로 활성 좋아요 중복 방지.

### 3.5 주문 스냅샷
- 주문 생성 시점의 상품명·단가를 OrderItem에 저장.
- 이후 상품 정보가 변경되어도 주문 내역은 변경 시점 이전 값으로 보존.

### 3.6 주문 상태 흐름
```
PENDING  →  CONFIRMED  (결제 완료, 추후 구현)
PENDING  →  CANCELLED  (취소)
```
현재 주문 생성 시 상태는 PENDING으로 시작.

### 3.7 브랜드 계약 중지
- 계약 중지는 `suspended_at` 컬럼으로 soft-delete 방식으로 표현한다.
- `suspended_at IS NOT NULL`이면 해당 브랜드와 그 소속 상품은 공개 API에서 조회 불가.
- 계약 재개 시 `suspended_at`을 NULL로 초기화하며, 별도 상품 cascade 없이 즉시 노출된다.
- 영구 삭제(`deleted_at`)와 달리 계약 중지는 가역적(reversible)이다.

---

## 4. API 목록

### 공개 API (사용자)

| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | /api/v1/users | X | 회원가입 |
| GET | /api/v1/users/me | O | 내 정보 조회 |
| PUT | /api/v1/users/password | O | 비밀번호 변경 |
| GET | /api/v1/brands/{brandId} | X | 브랜드 상세 조회 |
| GET | /api/v1/products | X | 상품 목록 조회 |
| GET | /api/v1/products/{productId} | X | 상품 상세 조회 |
| POST | /api/v1/products/{productId}/likes | O | 좋아요 등록 |
| DELETE | /api/v1/products/{productId}/likes | O | 좋아요 취소 |
| GET | /api/v1/users/{userId}/likes | O | 내가 좋아요한 상품 목록 |
| POST | /api/v1/orders | O | 주문 생성 |
| GET | /api/v1/orders | O | 주문 목록 조회 |
| GET | /api/v1/orders/{orderId} | O | 주문 상세 조회 |

### 어드민 API (LDAP 인증)

| Method | URI | 설명 |
|--------|-----|------|
| GET | /api-admin/v1/brands | 브랜드 목록 조회 |
| GET | /api-admin/v1/brands/{brandId} | 브랜드 상세 |
| POST | /api-admin/v1/brands | 브랜드 등록 |
| PUT | /api-admin/v1/brands/{brandId} | 브랜드 수정 |
| DELETE | /api-admin/v1/brands/{brandId} | 브랜드 삭제 (상품 cascade) |
| PUT | /api-admin/v1/brands/{brandId}/suspend | 브랜드 계약 중지 |
| PUT | /api-admin/v1/brands/{brandId}/reinstate | 브랜드 계약 재개 |
| GET | /api-admin/v1/products | 상품 목록 조회 |
| GET | /api-admin/v1/products/{productId} | 상품 상세 |
| POST | /api-admin/v1/products | 상품 등록 |
| PUT | /api-admin/v1/products/{productId} | 상품 수정 |
| DELETE | /api-admin/v1/products/{productId} | 상품 삭제 |
| GET | /api-admin/v1/orders | 전체 주문 목록 |
| GET | /api-admin/v1/orders/{orderId} | 주문 상세 |

---

## 5. 잠재 리스크

| 리스크 | 설명 | 대응 방향 |
|--------|------|-----------|
| 재고 동시성 | 다수의 주문이 동시에 같은 상품 재고를 차감할 때 race condition 발생 가능 | Pessimistic Lock (`SELECT FOR UPDATE`) 또는 Optimistic Lock (`@Version`) 검토 |
| sold_out_at 갱신 누락 | stock 차감 트랜잭션과 sold_out_at 갱신이 분리되면 품절 상태 불일치 발생 가능 | stock 차감과 sold_out_at 갱신을 동일 UPDATE 또는 동일 트랜잭션 내에서 처리 |
| 결제 전 재고 점유 | 결제 미구현 상태에서 PENDING 주문이 재고를 점유해 실 구매 불가 상태 지속 가능 | 결제 개발 시 주문 만료(TTL) 또는 취소 정책 함께 설계 필요 |
| 좋아요 수 조회 성능 | likes_desc 정렬 시 상품마다 COUNT 쿼리 발생 → 대량 트래픽에서 병목 | 현재는 COUNT 쿼리로 단순 구현; 추후 좋아요 수 컬럼 캐시 또는 Redis 집계 고려 |
| 브랜드 cascade 성능 | 브랜드 삭제 시 상품 수에 따라 UPDATE 쿼리 다수 발생 | JPQL 벌크 UPDATE로 단일 쿼리 처리 |
