# 05. 설계 의사결정 기록 (Week 4 — Coupon)

> 설계 과정에서 고민하고 결정한 사항들을 기록합니다.  
> "왜 이렇게 했는가"와 "왜 다른 선택지를 버렸는가"를 함께 남깁니다.

---

## DD-012. CouponModel / UserCouponModel 분리

**고민**

쿠폰 데이터를 단일 테이블로 설계할지, 템플릿과 발급 이력을 분리할지.

```
// 단일 테이블 안
coupons { id, user_id, name, type, value, status, expired_at }
```

이렇게 하면 테이블이 하나지만, `user_id IS NULL`(미발급)과 `user_id IS NOT NULL`(발급됨)이 섞인다.

**제외한 선택지 — 단일 테이블**

1. `user_id` 컬럼이 NULL 허용 → 발급 전/후 상태를 컬럼 존재 여부로 구분하는 설계는 의미가 불명확하다.
2. 동일한 쿠폰을 여러 사용자에게 발급할 수 없다 (행이 복수로 존재해야 하므로 템플릿의 의미가 사라진다).
3. Admin(템플릿 관리)과 User(발급 이력 조회)의 관심사가 동일 테이블에 혼재한다.

**결정**

`coupons` (쿠폰 템플릿, Admin이 관리) / `user_coupons` (발급된 쿠폰, User 소유) 분리.

```
coupons { id, name, type, value, min_order_amount, expired_at }
    ↓ 1 : N
user_coupons { id, user_id, coupon_id, status, version }
```

**근거**

| 가치 | 설명 |
|---|---|
| 관심사 분리 | Admin은 템플릿 관리, User는 발급 이력 조회 — 쿼리 대상이 다르다 |
| 확장성 | 동일 쿠폰을 N명에게 발급 가능. 발급 수량 제한, 선착순 발급 등 이후 요구사항에 자연스럽게 대응 |
| 락 경합 분리 | 쿠폰 사용(user_coupons 행 락) / 템플릿 수정(coupons 행 락) 이 별개 행 |

---

## DD-013. EXPIRED 상태 — DB 비저장, 동적 계산

**고민**

`user_coupons.status` 컬럼에 AVAILABLE / USED / EXPIRED 세 값을 모두 저장할지,
EXPIRED는 `coupons.expired_at` 기준으로 매번 계산할지.

**제외한 선택지 — EXPIRED DB 저장**

만료 처리를 DB에 반영하려면 배치 잡이 필요하다.

```
// 매일 자정 실행
UPDATE user_coupons SET status = 'EXPIRED'
WHERE status = 'AVAILABLE' AND coupon.expired_at < NOW()
```

배치가 돌기 전 시간대에는 만료된 쿠폰이 AVAILABLE로 보이는 시간 차이가 생긴다.
또한 배치 실패 시 만료 처리가 누락된다.

**결정**

DB에는 AVAILABLE / USED 두 값만 저장하고, EXPIRED는 `computedStatus()` 에서 동적 계산.

```java
public UserCouponStatus computedStatus() {
    if (this.status == UserCouponStatus.USED) return UserCouponStatus.USED;
    if (coupon.isExpired()) return UserCouponStatus.EXPIRED;  // expired_at < now()
    return UserCouponStatus.AVAILABLE;
}
```

**근거**

- 배치 없이도 `expired_at` 변경이 즉시 반영된다.
- 만료 판정이 단일 지점(computedStatus)에서 이루어져 일관성이 보장된다.
- 쿠폰 사용 시 `UserCouponModel.use()` 내부에서도 `coupon.isExpired()` 를 검사해 이중 안전망을 갖춘다.

**트레이드오프**

EXPIRED 필터링 쿼리(`WHERE status = 'EXPIRED'`)가 DB 레벨에서 불가능하다.
현재 요구사항에 해당 쿼리가 없으므로 수용 가능하다. 필요 시 계산 컬럼 또는 배치로 전환한다.

---

