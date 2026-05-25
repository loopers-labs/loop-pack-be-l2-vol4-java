---
name: design
description: Use when deciding Java/Spring feature structure, domain responsibility, package boundaries, Facade vs Service transaction placement, interfaces, SOLID trade-offs, or design pattern use in this repository.
user-invocable: true
---

# /design — 설계 룰

명제: **도메인 책임을 먼저 보고, 단순하게 시작하고, 필요할 때만 추상화한다.**

## 1. 단순한 설계 우선

처음부터 확장성 있는 구조를 만들지 않는다.

- 구현체가 하나뿐인 인터페이스는 만들지 않는다. 단, Repository port-adapter 예외는 유지한다.
- 한 번만 쓰이는 Strategy/Factory/Template Method 를 만들지 않는다.
- 미래에 필요할 것 같은 옵션, 확장 포인트, 에러 처리를 미리 넣지 않는다.

먼저 단순하게 작성하고, 반복되는 변경 요구가 생기면 그때 확장한다.

> **Repository port-adapter 예외**
>
> 도메인 `*Repository` 인터페이스 + `infrastructure/*RepositoryImpl` 분리는 구현이 1 개여도 유지한다. 도메인이 Spring Data JPA 에 직접 의존하지 않게 격리하는 것이 목적이다.

## 2. 도메인 책임 먼저 보기

도메인은 DB 테이블이나 DTO 구조를 옮긴 계층이 아니다. 요구사항의 비즈니스 개념·행위·규칙을 코드로 표현하는 계층이다.

- 책임은 **그 상태와 불변식을 소유한 객체** 에 둔다.
- 도메인 이름은 요구사항/설계 문서의 용어를 우선 따른다.
- 상태 변경은 setter 대신 의미 있는 행위 메서드로 표현한다.
- 생성/변경 규칙은 생성자·정적 팩토리·행위 메서드 안에서 검증한다.
- VO 는 값의 검증·동등성·계산 규칙이 중요할 때만 도입한다.

예: `Product` 는 상품 기본 정보와 가격을 책임지고, 현재 재고 수량과 차감 규칙은 `ProductStock` 이 책임진다.

```java
// Bad — Service 가 재고 규칙을 직접 판단하고 값을 변경
if (productStock.getQuantity() < quantity) {
    throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다.");
}
productStock.setQuantity(productStock.getQuantity() - quantity);

// Good — 재고 도메인이 자기 규칙과 상태 변경을 책임진다
productStock.deduct(quantity);
```

## 3. 패키지와 경계

패키지는 기술 분류만이 아니라 비즈니스 기능과 레이어 책임을 함께 드러낸다. 기본 구조는 `<layer>/<feature>` 이다.

- `<feature>` 이름은 도메인 용어를 우선 따른다. 예: `product`, `stock`, `order`, `like`.
- 같은 기능 흐름은 레이어가 달라도 같은 feature 이름으로 맞춘다.
- 책임이 분리된 개념은 패키지도 억지로 합치지 않는다.
- `Request`/`Response` 이름은 HTTP 입출력을 표현하는 `interfaces/api` 에만 사용한다.
- `application` 입력은 `SignUpCommand`, `CreateOrderCommand` 처럼 `Request` 를 빼고 유스케이스 의미로 이름 짓는다.
- `domain` 에 JPA/Redis/Kafka/HTTP 같은 구현 기술 이름을 넣지 않는다.
- 공통 코드는 도메인 의미가 없고 여러 기능에서 반복될 때만 `support` 에 둔다.

새 패키지를 만들기 전, 기존 feature 의 책임인지 새 도메인/유스케이스인지 먼저 판단한다.

## 4. Facade / Service / Domain 책임 기준

`Controller` 는 요청 파싱과 응답 래핑만 한다.

- HTTP request shape 검증은 `@Valid`/Bean Validation 으로 `interfaces/api` 경계에서 처리한다.
- 인증 사용자 식별은 인증 필터/시큐리티 경계의 책임이다.

`Facade` 는 API 유스케이스의 진입점이다.

