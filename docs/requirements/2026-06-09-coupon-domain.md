# Design Doc: Coupon 도메인

- 작성일: 2026-06-09
- 작성자: Hangju0610
- 상태: 검토 중

---

## 1. 제품 개요

주문 시 쿠폰을 적용해 할인 혜택을 제공하는 Coupon 도메인을 구현한다.
어드민은 쿠폰 템플릿(할인 정책)을 정의하고, 유저는 템플릿 기반으로 쿠폰을 발급받아 주문에 사용한다.
동시에 같은 쿠폰을 사용하려는 요청에도 중복 사용이 발생하지 않도록 비관적 락으로 동시성을 제어한다.

---

## 2. 사용자 시나리오

### 어드민
1. 쿠폰 템플릿을 등록한다 (FIXED/RATE 타입, 할인값, 최소 주문금액, 만료일 설정).
2. 등록된 템플릿 목록 및 단건을 조회한다.
3. 템플릿의 이름, 최소 주문금액, 만료일을 수정한다 (타입·할인값은 수정 불가).
4. 템플릿을 삭제한다 (발급된 쿠폰 연쇄 soft delete).
5. 특정 템플릿의 발급 내역을 조회한다.

### 유저
1. 쿠폰 템플릿 ID로 자신에게 쿠폰을 발급 요청한다.
2. 내 쿠폰 목록을 조회한다 (AVAILABLE / USED / EXPIRED 전체 반환).
3. 주문 시 보유한 쿠폰 ID를 포함해 할인을 적용한다.

---

## 3. 유저 스토리

| # | Actor | 기능 | 인수 조건 |
|---|---|---|---|
| US-01 | Admin | 쿠폰 템플릿 등록 | 필수 필드 검증 통과 시 201 반환 |
| US-02 | Admin | 쿠폰 템플릿 목록 조회 | 페이지네이션 적용, soft delete 제외 |
| US-03 | Admin | 쿠폰 템플릿 단건 조회 | 없으면 404 |
| US-04 | Admin | 쿠폰 템플릿 수정 | type/value 수정 불가, 나머지 수정 가능 |
| US-05 | Admin | 쿠폰 템플릿 삭제 | 발급된 쿠폰 연쇄 soft delete |
| US-06 | Admin | 쿠폰 발급 내역 조회 | 특정 템플릿 기준 페이지네이션 |
| US-07 | User | 쿠폰 발급 요청 | 템플릿 존재·미만료 검증, AVAILABLE 상태로 INSERT |
| US-08 | User | 내 쿠폰 목록 조회 | 전체 상태 반환, status는 lazy 계산값 |
| US-09 | User | 주문 시 쿠폰 적용 | 쿠폰 없는 주문도 가능 (couponId nullable) |

---

## 4. 기능 요구사항

### 4-1. 쿠폰 템플릿 (Admin)

#### 등록
| 필드 | 규칙 |
|---|---|
| `name` | 필수, 빈 문자열 불가 |
| `type` | `FIXED` 또는 `RATE` |
| `value` | FIXED: 원 단위 양의 정수 / RATE: 1~100 정수 (%) |
| `minOrderAmount` | nullable, 0 이상 정수 |
| `expiredAt` | 필수, 미래 일시 |

#### 수정
| 필드 | 수정 가능 여부 |
|---|:---:|
| `name` | O |
| `type` | X — 발급된 쿠폰 정합성 깨짐 |
| `value` | X — 동일 이유 |
| `minOrderAmount` | O |
| `expiredAt` | O (미래 일시만 허용) |

#### 삭제
```
CouponApplicationService.deleteTemplate(couponTemplateId)
  ├── CouponTemplateEntity 조회 → 없으면 404
  ├── couponTemplate.delete()
  └── 발급된 쿠폰 전체 soft delete (COUPONS WHERE coupon_template_id = ?)
```

#### 응답 필드

| 필드 | 목록 | 단건 |
|---|:---:|:---:|
| couponTemplateId | ✅ | ✅ |
| name | ✅ | ✅ |
| type | ✅ | ✅ |
| value | ✅ | ✅ |
| minOrderAmount | ✅ | ✅ |
| expiredAt | ✅ | ✅ |
| createdAt | ✅ | ✅ |
| updatedAt | ❌ | ✅ |

---

### 4-2. 쿠폰 발급 (User)

- `couponTemplateId`로 템플릿 존재 여부 검증 (없으면 404)
- `isExpired()` 검증 — 만료된 템플릿으로 발급 불가 (400)
- AVAILABLE 상태로 INSERT, userId = 인증된 유저

---

### 4-3. 내 쿠폰 목록 조회 (User)

