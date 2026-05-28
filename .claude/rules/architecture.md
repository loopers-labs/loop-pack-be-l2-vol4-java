# 아키텍처 구성 전략

본 문서는 도메인 주도 설계(DDD) 관점에서 도메인 모델과 객체를 설계할 때 적용할 기준을 정의한다.
단순히 DB 테이블을 객체로 옮기는 방식이 아니라, 업무 규칙과 상태 변경 책임을 객체에 적절히 배치하는 것을 목표로 한다.

## 기본 원칙

본 프로젝트는 4계층의 레이어드 아키텍처를 따르며 DIP (의존성 역전 원칙)를 준수한다.

- MSA 배포 구조를 고려하여 패키지 구조의 경우 도메인 > 계층으로 구성한다.
- API request, response DTO와 응용 레이어의 DTO는 분리해 작성하도록 합니다.
- 도메인 객체는 단순 데이터 보관용 객체가 아니라 업무 규칙과 상태 변경 책임을 가진다.
- Application Service는 유스케이스 흐름을 조율하고, 핵심 비즈니스 판단은 Domain 객체가 수행한다.
- Entity, Value Object, Aggregate, Domain Service의 책임을 명확히 구분한다.
- Aggregate 내부 객체의 상태 변경은 Aggregate Root를 통해 수행한다.
- Aggregate 간 직접 객체 참조는 지양하고, 식별자 참조 또는 도메인 이벤트를 우선 고려한다.
- 규칙이 여러 서비스에 반복되면 Domain 객체 또는 Domain Service의 책임인지 먼저 검토한다.
- 각 기능의 책임과 결합도가 애매하면 개발자의 의도를 확인하고 진행한다.

## 레이어별 책임

각 레이어는 다음 책임을 가진다.

예: 
- **interfaces** (presentation 레이어): 외부 요청/응답 계약, 입력 검증, HTTP 표현
- **application** (application 레이어): 유스케이스 흐름, 트랜잭션 경계, 여러 도메인 조합
- **domain** (domain 레이어): 도메인 객체, 비즈니스 규칙, Repository 계약
- **infrastructure** (infrastructure 레이어): JPA, Redis, Kafka 등 기술 구현체

패키지는 다음 구조를 기준으로 한다. 상세 모듈 트리는 `.claude/rules/module-structure.md`를 따른다.

```text
<domain>.interfaces.api       presentation 레이어
<domain>.application          application 레이어
<domain>.domain               domain 레이어
<domain>.infrastructure       infrastructure 레이어
```

## 도메인 설계 절차

도메인 객체를 설계할 때는 다음 순서를 따른다.

1. 사용자 시나리오 또는 업무 흐름을 먼저 정리한다.
2. 업무 흐름에서 핵심 도메인 개념을 추출한다.
3. 함께 변경되어야 하는 객체 묶음을 기준으로 Aggregate 후보를 식별한다.
4. Aggregate Root를 정한다.
5. Entity와 Value Object를 구분한다.
6. 상태 변경 메서드와 비즈니스 규칙을 도메인 객체 내부에 배치한다.
7. 하나의 객체에 귀속되기 어려운 도메인 규칙은 Domain Service로 분리한다.
8. Repository는 Aggregate Root 단위로 정의한다.
9. 외부 도메인 또는 외부 시스템과의 연동은 Port, Adapter, Event를 통해 분리한다.

## 현재 서비스 흐름 기준

현재 서비스 흐름은 회원가입, 브랜드/상품 탐색, 상품 좋아요, 쿠폰 발급, 복수 상품 주문/결제, 사용자 행동 기록으로 구성된다.
DDD 설계 시 다음 책임 배치를 우선 기준으로 삼는다.

