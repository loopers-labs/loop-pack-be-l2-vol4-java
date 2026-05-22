# 01. 요구사항 명세

## 1. 도메인 개요

### 1.1 배경
루프팩 이커머스는 사용자가 브랜드와 상품을 둘러보고, 마음에 드는 상품에 좋아요를 표시한 뒤, 한 번에 여러 상품을 주문/결제하는 감성 이커머스 서비스다.

본 문서는 Round 2 설계 범위인 **상품·브랜드·좋아요·주문** 도메인의 기능 요구사항을 정의한다. 사용자 도메인(User)은 Round 1에서 구현되었으며, 본 라운드에서는 `userId / loginId` 로 참조만 한다.

### 1.2 설계 범위
- 상품 목록 / 상품 상세 / 브랜드 조회 (대고객)
- 상품 좋아요 등록·취소 (멱등)
- 주문 생성 + 외부 결제 흐름 (재고 차감, 외부 PG 연동)
- 어드민 카탈로그(브랜드/상품) 운영 + 주문 조회

### 1.3 제외 범위
- 회원가입, 내 정보 조회, 비밀번호 변경 (Round 1)
- **포인트 차감** (공식 슬랙 공지로 제외 확정)
- 쿠폰
- 상품 옵션·SKU (사이즈/색상 등)
- 키워드 검색
- 정렬 옵션 `likes_desc`
- 동시성·일관성·느린 조회 등 운영 이슈 (다음 라운드)

### 1.4 관점별 해석
| 관점 | 해석 |
|---|---|
| 사용자 | 브랜드별 상품을 둘러보고, 좋아요로 관심을 표현하고, 여러 상품을 한 번에 주문한다. |
| 비즈니스 | 카탈로그 노출 → 주문 전환이 매출의 시작. 주문 내역은 회계·CS·분쟁의 근거이므로 불변 보장 필요. |
| 시스템 | 읽기 트래픽 중심(상품/브랜드 조회). 주문은 다도메인(재고·외부 결제)이 얽힌 일관성 영역. |

---

## 2. 액터

| 액터 | 식별 방법 | 권한 |
|---|---|---|
| 방문자 (Visitor) | (비로그인) | 상품·브랜드 조회 |
| 사용자 (User) | `X-Loopers-LoginId` + `X-Loopers-LoginPw` | + 좋아요, 주문, 본인 정보 조회 |
| 어드민 (Admin) | `X-Loopers-Ldap: loopers.admin` | 카탈로그·주문 관리 |
| 외부 결제 시스템 (PG) | — | (외부) 결제 처리 |

> 인증/인가 메커니즘 구현은 본 라운드 스코프 외. 헤더 존재 여부로 단순 식별만 한다. 사용자는 타 사용자의 정보에 직접 접근할 수 없다.

---

## 3. 유비쿼터스 언어

| 한국어 | 영어 | 설명 |
|---|---|---|
| 브랜드 | Brand | 상품의 제조/판매 주체 |
| 상품 | Product | 판매되는 단일 단위 (단순 상품 모델, SKU 미분리) |
| 좋아요 | Like | 사용자가 상품에 표시하는 관심. 멱등 동작 |
| 좋아요 수 | likeCount | 상품에 대한 좋아요 총 개수 (캐시 컬럼) |
| 주문 | Order | 사용자의 한 번의 주문 행위 (다건 상품 포함, 집계 루트) |
| 주문 항목 | OrderItem | 주문에 포함된 각 상품 (스냅샷 보유) |
| 스냅샷 | Snapshot | 주문 시점의 상품 정보 박제 (가격·상품명·브랜드명·이미지 URL) |
| 재고 | stockQuantity | 상품의 판매 가능 수량 (내부값) |
| 재고 상태 | isAvailable | 대고객 노출용 boolean (재고 > 0) |
| 결제 게이트웨이 | PaymentGateway | 외부 PG 호출 추상화 (Port) |
| 소프트 삭제 | Soft Delete | `deleted_at` 컬럼으로 논리 삭제 표시 |
| 방문자 | Visitor | 비로그인 사용자 |
| 사용자 | User | 로그인된 사용자 |
| 어드민 | Admin | 관리자 |

### 주요 상태값
| 도메인 | 상태 | 의미 |
|---|---|---|
| Order | `PENDING` | 주문 생성 직후 (결제 진행 중) |
| Order | `COMPLETED` | 결제 성공, 주문 확정 |
| Order | `FAILED` | 결제/검증 실패, 보상 완료 |

---

## 4. 기능 요구사항

### 4.1 상품·브랜드 조회 (대고객)

