# 01. 요구사항 명세

## 개요

| 항목 | 내용 |
|------|------|
| 목적 | 커머스 플랫폼에서 사용자가 상품을 탐색하고, 좋아요를 남기고, 주문·결제를 완료하는 핵심 흐름을 제공한다 |
| 대상 모듈 | `apps/commerce-api` |
| 작성일 | 2026-05-18 (주문/결제: 2026-05-20) |

---

## 사용자 유형

| 유형 | 식별 방법 | 접근 가능 목표 |
|------|-----------|---------------|
| 비인증 사용자 | 헤더 없음 | 상품 탐색, 브랜드 조회 |
| 인증된 사용자 | `X-User-Id` 또는 `X-Loopers-LoginId/Pw` 헤더 | 좋아요, 주문, 결제, 내 주문 조회 |
| Admin | `X-Loopers-Ldap: loopers.admin` 헤더 | 전체 주문 관리 |

---

## 사용자 목표 1: 원하는 상품 찾기

> **비인증 사용자**가 상품 목록을 탐색하고 상세 정보를 확인한다.

### 왜 이 기능이 필요한가

사용자는 구매 결정 전에 상품을 자유롭게 둘러봐야 한다.
로그인 없이도 탐색이 가능해야 유입 사용자의 이탈을 줄일 수 있다.
브랜드 필터와 정렬 기능이 없으면 상품이 많아질수록 탐색 경험이 나빠진다.

### 사용자 스토리

- 쇼핑몰 방문자로서, 최신 상품 목록을 페이징으로 탐색하고 싶다. 한 번에 전체를 불러오면 느리기 때문이다.
- 쇼핑몰 방문자로서, 선호 브랜드의 상품만 필터링하고 싶다. 관심 없는 브랜드 상품은 보고 싶지 않기 때문이다.
- 쇼핑몰 방문자로서, 가격 오름차순이나 인기순으로 정렬을 바꾸고 싶다. 예산과 인기도를 기준으로 빠르게 선택하기 위해서다.
- 쇼핑몰 방문자로서, 관심 상품의 상세 정보(이름, 설명, 가격, 재고, 좋아요 수, 브랜드)를 확인하고 싶다. 구매 결정 전 충분한 정보가 필요하기 때문이다.

### 시나리오

**시나리오 1: 상품 목록 탐색**
1. 사용자가 상품 목록 페이지에 접속한다
2. 최신순(기본)으로 정렬된 상품 목록이 20개씩 페이징되어 노출된다
3. 각 상품에는 이름, 설명, 가격, 재고, 좋아요 수, 브랜드명이 표시된다
4. 사용자가 다음 페이지로 이동하여 추가 상품을 확인한다

**시나리오 2: 브랜드별 필터링**
1. 사용자가 브랜드를 선택한다 (`brandId` 파라미터)
2. 해당 브랜드의 상품만 필터링되어 노출된다
3. 해당 브랜드의 상품이 없으면 빈 목록이 반환된다 (에러 아님)

**시나리오 3: 정렬 변경**
1. 사용자가 정렬 기준을 선택한다 (`latest` / `price_asc` / `likes_desc`)
2. 선택한 기준으로 재정렬된 목록이 노출된다
3. 잘못된 정렬 값 입력 시 400 에러가 반환된다

**시나리오 4: 상품 상세 조회**
1. 사용자가 특정 상품을 선택한다
2. 상품의 상세 정보가 노출된다
3. 존재하지 않거나 삭제된 상품 조회 시 404 에러가 반환된다

### 완료 기준

- [ ] `page`, `size`, `sort`, `brandId` 파라미터가 모두 지원된다
- [ ] 기본 정렬은 `latest`(최신순)이다
- [ ] 삭제된 상품은 목록·상세 모두에서 제외된다
- [ ] 존재하지 않는 상품 → 404, 잘못된 sort → 400

### API 명세 (지원 상세)

