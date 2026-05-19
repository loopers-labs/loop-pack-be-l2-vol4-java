# 01. 요구사항 명세서

## 1. 개요

Loopers는 여러 브랜드의 상품을 둘러보고, 마음에 드는 상품에 좋아요를 누르며, 여러 상품을 한 번에 주문·결제할 수 있는 감성 이커머스 서비스다.

User는 회원으로 가입해 자신의 비밀번호를 직접 관리하고, 보유한 포인트로 결제 일부를 충당한다. Admin은 백오피스에서 브랜드와 상품 카탈로그를 관리하고, 발생한 주문을 모니터링한다. 결제는 외부 결제 시스템(Payment Gateway)을 통해 처리된다.

유저가 서비스 내에서 발생시키는 행동(상품 조회, 좋아요, 주문 등)은 모두 기록되어, 이후 랭킹·추천 같은 데이터 기반 기능의 기반이 된다.

## 2. 액터

| 액터 | 설명 | 식별 방식 |
| --- | --- | --- |
| **User** | 서비스 회원. 상품을 탐색하고 좋아요를 누르며 주문·결제한다. 본인 소유 자원(좋아요, 주문, 포인트, 비밀번호)만 다룰 수 있다. | 요청 헤더 `X-Loopers-LoginId`, `X-Loopers-LoginPw` |
| **Guest** | 비회원 방문자. 카탈로그(브랜드/상품 목록·상세)만 조회 가능. 좋아요·주문은 불가. | 인증 헤더 없음 |
| **Admin** | 운영자. 브랜드/상품 카탈로그를 관리하고 전체 주문을 모니터링한다. | 요청 헤더 `X-Loopers-Ldap: loopers.admin` |
| **Payment Gateway** | 외부 결제 시스템. 주문 결제를 위임받아 처리하고 결과를 반환한다. | 시스템 내부 통신 |

- 대고객 기능은 `/api/v1` prefix, 어드민 기능은 `/api-admin/v1` prefix를 사용한다.
- User는 다른 User의 자원에 접근할 수 없다. 본인 식별이 실패하거나 본인 소유가 아닌 자원을 요청하면 예외 흐름으로 분기한다(각 유스케이스 Exception Flow 참조).

## 3. 유저 스토리

### 3.1 User

- 사용자는 본인의 비밀번호를 변경할 수 있다.
- 사용자는 브랜드 정보를 조회할 수 있다.
- 사용자는 상품 목록을 브랜드·정렬·페이지 조건으로 둘러볼 수 있다.
- 사용자는 상품 목록을 최신순/가격 오름차순/가격 내림차순/좋아요 많은 순으로 정렬할 수 있다.
- 사용자는 특정 상품의 상세 정보(사진·설명·가격·재고 여부)를 볼 수 있다.
- 사용자는 상품에 좋아요를 누를 수 있다.
- 사용자는 좋아요한 상품의 좋아요를 취소할 수 있다.
- 사용자는 본인이 좋아요한 상품 목록을 조회할 수 있다.
- 사용자는 여러 상품을 한 번에 주문·결제할 수 있다.
- 사용자는 주문 시 보유한 포인트의 일부를 결제에 사용할 수 있다.
- 사용자는 본인의 주문 목록과 상세를 언제든 조회할 수 있다.
- 사용자는 주문 후 상품의 가격이나 노출 상태가 변하더라도 주문 내역에서 주문 시점의 정보를 그대로 확인할 수 있다.

### 3.2 Guest

- 비회원은 브랜드 정보를 조회할 수 있다.
- 비회원은 상품 목록과 상세를 조회할 수 있다.
- 비회원은 좋아요·주문·내 정보 관련 기능을 사용할 수 없으며, 시도 시 인증 실패 응답을 받는다.

### 3.3 Admin

- 운영자는 브랜드를 등록·수정·조회·삭제(soft delete)할 수 있다.
- 운영자는 상품을 등록·수정·조회·삭제(soft delete)할 수 있다.
- 운영자는 브랜드를 삭제하면 소속 상품과 해당 상품에 걸린 좋아요까지 함께 내려가는 결과를 얻는다. 단, 이미 발생한 주문 내역은 영향을 받지 않는다.
- 운영자는 본인 격리 규칙과 무관하게 전체 유저의 주문 내역을 조회할 수 있다.
- 운영자는 어드민이 수행한 카탈로그/주문 액션을 audit 로그로 추적할 수 있다.

### 3.4 시스템 — 결제 처리

- 시스템은 주문 생성 시 재고와 포인트를 원자적으로 차감한다.
- 시스템은 외부 결제 시스템(Payment Gateway)에 결제를 위임한다.
- 시스템은 결제 성공 시 주문을 결제 완료 상태로 확정한다.
- 시스템은 결제 실패 시 차감했던 재고와 포인트를 복원하고 주문을 실패 상태로 마감한다.
- 시스템은 PG와의 통신이 불안정한 경우(타임아웃 등) 주문을 PENDING 상태로 유지하고 비동기 재조회로 최종 상태를 확정한다.

## 4. 행위 중심 기능 목록

사용자 관점에서 시스템이 무엇을 할 수 있어야 하는지를 한 줄씩 정리한다. 각 항목은 5장의 유스케이스와 1:1로 대응한다.

### 4.1 대고객 (User)

| ID | 행위 |
| --- | --- |
| UC-01 | 내 비밀번호를 변경한다 |
| UC-02 | 특정 브랜드의 정보를 본다 |
| UC-03 | 브랜드·정렬·페이지 조건으로 상품 목록을 둘러본다 |
| UC-04 | 특정 상품의 상세 정보를 본다 |
| UC-05 | 상품에 좋아요를 누른다 |
| UC-06 | 좋아요를 취소한다 |
| UC-07 | 내가 좋아요한 상품 목록을 본다 |
| UC-08 | 여러 상품을 한 번에 주문·결제한다 |
| UC-09 | 내 주문 목록·상세를 본다 |

### 4.2 게스트 (Guest)

| ID | 행위 |
| --- | --- |
| — | 브랜드·상품 카탈로그를 자유롭게 둘러본다 (UC-02, UC-03, UC-04를 비로그인으로 호출) |
| — | 좋아요·주문 시도 시 인증 실패로 막힌다 (UC-05~UC-09의 Exception Flow) |

### 4.3 어드민 (Admin)