#### 4.1.1 상품 목록 조회

**[유저 스토리]**
- 방문자는 등록된 상품들을 페이지 단위로 둘러볼 수 있다.
- 방문자는 특정 브랜드의 상품만 모아서 볼 수 있다.
- 방문자는 정렬 기준을 선택해 볼 수 있다 (기본: 최신순).

**[기능 흐름]**
1. 인증 없이 접근 가능
2. `brandId` 있으면 해당 브랜드 상품만 필터링
3. `sort` 값에 따라 정렬 (`latest` / `price_asc`, 기본 `latest`)
4. `page` / `size` 페이징 (기본 `page=0`, `size=20`, `size` 최대 100)

**API:** `GET /api/v1/products?brandId=&sort=&page=&size=`

**Main Flow:** 조건에 맞는 상품을 페이징하여 반환 (200)

**Exception Flow:**
- 존재하지 않는 `brandId` 필터 → 200, 빈 목록
- `size` 음수/0/초과(>100) → 400
- 잘못된 `sort` 값 → 400
- soft delete된 상품 → 응답에 포함하지 않음
- 재고 0 상품 → 포함, `isAvailable=false` 표시

**노출 정보:** `id, name, price, brandId, brandName, imageUrl, likeCount, isAvailable`

---

#### 4.1.2 상품 상세 조회

**[유저 스토리]**
- 방문자는 특정 상품의 자세한 정보를 볼 수 있다.

**[기능 흐름]**
1. 인증 없이 접근 가능
2. `productId`로 단건 조회

**API:** `GET /api/v1/products/{productId}`

**Main Flow:** 상품 상세 반환 (200)

**Exception Flow:**
- 존재하지 않는 `productId` → 404
- soft delete된 상품 → 404
- 재고 0 → 200, `isAvailable=false`

**노출 정보:** `id, name, price, brandId, brandName, imageUrl, likeCount, isAvailable, description`

---

#### 4.1.3 브랜드 정보 조회

**[유저 스토리]**
- 방문자는 특정 브랜드의 정보를 볼 수 있다.

**[기능 흐름]**
1. 인증 없이 접근 가능
2. `brandId`로 단건 조회

**API:** `GET /api/v1/brands/{brandId}`

**Main Flow:** 브랜드 정보 반환 (200)

**Exception Flow:**
- 존재하지 않는 `brandId` → 404
- soft delete된 브랜드 → 404

**노출 정보:** `id, name, description`

---

### 4.2 좋아요 (사용자)

#### 4.2.1 좋아요 등록·취소

**[유저 스토리]**
- 사용자는 마음에 드는 상품에 좋아요를 누를 수 있다.
- 사용자는 좋아요를 취소할 수 있다.
- **같은 버튼을 여러 번 눌러도 결과는 한 번 누른 것과 동일해야 한다 (멱등).**

**[기능 흐름]**
1. 로그인 사용자만 가능
2. `POST` → 등록, `DELETE` → 취소
3. 같은 요청 N번 호출 = 1번 호출과 동일한 최종 상태
4. 등록·취소 시 `Product.likeCount` 캐시 컬럼 ±1 (같은 트랜잭션)

**API:**
- `POST /api/v1/products/{productId}/likes`
- `DELETE /api/v1/products/{productId}/likes`

**Main / Alternate Flow (멱등 분기):**
| 상태 | 요청 | 동작 | 응답 |
|---|---|---|---|
| 좋아요 없음 | POST | 신규 생성 + likeCount +1 | 200 |
| 이미 좋아요 | POST | 무변경 | 200 |
| 좋아요 있음 | DELETE | 삭제 + likeCount -1 | 200 |
| 좋아요 없음 | DELETE | 무변경 | 200 |

**Exception Flow:**
- 존재하지 않는 `productId` → 404
- soft delete된 상품 → 404
- 비로그인 → 401

---

#### 4.2.2 내 좋아요 목록 조회

**[유저 스토리]**
- 사용자는 자신이 좋아요한 상품들을 모아 볼 수 있다.

**[기능 흐름]**
1. 로그인 사용자만 가능
2. 본인만 자신의 목록 조회 가능

**API:** `GET /api/v1/users/{userId}/likes`

**Main Flow:** 본인 좋아요 목록 반환 (200)

**Exception Flow:**
- 다른 사용자의 likes 조회 시도 → 404 (정보 노출 방지)
- 좋아요한 상품이 soft delete됨 → 응답에서 제외
- 비로그인 → 401

---

### 4.3 주문 (사용자)

#### 4.3.1 주문 생성

