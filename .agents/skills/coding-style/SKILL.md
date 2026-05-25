---
name: coding-style
description: Use when writing or revising Java 21/Spring Boot code in this repository, especially naming classes or methods, shaping domain behavior, reducing nesting, handling null, choosing Java 21 features, or using Lombok.
user-invocable: true
---

# /coding-style — 일반 코드 작성 룰

목표: **도메인 의도가 읽히는 이름, 단순한 흐름, 필요한 만큼의 Java/Spring 표현**.

## 1. 코드는 도메인 의도를 드러낸다

코드는 작성보다 읽히는 시간이 길다. 짧은 코드보다 **요구사항의 말과 책임이 드러나는 코드** 를 택한다.

```java
// Bad
if (stock.getQuantity() < quantity) {}

// Good
if (!stock.hasStock(quantity)) {}
```

## 2. 이름은 의도를 드러낸다

- 변수는 역할이 보이게 짓는다. `list` → `orders`, `str` → `productName`, `flag` → `approved`.
- 메서드는 행위가 보이게 동사로 시작한다. `cancel()`, `deduct()`, `validateDuplicate()`, `calculateTotalPrice()`.
- boolean 은 자연스러운 질문처럼 읽히게 한다. `isDeleted()`, `hasStock()`, `canOrder()`, `shouldLock()`.
- 이름은 짧고 자연스러운 도메인 말이어야 한다. `isInsufficientFor(quantity)` 처럼 장황한 이름보다 `hasStock(quantity)` 처럼 읽히는 이름을 우선한다.
- 도메인 이름은 요구사항/설계 문서의 용어를 우선 따른다. 재고 책임은 `Product` 가 아니라 `ProductStock` 처럼 실제 책임을 가진 이름에 둔다.

## 3. 상태 변경은 행위 메서드로 표현

도메인 상태는 public setter 로 바꾸지 않는다. 상태를 바꾸는 이유와 규칙이 드러나는 행위 메서드를 둔다.

```java
// Bad
productStock.setQuantity(productStock.getQuantity() - quantity);
product.setDeletedAt(LocalDateTime.now());

// Good
productStock.deduct(quantity);
product.delete();
```

검증도 상태를 가진 객체에 둔다. `Service` 는 흐름과 트랜잭션을 조율하고, 핵심 판단은 도메인 객체에게 위임한다.

## 4. 메서드는 하나의 흐름으로 읽힌다

조회·검증·도메인 행위·저장·외부 알림을 한 메서드에 모두 몰지 않는다. 다만 단순 한 줄 위임을 위해 무의미하게 쪼개지도 않는다.

분리 기준:

- 이름 붙일 수 있는 도메인 개념이면 분리한다.
- 같은 추상화 수준끼리 한 메서드에 둔다.
- 분리한 메서드 이름이 내부 구현을 그대로 반복하면 분리하지 않는다.
- 외부 API/Kafka/메일처럼 롤백되지 않는 작업은 트랜잭션 흐름과 분리한다.

```java
@Transactional
public Product updateProduct(UpdateProductCommand command) {
    Product product = findRequiredProduct(command.productId());
    product.update(command.name(), command.description(), command.price());
    return product;
}
```

## 5. 조건문은 숨기지 말고 이름을 부여

복잡한 조건은 boolean 변수나 도메인 메서드로 의도를 드러낸다.

```java
boolean orderableStock = productStock.canDeduct(quantity)
        && product.isSelling();

if (!orderableStock) {
    throw new CoreException(ErrorType.CONFLICT, "주문할 수 없는 상품입니다.");
}
```

반복되거나 도메인 규칙이면 객체 메서드로 옮긴다. 예: `productStock.canDeduct(quantity)`.

## 6. Early Return 으로 중첩 줄이기

예외 상황을 먼저 처리하고 정상 흐름을 아래로 읽히게 한다.

```java
public void validate(Order order) {
    if (order == null) return;
    if (order.isCancelled()) return;
    if (!order.hasItems()) return;

    validateItems(order);
}
```

## 7. 파라미터와 Command 객체 기준

신규 코드에서 파라미터가 4 개 이상이거나 같은 묶음이 여러 계층을 지나가면 Command/Context 로 묶는 것을 고려한다.

