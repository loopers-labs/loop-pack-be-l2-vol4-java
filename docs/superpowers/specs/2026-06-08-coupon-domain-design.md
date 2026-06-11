# Coupon 도메인 설계

Date: 2026-06-08
Scope: 4주차 — 쿠폰 도메인 신규 생성 + 주문 스냅샷 구조 변경

---

## 배경

4주차 요구사항에 따라 쿠폰 기능을 추가한다. 동시에 기존 Order 도메인의 주문 항목 저장 방식을 JSON 스냅샷으로 전환하여 쿠폰 적용 금액 정보를 함께 저장한다.

핵심 목표:
- 쿠폰 템플릿 관리 (Admin) + 쿠폰 발급/사용 (User)
- 주문 시 쿠폰 적용 및 할인 계산
- 동시성 이슈 제어 (쿠폰 사용 처리)
- DB 트랜잭션으로 재고/쿠폰/주문 정합성 보장

---

## 확정된 설계 결정

| 항목 | 결정 |
|---|---|
| 쿠폰 템플릿 Entity 이름 | `CouponTemplateEntity` |
| 발급된 쿠폰 Entity 이름 | `CouponEntity` |
| 만료 처리 방식 | status 컬럼 저장 + lazy 만료 전환 |
| 패키지 구조 | 단일 `coupon` 패키지 |
| Application 계층 이름 | `CouponApplicationService` (Facade 아님) |
| Domain Service | 없음 (비즈니스 로직은 Entity에 캡슐화) |
| 주문 스냅샷 방식 | 전체 JSON 스냅샷 (`OrderSnapshot`) |
| 기존 OrderItemVO | 제거 (OrderSnapshot으로 통합) |

---

## 1. Coupon 도메인

### 1-1. Domain Layer (`domain/coupon/`)

#### CouponTemplateEntity

```
fields:
  - name: String                    // 쿠폰명
  - type: CouponType                // FIXED | RATE
  - value: Long                     // FIXED: 할인 금액(원), RATE: 할인율(%)
  - minOrderAmount: Long (nullable) // 최소 주문 금액 조건
  - expiredAt: ZonedDateTime        // 만료일시

business methods:
  - isExpired(): boolean            // ZonedDateTime.now() > expiredAt
  - calculateDiscount(Long orderAmount): Long
      // FIXED: min(value, orderAmount)
      // RATE: orderAmount * value / 100
```

#### CouponEntity

```
fields:
  - templateId: Long
  - userId: Long
  - status: CouponStatus            // AVAILABLE | USED | EXPIRED

business methods:
  - use(): void
      // status != AVAILABLE → CoreException
      // status = USED
  - resolveStatus(ZonedDateTime expiredAt): CouponStatus
      // status == AVAILABLE && expiredAt < now() → EXPIRED 반환 (lazy, DB 미반영)
      // 그 외 → status 그대로 반환
  - isOwnedBy(Long userId): boolean
```

#### Enums

```java
enum CouponType { FIXED, RATE }
enum CouponStatus { AVAILABLE, USED, EXPIRED }
```

#### Repository Interfaces

```
CouponTemplateRepository
  - save(CouponTemplateEntity): CouponTemplateEntity
  - findById(Long id): Optional<CouponTemplateEntity>
  - findAll(Pageable): Page<CouponTemplateEntity>
  - delete(Long id): void

CouponRepository
  - save(CouponEntity): CouponEntity
  - findById(Long id): Optional<CouponEntity>
  - findByIdWithLock(Long id): Optional<CouponEntity>  // 비관적 락 (주문 시 사용)
  - findAllByUserId(Long userId, Pageable): Page<CouponEntity>
  - findAllByCouponTemplateId(Long templateId, Pageable): Page<CouponEntity>
```

### 1-2. Infrastructure Layer (`infrastructure/coupon/`)

```
CouponTemplateJpaEntity (extends BaseJpaEntity)
  - @Table(name = "coupon_templates")
  - 필드: name, type, value, min_order_amount, expired_at

CouponTemplateJpaRepository (extends JpaRepository)

CouponTemplateMapper
  - toDomain(CouponTemplateJpaEntity): CouponTemplateEntity
  - toJpaEntity(CouponTemplateEntity): CouponTemplateJpaEntity

CouponTemplateRepositoryImpl (implements CouponTemplateRepository)

CouponJpaEntity (extends BaseJpaEntity)
  - @Table(name = "coupons")
  - 필드: template_id, user_id, status

CouponJpaRepository (extends JpaRepository)
  - @Lock(LockModeType.PESSIMISTIC_WRITE) findByIdWithLock

CouponMapper
CouponRepositoryImpl (implements CouponRepository)
```

### 1-3. Application Layer (`application/coupon/`)