**상품 목록 조회** `GET /api/v1/products`

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `brandId` | Long | — | 브랜드 필터 |
| `sort` | String | `latest` | `latest` / `price_asc` / `likes_desc` |
| `page` | int | `0` | 페이지 번호 |
| `size` | int | `20` | 페이지당 상품 수 |

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      { "id": 1, "name": "에어맥스 90", "description": "클래식 러닝화",
        "price": 159000, "stock": 50, "likeCount": 120, "brandId": 1, "brandName": "나이키" }
    ],
    "page": 0, "size": 20, "totalElements": 100, "totalPages": 5
  }
}
```

**상품 상세 조회** `GET /api/v1/products/{productId}`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "id": 1, "name": "에어맥스 90", "price": 159000, "stock": 50,
            "likeCount": 120, "brandId": 1, "brandName": "나이키" }
}
```

---

## 사용자 목표 2: 브랜드 정보 확인하기

> **비인증 사용자**가 브랜드 페이지에서 상세 정보를 확인한다.

### 왜 이 기능이 필요한가

처음 접하는 브랜드의 신뢰도를 판단하기 위해 설명과 로고가 필요하다.
상품 목록에 브랜드명만 표시되므로, 클릭해서 더 자세한 정보를 볼 수 있어야 한다.

### 사용자 스토리

- 쇼핑몰 방문자로서, 브랜드 이름·설명·로고를 확인하고 싶다. 처음 보는 브랜드의 신뢰도를 판단하기 위해서다.

### 시나리오

**시나리오 5: 브랜드 정보 조회**
1. 사용자가 브랜드 페이지에 접속한다
2. 브랜드 이름, 설명, 로고 URL이 노출된다
3. 존재하지 않거나 삭제된 브랜드 조회 시 404 에러가 반환된다

### 완료 기준

- [ ] 브랜드 id로 이름, 설명, 로고 URL을 조회할 수 있다
- [ ] 삭제된 브랜드는 404로 처리된다

### API 명세 (지원 상세)

**브랜드 조회** `GET /api/v1/brands/{brandId}`

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "id": 1, "name": "나이키", "description": "스포츠 브랜드", "logoUrl": "https://example.com/logo.png" }
}
```

---

## 사용자 목표 3: 상품 좋아요 남기기 / 취소하기

> **인증된 사용자**가 마음에 드는 상품에 좋아요를 표시하거나 취소한다.

### 왜 이 기능이 필요한가

좋아요는 관심 상품을 저장하는 가장 빠른 방법이다.
누적된 좋아요 수는 다른 사용자에게 인기 지표로 활용된다.
멱등 설계 없이 구현하면 네트워크 재시도 시 좋아요가 중복 등록되어 count가 오염된다.

### 사용자 스토리

- 인증된 사용자로서, 관심 상품에 좋아요를 누르고 싶다. 나중에 다시 찾아보기 위해서다.
- 인증된 사용자로서, 더 이상 관심 없는 상품의 좋아요를 취소하고 싶다. 좋아요 목록을 최신 관심사 위주로 유지하기 위해서다.

### 시나리오

**시나리오 6: 상품 좋아요 등록**
1. 사용자가 상품 상세 페이지에서 좋아요 버튼을 클릭한다
2. 좋아요가 등록되고, 상품의 좋아요 수가 1 증가한다
3. 이미 좋아요한 상품에 다시 요청해도 중복 등록되지 않는다 (멱등)

**시나리오 7: 상품 좋아요 취소**
1. 사용자가 이미 좋아요한 상품에서 좋아요 버튼을 다시 클릭한다
2. 좋아요가 취소되고, 상품의 좋아요 수가 1 감소한다
3. 좋아요하지 않은 상품에 취소를 요청해도 에러 없이 성공 응답한다 (멱등)

### 완료 기준

- [ ] 등록/취소 모두 멱등하게 동작한다
- [ ] 등록 시 `product.likeCount` +1, 취소 시 -1이 동기 반영된다
- [ ] `X-User-Id` 헤더 누락 시 400, 존재하지 않는 상품 → 404

### API 명세 (지원 상세)

**좋아요 등록** `POST /api/v1/products/{productId}/likes`
**좋아요 취소** `DELETE /api/v1/products/{productId}/likes`
헤더: `X-User-Id: {userId}` (필수)

```json
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null }, "data": null }
```

---

## 사용자 목표 4: 내 좋아요 목록 보기

> **인증된 사용자**가 자신이 좋아요한 상품 목록을 확인한다.

### 왜 이 기능이 필요한가

사용자는 관심 표시한 상품을 나중에 다시 찾아 구매를 검토한다.
별도 목록이 없으면 다시 상품을 검색해야 하므로 구매 전환율이 낮아진다.

### 사용자 스토리

- 인증된 사용자로서, 내가 좋아요한 상품 목록을 최신순으로 확인하고 싶다. 나중에 구매를 검토하기 위해서다.

### 시나리오

**시나리오 8: 내가 좋아요한 상품 목록 조회**
1. 사용자가 마이페이지에서 좋아요 목록을 조회한다
2. 좋아요한 시간 최신순으로 페이징된 상품 목록이 노출된다
3. 좋아요한 상품이 없으면 빈 목록이 반환된다

### 완료 기준

- [ ] 본인의 좋아요 목록만 조회된다
- [ ] 좋아요한 시간 최신순으로 정렬된다
- [ ] 페이징(page, size)이 지원된다

### API 명세 (지원 상세)

**내 좋아요 목록** `GET /api/v1/users/{userId}/likes`
헤더: `X-User-Id: {userId}` (필수)

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      { "id": 1, "name": "에어맥스 90", "price": 159000, "stock": 50,
        "likeCount": 120, "brandId": 1, "brandName": "나이키" }
    ],
    "page": 0, "size": 20, "totalElements": 5, "totalPages": 1
  }
}
```