- 여러 도메인 `Service` 를 조합한다.
- `Command` 입력과 도메인 결과를 유스케이스 흐름에 맞게 변환한다.
- 단일 Service 호출만 감싸는 경우 얇게 유지하고 비즈니스 규칙을 넣지 않는다.
- adapter 가 만든 유효한 `Command` 를 전제로 흐름을 조합한다. 위 레이어에서 보장한 `command == null`, DTO 필드 null, 인증 사용자 null 같은 request shape 검증을 Facade 에서 반복하지 않는다.
- HTTP 외 adapter 가 같은 유스케이스를 호출한다면 그 adapter 경계에서 검증하거나 Command 생성 경로를 안전하게 만든다. 미래 호출자를 가정해 Facade 에 중복 방어를 미리 넣지 않는다.

`Service` 는 도메인 단위의 트랜잭션/비즈니스 흐름을 담당한다.

- Repository 조회/저장
- 트랜잭션 경계
- 도메인 객체 행위 호출
- 여러 도메인이 아닌 같은 도메인 안의 흐름 조율

핵심 판단은 `Service` 내부 if 문보다 도메인 객체의 행위 메서드에 둔다.

## 5. 트랜잭션 위치

트랜잭션은 **함께 성공하거나 함께 실패해야 하는 유스케이스의 가장 바깥쪽** 에 둔다.

- 한 도메인 안에서 끝나는 생성/수정/삭제는 해당 `Service` 메서드에 `@Transactional` 을 둔다.
- 여러 도메인 변경이 하나의 성공/실패 단위로 묶이면 `Facade` 에 `@Transactional` 을 둔다.
- `Facade` 에 트랜잭션을 둔 경우 하위 `Service` 는 같은 트랜잭션에 참여하게 한다.
- 특별한 이유 없이 `REQUIRES_NEW` 를 쓰지 않는다.
- 조회는 기본적으로 `Service` 에 `@Transactional(readOnly = true)` 를 둔다.
- 외부 API/Kafka/메일처럼 롤백되지 않는 작업은 쓰기 트랜잭션 안에서 직접 실행하지 않는다. 필요하면 커밋 이후 이벤트/아웃박스로 분리한다.

## 6. SOLID 는 판단 기준

SOLID 는 기계적으로 적용하지 않는다. 코드를 더 단순하게 만들거나 변경 범위를 줄일 때만 적용한다.

- SRP: 클래스가 여러 이유로 변경되면 책임이 많다는 신호다.
- OCP: 분기가 자주 바뀌거나 정책이 복잡해지면 Strategy 를 고려한다.
- DIP: 외부 API/메일/파일/외부 시스템처럼 바뀔 수 있는 구현은 인터페이스 뒤로 숨긴다.

분기 2~3 개에 변경 가능성이 낮으면 `switch` 가 Strategy 보다 낫다.

## 7. 디자인 패턴은 필요할 때만

### Strategy

다음 모두에 해당하면 도입한다.

- 타입별 정책이 실제로 다르다.
- `switch`/`if` 분기가 계속 늘어난다.
- 정책별 테스트를 분리하고 싶다.

### Factory

객체 생성 규칙이 복잡하거나 타입별 생성 로직이 다를 때 사용한다.

단순 생성은 정적 팩토리 메서드로 충분하다. 예: `Order.create(user, items)`.

### Template Method

전체 흐름은 같고 일부 단계만 다를 때 검토한다. 상속 구조가 복잡해지므로 조합으로 해결 가능한지 먼저 본다.

## 8. 설계 판단 체크

- [ ] 책임이 상태와 불변식을 가진 객체에 있는가?
- [ ] 패키지/클래스 이름이 도메인 용어와 맞는가?
- [ ] 트랜잭션 경계가 함께 성공/실패해야 하는 가장 바깥쪽인가?
- [ ] 새 추상화가 변경 범위를 실제로 줄이는가?
- [ ] 테스트하기 쉬워졌는가?
- [ ] 추상화 때문에 오히려 읽기 어려워지지 않았는가?

마지막 항목이 "예" 면 그 추상화는 빼는 게 낫다.

## 9. 멈추고 질문해야 할 때

- 새 인터페이스/패턴 도입 근거가 "나중을 위해" 외에 떠오르지 않을 때.
- 같은 책임이 여러 레이어에 흩어져 있는데 어디로 모을지 모호할 때.
- Repository port-adapter 외의 신규 인터페이스를 추가하려 하는데 구현체가 1 개일 때.
- Facade 와 Service 중 어디에 트랜잭션을 둘지 판단이 갈릴 때.
- 요구사항/설계 문서의 도메인 책임과 현재 코드 구조가 충돌할 때.