- 회원가입과 회원 상태 변경은 `Member` Aggregate Root의 책임으로 둔다.
- 브랜드 조회/등록/수정/삭제는 `Brand` Aggregate Root의 책임으로 둔다.
- 상품 조회/등록/수정/삭제와 주문 상품 스냅샷 생성은 `Product` Aggregate Root의 책임으로 둔다.
- 상품별 주문 가능 수량 확인, 차감, 복구는 독립 생명주기를 가진 `Inventory` Aggregate Root의 책임으로 둔다.
- 회원과 상품의 좋아요 관계는 `ProductLike` Association Entity로 표현하고, `memberId`와 `productId` 조합의 유일성을 보장한다.
- 쿠폰 발급 가능 여부와 할인 정책은 `Coupon` Aggregate Root의 책임으로 둔다.
- 회원에게 발급된 쿠폰과 사용 상태는 `MemberCoupon` Entity로 표현한다.
- 여러 상품을 한 번에 주문하는 흐름은 `Order` Aggregate Root가 `OrderItem`과 `OrderItemSnapshot`을 통해 주문 당시 상품 정보를 고정 저장한다.
- 결제 요청과 성공/실패 상태 전이는 `Payment` Aggregate Root의 책임으로 둔다.
- 사용자 행동 기록은 `UserActionLog`로 저장하고, 후속 확장을 위해 이벤트 발행 또는 outbox 패턴 적용을 고려한다.
- `OutboxEvent`는 이벤트 발행 신뢰성을 위한 기술 객체이며 도메인 Aggregate로 취급하지 않는다.

위 Aggregate Root 기준은 `.docs/design/03-class-diagram.md`의 "애그리거트 및 값 객체" 설계를 따른다. 설계 문서의 Aggregate Root가 변경되면 이 문서의 책임 기준도 함께 갱신한다.

## Entity 설계 규칙

Entity는 식별자가 중요하고, 상태가 변경되어도 동일성을 유지해야 하는 도메인 객체에 사용한다.

- 의미 없는 public setter를 남발하지 않는다.
- 상태 변경은 명확한 도메인 메서드로 표현한다.
- 상태 변경 가능 여부는 Entity 내부에서 판단한다.
- 외부에서 Entity의 내부 상태를 임의로 변경하지 못하도록 한다.

예:

```java
order.cancel();
```

```java
public void cancel() {
    if (!this.status.isCancelable()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다.");
    }

    this.status = OrderStatus.CANCELED;
}
```

## Value Object 설계 규칙

Value Object는 식별자가 아니라 값 자체가 중요한 객체에 사용한다.

- 가능하면 불변 객체로 설계한다.
- 생성 시점에 유효성 검증을 수행한다.
- 값이 같으면 같은 객체로 볼 수 있어야 한다.
- Entity의 속성 중 의미 있는 값 묶음은 Value Object 분리를 고려한다.

예: `Money`, `Quantity`, `DeliveryAddress`, `OrderItemSnapshot`, `Period`, `DiscountPolicy`

## Aggregate 설계 규칙

Aggregate는 일관성을 함께 지켜야 하는 객체 묶음이다.

- Aggregate Root를 통해서만 내부 객체를 변경한다.
- 외부에서 Aggregate 내부 Entity를 직접 저장하거나 수정하지 않는다.
- Repository는 Aggregate Root 단위로만 만든다.
- Aggregate 내부 객체의 생명주기는 Aggregate Root가 관리한다.
- Aggregate는 가능한 작게 유지한다.
- 트랜잭션 일관성이 필요한 범위를 기준으로 Aggregate를 설계한다.

예:

```java
order.addItem(productSnapshot, quantity);
orderRepository.save(order);
```

## Repository 설계 규칙

Repository는 Aggregate Root 단위로 정의한다.

```java
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(OrderId orderId);
}
```

Aggregate 내부 Entity만을 저장하기 위한 Repository는 만들지 않는다. 단, 조회 성능을 위한 Query 전용 Repository는 application 또는 infrastructure 계층에 별도로 둘 수 있다.

## Application Service 설계 규칙

Application Service는 유스케이스 흐름을 조율한다.

Application Service의 책임:

- 트랜잭션 경계 설정
- Repository 조회 및 저장
- Domain 객체의 메서드 호출
- 외부 시스템 호출을 위한 Port 사용
- Domain Event 발행
- Command/Info/Result 변환

Application Service가 직접 처리하지 말아야 할 책임:

- 주문 취소 가능 여부 판단
- 재고 차감 가능 여부 판단
- 쿠폰 적용 가능 여부 판단
- 상태 전이 규칙 판단
- 가격 계산 규칙 판단

이런 판단은 가능한 한 `Order`, `Inventory`, `Coupon`, `DiscountPolicy`, `Payment` 등 도메인 객체에 둔다.

## Domain Service 사용 기준

다음 경우에만 Domain Service 사용을 고려한다.

- 특정 Entity 하나의 책임으로 보기 어려운 도메인 규칙
- 여러 Aggregate를 비교하거나 조합해야 하는 규칙
- 정책성 계산 또는 판정 로직
- 객체 내부에 넣으면 부자연스러운 도메인 로직