| ID | 행위 |
| --- | --- |
| UC-10 | 브랜드를 등록·수정·조회·삭제(soft delete)한다 |
| UC-11 | 상품을 등록·수정·조회·삭제(soft delete)한다 |
| UC-12 | 전체 유저의 주문 내역을 모니터링한다 |

---

## 5. 유스케이스 명세

> 각 유스케이스는 다음 구조를 갖는다:
> - **액터** / **사전 조건** / **트리거**
> - **Main Flow** — 가장 흔한 정상 흐름
> - **Alternate Flow** — 정상 범위 내 분기
> - **Exception Flow** — 실패·오류 흐름
> - **사후 조건**
> - **API 인터페이스** — Method/Path, 헤더, 요청·응답 스키마, 상태 코드
> - **정책·제약** — 검증 규칙, 비즈니스 룰, 도메인 제약 (횡단 규칙은 §8 참조)
> - **비고** — 멱등성·동시성·로깅 등 도메인 특이사항

### UC-01. 비밀번호 변경

**액터**: User
**사전 조건**: User가 본인의 `X-Loopers-LoginId` / `X-Loopers-LoginPw`로 식별 가능한 상태
**트리거**: User가 새 비밀번호로 교체하고자 한다

**Main Flow**
1. User가 새 비밀번호를 담아 `PUT /api/v1/users/password`를 호출한다
2. 시스템은 인증 헤더로 본인을 식별한다 (현재 비밀번호 확인이 이 단계에 포함됨)
3. 시스템은 `newPassword`가 비밀번호 정책(§8.1)을 만족하는지 검증한다
4. 시스템은 `newPassword`가 현재 비밀번호와 동일하지 않은지 검증한다
5. 시스템은 `newPassword`를 해시하여 저장한다
6. 시스템은 비밀번호 변경 이벤트를 행동 로그에 기록한다
7. 시스템은 `204 No Content`를 반환한다

**Alternate Flow**
- 없음

**Exception Flow**
- E1. 인증 헤더 누락 또는 `LoginId/LoginPw` 불일치 → `401 UNAUTHORIZED`
- E2. `newPassword`가 비밀번호 정책 위반(길이·문자종 등) → `400 BAD_REQUEST` + 위반 사유 코드
- E3. `newPassword`가 현재 비밀번호와 동일 → `400 BAD_REQUEST` (code: `SAME_AS_CURRENT`)
- E4. `newPassword` 필드 누락 또는 빈 문자열 → `400 BAD_REQUEST`

**사후 조건**
- 해당 User 레코드의 비밀번호 해시가 새 값으로 갱신된다
- 이후 요청은 새 비밀번호로만 인증된다 (이전 비밀번호는 무효)
- 행동 로그에 `password_changed` 이벤트가 1건 기록된다

**API 인터페이스**
- `PUT /api/v1/users/password`
- Headers: `X-Loopers-LoginId`, `X-Loopers-LoginPw`
- Request Body:
  ```json
  { "newPassword": "string" }
  ```
- Success: `204 No Content` (응답 본문 없음)
- Error: 표준 에러 응답 (`{ "code": "...", "message": "..." }`)

**정책·제약**
- 현재 비밀번호는 별도 필드로 받지 않고 인증 헤더(`X-Loopers-LoginPw`)로 확인한다
- 비밀번호 정책 상세는 §8.1에 정의 (글로벌 규칙)
- 비밀번호는 평문 저장 금지. 해시 + salt (bcrypt 권장)
- MD5/SHA1 등 단방향 약식 해시 금지

**비고**
- **멱등성**: 동일 `newPassword`로 재호출 시 2회차는 E3에 걸려 `400` 반환. 부작용 없음
- **동시성**: 같은 User의 동시 변경 요청은 last-write-wins. 단일 행 UPDATE라 별도 락 불필요
- **로깅**: 비밀번호 평문/해시 모두 로그 출력 금지. 행동 로그에는 이벤트 메타데이터만 기록
### UC-02. 브랜드 조회

**액터**: User, Guest
**사전 조건**: 없음 (인증 불요)
**트리거**: 사용자가 특정 브랜드 정보를 보고자 한다

**Main Flow**
1. 사용자가 `GET /api/v1/brands/{brandId}` 호출
2. 시스템은 brandId로 브랜드를 조회 (soft deleted 제외)
3. 브랜드 정보 반환 (`200 OK`)

**Alternate Flow**
- 없음

**Exception Flow**
- E1. brandId 미존재 또는 soft deleted 상태 → `404 NOT_FOUND` (code: `BRAND_NOT_FOUND`)
- E2. brandId 형식 위반 → `400 BAD_REQUEST`

**사후 조건**
- 변경 없음 (조회)

**API 인터페이스**
- `GET /api/v1/brands/{brandId}`
- Headers: 없음 (Guest 가능)
- Response `200 OK`:
  ```json
  { "brandId": 1, "name": "string", "description": "string" }
  ```

**정책·제약**
- 비회원 조회 가능
- soft deleted 브랜드는 응답 제외 (§8.2)

**비고**
- 변경 빈도 낮음 → HTTP 캐시 헤더 활용 검토
- 응답에 소속 상품 수 같은 통계 노출 시 별도 집계 endpoint 분리 권장 (N+1 회피)

### UC-03. 상품 목록 둘러보기

**액터**: User, Guest
**사전 조건**: 없음
**트리거**: 사용자가 브랜드·정렬·페이지 조건으로 상품을 둘러보고자 한다

**Main Flow**
1. 사용자가 `GET /api/v1/products?brandId=&sort=&page=&size=` 호출
2. 시스템은 page/size/sort 유효성 검증
3. brandId 필터 적용 (있을 시 — 활성 브랜드만, soft deleted 브랜드의 상품은 제외)
4. soft deleted 상품 제외
5. sort에 따라 정렬:
   - `latest` (default) — createdAt DESC
   - `price_asc` / `price_desc` — price ASC/DESC
   - `likes_desc` — likesCount DESC
   - 모두 productId DESC를 tiebreaker로 적용
6. page × size offset으로 페이지 조회
7. (User 인증 시) 페이지 내 productId IN 쿼리로 본인 like 여부를 일괄 조회 → `likedByMe`
8. (Guest 또는 인증 실패) `likedByMe` 일괄 false
9. 응답 반환

**Alternate Flow**
- A1. brandId 미지정 → 전체 상품 대상
- A2. 결과 0건 → 빈 페이지 (`totalElements: 0`) 반환