---

## 사용자 목표 5: 원하는 상품을 담아 주문 만들기

> **인증된 사용자**가 여러 상품과 수량을 선택해 주문을 생성한다.

### 왜 이 기능이 필요한가

상품 탐색 후 구매 의사를 시스템에 기록해야 한다.
재고가 없는 상품을 주문하면 결제 후 취소를 겪는 최악의 경험이 생기므로,
주문 시점에 재고를 즉시 확인·차감한다.

### 사용자 스토리

- 인증된 사용자로서, 여러 상품과 수량을 한 번에 담아 주문하고 싶다. 여러 번 결제하는 번거로움을 피하기 위해서다.
- 인증된 사용자로서, 주문 생성 시 재고 부족 여부를 즉시 알고 싶다. 결제 후에야 재고 문제를 알게 되는 상황을 피하기 위해서다.

### 시나리오

**시나리오 9: 주문 생성 (정상)**
1. 사용자가 원하는 상품과 수량을 선택한다
2. 주문 생성 요청 시 각 상품의 재고를 확인한다
3. 재고가 충분하면 재고를 차감하고 주문을 생성한다
4. 주문 아이템에 당시 상품명·단가가 스냅샷으로 저장된다
5. 주문은 PENDING 상태로 생성된다

**시나리오 10: 재고 부족으로 주문 실패**
1. 사용자가 재고보다 많은 수량을 요청한다
2. 400 에러와 함께 재고 부족 메시지가 반환된다
3. 어떤 상품도 재고가 차감되지 않는다 (부분 성공 없음)

### 완료 기준

- [ ] items 1개 이상, 각 quantity 1 이상이어야 한다
- [ ] 재고 < 요청 수량이면 주문 전체가 실패하고 재고가 차감되지 않는다
- [ ] productName, unitPrice가 스냅샷으로 저장된다
- [ ] 생성된 주문의 초기 상태는 PENDING이다

### API 명세 (지원 상세)

**주문 생성** `POST /api/v1/orders`
헤더: `X-Loopers-LoginId`, `X-Loopers-LoginPw`

```json
// Request
{ "items": [{ "productId": 1, "quantity": 2 }, { "productId": 3, "quantity": 1 }] }

// Response
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "orderId": 1, "status": "PENDING", "totalPrice": 318000,
    "items": [{ "productId": 1, "productName": "에어맥스 90", "unitPrice": 159000, "quantity": 2 }],
    "createdAt": "2026-05-20T10:00:00"
  }
}
```