**[유저 스토리]**
- 사용자는 여러 상품을 한 번에 주문할 수 있다.
- 주문 후 상품 정보가 바뀌어도 "주문 당시의 정보"가 보존되어야 한다.
- 재고 없는 상품은 주문할 수 없다 (한 줄이라도 부족하면 전체 거부).

**[기능 흐름]**
1. 로그인 사용자만 가능
2. `items` 배열로 `(productId, quantity)` 다건 전달
3. 처리 순서:
   1. 모든 상품 존재 확인
   2. 모든 상품 재고 충분 확인 (한 줄이라도 부족하면 전체 거부)
   3. 재고 차감 (한 트랜잭션)
   4. Order 생성 + OrderItem 스냅샷 저장 (`status=PENDING`)
   5. PaymentGateway 호출
   6. 결제 성공 → `status=COMPLETED`
   7. 결제 실패 → 재고 복원 + `status=FAILED`
4. 스냅샷 범위: 가격, 상품명, 브랜드명, 이미지 URL

**API:** `POST /api/v1/orders` body `{ items: [{ productId, quantity }, ...] }`

**Main Flow:** 주문 ID + 정보 반환 (201)

**Exception Flow:**
- 일부 상품 재고 부족 → 400 (어떤 상품인지 표시)
- 일부 상품 존재 안 함 / soft delete됨 → 400
- `quantity` 0/음수 → 400
- 외부 결제 실패 → 재고 복원, `status=FAILED`, 502
- 비로그인 → 401

---

#### 4.3.2 주문 목록 조회

**[유저 스토리]**
- 사용자는 자신의 주문을 기간으로 필터링해서 볼 수 있다.

**[기능 흐름]**
1. 로그인 사용자만 가능
2. `startAt` / `endAt` 기간 필터 (기준: `Order.createdAt`)
3. 본인 주문만 조회

**API:** `GET /api/v1/orders?startAt=&endAt=`

**Main Flow:** 기간 내 본인 주문 목록 반환 (200)

**Exception Flow:**
- `startAt > endAt` → 400
- 비로그인 → 401

---

#### 4.3.3 주문 상세 조회

**[유저 스토리]**
- 사용자는 특정 주문의 상세 내역(스냅샷 포함)을 확인할 수 있다.

**[기능 흐름]**
1. 로그인 사용자만 가능
2. `orderId` 단건 조회
3. 본인 주문만 접근 가능

**API:** `GET /api/v1/orders/{orderId}`

**Main Flow:** 주문 상세 (스냅샷 포함) 반환 (200)

**Exception Flow:**
- 본인 주문 아님 → 404 (정보 노출 방지)
- 존재하지 않는 `orderId` → 404
- 비로그인 → 401

---

### 4.4 어드민 — 브랜드 관리

**[유저 스토리]**
- 어드민은 등록된 브랜드 목록을 페이지 단위로 조회할 수 있다.
- 어드민은 특정 브랜드의 상세를 조회할 수 있다.
- 어드민은 새 브랜드를 등록할 수 있다.
- 어드민은 등록된 브랜드의 정보를 수정할 수 있다.
- 어드민은 브랜드를 삭제할 수 있으며, **이때 해당 브랜드의 상품들도 함께 soft delete 되어야 한다.**

**[기능 흐름]**
1. 모든 API: `X-Loopers-Ldap` 헤더 검증
2. 삭제는 soft delete (`deleted_at`)
3. 브랜드 삭제 시 소속 상품 일괄 soft delete

**API & Main Flow:**
| API | 동작 |
|---|---|
| `GET /api-admin/v1/brands?page=&size=` | 페이징 브랜드 목록 (기본 `createdAt desc`) |
| `GET /api-admin/v1/brands/{brandId}` | 브랜드 상세 (메타데이터 포함) |
| `POST /api-admin/v1/brands` | 신규 등록 |
| `PUT /api-admin/v1/brands/{brandId}` | 정보 수정 |
| `DELETE /api-admin/v1/brands/{brandId}` | 브랜드 + 소속 상품 soft delete |

**Exception Flow:**
- 헤더 누락/잘못됨 → 401
- 중복 브랜드명 등록 → 400
- 브랜드명 빈 값/길이 초과 → 400
- 존재하지 않는 `brandId` 조작 → 404

**노출 정보 (어드민):** `id, name, description, createdAt, updatedAt, deletedAt`

---

### 4.5 어드민 — 상품 관리

