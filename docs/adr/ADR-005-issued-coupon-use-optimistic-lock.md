# ADR-005: 쿠폰 사용 처리에 낙관적 락 채택

## 상태
Accepted

## 배경

주문에 쿠폰을 적용할 때 `OrderFacade`는 다음 순서로 발급 쿠폰을 USED 상태로 변경해야 한다.

```
1. issuedCouponService.getMyIssuedCoupon(issuedCouponId, userId)
   → DB에서 발급 쿠폰을 조회하고 소유권을 검증한 뒤 IssuedCouponModel을 반환

2. couponTemplateService.getById(issued.getCouponTemplateId())
   → 쿠폰 템플릿 조회 후 template.validateApplicability(originalPrice)로 만료 여부 및 최소 주문 금액 검증
   → 검증 실패 시 예외를 던져 흐름을 중단

3. issuedCouponService.use(issued.getId())
   → use() 내부에서 USED 상태 여부를 먼저 검증한 뒤 발급 쿠폰을 USED 상태로 변경
```

`use()`를 naive하게 구현하면 read-modify-write 패턴이 된다.

```java
// naive 구현
IssuedCouponModel issued = getById(id);  // READ
issued.use();                             // MODIFY (메모리 상태 변경)
issuedCouponRepository.save(issued);      // WRITE
```

이 패턴에는 TOCTOU(Time-of-Check-Time-of-Use) race condition이 있다.  
동일 사용자가 동일 쿠폰으로 동시에 두 요청을 보내면(버튼 중복 클릭, 네트워크 재전송 등),
두 트랜잭션이 모두 `status = AVAILABLE`을 읽고 각자 USED로 저장하여 쿠폰이 두 번 소비될 수 있다.

## 결정

`IssuedCouponModel`에 `@Version` 컬럼을 추가한다. 충돌 시 JPA가 던지는 `ObjectOptimisticLockingFailureException`은 `ApiControllerAdvice`에서 `CONFLICT` HTTP 응답으로 변환된다.

## 선택지

핵심 질문: 동시 요청이 들어왔을 때 **순서를 보장해 기다리더라도 완료시킬 것인가**, 아니면 **충돌을 감지해 즉시 실패를 반환할 것인가**.

### Option A: 비관적 락 (`@Lock(PESSIMISTIC_WRITE)`) — 순서 보장 후 완료

`SELECT ... FOR UPDATE`로 행을 잠가 요청을 직렬화한다. 먼저 온 요청이 완료된 후 다음 요청이 진행된다.

- **기다리더라도 완료시키는** 것이 의미 있으려면, 기다린 요청이 성공할 수 있어야 한다. 재고 차감처럼 "앞 요청과 독립적으로 성공 가능한" 경우가 이에 해당한다.
- 쿠폰 사용은 앞 요청이 성공하면 뒤 요청은 어차피 실패한다. 기다린 끝에 결국 실패를 돌려받으므로 **대기의 가치가 없고, 즉시 실패 반환보다 나쁜 UX**다.
- 대기 중 DB 락 경쟁이 발생해 불필요한 성능 비용이 생긴다.

### Option B: 낙관적 락 (`@Version`) — 충돌 시 즉시 실패 반환 (채택)

`@Version` 컬럼으로 충돌을 감지하고, `ObjectOptimisticLockingFailureException`을 던져 사용자에게 즉시 응답한다.

- 쿠폰 사용은 첫 요청만 성공하고 나머지는 영구 실패해야 하는 단방향 상태 전이다. **"충돌 시 즉시 실패"가 이 특성에 정확히 맞다.**
- 도메인 규칙(`AVAILABLE → USED`)이 `IssuedCouponModel.use()` 안에 Java 코드로 캡슐화되어 있다.
- 락 경쟁 없이 충돌을 감지하므로, 충돌 빈도가 낮은 경우 비관적 락보다 성능이 좋다.

## 근거

**쿠폰 사용은 첫 요청만 성공하고 나머지는 영구 실패해야 하는 단방향 상태 전이다.**

이 특성에서 두 가지가 결정된다.

- **기다림은 의미 없다**: 비관적 락처럼 순서를 보장해 기다리더라도, 앞 요청이 성공한 순간 뒤 요청은 성공할 수 없다. 대기 후 실패는 즉시 실패보다 나쁜 UX다.
- **낙관적 락이 자연스럽다**: 충돌 시 즉시 실패를 반환하고, 도메인 규칙(`use()` 내 상태 검증 + 전이)은 Java 도메인 객체에 캡슐화된 채 유지된다.

**성능이 중요해지면 원자적 UPDATE로 내려갈 수 있다.**

낙관적 락은 SELECT + UPDATE 두 쿼리가 필요하다. 트래픽이 높아져 이 차이가 병목이 된다면, 단일 쿼리로 처리하는 원자적 UPDATE로 전환을 고려할 수 있다.

```sql
UPDATE issued_coupon
   SET status = 'USED'
 WHERE id = :id
   AND status = 'AVAILABLE'
```

단, 이 전환은 트레이드오프를 수반한다. `AVAILABLE → USED` 전이 조건이 `IssuedCouponModel` 밖으로 나와 SQL WHERE 절 안에 인코딩된다. **도메인 규칙이 저수준(DB 쿼리)으로 내려가는 것**이다. 이 경우 컴파일 타임 검증이 불가능해지므로, Repository 계약 테스트(`IssuedCouponRepositoryContractTest`)로 보완한다. 쿼리를 수정하면 테스트가 깨지도록 하여 저수준으로 내려간 도메인 규칙의 안전망을 확보한다.

## 결과

- **긍정**: 충돌 시 즉시 실패를 반환하여 UX가 자연스럽다. 도메인 규칙이 Java 객체 안에 캡슐화된다. 재시도 로직 없음.
- **부정**: `@Version` 컬럼이 스키마에 추가된다. `ApiControllerAdvice`에서 `ObjectOptimisticLockingFailureException` → `CONFLICT` 변환 처리가 필요하다.

## 참고

- 관련 파일:
  - `domain/coupon/IssuedCouponModel.java`
  - `domain/coupon/IssuedCouponRepository.java`
  - `infrastructure/coupon/IssuedCouponJpaRepository.java`
  - `infrastructure/coupon/IssuedCouponRepositoryImpl.java`
  - `domain/coupon/IssuedCouponService.java`
  - `application/order/OrderFacade.java`
- 관련 ADR:
  - [ADR-004](./ADR-004-issued-coupon-use-parameter.md) — IssuedCouponService.use() 파라미터를 ID로 결정