---

## 사용자 목표 6: 주문 결제 완료하기

> **인증된 사용자**가 생성된 주문에 대해 결제를 완료한다.

### 왜 이 기능이 필요한가

주문 생성만으로는 거래가 확정되지 않는다.
결제 완료 후에야 구매가 확정되고 배송 흐름으로 이어진다.
결제는 외부 PG(Payment Gateway) 시스템을 경유하므로 네트워크 실패, PG 거절 등의 예외 흐름을 명확히 설계해야 한다.

### 외부 시스템 연동 설계

**PgClient 인터페이스 (포트)**
- domain 레이어에 `PgClient` 인터페이스를 선언한다 (domain이 외부에 직접 의존하지 않도록)
- infrastructure 레이어에서 구현체를 제공한다 (현재: `StubPgClient`, 추후: 실제 PG 어댑터)
- 요청: `orderId`, `amount`, `idempotencyKey`
- 응답: `success`, `transactionId`(성공 시), `failureReason`(실패 시)

**멱등키 (Idempotency Key)**
- 네트워크 재시도로 인한 이중 결제를 방지하기 위해 PG 호출 시 멱등키를 함께 전송한다
- 멱등키는 `memberId-orderId` 조합으로 생성한다 (동일 주문에 대한 중복 결제 원천 차단)
- `PaymentModel`에 `idempotencyKey`와 `pgTransactionId`를 저장한다

**PG 실패 시 상태 처리**
- PG 호출이 실패하면 주문은 PENDING 상태를 유지한다 (재결제 시도 가능)
- 재고는 주문 생성 시 이미 차감된 상태이므로 PG 실패 시 재고를 복구하지 않는다
- 주문을 취소할 경우에만 재고를 복구한다 (추후 구현)

### 사용자 스토리

- 인증된 사용자로서, PENDING 상태의 주문에 결제를 완료하고 싶다. 구매를 최종 확정하기 위해서다.
- 인증된 사용자로서, 결제가 실패했을 때 명확한 실패 이유를 알고 싶다. 재시도 여부를 판단하기 위해서다.

### 시나리오

**시나리오 11: 결제 요청 성공**
1. 사용자가 PENDING 상태의 주문에 결제를 요청한다
2. 시스템이 멱등키(`memberId-orderId`)를 생성해 PG에 결제를 요청한다
3. PG가 성공 응답과 함께 `pgTransactionId`를 반환한다
4. 주문 상태가 PENDING → PAID로 변경된다
5. `PaymentModel`이 `idempotencyKey`, `pgTransactionId`와 함께 저장된다

**시나리오 12: PG 결제 실패**
1. 사용자가 PENDING 상태의 주문에 결제를 요청한다
2. PG가 실패 응답을 반환한다 (잔액 부족, 카드 거절 등)
3. 502 PAYMENT_FAILED 에러가 반환된다
4. 주문은 PENDING 상태를 유지한다 (재결제 시도 가능)
5. 재고는 복구되지 않는다 (주문 취소 시에만 복구)

**시나리오 13: 이미 결제된 주문에 재결제 시도**
1. 사용자가 PAID 상태의 주문에 결제를 재요청한다
2. 409 CONFLICT 에러가 반환된다

**시나리오 14: 동일 요청 중복 전송 (멱등 보장)**
1. 네트워크 오류로 동일한 결제 요청이 두 번 전송된다
2. 멱등키 덕분에 PG는 두 번째 요청을 중복으로 인식하여 동일 결과를 반환한다
3. 사용자는 한 번만 결제된다

### 완료 기준

- [ ] PENDING 상태 주문에만 결제 가능하다
- [ ] PG 결제 성공 시 주문 상태 PAID 변경 + `PaymentModel` 저장
- [ ] `PaymentModel`에 `idempotencyKey`와 `pgTransactionId`가 저장된다
- [ ] PG 결제 실패 시 502 PAYMENT_FAILED 응답이 반환되고 주문은 PENDING을 유지한다
- [ ] PENDING이 아닌 주문에 결제 시 409
- [ ] 동일 `memberId-orderId`에 대한 중복 결제가 발생하지 않는다