**[유저 스토리]**
- 어드민은 등록된 상품 목록을 페이지 단위로 조회할 수 있다 (브랜드 필터 가능).
- 어드민은 특정 상품의 상세를 조회할 수 있다.
- 어드민은 새 상품을 등록할 수 있다 (이미 등록된 브랜드에 한해).
- 어드민은 등록된 상품의 정보를 수정할 수 있다 (**단, 브랜드는 변경 불가**).
- 어드민은 상품을 삭제할 수 있다.

**[기능 흐름]**
1. 모든 API: `X-Loopers-Ldap` 헤더 검증
2. 등록 시 `brandId` 존재 검증
3. 수정 요청에 `brandId` 변경 시도 → 400
4. 삭제는 soft delete

**API & Main Flow:**
| API | 동작 |
|---|---|
| `GET /api-admin/v1/products?page=&size=&brandId=` | 페이징 상품 목록 (brandId 필터) |
| `GET /api-admin/v1/products/{productId}` | 상품 상세 (메타데이터 포함) |
| `POST /api-admin/v1/products` | 신규 등록 (brandId 존재 검증) |
| `PUT /api-admin/v1/products/{productId}` | 정보 수정 (brandId 제외) |
| `DELETE /api-admin/v1/products/{productId}` | 상품 soft delete |

**Exception Flow:**
- 헤더 누락 → 401
- 존재하지 않는 `brandId`로 등록 → 400
- 가격 < 0, 재고 < 0 → 400
- 이름 빈 값 또는 길이 초과(>100자) → 400
- 존재하지 않는 `productId` 조작 → 404
- 수정 시 `brandId` 변경 시도 → 400

**노출 정보 (어드민):** `id, name, description, price, brandId, brandName, imageUrl, stockQuantity, likeCount, createdAt, updatedAt, deletedAt`

---

### 4.6 어드민 — 주문 조회

**[유저 스토리]**
- 어드민은 모든 사용자의 주문을 조회할 수 있다.

**[기능 흐름]**
1. `X-Loopers-Ldap` 헤더 검증
2. 페이징 목록 + 단건 상세 조회

**API & Main Flow:**
| API | 동작 |
|---|---|
| `GET /api-admin/v1/orders?page=&size=` | 페이징 전체 주문 목록 (기본 `createdAt desc`) |
| `GET /api-admin/v1/orders/{orderId}` | 주문 상세 (스냅샷 포함) |

**Exception Flow:**
- 헤더 누락 → 401
- 존재하지 않는 `orderId` → 404

---

## 5. 정책 / 규칙

### 5.1 멱등성
좋아요 등록·취소는 멱등. 같은 요청 N번 호출 = 1번 호출. 응답 코드는 항상 200. 클라이언트 재시도 안전성과 데이터 일관성을 위한 핵심 정책.

### 5.2 스냅샷
주문 시점의 `price`, `productName`, `brandName`, `imageUrl`을 `OrderItem`에 박제. 이후 상품 정보가 변경되거나 soft delete되어도 주문 내역에는 박제된 정보가 노출된다.

### 5.3 Soft Delete
모든 핵심 엔티티(`Brand`, `Product`, `Like`, `Order`)는 `deleted_at` 컬럼 보유. 조회 시 `WHERE deleted_at IS NULL` 필터 기본 적용. 어드민 응답에는 `deleted_at` 노출 (운영 가시성).

### 5.4 권한
사용자는 본인 정보(좋아요 목록, 주문)에만 접근 가능. 타 사용자 정보 접근 시도 시 **404 반환** (존재 여부 노출 방지).

### 5.5 재고
주문 시 재고 차감은 **All-or-Nothing**. 한 항목이라도 부족하면 전체 주문 거부. 외부 결제 실패 시 차감된 재고는 즉시 복원.

### 5.6 결제 (외부 PG)
`PaymentGateway`는 Port로 추상화. 실제 구현(Adapter)은 인프라 계층. 본 라운드는 Port 인터페이스 정의 수준. PG 응답은 모킹/Stub 전제. 풀 결제 도메인(Payment 엔티티, 상태 머신, 콜백/재시도)은 다음 라운드.

### 5.7 좋아요 수 집계
`Product.likeCount`는 캐시 컬럼. 좋아요 등록/취소와 `likeCount` 갱신은 같은 트랜잭션에서 처리. 동시성 정합성(낙관적 락 등)은 다음 라운드.

### 5.8 페이징
- `page`: 0 이상 정수, 기본값 0
- `size`: 1~100, 기본값 20
- 범위 외 값: 400

### 5.9 검증
- 브랜드명: 1~50자, 공백/빈값 거부, 중복 거부
- 상품명: 1~100자
- 가격/재고: 0 이상 정수
