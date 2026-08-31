# 주문 확정 전 쿠폰 적용 계약

이 파일은 G3-R5 동결 learner record에서 W2 독립 재현을 위해 추출한 W1 입력 fixture다. 원문에서 확인된 사실과 실습에서 고정한 결정을 구분하며, W2는 아래 ID와 의미를 바꾸지 않는다.

## 범위와 외부 약속

- 구매자는 자신이 소유한 미확정 주문에 보유 쿠폰 한 장을 적용한다.
- 복수 쿠폰, 확정 뒤 변경, 환불, 정산, 다른 프로모션 조합은 이 실습 범위 밖이다.
- 외부 호출은 `POST /api/v1/orders/{orderId}/discount`, `X-USER-ID: {buyerId}`, body `{ "couponId": 10 }`을 사용한다.
- 성공 결과는 `orderId`, `originalAmount`, `discountAmount`, `finalAmount`, `confirmed`를 제공한다.

## W2로 넘기는 불변식과 반례

- `INV-001` 주문 소유권: 구매자는 자신의 주문만 바꾼다. 다른 `X-USER-ID` 요청은 거절한다.
- `INV-002` 쿠폰 한 장과 적용 시점: 미확정 주문에 한 장만 적용한다. 두 번째 다른 쿠폰과 확정 뒤 새 적용은 거절한다.
- `INV-003` 금액 관계: `0 <= discountAmount <= originalAmount`, `finalAmount = originalAmount - discountAmount`. 0원과 전액 할인은 허용하고 -1원과 초과 할인은 거절한다.
- `INV-004` 확정 결과 보존: 확정 뒤 저장한 할인 결과는 정책 변경으로 달라지지 않는다. 10%로 확정한 10,000원 주문은 정책이 20%가 되어도 9,000원이다.
- `INV-005` 같은 요청의 효과 한 번: 같은 `orderId`와 `couponId` 재요청은 저장된 결과를 반환하고 할인을 누적하지 않는다.
- `INV-006` 만료 판단 시각: 쿠폰 만료는 요청을 받기 시작한 시각으로 판단한다. 만료 직전에 시작한 요청은 처리 중 시각이 지나도 같은 결과를 낸다.

## 저장 의미

테스트용 최소 구조는 `orders(id, buyer_id, original_amount, discount_amount, final_amount, applied_coupon_id, discount_applied_at, confirmed)`다. 저장 뒤 영속성 context를 비우고 다시 읽어도 할인 결과와 확정 상태가 같아야 한다.

## 실패 의미

주문 없음, 소유자 불일치, 확정 주문, 사용할 수 없는 쿠폰과 잘못된 할인 금액은 서로 다른 업무 실패다. 정확한 HTTP 매핑은 공통 오류 정책을 따르되, 요청자가 입력 수정·권한 확인·변경 중단 중 다음 행동을 구분할 수 있어야 한다.
