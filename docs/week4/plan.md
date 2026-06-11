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
  - [ ] `CouponTemplate` 엔티티 업데이트: `maxDiscountAmount`(최대 할인 금액, nullable) 추가.
  - [ ] `CouponIssue` 엔티티 업데이트: 낙관적 락을 위한 `@Version` 추가.
- [ ] **Product & Stock (상품/재고)**
  - [ ] 물리적 스키마 변경 사항 반영 확인 및 정합성 테스트 데이터 준비.

## 2. 쿠폰 도메인 (Coupon) 로직 구현
- [ ] **쿠폰 어드민 API 구현**
  - [ ] 템플릿 생성(`POST`), 단건 조회, 목록 조회, 수정, 삭제 로직 개발.
  - [ ] 발급 내역(`/issues`) 페이징 조회 기능 추가.
- [ ] **쿠폰 발급 로직 (`CouponFacade.issueCoupon`)**
  - [ ] [중복 발급 방지] 유저당 1회 발급을 보장하기 위해 `user_id`와 `coupon_template_id`에 **유니크 제약조건(Unique Constraint)** 적용.
  - [ ] 이미 발급된 쿠폰인지 검증 및 유니크 제약조건 위반 시 `CoreException(CONFLICT)` 반환.
  - [ ] `CouponIssue` 발급 내역 저장(INSERT).
- [ ] **사용자 쿠폰 목록 조회 API**
  - [ ] 만료된 쿠폰 처리 방식(동적 판단 또는 배치 동기화)에 따라 `EXPIRED` 상태 매핑 로직 구현.
- [ ] **할인 금액 계산 로직 분리 (`DiscountService` 또는 도메인 내부)**
  - [ ] 정액(`FIXED`), 정률(`RATE`) 타입별 할인 로직 적용.
  - [ ] 정률 쿠폰의 경우 `maxDiscountAmount`에 따른 최대 할인 한도 차감 로직 구현.

## 3. 좋아요(Like) 로직 개선 (반정규화 제거)
- [ ] **좋아요 등록/취소 흐름 변경 (`LikeFacade`)**
  - [ ] 상품 존재 여부만 일반 `SELECT`로 조회 (비관적 락 제거).
  - [ ] `LikeService.existsLikeRecord()`로 존재 여부(Exist Check) 검증.
  - [ ] 존재 여부에 따라 멱등성 보장(이미 존재하면 리턴) 혹은 `PRODUCT_LIKES` 데이터 추가/삭제 수행. (Product 엔티티 수정 없음)

## 4. 주문 및 결제 (Order & Payment) 단일 API 처리
- [ ] **단일 API 진입 (`OrderFacade.checkout`)**
  - [ ] 단일 진입점 `/api/v1/orders/checkout` 정의 (결제 수단까지 한 번에 수신, **메서드 레벨 `@Transactional` 미적용**).
- [ ] **[트랜잭션 1] 주문 생성 및 재고 가선점**
  - [ ] 상품 정보 조회 및 쿠폰 검증/적용 (최종 할인 및 결제 금액 확정).
  - [ ] [동시성 제어] **재고 차감**: 상품 ID 오름차순 정렬 후 순차적 **비관적 락** 획득 및 `Stock.decrease()`.
  - [ ] 주문서 생성 (`OrderStatus.PENDING`).
- [ ] **결제 외부 인터페이스 모킹 (트랜잭션 외부)**
  - [ ] `PaymentGateway` 인터페이스 설계 및 `MockPaymentGateway` (무조건 성공하는 Stub).
  - [ ] 트랜잭션 없이 외부 API 호출하여 결제 승인 획득.
- [ ] **[트랜잭션 2] 결제 완료 처리 및 상태 업데이트**
  - [ ] `PaymentService.savePayment()`로 결제 내역 저장.
  - [ ] `OrderService.completeOrder()`로 주문 상태 `COMPLETED` 변경.
  - [ ] [동시성 제어] `CouponService.completeCouponUse()`로 쿠폰 `USED` 변경 (낙관적 락 검증).
- [ ] **예외 발생 시 보상 트랜잭션 (Fallback)**
  - [ ] 전체 흐름 중 예외 발생 시 `catch` 블록에서 주문 취소(재고 원복) 및 필요 시 PG사 결제 취소 API를 동기 호출.

## 6. 테스트 코드 작성 및 검증
- [ ] **동시성 테스트 (Concurrent Test)**
  - [ ] 좋아요: 다수의 사용자가 동시에 좋아요 등록/취소 시 락 없이 `PRODUCT_LIKES` 테이블에만 안전하게 이력이 추가/삭제되는지 검증.
  - [ ] 쿠폰 중복 발급: 1명의 사용자가 동시에 쿠폰 발급을 여러 번 요청했을 때 1개만 발급되고 유니크 제약조건에 의해 나머지는 실패하는지 검증.
  - [ ] 재고 차감: 재고 50개 상품에 동시에 100건 주문 시 50건만 성공하고 예외 발생하는지 검증.
  - [ ] 쿠폰 중복 사용(Double Spending): 낙관적 락을 통해 동일한 쿠폰이 여러 결제 스레드에서 한 번만 처리되는지 검증.
- [ ] **도메인 단위 테스트 (Unit Test)**
  - [ ] 정액/정률 계산 및 한도 검증 로직 정확성 테스트.
- [ ] **주문 통합 테스트 (Integration Test)**
  - [ ] 주문 -> 결제 확정(PG 성공) 시나리오 검증.
  - [ ] 결제 완료 처리 중 트랜잭션 실패로 인한 보상 트랜잭션(환불) 호출 검증.
