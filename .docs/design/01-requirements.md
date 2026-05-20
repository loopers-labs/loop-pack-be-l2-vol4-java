# 01. 요구사항 정의서

## 1. 서비스 배경 / 문제 정의

### 1.1 사용자 관점
유저는 여러 브랜드의 상품을 탐색하고, 마음에 드는 상품에 좋아요를 누르고, 여러 상품을 한 번에 주문하고 싶다.


### 1.2 비즈니스 관점
유저의 좋아요·주문 행동 데이터를 누적해 향후 랭킹·추천으로 확장한다.


### 1.3 시스템 관점
다상품 주문 시 ① 재고 정합성, ② 주문 당시 상품 정보 스냅샷, ③ (향후) 외부 PG 결제와 주문 상태의 원자성이 필요하다.

---

## 2. 액터

| 액터 | 식별 방법 | 역할 |
|---|---|---|
| 일반 유저 | `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 | 상품 탐색, 좋아요, 주문 |
| 어드민 | `X-Loopers-Ldap: loopers.admin` 헤더 | 브랜드·상품 CRUD, 주문 조회 |
| 외부 결제 시스템 (PG) | — | **이번 라운드 미구현**. 시퀀스 다이어그램에 확장 지점으로만 표시 |

- 인증·인가는 구현하지 않는다. 헤더 기반 식별만 사용 (고객 `/api/v1`, 어드민 `/api-admin/v1`).
- 유저는 타 유저의 좋아요·주문에 직접 접근할 수 없다.
- 어드민은 전체 유저의 주문을 조회할 수 있다.

---

## 3. 핵심 도메인 / 외부 시스템

### 3.1 도메인

| 도메인 | 상태 | 역할 |
|---|---|---|
| User | Round 1 완료 (재사용) | 식별자 `userId` 보유 |
| Brand | 신규 | 상품의 소속 기준. 고객은 단건 조회, 어드민은 CRUD (삭제 시 소속 상품 cascade) |
| Product | 기존 확장 | `brandId` 참조 + `likeCount` 비정규화 컬럼. 재고는 보유하지 않음 (Stock 분리) |
| Stock | 신규 | **재고 자원의 단일 책임자**. Product와 1:1. 차감·복원 도메인 메서드 |
| Like | 신규 | `(userId, productId)` 유일성 보장. 멱등 등록/취소 |
| Order / OrderItem | 신규 | 다상품 주문. 스냅샷 보존. 재고 차감은 Stock 도메인에 위임 |

### 3.2 결제 흐름 참여 도메인 / 외부 시스템

이번 라운드 **시퀀스 다이어그램에는 등장**하지만, 도메인 세부 구조(모델·테이블·정책 등)는 정의하지 않는다. *흐름 단계*만 합의하고 구현은 다음 라운드.

| 대상 | 형태 | 본 라운드 표현 범위 |
|---|---|---|
| Payment | 도메인 (세부 미정의) | **PG 결제 책임자**. 주문 도메인은 결제 한 군데만 호출 |
| 외부 결제 시스템 (PG) | 외부 시스템 | `PaymentGatewayClient` Port 호출로 표현. 통신·응답 형식 미정 |

> "외부 시스템 연동" = 본 라운드 맥락에서 **외부 결제 시스템(PG)** 을 의미. 향후 알림·배송 등 다른 외부 시스템 추가 시 별도 어휘 사용.

---

## 4. 기능 요구사항 (도메인별 — 고객 / 어드민 쌍)

> 같은 도메인이라도 **고객(`/api/v1`, LoginId/Pw)** 과 **어드민(`/api-admin/v1`, Ldap)** 의 오퍼레이션·노출 정보가 다르다. 도메인 모델은 공유하고 응답 DTO만 분리한다 (D15).

### 4.1 Brand

| 구분 | 기능 | METHOD · URI | 식별 | 설명 |
|---|---|---|---|---|
| 고객 | 브랜드 단건 조회 | `GET /api/v1/brands/{brandId}` | — | 공개 정보 |
| 어드민 | 브랜드 목록 조회 | `GET /api-admin/v1/brands?page=&size=` | Ldap | 등록된 브랜드 목록 (페이징) |
| 어드민 | 브랜드 상세 조회 | `GET /api-admin/v1/brands/{brandId}` | Ldap | 관리 정보 포함 |
| 어드민 | 브랜드 등록 | `POST /api-admin/v1/brands` | Ldap | — |
| 어드민 | 브랜드 수정 | `PUT /api-admin/v1/brands/{brandId}` | Ldap | — |
| 어드민 | 브랜드 삭제 | `DELETE /api-admin/v1/brands/{brandId}` | Ldap | **소속 상품·재고 cascade soft delete** (D14) |

**고객 / 어드민 응답 차등**
- 고객: `id`, `name`, `description`
- 어드민: + 소속 상품 수, 생성·수정 일시, 삭제 여부(soft delete 상태)

**시나리오 / 제약**
- 존재하지 않거나 soft delete된 브랜드 조회 → `404 NOT_FOUND`.
- 어드민 목록 조회는 **active(미삭제) 기준** — "등록된" 의미.
- 브랜드 삭제 시 **소속 상품과 각 상품의 재고까지 soft delete cascade** (D14). 물리 cascade(FK) 미사용 — 도메인 메서드로 수행.

---

### 4.2 Product

| 구분 | 기능 | METHOD · URI | 식별 | 설명 |
|---|---|---|---|---|
| 고객 | 상품 목록 조회 | `GET /api/v1/products` | — | 필터·정렬·페이징 |
| 고객 | 상품 단건 조회 | `GET /api/v1/products/{productId}` | — | — |
| 어드민 | 상품 목록 조회 | `GET /api-admin/v1/products?page=&size=&brandId=` | Ldap | 등록된 상품 목록 |
| 어드민 | 상품 상세 조회 | `GET /api-admin/v1/products/{productId}` | Ldap | 재고·관리 정보 포함 |
| 어드민 | 상품 등록 | `POST /api-admin/v1/products` | Ldap | **브랜드 존재 검증 + 초기 재고 동시 생성** (D16) |
| 어드민 | 상품 수정 | `PUT /api-admin/v1/products/{productId}` | Ldap | **브랜드 변경 불가** |
| 어드민 | 상품 삭제 | `DELETE /api-admin/v1/products/{productId}` | Ldap | 상품 + 재고 soft delete |

**상품 목록 쿼리 파라미터 (고객)**

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `brandId` | 없음 | 특정 브랜드 상품 필터 |
| `sort` | `latest` | `latest`, `price_asc`, `likes_desc` |
| `page` | `0` | — |
| `size` | `20` | — |

**고객 / 어드민 응답 차등**
- 고객: `id`, `name`, `description`, `price`, `brand(id, name)`, 좋아요 수, **구매 가능 여부(재고 > 0)**
- 어드민: + **재고 수량(정확값)**, 생성·수정 일시, 삭제 여부
- 고객에겐 정확한 재고 수량을 노출하지 않고 *구매 가능 여부*만 제공 (D15).

**시나리오 / 제약**
- (고객) 상품 목록을 페이지 단위로 조회하고, 브랜드 필터·정렬(최신/가격 오름차순/좋아요 내림차순)을 선택할 수 있다.
- (고객) 삭제된 상품은 목록·단건 모두 노출되지 않는다. 재고가 0인 상품은 노출하되 *구매 가능 여부*가 false.
- (어드민) 상품 등록 시 **이미 등록된 브랜드**여야 한다 — 미존재 브랜드 → `404 NOT_FOUND`.
- (어드민) 상품 수정 시 **브랜드는 변경 불가** — 요청에 다른 brandId가 와도 무시/거부.
- (어드민) 상품 등록은 Product + Stock(초기 수량)을 **함께 생성** (D16).

---

### 4.3 Like *(고객 전용 — 어드민 오퍼레이션 없음)*

| 기능 | API | 설명 |
|---|---|---|
| 좋아요 등록 | `POST /api/v1/products/{productId}/likes` | **멱등** |
| 좋아요 취소 | `DELETE /api/v1/products/{productId}/likes` | **멱등** |
| 내 좋아요 목록 | `GET /api/v1/users/{userId}/likes` | 본인만 |

**유저 시나리오**
- 이미 좋아요한 상품에 다시 등록해도 에러 없이 통과한다 (멱등).
- 좋아요하지 않은 상품을 취소해도 에러 없이 통과한다 (멱등).
- 타 유저의 좋아요 목록은 접근 불가.
- 삭제된 상품에는 좋아요를 등록할 수 없다 → `404`.

**제약**
- `(user_id, product_id)` 조합은 유일 (UNIQUE 제약).
- 좋아요 수는 `product.like_count` 비정규화 컬럼으로 관리한다. 등록/취소가 *실제로 반영될 때만* 증감하며(멱등), `likes_desc` 정렬도 이 컬럼 기반.
- `product_like`는 hard delete (`created_at`만, soft delete 미사용).

---

### 4.4 Order

| 구분 | 기능 | METHOD · URI | 식별 | 설명 |
|---|---|---|---|---|
| 고객 | 주문 생성 | `POST /api/v1/orders` | LoginId/Pw | 재고 차감 + 스냅샷 저장 |
| 고객 | 주문 목록 조회 | `GET /api/v1/orders?startAt=&endAt=` | LoginId/Pw | 본인 주문, 날짜 범위 |
| 고객 | 주문 단건 조회 | `GET /api/v1/orders/{orderId}` | LoginId/Pw | 본인 주문만 |
| 어드민 | 주문 목록 조회 | `GET /api-admin/v1/orders?page=&size=` | Ldap | **전체 유저** 주문 (페이징) |
| 어드민 | 주문 단건 조회 | `GET /api-admin/v1/orders/{orderId}` | Ldap | 임의 주문 |

**고객 / 어드민 차등**
- 고객: 본인 주문만, 날짜 범위(`startAt`/`endAt`) 필터.
- 어드민: 전체 유저 주문, 유저 식별자 포함, 페이징.

**주문 생성 요청 예시**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

**유저 시나리오**
- 유저는 여러 상품을 한 번에 주문할 수 있다.
- 주문 시 각 상품의 재고를 확인·차감한다.
- 재고가 부족한 상품이 하나라도 있으면 *전체 주문 실패* → `409 CONFLICT`.
- 존재하지 않는 상품 ID 포함 시 *전체 주문 실패* → `404 NOT_FOUND`.
- 주문 정보에는 *주문 당시* 상품 정보(`name`, `unitPrice`)가 OrderItem 스냅샷으로 저장된다.
- 주문 후 상품의 가격·이름이 바뀌어도 주문 내역은 *당시 값* 그대로 유지.
- 유저는 날짜 범위로 자신의 주문 목록을 조회할 수 있다.
- 타 유저의 주문은 접근 불가 (어드민은 전체 조회 가능).

**제약**
- 재고 차감과 주문 저장은 **단일 트랜잭션**.
- 스냅샷 필드 = `productId`, `productName`, `unitPrice`, `quantity` 4개로 한정.
- 결제는 이번 라운드 미구현 — **본 라운드 구현 범위는 주문 생성·재고 차감까지이며 Order는 `CREATED`로 종료**한다. `SUCCEEDED`/`FAILED` 분기는 결제 합류 시 확정되며, 시퀀스 다이어그램에는 *향후 흐름*으로만 그린다.

**결제 흐름 (시퀀스 표현만, 구현은 다음 라운드)**

주문 생성은 다음 단계를 동반한다. 본 라운드에서는 시퀀스 다이어그램에 *흐름*만 명시하고 도메인은 구현하지 않는다.

1. 주문 생성 + 재고 차감 *(본 라운드 구현)*
2. **결제 요청** — 주문 도메인이 결제 도메인 한 군데만 호출 (`PaymentService.pay(orderId, totalAmount)`)
3. 결제 도메인 내부에서:
   - **외부 PG 호출** — `PaymentGatewayClient.request(orderId, totalAmount)`
   - **Payment record 저장** — 지불 금액·결과를 *지불 사실의 단일 진실 원천*으로 보존
4. 결과에 따른 분기
   - 성공 → `SUCCEEDED`
   - 실패 → 결제 도메인이 *자기 영역* 보상(PG 취소). 그 후 주문 도메인이 *상품 도메인에게* 재고 복원 요청. **재고 자원은 상품 도메인 단일 책임이며, 다른 도메인은 직접 만지지 않는다.**

분기·보상 정책의 *상세 결정*은 결제 도메인 brainstorming 시점에 재논의.

---

## 5. 비기능 / 제약 사항

| 항목 | 기준 |
|---|---|
| 인증 방식 | 헤더 기반 식별. 인증·인가 구현 제외 |
| API prefix | 대고객 `/api/v1` (`LoginId`/`LoginPw`). 어드민 `/api-admin/v1` (`Ldap`) |
| 페이지네이션 | `page`/`size` 기반. 기본 `page=0`, `size=20` |
| Soft delete | `BaseEntity.delete()` / `restore()` 사용. 멱등 |
| 주문 스냅샷 | 주문 시점 상품 정보 불변 보존 |
| 타임존 | App `Asia/Seoul`, DB 저장은 UTC 정규화 |

---

## 6. 이번 라운드 결정사항

| # | 결정 | 사유 / 영향 |
|---|---|---|
| D1 | 결제 흐름을 **시퀀스 레벨에서만 표현**. Payment 도메인의 세부 모델·테이블·정책은 정의하지 않음 | Quest 원문 "결제 흐름 (재고 차감, 외부 시스템 연동)" 충실 + brainstorming 비용 절제. 구현은 다음 라운드. *흐름 단계*가 시퀀스에 그려져 있으면 다음 라운드 진입 시 시퀀스 재작성 불필요 |
| D2 | OrderItem 스냅샷 = `productId, productName, unitPrice, quantity` 4개 최소 | 영수증 수준의 완전 복사는 과잉. 브랜드명·이미지는 조회 시 fallback |
| D3 | 좋아요 수는 `product.like_count` **비정규화 컬럼**으로 관리. 등록/취소 반영 시 증감, `likes_desc` 정렬·목록 노출은 컬럼 기반 | COUNT 집계는 상품 대량 시 정렬·조회 비용이 큼. 비정규화로 목록 조회·정렬을 단순 컬럼으로 처리. `product_like` 테이블은 관계·멱등의 진실 원천으로 유지하며 증감 근거가 된다 |
| D4 | `OrderStatus` enum 도입. 값: `CREATED` → `SUCCEEDED` / `FAILED` | 시퀀스에 그려진 결제 흐름의 성공·실패 분기와 일치. `CANCELED`(결제 후 취소) 등은 추후 라운드에 추가 |
| D5 | 어드민 API **포함**. 대고객/어드민을 prefix·헤더로 분리 (`/api/v1`+LoginId/Pw vs `/api-admin/v1`+Ldap) | 요구사항 명세에 어드민 브랜드·상품 CRUD와 주문 조회가 명시됨. 도메인 모델은 공유하고 오퍼레이션·노출 정보만 분리 |
| D6 | 좋아요 멱등 = `UNIQUE(user_id, product_id)` + 서비스 분기 | DB 예외 의존 안 함. 흐름 가독성 우선 |
| D7 | **다도메인 협력 흐름은 Facade `@Transactional` 합성 (B안)**. 주문은 `OrderFacade`가 `ProductService` + `StockService` + `OrderService` 호출, 좋아요는 `LikeFacade`가 `ProductService` + `LikeService` 호출 | 도메인별 책임 분리 강화 + Service-Service 의존 회피. 외부 I/O 없는 본 라운드 한정 — 결제 합류 시 외부 호출은 트랜잭션 밖으로 분리 |
| D8 | `order_item.product_id` = 단순 BIGINT (논리적 FK도 약하게, JPA 연관 매핑 없음) | 스냅샷 의미 강조 + 상품 hard delete와 무관하게 주문 이력 보존. 다른 컬럼들과 달리 JPA `@ManyToOne` 매핑도 두지 않음 |
| D9 | `like` 테이블명 → `product_like` | SQL 예약어 회피 + 의미 명확성 |
| D10 | `order` 테이블명 → `orders` | SQL 예약어 회피 |
| D11 | 주문 도메인은 **결제 도메인 한 군데만 호출**(주문 → 결제 → PG). 주문이 PG Client를 직접 들지 않음 | Payment record가 *지불 사실의 단일 진실 원천*. 결제 수단·정책이 늘어나도 주문은 결제 인터페이스만 의존해 변경이 격리된다 |
| D12 | **논리적 FK는 명시, 물리적 FK 제약(DB constraint)은 사용 안 함**. JPA `@ManyToOne` 매핑은 유지하되 DDL에서 FK 제약 제외 | INSERT/UPDATE 성능, 데드락 회피, CASCADE 위험 제거. 무결성은 애플리케이션 레이어에서 검증 |
| D13 | **Stock 도메인 분리** (Product와 1:1 별도 테이블). 재고 자원의 단일 책임자 | Product에 다중 책임(카탈로그 + 재고 + 좋아요 카운트) 누적 방지. 재고는 *write-heavy*라 *read-heavy*인 카탈로그와 트래픽 분리 |
| D14 | 브랜드 삭제 = **소프트 cascade** (브랜드 → 소속 상품 → 각 상품 재고). 어드민 도메인 메서드로 수행 | BaseEntity soft delete 규약 일관 + 복원 가능성·이력 보존. 물리 cascade(FK `ON DELETE`)는 D12(FK 제약 미사용)와 충돌하므로 앱 레벨에서 처리 |
| D15 | 고객/어드민 **응답 정보 차등** (별도 DTO). 고객=카탈로그+구매 가능 여부, 어드민=재고 수량·삭제 상태·타임스탬프 포함 | 명세 "고객/어드민 제공 정보 고민" 반영. 정확한 재고 수량은 고객에 미노출(매점·경쟁 정보 차단), 어드민은 운영상 필요 |
| D16 | 어드민 상품 등록 = **Product + Stock 동시 생성** (Facade 합성). 수정 시 브랜드 변경 불가 | Stock과 Product는 1:1(D13)이라 상품 신설 시 재고 행도 함께 생성돼야 정합. 브랜드 불변은 명세 제약 |
