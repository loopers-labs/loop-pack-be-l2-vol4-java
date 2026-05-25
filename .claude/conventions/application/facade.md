# Facade 컨벤션

## 책임
유스케이스를 조합하는 application 계층의 오케스트레이터. 도메인 `Repository`로 객체를 조회하고, 엔티티 메서드·도메인 서비스에 협력을 위임하며, 트랜잭션 경계를 가진다. 도메인 모델을 application 출력 DTO(`*Info`)로 변환해 표현 계층으로부터 엔티티를 보호한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/application/user/UserFacade.java`

## 핵심 규칙
- `@Service` + 클래스 레벨 `@Transactional`로 선언한다. 조회 전용 유스케이스 메서드는 `@Transactional(readOnly = true)`로 오버라이드한다.
- 도메인 `Repository` 인터페이스와 도메인 서비스(`@Component`)를 주입한다(`@RequiredArgsConstructor`).
- 유스케이스 흐름: Repository로 도메인 객체 조회 → 엔티티 메서드/도메인 서비스에 협력 위임 → Repository로 저장 → `Info`로 변환해 반환.
- 존재 보장 조회(`mustFind*`)와 중복/충돌 검사(`exists*`)는 Repository를 쓰므로 Facade에 둔다. 없으면 `CoreException(NOT_FOUND)`, 충돌이면 `CoreException(CONFLICT)`.
- 입력은 raw 파라미터(또는 application 입력 객체), 출력은 `*Info`. 도메인 모델을 파라미터로 받거나 그대로 반환하지 않는다(표현계층 보호).

## 핵심 발췌
```java
@Service
@Transactional
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderPricingService orderPricingService;

    public OrderInfo placeOrder(Long userId, List<OrderLineCommand> lines) {
        List<OrderLine> orderLines = toOrderLines(lines);
        Money total = orderPricingService.calculateTotal(orderLines);
        Order order = Order.place(userId, orderLines, total);
        return OrderInfo.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderInfo readOrder(Long orderId) {
        return OrderInfo.from(mustFindOrderById(orderId));
    }

    private Order mustFindOrderById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));
    }
}
```

## do / don't
- ✅ Facade가 트랜잭션 경계·Repository 접근을 갖고, 도메인 객체를 조회해 도메인 서비스/엔티티에 위임한다.
- ✅ 조회 유스케이스는 `@Transactional(readOnly = true)`.
- ✅ 출력은 `Info.from(model)`로 변환해 반환한다.
- ❌ Facade에 도메인 규칙(불변식·계산)을 직접 두지 않는다 — 엔티티/VO/도메인 서비스에 위임.
- ❌ 도메인 모델을 파라미터로 받거나 그대로 반환하지 않는다.
