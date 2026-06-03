# ADR-027: Domain Service와 Application Service(Facade) 계층 분리 원칙

- 날짜: 2026-05-29
- 상태: 승인됨

## 결정

이 프로젝트는 4 Layered Architecture를 기반으로 하며, 계층별 책임을 아래와 같이 명확히 구분한다.

| 계층 | 구현체 | 핵심 책임 |
|------|--------|-----------|
| Interfaces | Controller, DTO | HTTP 요청/응답 변환 |
| Application | Facade, Info | 유스케이스 조합, 흐름 제어, DTO 변환 |
| Domain | Service, Model, Repository | 비즈니스 규칙, **트랜잭션 경계** |
| Infrastructure | JpaRepository, RepositoryImpl | 기술 구현 (DB, 외부 연동) |

---

## 배경

### Application Service란 무엇인가

DDD(Eric Evans)에서 **Application Service**는 도메인 모델을 외부(Controller, Batch, Consumer)에서 사용 가능하도록 조율하는 계층이다. 도메인 규칙 자체는 알지 못하며, 어떤 도메인 서비스를 어떤 순서로 호출할지만 안다.

### 왜 'Facade'라는 이름을 사용하는가

GoF Facade 패턴은 "복잡한 서브시스템에 단순화된 인터페이스를 제공한다"고 정의한다. 이 프로젝트에서:

- **복잡한 서브시스템** = 여러 Domain Service
- **단순화된 인터페이스** = Controller가 단 하나의 메서드만 호출

```java
// Controller는 내부 복잡성을 전혀 알 필요가 없다
public ApiResponse<OrderInfo> placeOrder(...) {
    return ApiResponse.success(orderFacade.placeOrder(command));
}
```

DDD의 Application Service와 GoF의 Facade는 **출신은 다르지만 이 프로젝트에서 동일한 계층, 동일한 클래스로 표현**된다. 'Facade'라는 이름은 "Controller에 단일 진입점을 제공한다"는 구조적 의도를 명시적으로 드러내기 위해 선택했다.

---

## 계층별 책임 상세

### Domain Service

Domain Service는 **도메인 규칙의 실행 주체이자 트랜잭션 경계의 소유자**이다.

```java
@Component
@Transactional(readOnly = true)
public class ProductService {

    @Transactional
    public ProductModel create(ProductModel product) {
        // 도메인 규칙: 동일 브랜드에 같은 이름의 상품 불가
        if (productRepository.existsByBrandIdAndName(...)) {
            throw new CoreException(ErrorType.CONFLICT, "중복 상품명");
        }
        return productRepository.save(product);
    }
}
```

- `@Transactional`은 Domain Service에서 선언한다
- 단일 엔티티로 표현하기 어려운 도메인 연산을 담는다
- 여러 유스케이스에서 재사용 가능한 단위로 설계한다

### Application Service (Facade)

Facade는 **유스케이스 오케스트레이터**이다. 비즈니스 규칙을 직접 구현하지 않는다.

```java
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final PaymentService paymentService;

    public OrderInfo placeOrder(Long userId, OrderCommand command) {
        productService.decreaseStock(command.productId(), command.quantity()); // Domain Service 1
        OrderModel order = orderService.create(userId, command);               // Domain Service 2
        paymentService.process(order.getId(), command.paymentInfo());          // Domain Service 3
        return OrderInfo.from(order);  // Model → Info 변환
    }
}
```

---

## 트랜잭션 경계 원칙

### 기본 원칙

> 트랜잭션은 가급적 Domain Service에서 관리한다.

### Facade에 @Transactional을 붙이지 않는 이유

Facade에 `@Transactional`을 붙이면 여러 Domain Service 호출이 하나의 트랜잭션으로 묶인다. 이는 세 가지 문제를 야기한다.

**1. 트랜잭션 범위 과대**

외부 API 호출, 이벤트 발행 등 롤백이 불가능한 작업이 트랜잭션 안으로 들어올 위험이 생긴다. 트랜잭션은 DB 연산에만 적용되어야 한다.

**2. 책임 혼재**

트랜잭션 경계가 도메인 계층이 아닌 응용 계층에서 결정된다. 도메인 규칙의 원자성은 도메인 계층이 알아야 할 지식이다.

**3. 테스트 복잡성**

Facade 단위 테스트 시 전체 트랜잭션 컨텍스트를 함께 올려야 하므로, 유스케이스 흐름 검증과 트랜잭션 검증이 뒤섞인다.

### 예외: Facade에 @Transactional을 허용하는 경우

여러 Service의 쓰기 작업이 **원자성을 반드시 요구**하고, 그 원자성을 Domain Service 단에서 표현하기 어려울 때에 한해 Facade에 `@Transactional`을 적용한다.

```java
// 예: LikeFacade — like 상태와 likeCount가 항상 함께 변경되어야 함 (ADR-022 참고)
@Transactional
public void addLike(Long userId, Long productId) {
    likeService.like(userId, productId);
    productService.incrementLikeCount(productId);
}
```

이 경우에도 이유를 명시적으로 기록하고, 관련 ADR을 남긴다.

---

## 결정의 이유

이 설계를 선택한 이유는 다음과 같다.

1. **도메인 모델 보호**: 비즈니스 규칙이 Controller나 Facade로 새어나오지 않도록 명확한 경계를 강제한다.
2. **재사용성**: Domain Service는 API, Batch, Consumer 등 여러 진입점에서 재사용 가능한 단위가 된다.
3. **테스트 용이성**: 계층별로 독립 테스트가 가능하다. Domain Service는 도메인 규칙만, Facade는 흐름만 검증한다.
4. **단일 책임**: 트랜잭션 경계, 도메인 규칙, 유스케이스 흐름이 각각 다른 계층에서 관리된다.

---

## 참고

- ADR-012: 트랜잭션 경계 원칙
- ADR-022: LikeFacade @Transactional 적용 (예외 케이스)