## DD-014. 쿠폰 동시 사용 — 낙관적 락 선택

**고민**

동일 쿠폰을 여러 기기에서 동시에 사용할 때 단 한 번만 사용되도록 보장해야 한다.
낙관적 락(`@Version`)과 비관적 락(`SELECT ... FOR UPDATE`) 중 선택.

**제외한 선택지 — 비관적 락**

비관적 락은 쿠폰 행을 트랜잭션 종료 시까지 잠근다.

```sql
SELECT * FROM user_coupons WHERE id = ? FOR UPDATE
-- 이후 재고 차감, 주문 저장이 완료될 때까지 잠금 유지
```

주문 트랜잭션은 재고 차감 + 쿠폰 사용 + 주문 저장을 모두 포함하므로 잠금 보유 시간이 길다.
재고(stocks)에도 락이 필요하다면 두 행을 동시에 잠그는 구조가 되어 데드락 위험이 생긴다.

**결정**

낙관적 락(`@Version`) 선택.

```java
@Version
@Column(name = "version", nullable = false)
private Long version;
```

UPDATE 시 version 불일치 → `OptimisticLockException` → 트랜잭션 롤백.

**근거**

1. **충돌 확률이 낮다.** 쿠폰은 특정 사용자에게 귀속된다. 충돌이 발생하려면 같은 사람이 여러 기기에서 동시에 주문해야 한다. 불특정 다수가 경합하는 재고와 성격이 다르다.
2. **데드락이 원천 불가능하다.** 낙관적 락은 실제 DB 락을 잡지 않으므로 재고와의 순서 문제가 없다.
3. **실패가 명확하다.** 충돌 시 한 쪽에 에러를 돌려주는 것이 올바른 동작이다. 어차피 두 건 중 한 건은 실패해야 한다.
4. **정합성 수준이 충분하다.** 송금처럼 수량이 엄격히 맞아야 하는 케이스가 아니다. 낙관적 락의 재시도 실패는 사용자에게 "이미 사용된 쿠폰" 에러로 돌아간다.

---

## DD-015. 1인 1회 발급 제한 — DB 제약 + 애플리케이션 체크 이중 방어

**고민**

동일 사용자가 같은 쿠폰을 여러 번 발급받지 못하도록 하는 방법.
애플리케이션 레벨 체크만으로 충분한지, DB 제약도 필요한지.

**결정**

DB 레벨의 `UNIQUE (user_id, coupon_id)` 제약 + 애플리케이션의 `existsByUserIdAndCouponId` 사전 체크 이중 방어.

```java
// 애플리케이션: 명확한 에러 메시지 반환
if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
    throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
}

// DB: UNIQUE 제약으로 동시 요청에도 중복 차단
UNIQUE KEY uq_user_coupon (user_id, coupon_id)
```

**근거**

애플리케이션 체크만으로는 동시 요청 시 두 스레드가 모두 `existsBy...` 결과로 `false`를 받아 중복 삽입이 발생할 수 있다 (TOCTOU 레이스 컨디션).
DB UNIQUE 제약이 최후 방어선이 되고, 애플리케이션 체크는 DB 예외 대신 도메인 에러 코드와 메시지를 반환하기 위한 UX 레이어다.

---

## DD-016. 주문 금액 3분할 스냅샷

**고민**

기존 `orders.total_amount` 단일 컬럼으로는 쿠폰 할인 내역을 담을 수 없다.
할인 금액을 어디에, 어떻게 저장할지.

**제외한 선택지 A — order_items에 할인 금액 배분**

주문 항목 각각에 비례 할인을 계산해 저장하는 방식.

```
item1: 30,000원 → 할인 3,000원 배분
item2: 20,000원 → 할인 2,000원 배분
```

쿠폰은 주문 전체에 적용되는 것이지 특정 항목에 적용되는 것이 아니다.
할인 배분 로직이 복잡하고, "주문 단위 쿠폰 1장"이라는 규칙과 맞지 않는다.

**제외한 선택지 B — coupon_id만 저장**