- Controller 요청은 `*V1Dto` 의 `*Request` 로 받고, application 으로 넘길 때는 필요한 경우 `*Command` 로 변환한다.
- `Request`/`Response` 이름은 HTTP 입출력인 `interfaces/api` 에만 사용한다.
- application 입력은 `SignUpCommand`, `CreateOrderCommand` 처럼 `Request` 를 빼고 유스케이스 의미로 이름 짓는다.
- domain Command 는 도메인 내부에서 그 묶음 자체가 의미 있거나 여러 곳에서 재사용될 때만 만든다.
- 한 번 쓰이는 단순 값 묶음이나 도메인 의미가 약한 객체는 만들지 않는다.

> 이 저장소 예외: 기존 코드 (`ProductFacade.create(name, desc, price, stock)` 등) 가 이미 4~5 개 파라미터를 사용 중. 신규 코드부터 적용하고, 기존 코드 리팩터링은 별도 요청 시에만 한다.

## 8. null 은 경계에서 정리

내부 로직에 null 이 흐르지 않게 한다. 입력 경계에서 검증하거나 기본값으로 바꾼다.

- HTTP request 의 null/blank/size/positive 같은 shape 검증은 DTO/Controller 의 `@Valid` 로 처리한다.
- 인증 principal null 검증은 인증 필터/시큐리티 경계가 책임진다.
- `Facade` 에서 `validateCommand(command)` 를 만들어 위 레이어에서 이미 보장한 `command == null`, DTO 필드 null, 인증 사용자 null 을 다시 검사하지 않는다.
- 도메인 invariant 는 DTO 검증으로 대체하지 않는다. `Order.create(...)`, `OrderItem.create(...)`, `ProductStock.deduct(...)` 처럼 Entity/VO/Domain Service 가 계속 책임진다.
- HTTP 외 adapter 가 같은 유스케이스를 호출하면 그 adapter 경계에서 검증하거나 Command 생성 경로를 안전하게 만든다.

```java
public List<Item> getItems() {
    return items == null ? List.of() : items;
}
```

컬렉션은 `null` 이 아니라 빈 컬렉션 (`List.of()`, `Set.of()`, `Map.of()`) 을 반환한다.

## 9. Java 21 기능은 가독성을 높일 때만

**record** — DTO/Command/단순 Context 에 사용한다. JPA Entity 에는 사용하지 않는다. 이 저장소에서는 `*V1Dto`, `*Info` 가 record 다.

**switch expression** — 단순 분기 반환에 사용한다.
```java
return switch (status) {
    case WAITING_PAYMENT -> "결제 대기";
    case PAID -> "결제 완료";
    case CANCELLED -> "주문 취소";
};
```

**text block** — 긴 SQL/JSON/메시지 템플릿에 사용한다.

**var** — 타입이 명확한 우변에서만 사용한다. `var order = new Order(...)` 는 가능하지만, `var result = repository.findX()` 처럼 의미가 흐려지면 명시 타입을 쓴다.

## 10. Lombok 은 필요한 만큼만

- `@Data` 는 사용하지 않는다.
- 도메인 엔티티는 `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 를 기본으로 한다.
- setter 는 만들지 않고, 상태 변경은 도메인 행위 메서드로만 한다.
- 생성 의도가 중요하면 정적 팩토리 메서드를 우선 고려한다. 예: `Order.create(...)`.
- 필드가 많거나 optional 값이 섞여 생성자가 읽히지 않으면 `@Builder` 를 사용한다.
- Service / Facade / Component 의 생성자 주입은 `@RequiredArgsConstructor` 를 사용한다.

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
}
```

## 체크

- [ ] 이름이 짧고 자연스러운 도메인 말로 읽히는가?
- [ ] 상태 변경이 setter 가 아니라 행위 메서드로 표현되었는가?
- [ ] 메서드가 같은 추상화 수준의 한 흐름으로 읽히는가?
- [ ] null 과 빈 컬렉션 처리를 경계에서 정리했는가?
- [ ] Java 21/Lombok 기능을 필요한 만큼만 사용했는가?