**Exception Flow**
- E1. `page` < 0 또는 `size` 범위 위반(1~100) → `400 BAD_REQUEST` (code: `INVALID_PAGE_PARAM`)
- E2. `sort` enum 외 값 → `400 BAD_REQUEST` (code: `INVALID_SORT`)
- E3. `brandId` 형식 위반(음수 등) → `400 BAD_REQUEST`

**사후 조건**
- 변경 없음 (조회)

**API 인터페이스**
- `GET /api/v1/products`
- Headers: `X-Loopers-LoginId`, `X-Loopers-LoginPw` (선택)
- Query:
  - `brandId`: optional, long
  - `sort`: optional, enum `latest|price_asc|price_desc|likes_desc`, default `latest`
  - `page`: optional, int ≥ 0, default `0`
  - `size`: optional, int 1~100, default `20`
- Response `200 OK`:
  ```json
  {
    "page": 0,
    "size": 20,
    "totalElements": 123,
    "totalPages": 7,
    "items": [
      {
        "productId": 1,
        "name": "string",
        "price": 0,
        "brandId": 1,
        "brandName": "string",
        "imageUrl": "string",
        "likesCount": 0,
        "inStock": true,
        "likedByMe": false,
        "description": "string"
      }
    ]
  }
  ```

**정책·제약**
- `size` 상한 100 (DB 부하 차단)
- `likedByMe`: 비로그인 시 항상 false
- soft deleted 상품 / soft deleted 브랜드 소속 상품 모두 제외 (§8.2)
- 정렬 안정성: tiebreaker로 `productId DESC` 추가 (페이지 경계 중복/누락 방지)

**비고**
- **likedByMe N+1 방지**: 페이지 내 productId 집합에 대해 단일 IN 쿼리 1회로 일괄 조회 (§7.2)
- **likes_desc 성능**: `products.likesCount` 비정규화 컬럼 사용. 매 요청 COUNT 금지 (§8.3)
- 행동 로깅(검색/조회)은 비동기 (§8.4)

### UC-04. 상품 상세 조회

**액터**: User, Guest
**사전 조건**: 없음
**트리거**: 사용자가 특정 상품의 상세 정보를 보고자 한다

**Main Flow**
1. 사용자가 `GET /api/v1/products/{productId}` 호출
2. 시스템은 productId로 상품 조회 (soft deleted 제외, 브랜드 cascade 포함 검사)
3. (User 인증 시) 해당 상품에 대한 본인 like 여부 → `likedByMe`
4. (Guest) `likedByMe = false`
5. 응답 반환

**Alternate Flow**
- 없음

**Exception Flow**
- E1. productId 미존재 또는 상품/소속 브랜드 soft deleted → `404 NOT_FOUND` (code: `PRODUCT_NOT_FOUND`)

**사후 조건**
- 변경 없음 (조회)

**API 인터페이스**
- `GET /api/v1/products/{productId}`
- Headers: `X-Loopers-LoginId`, `X-Loopers-LoginPw` (선택)
- Response `200 OK`: UC-03의 단일 item 스키마와 동일

**정책·제약**
- 응답 필드는 목록(UC-03)과 동일 (`stock`은 노출하지 않고 `inStock` boolean만)
- 브랜드 soft delete 상태 cascade 검사 (§8.2)

**비고**
- 캐싱 가능. 단 `likedByMe`는 사용자별 동적 값이라 캐시 키에서 분리하거나 별도 호출로 분리
- 행동 로깅 비동기 (§8.4)

### UC-05. 상품 좋아요 등록

**액터**: User
**사전 조건**: User 인증 가능
**트리거**: User가 상품에 좋아요를 누른다

**Main Flow**
1. User가 `POST /api/v1/products/{productId}/likes` 호출
2. 시스템은 헤더로 본인을 인증
3. 상품 존재 + 활성 상태 확인 (소속 브랜드 cascade 포함)
4. (userId, productId) Like 조회
   - 행 없음 → INSERT (`deleted=false`)
   - 행 있음, `deleted=true` → UPDATE `deleted=false`, `likedAt=now()` (reactivate)
   - 행 있음, `deleted=false` → no-op (멱등)
5. 실제 신규/재활성 케이스에서만 `products.likesCount` 원자 UPDATE +1
6. 행동 로그(`product_liked`) 비동기 기록 (실제 변화 케이스만)
7. `204 No Content` 반환

**Alternate Flow**
- A1. 이미 활성 좋아요 상태 → 부작용 없이 `204`

**Exception Flow**
- E1. 인증 실패 → `401 UNAUTHORIZED`
- E2. 상품 미존재 / 상품·브랜드 soft deleted → `404 PRODUCT_NOT_FOUND`
- E3. `UNIQUE(userId, productId)` race 위반 → 1회 재시도. 그래도 실패 시 `409 CONFLICT`

**사후 조건**
- `(userId, productId, deleted=false)` Like 1행 존재
- `products.likesCount` += 1 (변화 케이스)
- 행동 로그 1건 (변화 케이스)

**API 인터페이스**
- `POST /api/v1/products/{productId}/likes`
- Headers: `X-Loopers-LoginId`, `X-Loopers-LoginPw`
- Body: 없음
- Success: `204 No Content`

**정책·제약**
- **멱등성**: 같은 (user, product) 반복 호출은 동일 최종 상태로 수렴
- **DB 제약**: `UNIQUE(userId, productId)` + Service 선검사 이중 방어
- **Soft delete reactivate**: MariaDB는 부분 인덱스(`WHERE deleted=false`) 미지원 → 취소 → 재등록 시 UNIQUE 위반 회피를 위해 기존 행을 UPDATE로 reactivate
- `createdAt`은 최초 좋아요 시점 보존, `likedAt`은 reactivate 시 갱신
- `likesCount`는 원자 UPDATE (§7.2)

**비고**
- **동시성**: 동일 (user, product) 동시 호출 시 한쪽은 UNIQUE 위반 → 재시도 흐름으로 흡수
- **트랜잭션 범위**: Like INSERT/UPDATE + likesCount UPDATE를 단일 트랜잭션
- **로깅 정확성**: no-op 케이스는 행동 로그 미기록

### UC-06. 상품 좋아요 취소

**액터**: User
**사전 조건**: User 인증 가능
**트리거**: User가 좋아요를 취소한다