```
CouponApplicationService
  유즈케이스 목록:
  - createTemplate(command): CouponTemplateInfo         // Admin: 템플릿 등록
  - updateTemplate(id, command): CouponTemplateInfo     // Admin: 템플릿 수정
  - deleteTemplate(id): void                            // Admin: 템플릿 삭제
  - getTemplate(id): CouponTemplateInfo                 // Admin: 템플릿 단건 조회
  - getTemplates(pageable): Page<CouponTemplateInfo>    // Admin: 템플릿 목록 조회
  - getTemplateIssues(templateId, pageable): Page<CouponInfo>  // Admin: 발급 내역 조회
  - issue(userId, templateId): CouponInfo               // User: 쿠폰 발급
  - getMyCoupons(userId, pageable): Page<CouponInfo>    // User: 내 쿠폰 목록
  - useForOrder(userId, couponId, orderAmount): DiscountResult
      // 주문 Facade에서 호출, 쿠폰 검증 + 사용 + 할인 금액 반환

CouponTemplateInfo (record)
  - id, name, type, value, minOrderAmount, expiredAt, createdAt

CouponInfo (record)
  - id, templateId, userId, resolvedStatus, name, type, value, expiredAt
  // name, type, value, expiredAt은 CouponTemplateEntity에서 조회 시 조합

DiscountResult (record)
  - couponId: Long
  - discountAmount: Long
```

---

## 2. Order 도메인 변경

### 2-1. OrderSnapshot 도입

기존 `OrderItemVO` / `OrderItemJpaVO` / `OrderItemJpaRepository` 를 제거하고, 주문 정보 전체를 JSON 스냅샷으로 저장한다.

#### Domain Layer

```
OrderSnapshot (record)
  - items: List<OrderSnapshotItem>
  - originalAmount: Long
  - discountAmount: Long
  - finalAmount: Long
  - couponId: Long (nullable)

OrderSnapshotItem (record)
  - productId: Long
  - productName: String
  - productPrice: Long
  - quantity: Integer
  - subtotal: Long
```

#### OrderEntity 변경

```
before:
  - List<OrderItemVO> items
  - calculateTotalAmount(): Long  // 동적 계산

after:
  - OrderSnapshot snapshot        // JSON으로 저장된 스냅샷
  // calculateTotalAmount() 제거 → snapshot.finalAmount 사용
```

#### Infrastructure Layer 변경

```
제거:
  - OrderItemVO (domain)
  - OrderItemJpaVO (infrastructure)
  - OrderItemJpaRepository

추가:
  - OrderSnapshotConverter (JPA AttributeConverter<OrderSnapshot, String>)
    // Jackson으로 직렬화/역직렬화

변경:
  - OrderJpaEntity: order_items 관련 컬럼 제거, snapshot TEXT 컬럼 추가
  - OrderMapper: snapshot 매핑 추가
```

DB 스키마:
```sql
-- orders 테이블
ALTER TABLE orders ADD COLUMN snapshot TEXT NOT NULL;
-- order_items 테이블 제거
```

### 2-2. OrderFacade 변경

```
createOrder(userId, commands, couponId) 흐름:
  1. 상품 정보 조회 → OrderSnapshotItem 목록 생성
  2. originalAmount 계산
  3. couponId 있으면: CouponApplicationService.useForOrder() 호출
       → discountAmount, finalAmount 계산
     없으면: discountAmount = 0, finalAmount = originalAmount
  4. 재고 차감 (InventoryService.deductAll)
  5. OrderSnapshot 생성
  6. OrderService.createOrder(userId, snapshot)
```

---

## 3. 동시성 제어

| 위험 구간 | 방식 |
|---|---|
| 쿠폰 사용 처리 | 비관적 락 (`PESSIMISTIC_WRITE`) — `CouponRepository.findByIdWithLock()` |
| 재고 차감 | 기존 방식 유지 |

쿠폰 사용과 재고 차감은 `OrderFacade.createOrder()` 의 `@Transactional` 범위 내에서 처리되어 원자성을 보장한다.

---

## 4. API 매핑 요약

### 대고객 (User)

| Method | URI | 처리 |
|---|---|---|
| POST | `/api/v1/coupons/{couponId}/issue` | 쿠폰 발급 |
| GET | `/api/v1/users/me/coupons` | 내 쿠폰 목록 (resolvedStatus 포함) |
| POST | `/api/v1/orders` | 주문 (couponId 추가, nullable) |

### 어드민 (Admin)

| Method | URI | 처리 |
|---|---|---|
| GET/POST | `/api-admin/v1/coupons` | 템플릿 목록 조회 / 등록 |
| GET/PUT/DELETE | `/api-admin/v1/coupons/{couponId}` | 템플릿 상세/수정/삭제 |
| GET | `/api-admin/v1/coupons/{couponId}/issues` | 발급 내역 조회 |

---

## 5. 제외 범위

- 쿠폰 만료 배치 처리 (추후 `commerce-batch` 모듈에 별도 추가)
- 중복 발급 방지 (요구사항에 없음)
