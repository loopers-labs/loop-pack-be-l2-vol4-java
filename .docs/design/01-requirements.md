# Loopers 이커머스 — 비즈니스 요구사항

## 서비스 개요

좋아요를 누르고, 쿠폰을 쓰고, 포인트로 결제하는 감성 이커머스.  
내가 좋아하는 브랜드의 상품들을 한 번에 담아 주문하고,  
유저 행동은 랭킹과 추천으로 연결된다.

---

## 확정된 설계 결정사항

| 항목 | 결정 | 리서치 근거 | 비고 |
|------|------|------------|------|
| 기술 카테고리 필터 | `TechCategory` 기반 도서 필터 구현 | H1 ✅ 62.0%, 전 직군 채택 | 범직군 공통 최우선 기능 |
| 난이도 표시 | `Level` (BEGINNER/INTERMEDIATE/ADVANCED) | H2 ✅ 79.1%, 14/15 직군 채택 | 수준 불일치 경험자 79% |
| 좋아요 기능 | 단순 토글(on/off) — MVP | H4 ✅ 55.0%, 전 직군 채택 | 메모/태그 강화는 차기 검토 |
| 주문 구조 | 브랜드 무관 단일 주문 1건 / 배송 1번 | H5 ⚠️ 40.1% 임계값 근접 | 추가 검증 권고 |
| 출판사 필터 | Publisher UI는 보안·네트워크 카테고리 한정 | H3 ⚠️ 전체 13.7% / SECURITY_NET 36.2% | 일반 개발자 대상 비노출 |
| 쿠폰 발급 | 가입 시 자동 지급 + 코드 직접 입력 | H6 ❌ 측정 불가 — 차기 설문 필요 | MVP 확장 포인트 유지 |
| 랭킹/추천 | 행동 데이터 기록만, 기능은 확장 포인트 | 측정 미실시 | 현재 미구현 |
| 인증 방식 | HTTP 헤더 기반 (X-Loopers-LoginId/Pw) | — | 인증/인가 구현 없음 |

---

## 현재 구현 범위

```
회원 → 브랜드/상품 → 좋아요 → 주문
```

---

## API 명세

### API 공통 정책

- 대고객: `/api/v1` prefix
- 어드민: `/api-admin/v1` prefix
- 유저 인증 헤더: `X-Loopers-LoginId`, `X-Loopers-LoginPw`
- 어드민 인증 헤더: `X-Loopers-Ldap: loopers.admin`
- 유저는 타 유저 정보에 직접 접근 불가

#### 공통 에러 응답 정책

모든 API는 실패 시 아래 형식으로 응답한다.

```json
{
  "code": "ERROR_CODE",
  "message": "사람이 읽을 수 있는 설명"
}
```

| HTTP Status | 의미 | 예시 상황 |
|-------------|------|----------|
| `400 Bad Request` | 요청 형식·비즈니스 규칙 위반 | 비밀번호 형식 오류, 재고 부족, 수량 범위 초과 |
| `401 Unauthorized` | 인증 헤더 누락 또는 불일치 | `X-Loopers-LoginId`/`Pw` 없음, 비밀번호 불일치 |
| `403 Forbidden` | 타 유저 리소스 접근 시도 | 본인 것이 아닌 주문·좋아요 조회 |
| `404 Not Found` | 존재하지 않는 리소스 | 없는 productId, orderId |
| `409 Conflict` | 중복 요청 | 동일 상품 중복 좋아요 등록 |

#### 좋아요 멱등성 정책

| 상황 | 응답 |
|------|------|
| 이미 좋아요한 상품에 `POST` | `409 Conflict` |
| 좋아요하지 않은 상품에 `DELETE` | `404 Not Found` |

---

### 👤 유저 (Users)

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| PUT | `/api/v1/users/password` | O | 비밀번호 변경 |

---

### 🏷 브랜드 / 상품 — 대고객

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | X | 상품 정보 조회 |

#### 상품 목록 조회 파라미터

| 파라미터 | 예시 | 설명 | 리서치 근거 |
|---------|------|------|------------|
| `brandId` | `1` | 특정 브랜드(출판사) 필터 | H3 — SECURITY/NETWORK 한정 유의미 |
| `category` | `BACKEND` / `SECURITY` / `NETWORK` | 기술 카테고리 필터 | **H1 ✅ 62.0% — 최우선 구현** |
| `level` | `BEGINNER` / `INTERMEDIATE` / `ADVANCED` | 난이도 필터 | **H2 ✅ 79.1% — 최우선 구현** |
| `sort` | `latest` / `price_asc` / `likes_desc` | 정렬 (`latest` 필수, 나머지 선택). `latest` = 등록 기준 최신순(`createdAt DESC`) | — |
| `page` | `0` | 페이지 번호 (기본값 0, 0-based) | — |
| `size` | `20` | 페이지당 수 (기본값 20, 최대 100) | — |