**Main Flow**
1. User가 `DELETE /api/v1/products/{productId}/likes` 호출
2. 인증
3. 상품 존재 여부 확인
4. (userId, productId) 활성 Like 조회
   - 활성 있음 → `deleted=true`, `deletedAt=now()` UPDATE
   - 활성 없음 → no-op (멱등)
5. 실제 deletion 케이스에서만 `products.likesCount = GREATEST(likesCount - 1, 0)` UPDATE
6. 행동 로그(`product_unliked`) 비동기 기록 (변화 케이스)
7. `204 No Content` 반환

**Alternate Flow**
- A1. 좋아요한 적 없거나 이미 취소된 상태 → 부작용 없이 `204`

**Exception Flow**
- E1. 인증 실패 → `401`
- E2. 상품 미존재 → `404 PRODUCT_NOT_FOUND`

**사후 조건**
- 활성 Like 행이 `deleted=true`로 전이
- `products.likesCount` -= 1 (변화 케이스)

**API 인터페이스**
- `DELETE /api/v1/products/{productId}/likes`
- Headers: `X-Loopers-LoginId`, `X-Loopers-LoginPw`
- Body: 없음
- Success: `204 No Content`

**정책·제약**
- 멱등성 동일
- Like는 soft delete만 (행동 데이터 보존 목적)
- `likesCount` 음수 방지: `GREATEST(likesCount - 1, 0)`

**비고**
- 동시성: 동일 like 동시 취소 시 한쪽만 실제 변화 적용. likesCount 정합성은 원자 UPDATE로 보장 (§7.2)
- 트랜잭션 범위 UC-05와 동일

### UC-07. 내가 좋아요한 상품 조회

**액터**: User
**사전 조건**: User 인증 가능
**트리거**: User가 본인이 좋아요한 상품 목록을 보고자 한다

**Main Flow**
1. User가 `GET /api/v1/users/me/likes?page=&size=` 호출
2. 인증
3. page/size 유효성 검증
4. userId 기준 활성 Like(`deleted=false`)를 `likedAt DESC, productId DESC`로 페이지 조회
5. 같은 페이지의 productId에 대해 상품·브랜드 정보 + `likesCount`를 JOIN 또는 단일 IN 쿼리로 일괄 조회
6. soft deleted 상품/브랜드는 결과에서 제외 (§8.2)
7. 응답 반환 (`likedByMe` 항상 true)

**Alternate Flow**
- A1. cascade로 일부 상품/브랜드가 내려간 경우 → 결과에서 자동 제외
- A2. 결과 0건 → 빈 페이지

**Exception Flow**
- E1. 인증 실패 → `401`
- E2. page/size 범위 위반 → `400 INVALID_PAGE_PARAM`

**사후 조건**
- 변경 없음 (조회)

**API 인터페이스**
- `GET /api/v1/users/me/likes`
- Headers: `X-Loopers-LoginId`, `X-Loopers-LoginPw`
- Query: `page`(≥0, default 0), `size`(1~100, default 20)
- Response `200 OK`: UC-03와 동일 페이지 스키마. `items[].likedByMe`는 항상 true

**정책·제약**
- 본인 외 조회 불가 (URI `me` 고정. userId 노출 안 함)
- soft deleted 상품/브랜드 자동 제외
- size 상한 100
- 정렬: `likedAt DESC`. tiebreaker `productId DESC`

**비고**
- **N+1 방지**: 상품·브랜드·likesCount는 JOIN 또는 IN 쿼리로 일괄 (§7.2)
- 페이지 안정성: tiebreaker로 경계 중복/누락 방지

### UC-08. 상품 주문·결제

**액터**: User, Payment Gateway
**사전 조건**: User 인증 가능
**트리거**: User가 여러 상품을 한 번에 주문·결제하고자 한다

**Main Flow**
1. User가 `POST /api/v1/orders` 호출 (items, usedPoint, paymentMethod)
2. 인증
3. 입력 검증
   - items 1개 이상
   - 각 quantity ≥ 1, ≤ 상한(1,000)
   - items 내 productId 중복 없음
   - usedPoint ≥ 0
4. items의 모든 상품 존재 + 활성 상태 확인 (브랜드 cascade 포함)
5. 트랜잭션 시작
6. 각 항목에 원자적 재고 차감 UPDATE: `UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?`. 영향 행 0 = 재고 부족
7. 단가 = 현재 `products.price`. 총액 = Σ(price × quantity)
8. usedPoint ≤ 보유 포인트 확인
9. usedPoint ≤ 총액 확인 (음수 결제 차단)
10. 결제 금액 = 총액 − usedPoint
11. 포인트 차감 (`UPDATE users SET point = point - ? WHERE id = ? AND point >= ?`)
12. Order(`status=PENDING`) + OrderItem 스냅샷(상품명·단가·브랜드명·이미지URL) 생성
13. 트랜잭션 커밋
14. PaymentGateway에 결제 위임 (orderId, paidAmount, paymentMethod)
15. PG 응답에 따라 분기:
    - 성공 → `Order.status = PAID`, `paidAt` 기록, 행동 로그(`order_paid`)
    - 실패 → 보상 트랜잭션: 재고/포인트 복원, `Order.status = FAILED`, 행동 로그(`order_failed`)
    - 타임아웃 → Order `PENDING` 유지 + reconcile job에 위임
16. 응답 반환

**Alternate Flow**
- A1. `usedPoint = 0` → 포인트 차감 단계 생략. PG로 전액 결제
- A2. `usedPoint = totalAmount` → 결제 금액 0. PG 호출 생략, 즉시 `PAID`

**Exception Flow**
- E1. 인증 실패 → `401`
- E2. items 빈 배열 → `400` (code: `EMPTY_ORDER_ITEMS`)
- E3. quantity ≤ 0 또는 상한 초과 → `400 INVALID_QUANTITY`
- E4. items 내 productId 중복 → `400 DUPLICATE_PRODUCT`
- E5. usedPoint 음수 → `400 INVALID_USED_POINT`
- E6. 일부 productId 미존재/soft deleted → `404 PRODUCT_NOT_FOUND` (응답 본문에 누락 productId 목록)
- E7. 재고 부족 (1개 이상) → `409 CONFLICT` (code: `INSUFFICIENT_STOCK`) + 부족 항목 `[{productId, requested, available}]`. **전체 롤백** (Q8a)
- E8. 포인트 부족 → `409 CONFLICT` (code: `INSUFFICIENT_POINT`)
- E9. usedPoint > totalAmount → `400 USED_POINT_EXCEEDS_TOTAL`
- E10. PG 결제 실패 → 보상 후 응답 `200 OK` with `status=FAILED, failureReason`
- E11. PG 타임아웃/통신 오류 → `Order.status=PENDING` 유지, 재고/포인트는 차감 상태로 유지. 응답 `202 ACCEPTED` with `status=PENDING`. 비동기 reconcile
- E12. 동시성 충돌 (재고/포인트 원자 UPDATE 0행) → 최대 3회 재시도. 실패 시 E7/E8 경로

