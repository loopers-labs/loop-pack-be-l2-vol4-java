# ADR-031: 쿠폰 사용 시 비관적 락 적용

- 날짜: 2026-06-08
- 상태: 승인됨

---

## Introduction & Goals

- **Context / Background**:
  주문 처리 흐름에서 쿠폰 사용은 "읽기 → 유효성 검증 → 상태 변경 (AVAILABLE → USED)" 순서로 진행된다. 동시에 같은 쿠폰으로 두 건의 주문이 들어오면 두 트랜잭션 모두 `AVAILABLE`을 읽어 사용 처리하는 Lost Update가 발생할 수 있다.

- **Goals**:
  쿠폰 1장이 2건 이상의 주문에 사용되는 동시성 이슈를 방지한다.

---

## Detailed Design

### System Architecture

`CouponRepository.findByIdWithLock()`에서 `PESSIMISTIC_WRITE` 락을 획득하여, 동일 쿠폰에 대한 동시 접근을 직렬화한다.

```java
// CouponJpaRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM CouponJpaEntity c WHERE c.id = :id")
Optional<CouponJpaEntity> findByIdWithLock(@Param("id") Long id);
```

**주문 처리 흐름:**
```
OrderFacade.createOrder() [@Transactional]
  1. 상품 정보 조회
  2. couponId != null → CouponApplicationService.useCoupon()
       - findByIdWithLock(couponId)  ← 비관적 락 획득
       - resolveStatus() 검증
       - isOwnedBy() 검증
       - use() → AVAILABLE → USED
       - calculateDiscount() → discountAmount 반환
  3. InventoryService.deductAll()   ← 재고 차감 (기존 방식 유지)
  4. OrderService.createOrder()
```

트랜잭션이 커밋/롤백될 때 락이 해제되므로, 쿠폰 사용 실패 시 상태 변경이 롤백된다.

### Constraints

- `findByIdWithLock()`은 주문 처리 흐름에서만 사용한다. 단순 조회는 `findById()`를 사용한다.
- 락 획득 순서: 쿠폰 락 → 재고 차감. 순서를 일관되게 유지하여 데드락을 방지한다.

---

## Alternatives Considered

| 옵션 | Pros | Cons |
|------|------|------|
| **선택: 비관적 락 (PESSIMISTIC_WRITE)** | 충돌 발생 시 즉시 차단. 쿠폰처럼 1회성 사용 보장이 중요한 자원에 적합. 구현 단순. | 락 대기로 인한 처리 지연 가능. 트래픽이 높은 환경에서 병목. |
| 낙관적 락 (OPTIMISTIC) | 충돌이 드문 경우 성능 유리. 락 대기 없음. | 충돌 시 재시도 로직 필요. 쿠폰은 동일 자원에 대한 충돌 가능성이 높아 재시도 비용 증가. |
| 유니크 제약조건 (DB 레벨) | DB가 중복 사용을 최종 방어. | 별도 상태 관리 로직과 병행 필요. 에러 메시지 제어 어려움. |

**선택 근거:**

쿠폰은 1장당 1회만 사용 가능하며, 동일 쿠폰에 대한 동시 접근이 발생하면 반드시 하나만 성공해야 한다. 낙관적 락은 재시도 로직이 필요하고, 쿠폰 특성상 재시도해도 결국 실패할 가능성이 높아 불필요한 부하를 유발한다. 비관적 락이 쿠폰 사용 시나리오에 더 적합하다.