### API 명세 (지원 상세)

**결제 요청** `POST /api/v1/payments`
헤더: `X-Loopers-LoginId`, `X-Loopers-LoginPw`

```json
// Request
{ "orderId": 1 }

// Response (성공)
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "paymentId": 1, "orderId": 1, "status": "PAID", "amount": 318000, "paidAt": "2026-05-20T10:01:00" }
}

// Response (PG 실패)
{
  "meta": { "result": "FAIL", "errorCode": "PAYMENT_FAILED", "message": "잔액이 부족합니다." },
  "data": null
}
```

---

## 사용자 목표 7: 내 주문 내역 확인하기

> **인증된 사용자**가 기간 조건으로 본인의 주문 목록과 상세를 조회한다.

### 왜 이 기능이 필요한가

사용자는 구매 이력을 다시 확인하거나 배송 상태를 추적하고 싶어한다.
타인의 주문이 노출되면 심각한 개인정보 침해이므로 본인 주문만 조회 가능해야 한다.

### 사용자 스토리

- 인증된 사용자로서, 특정 기간의 내 주문 목록을 보고 싶다. 어떤 상품을 언제 구매했는지 파악하기 위해서다.
- 인증된 사용자로서, 특정 주문의 아이템 상세(스냅샷 단가·수량 포함)를 확인하고 싶다. 당시 구매 내역을 정확히 기억하기 위해서다.

### 시나리오

**시나리오 13: 주문 목록 조회**
1. 사용자가 `startAt`, `endAt`을 지정하여 주문 목록을 요청한다
2. 해당 기간 내 본인의 주문만 최신순으로 반환된다

**시나리오 14: 주문 상세 조회**
1. 사용자가 `orderId`로 주문 상세를 요청한다
2. 본인의 주문이면 아이템 목록(스냅샷)이 반환된다
3. 타인의 주문 조회 시 404 에러가 반환된다

### 완료 기준

- [ ] `startAt`, `endAt`(inclusive) 기간 내 본인 주문만 조회된다
- [ ] 타인의 orderId 조회 시 404
- [ ] 주문 상세에 스냅샷(productName, unitPrice, quantity) 포함
- [ ] 정렬: `createdAt DESC`

### API 명세 (지원 상세)

**주문 목록** `GET /api/v1/orders?startAt=2026-01-31&endAt=2026-02-10`
**주문 상세** `GET /api/v1/orders/{orderId}`
헤더: `X-Loopers-LoginId`, `X-Loopers-LoginPw`

---

## 사용자 목표 8: [Admin] 전체 주문 관리하기

> **Admin**이 전체 회원의 주문을 조회하고 관리한다.

### 왜 이 기능이 필요한가

CS 대응, 이상 거래 감지, 운영 모니터링을 위해 Admin은 모든 회원의 주문에 접근해야 한다.
일반 사용자와 경로를 분리(`/api-admin/`)하여 의도치 않은 접근을 방지한다.

### 사용자 스토리

- Admin으로서, 전체 회원의 주문 목록을 페이징으로 조회하고 싶다. 운영 이슈 대응을 위해서다.
- Admin으로서, 특정 주문의 상세 내역을 확인하고 싶다. CS 문의를 처리하기 위해서다.

### 시나리오

**시나리오 15: Admin 주문 목록 조회**
1. Admin이 `X-Loopers-Ldap: loopers.admin` 헤더를 포함하여 `/api-admin/v1/orders`에 접근한다
2. 전체 회원의 주문을 페이징으로 조회한다

**시나리오 16: Admin 헤더 없이 접근 시도**
1. Admin 헤더 없이 `/api-admin/**`에 접근한다
2. 401 에러가 반환된다

### 완료 기준

- [ ] Admin 헤더 없이 접근 시 401
- [ ] Admin은 모든 회원의 주문을 조회할 수 있다

