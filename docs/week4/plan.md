# 4주차 개발 계획서 (plan.md)

2주차 설계 문서(요구사항, 시퀀스, 클래스, ERD) 및 3, 4주차 의사결정 내역을 바탕으로 한 변경 및 구현 작업 계획입니다.

## 1. 도메인 엔티티 및 스키마 변경
- [ ] **Order (주문)**
  - [ ] `OrderStatus` Enum 업데이트: `PENDING`, `COMPLETED`, `CANCELED` 추가.
  - [ ] 필드 추가: `couponIssueId`, `totalOriginalAmount`, `totalDiscountAmount`, `totalPaymentAmount`.
  - [ ] 메서드 추가: `complete()`, `cancel()` 상태 전이 로직.
- [ ] **Payment (결제)**
  - [ ] `Payment` 엔티티 신설: `orderId`, `method`, `status`, `amount`, `transactionId`, `approvedAt` 등.
  - [ ] `PaymentMethod`(`CARD`, `TRANSFER`), `PaymentStatus`(`READY`, `APPROVED`, `FAILED`) Enum 신설.
  - [ ] 관련 JPA Repository 추가 및 `PAYMENTS` 테이블 스키마 작성(초기화 스크립트 반영).
- [ ] **Coupon (쿠폰)**
  - [ ] `CouponTemplate` 엔티티 업데이트: `maxDiscountAmount`(최대 할인 금액, nullable), `maxIssueCount`(최대 발급 수량), `issuedCount`(발급 수량) 추가.
  - [ ] `CouponIssue` 엔티티 업데이트: 낙관적 락을 위한 `@Version` 추가.
- [ ] **Product & Stock (상품/재고)**
  - [ ] 물리적 스키마 변경 사항 반영 확인 및 정합성 테스트 데이터 준비.

## 2. 쿠폰 도메인 (Coupon) 로직 구현
- [ ] **쿠폰 어드민 API 구현**
  - [ ] 템플릿 생성(`POST`), 단건 조회, 목록 조회, 수정, 삭제 로직 개발.
  - [ ] 발급 내역(`/issues`) 페이징 조회 기능 추가.
- [ ] **쿠폰 발급 로직 (`CouponFacade.issueCoupon`)**
  - [ ] [동시성 제어] `CouponTemplate` 조회 시 **비관적 락(`PESSIMISTIC_WRITE`)**을 사용하여 선착순 발급 시 동시성 충돌 방지 및 `issuedCount` 증가.
  - [ ] 이미 발급된 쿠폰인지 검증(`CoreException(CONFLICT)` 반환).
  - [ ] `CouponIssue` 발급 내역 저장(INSERT).
- [ ] **사용자 쿠폰 목록 조회 API**
  - [ ] 만료된 쿠폰 처리 방식(동적 판단 또는 배치 동기화)에 따라 `EXPIRED` 상태 매핑 로직 구현.
- [ ] **할인 금액 계산 로직 분리 (`DiscountService` 또는 도메인 내부)**
  - [ ] 정액(`FIXED`), 정률(`RATE`) 타입별 할인 로직 적용.
  - [ ] 정률 쿠폰의 경우 `maxDiscountAmount`에 따른 최대 할인 한도 차감 로직 구현.

## 3. 좋아요(Like) 동시성 및 로직 개선
- [ ] **좋아요 등록/취소 흐름 변경 (`LikeFacade`)**
  - [ ] [동시성 제어] `Product` 조회 시 **비관적 락(`PESSIMISTIC_WRITE`)**으로 선점.
  - [ ] 락 획득 즉시 `LikeService.existsLikeRecord()`로 존재 여부(Exist Check) 재검증.
  - [ ] 존재 여부에 따라 멱등성 보장(이미 존재하면 리턴) 혹은 `PRODUCT_LIKES` 저장 및 `Product.increaseLikeCount()` 수행.

## 4. 주문(Order) 도메인 생성 및 재고 동시성 제어
- [ ] **주문 생성 API (`OrderFacade.createOrder`)**
  - [ ] 상품 정보 조회.
  - [ ] **쿠폰 검증 및 적용**: 사용자가 보낸 쿠폰이 본인 소유인지, 만료되지 않았는지 검증 후 최종 할인 금액 및 결제 금액 확정.
  - [ ] [동시성 제어] **재고 차감**: 데드락 방지를 위해 상품 ID를 기준으로 **오름차순 정렬** 후 순차적으로 **비관적 락** 획득 및 `Stock.decrease()`.
  - [ ] 주문서 생성 및 저장 (`OrderStatus.PENDING`).
  - [ ] *참고: 쿠폰 상태를 USED로 물리적 변경하는 것은 결제 완료 시점(Payment)으로 이관.*

## 5. 결제(Payment) 도메인 신설 및 보상 트랜잭션
- [ ] **결제 외부 인터페이스 모킹**
  - [ ] `PaymentGateway` 인터페이스 설계 및 `MockPaymentGateway` 구현 (무조건 성공하는 Stub 형태).
- [ ] **결제 승인 로직 (`PaymentFacade.processPayment`)**
  - [ ] **트랜잭션 외부**: `PaymentGateway.requestPayment()` 호출하여 승인 획득 (가상의 PG).
  - [ ] **트랜잭션 내부**: 
    1. `PaymentService.savePayment()`로 결제 내역 저장.
    2. `OrderService.completeOrder()`로 주문 상태 `COMPLETED` 변경.
    3. [동시성 제어] `CouponService.completeCouponUse()`로 쿠폰 상태를 `USED`로 변경 (이때 **낙관적 락** 검증 발생. 이중 결제 방어).
  - [ ] **예외 발생 시 보상 트랜잭션**: 트랜잭션 내부 로직 중 실패(예: 쿠폰 낙관적 락 충돌, DB 장애 등) 시 `catch` 블록에서 `PaymentGateway.cancelPayment()`를 동기 호출하여 PG사 결제 취소 요청.

## 6. 테스트 코드 작성 및 검증
- [ ] **동시성 테스트 (Concurrent Test)**
  - [ ] 좋아요: 100명이 동시에 같은 상품에 좋아요를 눌렀을 때 `likeCount`가 정확히 반영되는지 검증.
  - [ ] 쿠폰 발급: 10개 한정 쿠폰에 100명이 동시에 요청 시 초과 발급되지 않는지 검증.
  - [ ] 재고 차감: 재고 50개 상품에 동시에 100건 주문 시 50건만 성공하고 예외 발생하는지 검증.
  - [ ] 쿠폰 중복 사용(Double Spending): 낙관적 락을 통해 동일한 쿠폰이 여러 결제 스레드에서 한 번만 처리되는지 검증.
- [ ] **도메인 단위 테스트 (Unit Test)**
  - [ ] 정액/정률 계산 및 한도 검증 로직 정확성 테스트.
- [ ] **주문 통합 테스트 (Integration Test)**
  - [ ] 주문 -> 결제 확정(PG 성공) 시나리오 검증.
  - [ ] 결제 완료 처리 중 트랜잭션 실패로 인한 보상 트랜잭션(환불) 호출 검증.