**사후 조건**
- 성공: Order(`PAID`) + OrderItem N건, 재고/포인트 감소, 행동 로그
- PG 실패: Order(`FAILED`), 재고/포인트 원복
- PG 타임아웃: Order(`PENDING`), 재고/포인트 차감 상태 유지, reconcile 대기

**API 인터페이스**
- `POST /api/v1/orders`
- Headers: `X-Loopers-LoginId`, `X-Loopers-LoginPw` / `Idempotency-Key` (선택)
- Request:
  ```json
  {
    "items": [
      { "productId": 1, "quantity": 2 }
    ],
    "usedPoint": 1000,
    "paymentMethod": "CARD"
  }
  ```
- Response (success):
  ```json
  {
    "orderId": 12345,
    "status": "PAID",
    "totalAmount": 50000,
    "usedPoint": 1000,
    "paidAmount": 49000,
    "paymentMethod": "CARD",
    "items": [
      {
        "productId": 1,
        "productName": "string",
        "brandName": "string",
        "imageUrl": "string",
        "unitPrice": 25000,
        "quantity": 2,
        "lineTotal": 50000
      }
    ],
    "createdAt": "2026-05-19T12:00:00Z",
    "paidAt": "2026-05-19T12:00:01Z"
  }
  ```
- Response (failure): `200 OK` with `status=FAILED, failureReason` 또는 표준 에러 응답

**정책·제약**
- **전체 실패 정책** (Q8a): items 중 한 건이라도 검증/재고/포인트 실패 시 주문 전체 실패. 부분 성공 없음
- **주문 스냅샷** (Q12): OrderItem은 상품명·단가·브랜드명·이미지URL을 생성 시점 값으로 보존. 이후 상품·브랜드 변경 영향 없음
- **결제 음수 방지**: `paidAmount ≥ 0`. usedPoint가 총액을 초과하지 않도록 사전 검증
- **재고/포인트 차감 전략**: 원자 UPDATE + `WHERE >= ?` 조건. 비관 락 미사용 (§7.1)
- **PG 인터페이스** (Q9): `PaymentGateway` 도메인 인터페이스 + Fake 구현체. 실 PG 전환 시 인터페이스 유지
- **주문 상태 머신**: `PENDING → (PAID | FAILED)`. 타임아웃은 PENDING 유지 + 비동기 reconcile
- **결제 수단** (Q10): `paymentMethod` 선택적 필드
- **Idempotency**: 클라이언트 중복 호출 방지를 위해 `Idempotency-Key` 헤더 지원 (동일 키 재요청 시 기존 결과 반환)
- **트랜잭션 경계**: 입력 검증~재고~포인트~Order 생성을 단일 트랜잭션. PG 호출은 트랜잭션 커밋 이후 (외부 시스템 호출은 트랜잭션 안에 두지 않음)

**비고**
- **동시성**: 동일 핫상품 동시 주문은 원자 UPDATE로 직렬화 — 1개 남은 재고를 둘이 잡으면 한쪽만 성공
- **PG 실패 복원**: 트랜잭션 커밋 이후 발생 → 보상 트랜잭션으로 처리 (재고/포인트 원복 + Order.status 갱신)
- **로깅**: 결제 카드 번호·CVC 등 민감 정보는 행동 로그·일반 로그 모두에서 마스킹 (§8.4)
- **금액 단위**: 정수 KRW 기준 (소수점 없음). 통화 다국화 시 별도 정책 필요

### UC-09. 내 주문 조회

**액터**: User
**사전 조건**: User 인증 가능
**트리거**: User가 본인 주문 내역을 조회한다

**Main Flow (목록)**
1. User가 `GET /api/v1/orders?page=&size=` 호출
2. 인증
3. page/size 유효성 검증
4. userId의 주문을 `createdAt DESC, orderId DESC`로 페이지 조회
5. 각 주문의 status·totalAmount·items 요약(상위 N개) 포함하여 반환

**Main Flow (상세)**
1. User가 `GET /api/v1/orders/{orderId}` 호출
2. 인증
3. orderId 조회. 본인 주문이 아니거나 미존재면 동일하게 미존재 취급
4. Order + OrderItem 전체 스냅샷 반환

**Alternate Flow**
- A1. 결과 0건 → 빈 페이지

**Exception Flow**
- E1. 인증 실패 → `401`
- E2. orderId 미존재 또는 본인 주문 아님 → `404 NOT_FOUND` (code: `ORDER_NOT_FOUND`). orderId 존재 여부 누설 방지 위해 `403`이 아닌 `404` 사용
- E3. page/size 범위 위반 → `400 INVALID_PAGE_PARAM`

**사후 조건**
- 변경 없음 (조회)

**API 인터페이스**
- 목록: `GET /api/v1/orders?page=0&size=20`
- 상세: `GET /api/v1/orders/{orderId}`
- Headers: `X-Loopers-LoginId`, `X-Loopers-LoginPw`
- Response (상세):
  ```json
  {
    "orderId": 12345,
    "status": "PAID",
    "totalAmount": 50000,
    "usedPoint": 1000,
    "paidAmount": 49000,
    "paymentMethod": "CARD",
    "items": [
      {
        "productId": 1,
        "productName": "string",
        "brandName": "string",
        "imageUrl": "string",
        "unitPrice": 25000,
        "quantity": 2,
        "lineTotal": 50000
      }
    ],
    "createdAt": "2026-05-19T12:00:00Z",
    "paidAt": "2026-05-19T12:00:01Z"
  }
  ```

**정책·제약**
- 본인 주문만. 타인 주문 시도는 `404` (정보 노출 방지)
- 페이지 size 상한 100
- 응답은 OrderItem **스냅샷** 기준. 이후 상품/브랜드 변경 영향 없음
- 상품/브랜드 soft delete 영향 없음 (Order/OrderItem cascade 제외 — §8.2)