### API 명세 (지원 상세)

**[Admin] 주문 목록** `GET /api-admin/v1/orders?page=0&size=20`
**[Admin] 주문 상세** `GET /api-admin/v1/orders/{orderId}`
헤더: `X-Loopers-Ldap: loopers.admin`

---

## 예외 케이스 (전체)

| 상황 | HTTP Status | ErrorType |
|------|-------------|-----------|
| 존재하지 않는 productId 조회 | 404 | `NOT_FOUND` |
| 존재하지 않는 brandId 조회 | 404 | `NOT_FOUND` |
| 삭제된 상품/브랜드 조회 | 404 | `NOT_FOUND` |
| 잘못된 sort 값 | 400 | `BAD_REQUEST` |
| brandId 필터 시 상품 없음 | 200 | — (빈 목록) |
| `X-User-Id` 헤더 누락 | 400 | `BAD_REQUEST` |
| 이미 좋아요한 상품에 등록 | 200 | — (멱등) |
| 좋아요하지 않은 상품에 취소 | 200 | — (멱등) |
| items 비어있음 | 400 | `BAD_REQUEST` |
| quantity < 1 | 400 | `BAD_REQUEST` |
| 재고 부족 | 400 | `BAD_REQUEST` |
| 타인의 주문 조회 | 404 | `NOT_FOUND` |
| PENDING이 아닌 주문에 결제 | 409 | `CONFLICT` |
| PG 결제 실패 (잔액 부족, 카드 거절 등) | 502 | `PAYMENT_FAILED` |
| Admin 헤더 없이 `/api-admin/**` 접근 | 401 | `UNAUTHORIZED` (신규 추가 필요) |

---

## 미결정 사항

| 항목 | 현재 상태 | 결정 필요 시점 |
|------|-----------|---------------|
| likeCount 동시성 제어 | 미정 (낙관적 락 vs 비관적 락) | 좋아요 API 구현 시 |
| 재고 차감 동시성 제어 | 미적용 (락 없음) | 트래픽 증가 시점 |
| size 최대값 제한 | 미정 | 구현 시 |
| User 인증 체계 전환 | 현재 헤더 → 추후 토큰 기반 | 인증 시스템 도입 시 |
| 주문 취소 기능 | 미포함 | 추후 요구사항 |
| `UNAUTHORIZED` ErrorType 추가 | 미정 | Admin 인증 필터 구현 시 |

---

## 부록: 기술 영향 범위

### 신규 생성
| 도메인 | 레이어 | 클래스 |
|--------|--------|--------|
| Brand | domain/infra/application/interfaces | BrandModel, BrandRepository, BrandService, BrandFacade, BrandInfo, BrandV1Controller |
| User | domain/infra | UserModel, UserRepository, UserService |
| ProductLike | domain/infra/application/interfaces | ProductLikeModel, ProductLikeRepository, ProductLikeService, ProductLikeFacade, ProductLikeV1Controller |
| Order | domain/infra/application/interfaces | OrderModel, OrderItemModel, OrderStatus, OrderRepository, OrderService, OrderFacade, OrderInfo, OrderV1Controller, OrderAdminV1Controller |
| Payment | domain/infra/application/interfaces | PaymentModel, PaymentStatus, PaymentRepository, PaymentService, PaymentFacade, PaymentInfo, PaymentV1Controller |

### 변경
| 파일 | 변경 내용 |
|------|-----------|
| `ProductModel` | brand(ManyToOne), likeCount, `incrementLikeCount()`, `decrementLikeCount()`, `deductStock()` |
| `ProductRepository` | 조건부 조회 메서드 추가 |
| `ProductService` | 목록 조회·재고 차감 로직 추가 |
| `ProductRepositoryImpl` | 조건부 쿼리 구현 |
| `ProductFacade` / `ProductInfo` | brandId, brandName, likeCount 추가 |
| `ProductV1Controller` / `ProductV1Dto` | 목록 파라미터 및 응답 변경 |
| `ErrorType` | `UNAUTHORIZED` 추가 검토 |