- `GET /api/v1/users/me/coupons` — 페이지네이션 적용
- AVAILABLE / USED / EXPIRED 전체 반환
- `status`는 `resolveStatus(expiredAt)` lazy 계산값 반환 (ADR-029)

| 필드 | 반환 여부 |
|---|:---:|
| couponId | ✅ |
| templateName | ✅ |
| type | ✅ |
| value | ✅ |
| minOrderAmount | ✅ |
| expiredAt | ✅ |
| status | ✅ |

---

### 4-4. 주문 시 쿠폰 사용

```
CouponApplicationService.useCoupon(couponId, userId, originalAmount)
  1. CouponRepository.findByIdWithLock(couponId)         → CouponEntity (PESSIMISTIC_WRITE, 없으면 404)
  2. coupon.isOwnedBy(userId)                            → 불일치 시 403
  3. CouponTemplateRepository.findById(couponTemplateId) → CouponTemplateEntity
  4. coupon.resolveStatus(template.expiredAt)            → EXPIRED 시 400
  5. coupon.status == USED                               → 400
  6. template.validateMinOrderAmount(originalAmount)      → 400 (도메인 규칙, CouponTemplateEntity)
  7. discountAmount = template.calculateDiscount(originalAmount)
  8. coupon.use()                                        → AVAILABLE → USED (인메모리)
  9. CouponRepository.save(coupon)                       → DB 반영 (도메인/JPA 엔티티 분리 구조)
  10. return discountAmount
```

#### 할인 계산
| 타입 | 계산식 |
|---|---|
| FIXED | `min(value, orderAmount)` |
| RATE | `orderAmount * value / 100` |

---

## 5. 비기능 요구사항

| 항목 | 내용 |
|---|---|
| 동시성 | 쿠폰 중복 사용 방지 — PESSIMISTIC_WRITE 락 (ADR-031) |
| 트랜잭션 | 쿠폰 사용 → 재고 차감 → 주문 생성 단일 트랜잭션 (ADR-032) |
| Soft Delete | CouponTemplate/Coupon 모두 soft delete, 템플릿 삭제 시 연쇄 |

---

## 6. API 엔드포인트

| Method | URI | 설명 | 인증 |
|---|---|---|:---:|
| GET | `/api-admin/v1/coupons?page=0&size=20` | 쿠폰 템플릿 목록 조회 | Admin |
| GET | `/api-admin/v1/coupons/{couponTemplateId}` | 쿠폰 템플릿 단건 조회 | Admin |
| POST | `/api-admin/v1/coupons` | 쿠폰 템플릿 등록 | Admin |
| PUT | `/api-admin/v1/coupons/{couponTemplateId}` | 쿠폰 템플릿 수정 | Admin |
| DELETE | `/api-admin/v1/coupons/{couponTemplateId}` | 쿠폰 템플릿 삭제 | Admin |
| GET | `/api-admin/v1/coupons/{couponTemplateId}/issues?page=0&size=20` | 발급 내역 조회 | Admin |
| POST | `/api/v1/coupons/{couponTemplateId}/issue` | 쿠폰 발급 요청 | User |
| GET | `/api/v1/users/me/coupons` | 내 쿠폰 목록 조회 | User |

---

## 7. 에러 처리

| 상황 | ErrorType | HTTP |
|---|---|---|
| 쿠폰 템플릿 없음 | `NOT_FOUND` | 404 |
| 쿠폰 없음 | `NOT_FOUND` | 404 |
| 발급 시 템플릿 만료됨 | `BAD_REQUEST` | 400 |
| 쿠폰 소유자 불일치 | `FORBIDDEN` | 403 |
| 쿠폰 이미 사용됨 | `BAD_REQUEST` | 400 |
| 쿠폰 만료됨 | `BAD_REQUEST` | 400 |
| 주문금액 < 최소 주문금액 | `BAD_REQUEST` | 400 |

---

## 8. 의존성

| 의존 대상 | 방향 | 용도 |
|---|---|---|
| Order 도메인 | Order → Coupon | 주문 생성 시 쿠폰 사용 처리 |
| User 도메인 | 없음 | userId는 인증 인터셉터 주입, 별도 User 조회 불필요 |

---

## 9. 관련 ADR

| ADR | 내용 |
|---|---|
| ADR-028 | 주문 전체 JSON 스냅샷 (couponId 포함) |
| ADR-029 | 쿠폰 상태 Lazy 만료 처리 |
| ADR-030 | ApplicationService 네이밍 |
| ADR-031 | 쿠폰 비관적 락 전략 |
| ADR-032 | 쿠폰 적용 시 주문 생성 흐름 변경 |
| ADR-033 | 쿠폰 중복 발급 허용 |
| ADR-034 | CouponDomainService 미도입 및 레이어 책임 재정의 |
