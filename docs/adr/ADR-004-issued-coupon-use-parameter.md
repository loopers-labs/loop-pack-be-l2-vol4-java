# ADR-004: IssuedCouponService.use() 파라미터를 ID로 결정

## 상태
Accepted

## 배경

`IssuedCouponService.use()`는 발급 쿠폰을 `USED` 상태로 전이시키는 메서드다. `OrderFacade`는 이 메서드를 호출하기 전에 `getMyIssuedCoupon(issuedCouponId, userId)`로 이미 `IssuedCouponModel`을 로드한 상태다.

```
Facade 흐름:
1. IssuedCouponModel issued = issuedCouponService.getMyIssuedCoupon(issuedCouponId, userId)
2. CouponTemplateModel template = couponTemplateService.getById(issued.getCouponTemplateId())
3. template.validateApplicability(originalPrice)
4. issuedCouponService.use(???)   ← 이 시점에 모델이 이미 있다
```

파라미터를 ID(`Long`)로 받을 것인가, 모델(`IssuedCouponModel`)로 받을 것인가를 결정해야 한다.

## 결정

`use(Long issuedCouponId)` — ID를 받는다.

```java
public void use(Long issuedCouponId) {
    IssuedCouponModel issued = getById(issuedCouponId);
    issued.use();  // use() 내부에서 USED 여부 검증 후 상태 전이
    issuedCouponRepository.save(issued);
}
```

## 선택지

### Option A: 모델을 받는다 (`use(IssuedCouponModel issued)`)

```java
public void use(IssuedCouponModel issued) {
    issued.use();
    issuedCouponRepository.save(issued);
}
```

- Facade가 이미 로드한 인스턴스를 그대로 넘기므로 추가 조회 코드가 없다.

### Option B: ID를 받는다 (`use(Long issuedCouponId)`) — 채택

- "쿠폰을 사용한다"는 유스케이스(조회 → 상태 전이 → 저장)가 서비스 안에 완결된다.
- 같은 트랜잭션 내에서 `findById` 재호출은 JPA 1차 캐시에서 반환되므로 DB 쿼리가 추가 발생하지 않는다.
- Facade는 ID만 전달하면 되고, 내부 구현 변경에 영향받지 않는다.

## 근거

**도메인 서비스는 유스케이스를 완결해야 한다.**

모델을 받으면 서비스가 "이미 준비된 객체를 저장하는 역할"로 축소된다. 이 경우 호출자(Facade)가 조회·검증·저장의 순서를 직접 조립해야 하고, 서비스는 순수한 위임자로 전락한다. ID를 받으면 서비스가 해당 유스케이스의 전체 흐름을 소유한다.

**1차 캐시가 성능 문제를 해소한다.**

같은 트랜잭션(`@Transactional`) 안에서 동일 ID로 `findById`를 두 번 호출하면 두 번째는 DB에 가지 않고 1차 캐시에서 반환된다. 모델을 직접 전달할 때와 실행 경로가 동일하다.

## 결과

- **긍정**: "쿠폰 사용" 유스케이스의 책임이 서비스에 응집된다. Facade는 ID 전달만 담당한다.
- **부정**: 없음. 1차 캐시 덕분에 실질적인 성능 차이가 없다.

## 참고

- 관련 파일:
  - `domain/coupon/IssuedCouponService.java`
  - `application/order/OrderFacade.java`
- 관련 ADR:
  - [ADR-005](./ADR-005-issued-coupon-use-optimistic-lock.md) — 쿠폰 사용 처리에 낙관적 락 채택
