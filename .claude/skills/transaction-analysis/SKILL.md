---
name: transaction-analysis
description:
  Spring @Transactional, JPA Repository, Facade/Application Service 흐름을 기준으로 트랜잭션 경계, 락 전략, 영속성 컨텍스트, rollback 정합성을 분석합니다.
  주문/쿠폰/재고/좋아요처럼 여러 도메인 상태가 함께 바뀌는 유스케이스에서 원자성, Lost Update, 데드락 가능성, 테스트 커버리지를 점검할 때 사용합니다.
---

트랜잭션을 분석할 때는 특정 메서드 하나만 보지 말고, 요청 유스케이스 전체 흐름을 기준으로 판단한다.
컨트롤러 → Facade/Application Service → Domain Service → Repository Port → Infrastructure Adapter 순서로 호출 경로와 의존 방향을 함께 확인한다.

## 1. 분석 범위 확정

먼저 분석 대상 유스케이스를 한 문장으로 정의한다.

예:
- "주문 생성 시 재고 차감, 쿠폰 사용, 주문 저장이 함께 처리된다."
- "좋아요 요청 시 Product.likeCount와 product_like row가 함께 변경된다."

그 다음 아래 항목을 나열한다.

- 진입 API 또는 public method
- 트랜잭션 시작 지점
- 트랜잭션 안에서 변경되는 도메인 상태
- 조회만 하는 작업과 쓰기 작업
- 외부 시스템 호출 여부
- 동시 접근 시 같은 row에 몰릴 가능성이 있는 데이터

## 2. Transaction Boundary 점검

다음을 순서대로 확인한다.

- `@Transactional`은 어느 계층에 있는가?
  - Controller에 있으면 위험 후보로 본다.
  - 여러 도메인을 조율하는 command는 Facade/Application Service 경계가 자연스럽다.
  - 단일 도메인 command는 Domain Service 경계도 가능하다.
- 읽기 전용 유스케이스에 `readOnly = true`가 적용되어 있는가?
- 쓰기 트랜잭션 안에 불필요한 조회, 응답 조립, 외부 API 호출이 포함되어 있지 않은가?
- 예외 발생 시 rollback 되어야 하는 변경이 같은 트랜잭션 안에 있는가?
- checked exception, 예외 catch, 이벤트 발행 때문에 rollback이 누락될 가능성은 없는가?

출력에는 현재 범위를 트리 형태로 요약한다.

```markdown
- 현재 트랜잭션 범위:
  OrderFacade.createOrder()
    ├─ ProductService.findProductsByIdsForUpdate()
    ├─ OrderProductProcessService.createOrder()
    ├─ CouponService.useCoupon()
    ├─ ProductService.saveProducts()
    └─ OrderService.saveOrder()

- 트랜잭션이 필요한 핵심 작업:
  - 상품 재고 차감
  - 쿠폰 USED 상태 변경
  - 주문 저장
```

## 3. Lock Boundary 점검

동시성 위험은 "트랜잭션이 있는가"가 아니라 "같은 데이터를 누가 동시에 읽고 쓰는가"로 판단한다.

다음을 확인한다.

- Lost Update 위험이 있는 row는 무엇인가?
  - 재고: `product.stock`
  - 쿠폰 사용: `issued_coupon.status`
  - 좋아요 수: `product.likeCount`
- 상태 변경 전에 해당 row를 잠그는가?
- 일반 조회 메서드와 락 조회 메서드가 분리되어 있는가?
  - 예: `findAllByIds` vs `findAllByIdsForUpdate`
- 여러 row를 잠글 때 락 획득 순서가 고정되어 있는가?
  - 예: product id 정렬 후 조회
- 여러 도메인 row를 함께 잠글 때 도메인 간 락 순서가 문서화되어 있는가?
  - 예: 주문 생성은 `Product -> IssuedCoupon` 순서로 잠근다.
  - 반대 순서의 유스케이스가 생기면 데드락 위험 후보로 표시한다.
- 비관적 락, 낙관적 락, 조건부 update 중 현재 선택이 유스케이스 특성과 맞는가?