**비고**
- 주문 상세 조회 시 OrderItem N건은 단일 JOIN으로 일괄 (N+1 회피)
- 목록 응답의 items 요약 형태(상위 N개 + 더보기 카운트)는 클라이언트 UX와 맞춰 협의

### UC-10. (Admin) 브랜드 관리

**액터**: Admin
**사전 조건**: Admin 인증 가능 (`X-Loopers-Ldap: loopers.admin`)
**트리거**: 운영자가 브랜드를 등록·수정·조회·삭제한다

**Main Flow (생성)**
1. `POST /api-admin/v1/brands` (name, description)
2. Admin 인증
3. 입력 검증 (name 필수, 길이 1~50)
4. INSERT
5. `201 CREATED` + brandId

**Main Flow (수정)**
1. `PUT /api-admin/v1/brands/{brandId}` (name, description)
2. 인증 + 활성 브랜드 존재 확인
3. UPDATE
4. `200 OK`

**Main Flow (조회)**
- `GET /api-admin/v1/brands?page=&size=&includeDeleted=`
- `GET /api-admin/v1/brands/{brandId}`
- `includeDeleted=true`이면 soft deleted 브랜드도 포함

**Main Flow (삭제)**
1. `DELETE /api-admin/v1/brands/{brandId}`
2. 인증 + 활성 브랜드 존재 확인
3. **Cascade soft delete** (§8.2): Brand → 소속 Product → 해당 Product의 Like 모두 `deletedAt=now()` 기록
4. Order/OrderItem은 영향 없음 (스냅샷 기반)
5. 행동 로그(`admin_brand_deleted`) 기록
6. `204 No Content`

**Alternate Flow**
- A1. 이미 soft deleted 상태 → `404` (활성 자원 기준 미존재)

**Exception Flow**
- E1. Admin 헤더 누락/불일치 → `401 UNAUTHORIZED` (또는 `403 FORBIDDEN`)
- E2. brandId 미존재 → `404 BRAND_NOT_FOUND`
- E3. name 누락/빈 문자열/길이 초과 → `400 INVALID_BRAND_NAME`
- E4. page/size 범위 위반 → `400 INVALID_PAGE_PARAM`

**사후 조건**
- 생성/수정: 브랜드 레코드 변경
- 삭제: Brand·하위 Product·하위 Like가 모두 `deletedAt` 기록. Order 무영향
- 운영 audit 로그 1건

**API 인터페이스**
- `POST /api-admin/v1/brands`
- `GET /api-admin/v1/brands`
- `GET /api-admin/v1/brands/{brandId}`
- `PUT /api-admin/v1/brands/{brandId}`
- `DELETE /api-admin/v1/brands/{brandId}`
- Headers: `X-Loopers-Ldap: loopers.admin`
- Request (생성/수정):
  ```json
  { "name": "string", "description": "string" }
  ```
- Response (조회): brandId, name, description, createdAt, (deletedAt nullable)

**정책·제약**
- 모든 삭제는 **soft delete** (hard delete 미지원)
- Brand soft delete → Product → Like cascade (§8.2)
- 이미 발생한 Order는 영향 없음 (OrderItem 스냅샷)
- name 중복 허용 (현 단계). 추후 unique 제약 도입 시 마이그레이션 필요
- 모든 어드민 액션은 audit 로그 필수 (§8.4)

**비고**
- 대량 cascade(상품·좋아요 수 만 건)는 동기 처리 시 응답 지연 → 비동기 job 분리 검토
- 운영 audit 로그: 누가·언제·무엇을 변경했는지 행 단위로 기록

### UC-11. (Admin) 상품 관리

**액터**: Admin
**사전 조건**: Admin 인증 가능
**트리거**: 운영자가 상품을 등록·수정·조회·삭제한다

**Main Flow (생성)**
1. `POST /api-admin/v1/products` (brandId, name, price, stock, imageUrl, description)
2. Admin 인증
3. 입력 검증 (price ≥ 0, stock ≥ 0, brandId 활성)
4. INSERT (`likesCount = 0`)
5. `201 CREATED` + productId

**Main Flow (수정)**
1. `PUT /api-admin/v1/products/{productId}`
2. 인증 + 활성 상품 존재 확인
3. UPDATE
4. `200 OK`

**Main Flow (조회)**
- `GET /api-admin/v1/products?brandId=&page=&size=&includeDeleted=`
- `GET /api-admin/v1/products/{productId}`

**Main Flow (삭제)**
1. `DELETE /api-admin/v1/products/{productId}`
2. 인증 + 활성 상품 존재 확인
3. **Cascade soft delete**: Product → 해당 Product의 Like
4. Order/OrderItem 영향 없음
5. audit 로그
6. `204 No Content`

**Alternate Flow**
- A1. 이미 soft deleted 상품 → `404`

**Exception Flow**
- E1. Admin 인증 실패 → `401/403`
- E2. productId 미존재 → `404 PRODUCT_NOT_FOUND`
- E3. brandId 미존재 또는 soft deleted (생성·수정) → `400 INVALID_BRAND`
- E4. price < 0, stock < 0, name 누락 → `400`
- E5. page/size 범위 위반 → `400`

**사후 조건**
- 생성/수정/삭제별 데이터 변경 + audit 로그 1건

**API 인터페이스**
- `POST /api-admin/v1/products`
- `GET /api-admin/v1/products`
- `GET /api-admin/v1/products/{productId}`
- `PUT /api-admin/v1/products/{productId}`
- `DELETE /api-admin/v1/products/{productId}`
- Headers: `X-Loopers-Ldap: loopers.admin`
- Request (생성/수정):
  ```json
  {
    "brandId": 1,
    "name": "string",
    "price": 0,
    "stock": 0,
    "imageUrl": "string",
    "description": "string"
  }
  ```

**정책·제약**
- 가격/재고 음수 불가
- 활성 Brand 소속만 허용. soft deleted Brand로의 이동/생성 차단
- Product soft delete → Like cascade. `likesCount`는 그대로 두되 대고객 노출에서 제외
- 가격 변경은 기존 Order에 영향 없음 (스냅샷)
- 가격·재고 변경은 audit 로그 필수

**비고**
- 재고 변경(차감 외 어드민 조정)도 audit 로그
- 동일 상품 대량 일괄 수정 endpoint는 별도로 분리 (현재는 단건 PUT만)

