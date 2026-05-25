# Domain Service 컨벤션

## 책임
한 엔티티/VO에 안착하기 애매한, 여러 도메인 객체의 협력 로직을 담는 무상태 순수 객체. 로직은 우선 Entity/VO에 두고, 그래도 어디에도 속하지 않는 협력만 도메인 서비스로 분리한다. 모든 도메인이 갖지는 않는다.

## 정식 참조
아직 표준 구현 없음 — 첫 Product/Order 도메인 서비스 작성 후 이 자리에 경로를 채운다(살아있는 문서). 그 전까지는 아래 규칙이 원천이다.

## 핵심 규칙
- 무상태 순수 POJO다. `@Component`로 빈 등록만 한다(Facade가 주입). `@Service`·`@Transactional`을 붙이지 않는다.
- Repository·EntityManager 등 영속성 의존을 주입받지 않는다. 가변 필드 상태를 갖지 않는다.
- 메서드는 이미 로드된 도메인 객체(엔티티/VO)를 인자로 받아 협력 로직을 수행하고 결과(도메인 객체/값)를 반환한다. 조회·저장·트랜잭션은 호출자(Facade)의 몫이다.
- 단일 객체의 불변식은 도메인 서비스가 아니라 그 객체(Entity/VO)에 둔다. 도메인 서비스는 "협력"만 책임진다.

## 핵심 발췌
```java
@Component
public class OrderPricingService {

    public Money calculateTotal(List<OrderLine> orderLines) {
        return orderLines.stream()
            .map(OrderLine::subtotal)
            .reduce(Money.zero(), Money::add);
    }
}
```

## do / don't
- ✅ `@Component` 무상태 POJO, 도메인 객체를 인자로 받아 협력한다.
- ❌ `@Transactional`·Repository를 주입하지 않는다 (그건 Facade 몫).
- ❌ 단일 객체 불변식을 도메인 서비스로 빼지 않는다 (Entity/VO에 둔다).