락 전략 평가는 선택지로 제시한다.

```markdown
| 전략 | 적합한 경우 | 현재 유스케이스 평가 |
| --- | --- | --- |
| 비관적 락 | 같은 row에 쓰기 충돌이 자주 발생 | 동일 상품 주문, 동일 쿠폰 사용에 적합 |
| 낙관적 락 | 충돌이 드물고 재시도 정책이 명확 | 재시도 UX/예외 정책이 필요 |
| 조건부 update | 단일 컬럼 원자 갱신이 핵심 | 도메인 객체 스냅샷과 함께 쓰면 경계가 복잡 |
```

## 4. JPA / Persistence Context 점검

JPA를 사용할 때는 변경 감지와 flush 시점을 함께 본다.

- Entity를 조회한 뒤 도메인 객체로 변환하는 구조인가?
- 변경된 도메인 객체를 다시 save/update하는 adapter가 있는가?
- save 전에 예외가 발생하면 실제 DB 변경이 남지 않는가?
- `@Lock(PESSIMISTIC_WRITE)`가 실제 Spring Data Repository 메서드에 선언되어 있는가?
- 락 쿼리에 `order by`가 필요한 경우 포함되어 있는가?
- lazy loading으로 트랜잭션 후반에 예상하지 못한 쿼리가 발생할 가능성은 없는가?
- 대량 조회/복잡한 QueryDSL이 쓰기 트랜잭션 안에 포함되어 있지 않은가?

## 5. Rollback 정합성 점검

원자성은 성공 케이스보다 실패 케이스에서 검증한다.

다음 질문에 답한다.

- 재고 차감 후 쿠폰 사용이 실패하면 재고가 원복되는가?
- 쿠폰 사용 후 주문 저장이 실패하면 쿠폰 상태가 원복되는가?
- 일부 상품 재고 부족 시 주문이 생성되지 않는가?
- 중복 상품 요청이 들어올 때 aggregate quantity 기준으로 검증하는가?
- 실패 시 API 응답은 `ApiControllerAdvice`를 통해 일관되게 내려가는가?

## 6. 테스트 커버리지 점검

테스트는 역할별로 분리해서 본다.

- Domain unit test:
  - 순수 규칙, 상태 전이, 금액 계산
- Service/Integration test:
  - Repository adapter, JPA lock, unique constraint, rollback
- API E2E test:
  - 인증, 요청/응답 계약, 실제 저장 결과
- Concurrency test:
  - `CountDownLatch` 등으로 시작 시점을 맞춘 실제 동시 호출
  - 예외 수뿐 아니라 최종 DB 상태까지 검증

동시성 테스트는 최소 아래 상태를 확인한다.

- 성공 요청 수
- 실패 요청 수와 실패 사유
- 최종 재고 또는 likeCount
- 주문 row 수
- 쿠폰 최종 상태

## 7. 출력 형식

분석 결과는 아래 구조로 작성한다.

```markdown
## 트랜잭션 분석 결과

### 대상 유스케이스
- ...

### 현재 트랜잭션 경계
- ...

### 정합성 위험
- [High] ...
- [Medium] ...
- [Low] ...

### 락 / 동시성 판단
- 현재 전략:
- 락 획득 순서:
- 대안:
- 선택 근거:
- 남은 리스크:

### JPA / Flush / Persistence Context
- ...

### 테스트 커버리지
- 커버됨:
- 부족함:

### 개선 제안
1. ...
2. ...
```

## 톤 & 판단 기준

- 정답처럼 단정하지 말고 현재 요구사항과 트레이드오프를 기준으로 말한다.
- "트랜잭션이 있으니 안전하다"는 식의 설명을 피한다.
- 코드 변경 제안은 반드시 어떤 실패 시나리오를 막기 위한 것인지 함께 설명한다.
- 불필요하게 모든 조회에 락을 권하지 않는다.
- 외부 호출, 긴 조회, 이벤트 발행이 트랜잭션 안에 있으면 우선 위험 후보로 표시한다.
- 개선안은 적용 우선순위를 함께 제시한다.