### UC-12. (Admin) 주문 모니터링

**액터**: Admin
**사전 조건**: Admin 인증 가능
**트리거**: 운영자가 전체 유저의 주문을 모니터링한다

**Main Flow (목록)**
1. `GET /api-admin/v1/orders?status=&userId=&from=&to=&page=&size=` 호출
2. Admin 인증
3. 필터 + page/size 유효성 검증
4. 조건 일치 주문을 `createdAt DESC, orderId DESC`로 페이지 조회
5. 응답 반환

**Main Flow (상세)**
- `GET /api-admin/v1/orders/{orderId}`
- 본인 격리 규칙 미적용 — 운영자는 전체 접근

**Alternate Flow**
- A1. 필터 조건에 일치 0건 → 빈 페이지

**Exception Flow**
- E1. Admin 인증 실패 → `401/403`
- E2. orderId 미존재 → `404 ORDER_NOT_FOUND`
- E3. 필터 파라미터 형식 위반 (`status` enum 외, `from/to` 형식 등) → `400`
- E4. page/size 범위 위반 → `400`

**사후 조건**
- 변경 없음 (조회). 단, 어드민 조회 audit 로그 1건 기록 (§8.4)

**API 인터페이스**
- `GET /api-admin/v1/orders`
- `GET /api-admin/v1/orders/{orderId}`
- Headers: `X-Loopers-Ldap: loopers.admin`
- Query:
  - `status`: optional, enum `PENDING|PAID|FAILED`
  - `userId`: optional, long
  - `from` / `to`: optional, ISO datetime
  - `page` / `size`: 표준 페이지 파라미터

**정책·제약**
- 본인 격리 규칙은 대고객(UC-09)에만 적용. Admin은 전체 접근
- 응답에 User 식별자(userId, 마스킹된 loginId) 포함. PII 정책에 따라 마스킹 강도 조정
- size 상한 100

**비고**
- 인덱스: `(status, createdAt)`, `(userId, createdAt)` 필수 — 운영 조회 패턴 다양함
- Admin 조회도 audit 로그 (`admin_order_viewed`) 기록 — 어떤 운영자가 누구 주문을 봤는지 추적

---

## 6. 도메인 모델 (요약)

> 상세 ERD는 `04-erd.md`에서 정의. 본 절은 명세 본문 이해에 필요한 최소 개념만 정리한다.

### 6.1 주요 엔티티

| 엔티티 | 핵심 책임 | 주요 속성 |
| --- | --- | --- |
| **User** | 회원 식별, 비밀번호·포인트 보유 | id, loginId, passwordHash, point, createdAt |
| **Brand** | 상품 그룹의 운영 단위 | id, name, description, createdAt, deletedAt |
| **Product** | 판매 대상 상품. 카탈로그·재고·좋아요 카운터 보유 | id, brandId, name, price, stock, imageUrl, description, likesCount, createdAt, deletedAt |
| **Like** | User가 Product에 누른 좋아요 | id, userId, productId, deleted, createdAt, likedAt, deletedAt — `UNIQUE(userId, productId)` |
| **Order** | 주문 헤더. 결제 결과·총액 보유 | id, userId, status, totalAmount, usedPoint, paidAmount, paymentMethod, createdAt, paidAt |
| **OrderItem** | 주문 라인. **주문 시점 스냅샷** 보존 | id, orderId, productId, productName, brandName, imageUrl, unitPrice, quantity, lineTotal |

### 6.2 관계 요약

- Brand 1:N Product
- Product 1:N Like, User 1:N Like, `(userId, productId)`는 UNIQUE
- User 1:N Order, Order 1:N OrderItem
- OrderItem.productId는 참조용. 실제 표시 데이터는 OrderItem이 스냅샷으로 보유 (Product 변경/삭제 영향 없음)

### 6.3 카운터·비정규화

- `Product.likesCount`: 비정규화 카운터 (§8.3). Like 활성 행 수와 일치하도록 ±1 원자 UPDATE로 유지
- `Product.stock`: 재고 카운터. 주문 시 원자 UPDATE로 차감 (§7.1)
- `User.point`: 포인트 카운터. 주문 시 원자 UPDATE로 차감

## 7. 비기능 요구사항

### 7.1 동시성 — 재고·포인트

- 재고 차감: 원자 UPDATE `UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?`. 영향 행 0이면 재고 부족
- 포인트 차감: 동일 패턴 `UPDATE users SET point = point - ? WHERE id = ? AND point >= ?`
- 비관 락(`SELECT ... FOR UPDATE`) 미사용 — 핫상품에서도 락 경합 없이 처리
- 충돌 시 최대 3회 트랜잭션 재시도. 그래도 실패 시 `409 CONFLICT`
- 분산락(Redis 등) 미도입 — 단일 DB 가정. 멀티 인스턴스 환경에서도 DB 원자성으로 충분

### 7.2 동시성 — 비정규화 카운터

- `Product.likesCount`: 원자 UPDATE `SET likesCount = likesCount + 1` / `SET likesCount = GREATEST(likesCount - 1, 0)`
- 좋아요 변화 케이스에만 카운터 갱신. no-op 케이스는 카운터 무변화
- Like row 변경 + likesCount UPDATE는 단일 트랜잭션
- 정합성 보정: 주기적 reconcile 배치로 `COUNT(Like WHERE deleted=false)`와 비교·보정 (옵션)

### 7.3 페이지네이션 제약

- 모든 페이지 조회 endpoint 공통:
  - `page`: ≥ 0, default `0` (0-based)
  - `size`: 1~100, default `20`. **상한 100** (DB 부하 차단)
  - 위반 시 `400 INVALID_PAGE_PARAM`
- 정렬 안정성: 동률 발생 가능 정렬 키에는 `id DESC` tiebreaker 추가
- offset 기반. 대용량 데이터셋(수십만+) 환경에서는 커서 기반으로 전환 검토

### 7.4 N+1 회피 (성능 가드레일)

- `likedByMe`: 페이지 내 productId 집합에 대해 단일 IN 쿼리 1회로 일괄 조회
- 상품 목록의 brandName, likesCount: 비정규화 또는 JOIN 한 번으로 일괄
- 주문 상세의 OrderItem: 단일 JOIN
- 코드 리뷰·테스트에서 N+1 발생 여부 가드(쿼리 수 단언 등) 도입 권장