기준:

```text
Entity 또는 Value Object가 자연스럽게 책임질 수 있으면 객체 내부에 둔다.
여러 객체를 아우르는 순수 도메인 규칙이면 Domain Service로 둔다.
흐름 제어, 트랜잭션, 외부 연동은 Application Service로 둔다.
```

## Aggregate 간 참조 규칙

Aggregate 간에는 직접 객체 참조보다 ID 참조를 우선 고려한다.

```java
public class Order {
    private MemberId memberId;
    private List<OrderItem> orderItems;
}
```

주문 당시 상품명, 브랜드명, 가격 등은 Snapshot Value Object로 보존한다.

```java
public record OrderItemSnapshot(
    ProductId productId,
    String productName,
    String brandName,
    Money price
) {
}
```

## 도메인 이벤트 규칙

다른 도메인 또는 외부 시스템으로 후속 처리가 필요한 경우 도메인 이벤트를 고려한다.

예: `MemberRegisteredEvent`, `ProductLikedEvent`, `CouponIssuedEvent`, `OrderCreatedEvent`, `PaymentApprovedEvent`, `PaymentFailedEvent`, `UserActionRecordedEvent`

도메인 이벤트는 다음 상황에서 사용한다.

- 다른 Aggregate에 영향을 주는 후속 처리가 필요할 때
- 외부 시스템 연동이 필요할 때
- 도메인 간 결합도를 낮추고 싶을 때
- MSA 또는 모듈 분리 구조에서 직접 의존을 줄이고 싶을 때
- 사용자 행동 기록처럼 이후 다양한 기능으로 확장될 데이터를 안정적으로 적재해야 할 때

주의사항:

- 이벤트 기반 설계에서는 최종적 일관성을 고려한다.
- 이벤트 중복 수신에 대비해 멱등성을 확보한다.
- 실패 시 재시도 또는 보상 처리를 고려한다.
- 중요한 이벤트는 Outbox Pattern 적용을 검토한다.

## 테스트 설계 기준

도메인 모델은 Spring 없이 순수 단위 테스트로 검증한다.

```text
Domain Layer
-> 순수 JUnit 테스트

Application Layer
-> Port Mock 기반 유스케이스 테스트

Infrastructure Layer
-> Repository, 외부 API, Message Broker 통합 테스트

Presentation Layer
-> Controller Slice 테스트
```

Domain 테스트에서는 다음을 중점적으로 검증한다.

- 상태 전이 규칙
- 불변 조건
- 값 객체 유효성
- Aggregate 내부 일관성
- 예외 상황

## 코드 생성 시 준수 사항

Agent는 DDD 객체를 생성할 때 다음을 준수한다.

- Entity에 무분별한 public setter를 생성하지 않는다.
- 상태 변경은 도메인 메서드로 표현한다.
- Value Object는 가능하면 record 또는 불변 클래스로 작성한다.
- Repository는 Aggregate Root 기준으로 생성한다.
- Application Service에 도메인 규칙을 몰아넣지 않는다.
- Infrastructure 구현체가 Domain 객체의 규칙을 우회하지 않도록 한다.
- 외부 시스템 연동은 Port 인터페이스를 통해 분리한다.
- 도메인 이벤트가 필요한 경우 Event 객체와 발행 위치를 함께 제안한다.
- 테스트 코드는 도메인 테스트와 유스케이스 테스트를 분리해서 작성한다.

## 판단 기준

도메인 설계 시 항상 다음 질문을 기준으로 판단한다.

```text
이 상태 변경의 책임은 누구에게 있는가?
```

예:

```text
회원가입과 회원 상태 변경
-> Member

상품 판매 상태 변경
-> Product

재고 차감 가능 여부
-> Inventory

쿠폰 발급 가능 여부
-> Coupon

쿠폰 사용 상태 변경
-> MemberCoupon 또는 Order 유스케이스 내 도메인 협력

주문 생성과 주문 상태 전이
-> Order

결제 승인/실패 상태 반영
-> Payment

주문 생성 흐름 조율
-> Application Service

결제 API 호출
-> PaymentPort / PaymentClient

사용자 행동 기록 후속 처리
-> Domain Event / UserActionLog / OutboxEvent
```

DDD 설계의 핵심은 데이터 구조가 아니라 업무 규칙을 표현하는 객체 모델을 만드는 것이다.