`orders.coupon_id`만 저장하고 할인 금액은 조회 시 재계산.

쿠폰 템플릿이 수정되거나 삭제되면 재계산 결과가 달라진다.
주문은 계약이므로 할인 금액도 계약 시점에 확정되어야 한다.

**결정**

`orders` 테이블에 `original_amount`, `discount_amount`, `total_amount` 3개 컬럼으로 스냅샷 저장.

```
original_amount: 쿠폰 적용 전 원래 금액
discount_amount: 적용된 할인 금액 (미적용 시 0)
total_amount:    최종 결제 금액 = original_amount - discount_amount
```

**근거**

주문은 구매자·판매자 간 계약이다. 계약 시점의 할인 내역은 이후 쿠폰 변경과 무관하게 고정되어야 한다.
3개 컬럼이 모두 있으면 환불·취소·정산 시 할인 배분을 별도 계산 없이 바로 사용할 수 있다.

---

## DD-017. 주문 요청의 couponId — UserCouponModel.id 사용

**고민**

주문 요청 `{ items, couponId }` 에서 `couponId`가 무엇을 가리키는지.

```
// 선택지 A: 쿠폰 템플릿 ID (CouponModel.id)
{ "couponId": 5 }  // "신규가입 10% 할인" 템플릿

// 선택지 B: 발급된 쿠폰 ID (UserCouponModel.id)
{ "couponId": 42 }  // 내가 발급받은 쿠폰 #42
```

**결정**

`UserCouponModel.id` (발급된 쿠폰 ID) 사용.

**근거**

1. 클라이언트는 `GET /api/v1/users/me/coupons` 응답에서 발급 쿠폰 ID를 받아 선택한다. 자연스러운 흐름이다.
2. 소유권 검증이 단순해진다. `userCoupon.getUserId().equals(command.userId())` 한 번으로 확인 가능.
3. 동일 사용자가 같은 쿠폰을 여러 장 보유하는 시나리오(현재는 1인 1회 제한이지만 추후 변경 가능)에도 대응된다.

템플릿 ID를 쓰는 경우 `findByUserIdAndCouponTemplateId` 쿼리가 필요하고, 서버에서 "어떤 발급 쿠폰인지"를 결정해야 한다. 클라이언트에서 명시하는 것이 더 명확하다.

---

## DD-018. OrderFacade 쿠폰 처리 순서

**고민**

주문 트랜잭션 내에서 쿠폰 처리와 재고 차감의 순서.

```
안 A: 재고 확인 → 재고 차감 → 쿠폰 검증·사용 → 주문 저장
안 B: 쿠폰 사전 조회 → 재고 확인 → 재고 차감 → 쿠폰 사용 → 주문 저장
```

**결정**

안 B — 쿠폰 사전 조회(존재·소유권 확인)를 가장 먼저 수행.

```java
// 1. 쿠폰 사전 조회 — fail fast (존재 + 소유권만 확인, 상태 변경 없음)
UserCouponModel userCoupon = userCouponRepository.findByIdWithCoupon(couponId)...

// 2-5. 상품 조회, 재고 검증, 재고 차감, 주문 조립

// 6. 쿠폰 사용 처리 + 할인 금액 반영 (상태 변경은 여기서만)
userCoupon.use();
order.applyPricing(originalAmount, discount);
```

**근거**

- 유효하지 않은 쿠폰이면 상품 조회·재고 차감 없이 즉시 실패할 수 있다 (fail fast).
- `userCoupon.use()` (상태 변경)는 주문 엔티티가 완성된 후에 호출해 최소 필요한 시점에만 DB 쓰기가 발생한다.
- 모든 작업이 단일 `@Transactional` 내에 있으므로 순서와 무관하게 하나라도 실패하면 전체 롤백된다.

**동시성 안전성**

쿠폰 `use()` 는 `@Version` 낙관적 락으로 보호된다. 재고와 쿠폰이 서로 다른 테이블 행이므로 데드락 위험이 없다.