### 7.5 격리·권한

- **User 본인 자원**: 좋아요·주문·포인트·비밀번호. 본인 외 접근 시 `404` (정보 노출 방지 — `403` 미사용)
- **Guest**: 카탈로그 조회만. 좋아요·주문 시도 시 `401`
- **Admin**: 어드민 prefix(`/api-admin/v1`)로만 접근. 대고객 endpoint는 호출 금지
- 인증 헤더 누락/불일치 → `401`

### 7.6 결제 외부 통신

- PaymentGateway 호출은 **트랜잭션 커밋 이후** 별도 단계에서 수행 — 외부 시스템 호출을 DB 트랜잭션 안에 두지 않음
- 타임아웃 정책: `Order.status=PENDING` 유지 + 재고/포인트 차감 유지. 비동기 reconcile job이 PG 상태 재조회하여 PAID/FAILED 확정
- PG 실패 처리: 보상 트랜잭션으로 재고/포인트 원복 + `Order.status=FAILED`
- `Idempotency-Key` 헤더 지원 (클라이언트 중복 호출 방어)

### 7.7 관측가능성·Audit

- **행동 로그**: 모든 사용자 행위(상품 조회, 좋아요, 주문, 검색, 비밀번호 변경) 비동기 기록 (§8.4)
- **Admin audit**: 어드민 CUD 액션 + 조회는 모두 별도 audit 로그
- **결제 추적 로그**: 주문 상태 변화(PENDING→PAID/FAILED)는 audit + 행동 로그 양쪽 기록
- **민감 정보**: 비밀번호(평문/해시), 결제 카드 상세, PG raw response는 로그에서 마스킹

### 7.8 시간 처리

- 모든 시간 값은 서버 UTC 저장. 응답은 ISO 8601 (UTC `Z` 접미사)
- 클라이언트 로컬 시간 표시는 클라이언트 책임

## 8. 비즈니스 규칙 (글로벌)

횡단 정책. 각 UC에서 본 절을 참조한다.

### 8.1 비밀번호 정책

- 길이: 8자 이상, 32자 이하
- 문자종: 영문 대문자·소문자·숫자·특수문자 중 **3종 이상** 포함
- 저장: bcrypt 해시 + salt. MD5/SHA1 단방향 약식 해시 금지
- 평문 비밀번호는 로그·응답·DB 어디에도 저장 금지
- 비밀번호 변경 후 이전 비밀번호로의 인증은 즉시 무효

### 8.2 Soft Delete Cascade

- **모든 삭제는 soft delete**: `deletedAt` 컬럼 기록 (`deleted` boolean 또는 nullable timestamp)
- Cascade 순서 (Q15):
  - **Brand 삭제** → 소속 Product 모두 soft delete → 해당 Product의 Like 모두 soft delete
  - **Product 삭제** → 해당 Product의 Like 모두 soft delete
  - **Like 삭제** (사용자 취소): 단일 행만 soft delete
- **Order/OrderItem은 cascade 대상 아님** — 주문은 스냅샷으로 보존. Brand/Product 삭제·변경 영향 없음
- 대고객 조회는 기본적으로 `deletedAt IS NULL` 필터 적용
- 어드민은 `?includeDeleted=true` 옵션으로 soft deleted 자원 조회 가능
- Like soft delete + UNIQUE 충돌 회피: 재등록 시 새 INSERT 대신 기존 행을 UPDATE로 reactivate (MariaDB 부분 인덱스 미지원 회피 — UC-05 참조)

### 8.3 비정규화 카운터 (likesCount)

- `Product.likesCount`는 활성 Like 수의 비정규화 사본
- 좋아요 등록/취소 시 ±1 원자 UPDATE로 동기 유지
- 매 요청 `COUNT(*)` 실시간 산출 금지 (`likes_desc` 정렬·목록 응답 성능 위해)
- 음수 방지: `GREATEST(likesCount - 1, 0)`
- 정합성 보정: 주기적 reconcile 배치(옵션) — 실제 활성 Like 수와 비교하여 보정

### 8.4 행동 로깅

- **기록 대상 이벤트** (대고객):
  - `brand_viewed`, `product_viewed`, `product_searched`
  - `product_liked`, `product_unliked`
  - `order_created`, `order_paid`, `order_failed`
  - `password_changed`
- **기록 대상 이벤트** (Admin):
  - `admin_brand_created/updated/deleted`
  - `admin_product_created/updated/deleted`
  - `admin_order_viewed`
- **스키마**: `eventId, eventType, actorType(user|guest|admin), actorId(nullable), entityType, entityId, occurredAt, metadata(JSON)`
- **처리 방식**: 비동기. 메인 트랜잭션 외부에서 발행 (Outbox 패턴 권장)
- **저장소**: 별도 audit 테이블 (현 단계). 추후 메시지 큐 → 데이터 웨어하우스로 분리 검토
- **PII/민감정보 마스킹**: 비밀번호·결제 카드 상세는 metadata에서 제외 또는 마스킹

### 8.5 응답 코드 정책

- `200 OK`: 일반 성공 (body 있음)
- `201 CREATED`: 자원 생성 성공 (어드민 생성)
- `202 ACCEPTED`: 비동기 처리 시작 (PG 타임아웃 케이스)
- `204 No Content`: 성공 + body 없음 (좋아요, 비밀번호 변경, 삭제)
- `400 BAD_REQUEST`: 입력 검증 실패. body에 `{code, message, details}` 포함
- `401 UNAUTHORIZED`: 인증 실패 (헤더 누락/불일치)
- `404 NOT_FOUND`: 자원 미존재. **본인 외 자원 접근도 404로 통일** (정보 노출 방지)
- `409 CONFLICT`: 상태 충돌 (재고·포인트 부족, race condition 등)
- `403 FORBIDDEN`: 권한 부족이 명백한 경우만 사용 (예: Admin 자원에 대고객 토큰 접근). 본인 격리는 `404`로 대체

### 8.6 식별·인증

- 대고객: `X-Loopers-LoginId` + `X-Loopers-LoginPw` 헤더로 매 요청 stateless 식별
- 어드민: `X-Loopers-Ldap: loopers.admin` 헤더
- 비밀번호 해시 비교는 상수 시간 알고리즘 사용 (timing attack 방어)
- 헤더 인증이라 별도 세션/토큰 무효화 절차 없음 — 비밀번호 변경 즉시 이전 비번 무효