> `category`와 `level`은 리서치(H1·H2)로 전 직군 채택 확인. 기존 API 명세에 누락되어 있어 추가를 강권한다.

---

### 🏷 브랜드 / 상품 — 어드민

| METHOD | URI | 설명 | 비즈니스 규칙 |
|--------|-----|------|--------------|
| GET | `/api-admin/v1/brands?page=0&size=20` | 브랜드 목록 조회 | |
| GET | `/api-admin/v1/brands/{brandId}` | 브랜드 상세 조회 | |
| POST | `/api-admin/v1/brands` | 브랜드 등록 | |
| PUT | `/api-admin/v1/brands/{brandId}` | 브랜드 수정 | |
| DELETE | `/api-admin/v1/brands/{brandId}` | 브랜드 삭제 | **소속 상품 모두 삭제** |
| GET | `/api-admin/v1/products?page=0&size=20&brandId={brandId}` | 상품 목록 조회 | |
| GET | `/api-admin/v1/products/{productId}` | 상품 상세 조회 | |
| POST | `/api-admin/v1/products` | 상품 등록 | **등록된 브랜드여야 함** |
| PUT | `/api-admin/v1/products/{productId}` | 상품 수정 | **브랜드 수정 불가** |
| DELETE | `/api-admin/v1/products/{productId}` | 상품 삭제 | |

---

### ❤️ 좋아요 (Likes)

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/products/{productId}/likes` | O | 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | O | 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | O | 내가 좋아요 한 상품 목록 |

**비즈니스 규칙:**
- 같은 회원이 같은 상품에 중복 좋아요 불가
- 좋아요 등록/취소 시 상품의 `likeCount` 증감

---

### 🧾 주문 (Orders) — 대고객

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/orders` | O | 주문 요청 |
| GET | `/api/v1/orders?startAt=2026-01-31&endAt=2026-02-10` | O | 주문 목록 조회. 날짜 형식: `YYYY-MM-DD` (KST 기준). `endAt` 당일 23:59:59까지 포함. 최대 조회 범위 365일 |
| GET | `/api/v1/orders/{orderId}` | O | 주문 상세 조회 |

#### 주문 요청 예시

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

**비즈니스 규칙:**
- 주문 시 재고 확인 및 차감 필수
- 주문 정보에 주문 당시 상품명·가격 **스냅샷** 저장
- 유저는 자신의 주문만 조회 가능

---

### 🧾 주문 (Orders) — 어드민

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api-admin/v1/orders?page=0&size=20` | 전체 주문 목록. `size` 최대 100 |
| GET | `/api-admin/v1/orders/{orderId}` | 주문 상세 조회 |

---

## 확장 예정 기능 (현재 미구현)

| 기능 | 트리거 | 설명 |
|------|--------|------|
| 쿠폰 자동 발급 | 회원가입 완료 | 웰컴 쿠폰 지급 |
| 쿠폰 코드 등록 | 유저 직접 입력 | CouponCode 기반 |
| 결제 | 주문 완료 | 포인트·쿠폰 적용 |
| 포인트 | 결제 연동 | 적립·사용 |
| 랭킹/추천 | 좋아요·주문 행동 | UserActivity 기반 |

---

## 향후 해결해야 할 기술 과제

- **동시성**: 동시 주문 시 재고 음수 방지
- **멱등성**: 중복 요청 방지
- **일관성**: 주문-재고 트랜잭션 경계
- **성능**: 날짜 범위 주문 조회 인덱스, 좋아요 많은 상품 정렬

---

## 검증 필요 항목 (리서치 후속 조치)

| 항목 | 현재 상태 | 필요 조치 | 우선순위 |
|------|----------|----------|---------|
| H5 단일 주문 니즈 강도 | 40.1% 임계값 근접 | "통합 배송 시 구매 권수 증가 여부" 의향 문항 추가 설문 | 🟠 중 |
| H6 쿠폰 효과 | 측정 불가 | 차기 설문 Q6 전용 문항 신설 | 🔵 낮음 |
| Like 기능 강화 | MVP 토글만 구현 | 메모/태그 추가 여부 — MVP 출시 후 행동 데이터 기반 결정 | 🟠 중 |
| Book 기술 버전 표시 | 필드 없음 | `publishedYear` + `targetTechVersion` 필드 추가 여부 설계 검토 | 🟠 중 |
| Publisher 필터 UX | 전체 노출 중 | 보안·네트워크 카테고리 선택 시에만 출판사 필터 노출 UX 검증 | 🟡 낮음 |

> 출처: `.docs/planning/results/survey/02-종합_분석.md`, `results/interview/interview_results.md`

> 도메인 스팩 상세: [`00-domain-spec.md`](./00-domain-spec.md)