---

## DD-019. UserCouponJpaRepository — @EntityGraph로 N+1 방지

**고민**

`UserCouponModel`의 `coupon` 필드는 `FetchType.LAZY`다.  
`UserCouponInfo.from()` 내부에서 `model.computedStatus()` → `coupon.isExpired()` 를 호출하므로,
페이지 조회 시 쿠폰 목록 N건에 대해 추가 쿼리 N건이 발생한다.

**제외한 선택지 — JPQL JOIN FETCH**

```java
@Query("SELECT uc FROM UserCouponModel uc JOIN FETCH uc.coupon WHERE uc.userId = :userId")
Page<UserCouponModel> findAllByUserId(@Param("userId") Long userId, Pageable pageable);
```

컬렉션 관계가 아닌 `@ManyToOne`이므로 페이지네이션과의 충돌(HHH90003004 경고)은 없다.  
하지만 `@EntityGraph`가 더 간결하고 기존 Spring Data 메서드 이름 규칙을 유지할 수 있다.

**결정**

`@EntityGraph(attributePaths = {"coupon"})` 어노테이션으로 coupon을 즉시 로딩.

```java
@EntityGraph(attributePaths = {"coupon"})
Page<UserCouponModel> findAllByUserId(Long userId, Pageable pageable);
```

**근거**

`@ManyToOne` 단일 관계에 대한 즉시 로딩은 LEFT OUTER JOIN 단일 쿼리로 실행된다.  
N+1이 발생하지 않으면서 도메인 모델의 `FetchType.LAZY` 기본값을 변경하지 않아도 된다.  
쿼리 이름 파생 규칙도 유지되어 코드 일관성이 높다.

---

## DD-020. 재고 차감 — 비관적 락에서 원자적 UPDATE로 변경

**고민**

재고 차감 시 동시성을 보장하는 방법으로 `SELECT ... FOR UPDATE`(비관적 락)와 조건부 UPDATE(원자적 업데이트) 중 선택.

**제외한 선택지 — 비관적 락**

```sql
SELECT * FROM stocks WHERE product_id = ? FOR UPDATE
-- 이후 검증 → UPDATE quantity = quantity - ? → 커밋까지 락 유지
```

`createOrder` 트랜잭션은 쿠폰 처리 + 주문 저장까지 포함해 실행 시간이 길다.  
`SELECT FOR UPDATE`는 이 트랜잭션 전체 동안 row lock을 점유하므로, 인기 상품에 주문이 몰릴 경우 다른 트랜잭션이 장시간 대기하게 된다.

**결정**

조건부 UPDATE 한 쿼리로 검증과 차감을 원자적으로 처리.

```sql
UPDATE stocks
SET quantity = quantity - :qty
WHERE product_id = :productId AND quantity >= :qty
```

`affected rows = 0` → 재고 부족 → 즉시 `BAD_REQUEST`. 재시도 없음.

**근거**

| 관점 | 비관적 락 | 원자적 UPDATE |
|---|---|---|
| 락 점유 시간 | 트랜잭션 전체 | UPDATE 쿼리 수준 |
| 동시 처리량 | 낮음 (대기 발생) | 높음 |
| 코드 복잡도 | SELECT + 검증 + UPDATE | UPDATE 단일 쿼리 |
| 재시도 필요 여부 | 불필요 | 불필요 |

`affected rows = 0`의 원인이 "재고 부족"이든 "동시 충돌로 마지막 재고를 빼앗긴 경우"든, 결과적으로 재고가 없다는 의미이므로 재시도해도 성공 가능성이 없다. 즉시 실패가 올바른 동작이다.

**쿠폰과의 차이**

쿠폰은 소유자 1인의 낮은 충돌 확률 케이스 → 낙관적 락(`@Version`).  
재고는 불특정 다수의 높은 충돌 확률 케이스 → 원자적 UPDATE.  
두 도메인의 충돌 특성이 다르므로 전략을 달리 적용했다.
