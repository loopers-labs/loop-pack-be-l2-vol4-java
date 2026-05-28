# DDD 프로젝트 개발 가이드라인 (통합본)

> 이 문서는 사용자(대광)와 이전 Claude가 나눈 DDD 학습 대화를 바탕으로 정리한 **프로젝트 개발 가이드라인**입니다.
> 신규/기존 프로젝트에 DDD를 적용해 개발할 때 이 문서의 원칙과 패턴을 기준으로 삼습니다.

---

## 📌 이 문서를 읽는 Claude를 위한 안내

당신은 지금부터 **이 문서를 기반으로 사용자의 프로젝트 개발을 돕는 DDD 개발 파트너**입니다.

### 사용자 정보
- Java / Spring / JPA 백엔드 개발자
- DDD Start! (최범균) 책 학습 완료
- 동적 쿼리는 **QueryDSL** 선호
- 본인 프로젝트는 "DDD 전술 패턴을 곁들인 레이어드 아키텍처" 사용
  - interfaces.api / application / domain / infrastructure 4계층
  - Repository만 헥사고날 Port/Adapter 적용 (Outbound)
  - Inbound는 Facade 직접 호출, Info 객체로 응답 변환

### 답변 규칙
- **한국어**로 답변
- 마크다운 과한 포맷팅 자제 (불필요한 헤더/볼드 남발 X)
- 코드 작성/리뷰 시 이 문서의 DDD 원칙과 패턴을 따를 것

### 🔑 가장 중요한 - 개발 진행 방식 (반드시 준수)

**코드를 바로 쏟아내지 말 것.** 대신 아래 상호작용 플로우를 따른다.

```
1. 사용자가 기능/개발 요청
        ↓
2. 이 문서(DDD 가이드라인) 참조
        ↓
3. 해당 상황에 적용 가능한 DDD 패턴 식별
        ↓
4. 선택지를 장단점과 함께 제시 (← 핵심!)
   "이 필드들은 항상 같이 사용되므로 VO로 묶는 게 좋아 보입니다.
    그렇게 하시겠습니까?
    - 옵션 A: VO로 묶기 (장점: ..., 단점: ...)
    - 옵션 B: 개별 필드 유지 (장점: ..., 단점: ...)"
        ↓
5. 사용자가 선택
        ↓
6. 선택에 따라 개발 진행
```

**예시 흐름:**

```
사용자: "주문에 배송 정보를 추가하고 싶어"

Claude: 배송 정보 관련 필드(받는사람 이름, 전화번호, 주소)를 보니
        DDD 관점에서 두 가지 선택지가 있습니다.

        [옵션 A] Receiver + Address VO로 묶기
        - 장점: 관련 필드 응집, 검증 로직 한 곳에, 재사용 가능
        - 단점: 클래스 수 증가, @Embedded 매핑 필요

        [옵션 B] Order에 개별 필드로 추가
        - 장점: 단순, 빠른 구현
        - 단점: 필드 흩어짐, 검증 분산, ShippingInfo 재사용 불가

        배송 정보는 항상 같이 다니고 검증 규칙도 있어서
        옵션 A(VO로 묶기)를 추천합니다. 어떻게 하시겠습니까?

사용자: "옵션 A로 가자"

Claude: [VO + Entity 매핑 코드 작성 및 진행]
```

이런 식으로 **DDD 의사결정을 사용자와 함께** 내리면서 개발한다.
사용자가 "그냥 알아서 해줘" 라고 하면 그때는 추천안대로 진행하되, 어떤 DDD 원칙을 적용했는지 간단히 설명한다.

---

## 🚀 프로젝트 적용 시 의사결정 빠른 참조

개발 중 자주 마주치는 상황과 DDD 판단 기준. 상세 내용은 본문(PART 1, 2) 참조.

### 도메인 모델링

| 상황 | 판단 / 제안 |
|---|---|
| 비즈니스 로직을 어디 둘까 | → 도메인 객체에 (서비스에 if문 쌓이면 신호) |
| 항상 같이 다니는 필드 | → VO로 묶기 (Receiver, Address, Money 등) |
| 식별자가 의미를 가짐 | → VO ID로 (OrderNo, MemberId) |
| 같은 id면 같은 객체? | → Entity / 값이 같으면? → VO |
| 도메인 용어 정하기 | → 비즈니스 용어 그대로 (STEP1 X, PAYMENT_WAITING O) |

### 애그리거트 / 트랜잭션

| 상황 | 판단 / 제안 |
|---|---|
| 객체 관계가 복잡 | → 애그리거트로 묶고 루트로만 접근 |
| 다른 애그리거트 참조 | → 객체 직접 참조 X, ID로 참조 |
| 여러 애그리거트 동시 변경 | → "동시에 성공/실패?" → 대부분 NO → 이벤트로 분리 |
| 진짜 강한 일관성 필요 | → 한 트랜잭션 (계좌이체, 재고+결제) |
| 자식 객체 조회 Repository | → 만들지 말 것 (루트 Repository만) |

### 서비스 계층

| 상황 | 판단 / 제안 |
|---|---|
| 흐름 조율, 트랜잭션 | → Application Service (비즈니스 규칙 X) |
| 여러 애그리거트 걸친 규칙 | → Domain Service (Repository 의존 X) |
| 한 엔티티에 넣을 수 있는 로직 | → 엔티티에 (Domain Service 남용 금지) |
| 여러 도메인 조율 | → Facade |
| 응용 서비스에 if문이 쌓임 | → 도메인으로 로직 이동 신호 |

### 동시성 제어

| 상황 | 판단 / 제안 |
|---|---|
| 충돌 빈번 + 짧은 작업 | → 선점 잠금 (@Lock PESSIMISTIC_WRITE) |
| 충돌 드문 일반 업데이트 | → 비선점 잠금 (@Version) |
| 조회→수정→저장 긴 흐름 | → 오프라인 선점 잠금 |
| 락 타임아웃 (MySQL) | → @Transactional(timeout) (@QueryHint X) |
| 교착 상태 우려 | → 다수 락 자체를 회피 (이벤트 분리) |

### 이벤트

| 상황 | 판단 / 제안 |
|---|---|
| 외부 시스템 호출 (환불 등) | → 이벤트로 분리 |
| 함께 성공/실패 필요 | → 동기 (@EventListener) |
| 실패해도 메인 OK | → 비동기 |
| 커밋 후 처리 + 안전 | → @TransactionalEventListener(AFTER_COMMIT) |
| 도메인 이벤트 발행 | → AbstractAggregateRoot 권장 |
| 정합성 최우선 | → Outbox 패턴 |

### 조회 / CQRS

| 상황 | 판단 / 제안 |
|---|---|
| 단일 애그리거트 조회 | → Lazy + Info 변환으로 충분 |
| 여러 애그리거트 조인 조회 | → CQRS (Query 전용 모델) |
| 동적 쿼리 | → **QueryDSL + DTO 프로젝션** (@QueryProjection) |
| 핵심 도메인(결제/주문/예약) | → 처음부터 소스 레벨 CQRS |
| 단순 CRUD | → CQRS 도입 금지 (오버엔지니어링) |
| 조회 트래픽 폭발 | → Read DB 분리 + 역정규화 |

### 바운디드 컨텍스트

| 상황 | 판단 / 제안 |
|---|---|
| 같은 용어 다른 의미 | → 컨텍스트별 독립 클래스 |
| 외부 시스템 연동 | → ACL로 격리 (준수자) |
| 컨텍스트 분리 시작 | → 패키지 분리부터 (MSA는 나중) |
| 의미 동일한 기반 타입 | → 공유 커널 (Money, Id만, 핵심 도메인 X) |

---

## 📂 문서 구성

- **PART 1: DDD 순수 개념** - 무엇을(What), 왜(Why), 언제(When) - 설계 의사결정 기준
- **PART 2: Spring/JPA 구현** - 어떻게(How) - 코드 템플릿, 베스트 프랙티스

개발 시: PART 1로 "무엇을 적용할지" 판단 → PART 2로 "어떻게 구현할지" 참조

---
---


# PART 1. DDD 순수 개념 - 설계 의사결정 기준


> *DDD Start!* 1~11장 중 **순수 DDD 개념**만 추출하여 흐름 있게 정리

이 문서 하나로 DDD의 핵심을 이해할 수 있도록 작성했습니다.

---

## 들어가며 - DDD는 왜 필요한가

소프트웨어 개발에서 가장 어려운 건 코딩 자체가 아니라 **"무엇을 만들어야 하는가"** 다.
비즈니스가 복잡할수록 요구사항과 코드 사이의 간극이 벌어지고, 결국 유지보수가 불가능한 시스템이 만들어진다.

DDD(Domain-Driven Design)는 2003년 Eric Evans가 제안한 설계 방법론이다.
핵심 주장은 단순하다.

> **"소프트웨어의 복잡성은 도메인 자체의 복잡성에서 온다.
> 그렇다면 도메인을 잘 모델링하는 것이 핵심이다."**

DDD는 패턴 모음이 아니라 **사고방식**이다. 다섯 가지 원칙으로 요약하면:

1. 도메인 전문가의 언어를 코드에 그대로 반영하라
2. 비즈니스 규칙은 도메인 객체 안에 있어야 한다
3. 인프라가 도메인을 오염시키지 못하게 하라
4. 큰 도메인은 컨텍스트로 나눠라
5. 각 컨텍스트는 독립적으로 진화한다

이 원칙들을 구체적으로 어떻게 적용하는지 하나씩 살펴보자.

---

## 1. 도메인 모델 패턴 - 비즈니스 로직은 어디에 살아야 하는가

DDD를 이해하는 출발점이다. **비즈니스 규칙을 어디에 두느냐**의 문제다.

전통적인 Spring 개발에서 흔히 보는 코드는 이렇다.

```java
@Service
public class OrderService {
    public void cancel(Long orderId) {
        Order order = orderRepository.findById(orderId);

        if (order.getState() != PAYMENT_WAITING && order.getState() != PREPARING) {
            throw new IllegalStateException("취소 불가");
        }
        order.setState(CANCELED);
    }
}
```

이걸 **트랜잭션 스크립트 패턴**이라고 한다. 비즈니스 규칙("결제 대기와 준비 중 상태에서만 취소 가능")이 서비스에 들어가 있다.

처음엔 단순해 보이지만 문제가 점점 커진다. 같은 규칙이 여러 서비스에 흩어진다. `OrderCancelService`, `OrderRefundService`, `AdminCancelService` 모두에서 상태 체크 로직을 가져야 한다. 규칙이 바뀌면 모든 서비스를 찾아 수정해야 하고, Order가 자기 자신의 상태에 대한 권한을 가지지 못한다 (외부에서 `setState` 호출 가능). 결국 누군가 한 곳에서 검증 없이 상태를 바꿔버리면 데이터가 깨진다.

도메인 모델 패턴은 이 문제를 해결한다.

```java
public class Order {

    private OrderState state;

    public void cancel() {
        if (!state.isCancelable()) {
            throw new IllegalStateException("취소 불가");
        }
        this.state = OrderState.CANCELED;
    }
}

@Service
public class OrderService {
    public void cancel(Long orderId) {
        Order order = orderRepository.findById(orderId);
        order.cancel();  // 도메인에 위임
    }
}
```

이제 **Order 자기 자신이 취소 가능 여부를 판단**한다. 외부에서는 `cancel()`을 호출할 뿐, 어떤 상태에서 취소가 되는지는 Order만 안다. 같은 규칙이 여러 곳에 흩어질 일이 없고, 규칙이 바뀌어도 Order만 수정하면 된다. 무엇보다 Order 자신이 자기 일관성을 책임진다.

핵심 원칙은 이거다:

> **"행위는 데이터를 가진 객체에게 위임하라"**
>
> Service는 흐름 조율, Domain은 규칙 판단을 책임진다.

언제 도메인 모델 패턴을 쓸까? **비즈니스 규칙이 있는 모든 도메인**이다. 단순 CRUD(상태 변경 정도)라면 굳이 도메인 모델 패턴이 필요 없지만, 조금이라도 규칙이 있다면 도메인 모델 패턴이 낫다.

물론 단점도 있다. 도메인 객체가 무거워지고, 절차적 사고에 익숙한 팀에게는 학습 곡선이 있다. 작은 프로젝트엔 오버엔지니어링이 될 수 있다. 하지만 시스템이 자라면서 얻는 가치가 압도적이다. **도메인 단위 테스트가 가능**해진다는 점도 빼놓을 수 없는 장점이다.

---

## 2. Entity와 Value Object - 객체 모델링의 기본 단위

도메인 모델은 두 가지 객체로 구성된다. **Entity(엔티티)** 와 **Value Object(밸류 객체, VO)**.

### Entity - 식별자로 구분되는 객체

엔티티는 **id로 구분**된다. 같은 id라면 다른 속성을 가져도 같은 객체다.

```java
public class Order {
    private Long id;              // 식별자
    private OrderState state;     // 변경 가능
    private LocalDateTime orderedAt;
}
```

주문이 좋은 예다. 같은 주문번호 `ORD-001`은 어제도 오늘도 같은 주문이다. 상태가 `PAYMENT_WAITING`에서 `SHIPPED`로 바뀌어도 여전히 같은 주문이다.

엔티티는 **시간에 따라 상태가 변하는 객체**다. 회원, 주문, 게시글 등 비즈니스에서 "동일성"을 추적해야 하는 것들이 엔티티다.

### Value Object - 값 자체로 구분되는 객체

VO는 **값 자체로 구분**된다. 값이 같으면 같은 객체로 본다.

```java
public class Money {
    private final BigDecimal amount;

    public Money(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("금액은 0 이상");
        }
        this.amount = amount;
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }
}
```

10,000원이라는 Money는 어디서 생성되든 같은 값이다. 식별자가 없고, **불변(immutable)** 이어야 한다.

왜 불변이어야 하느냐면, **값 자체가 신원**이기 때문이다. 값이 바뀌면 더 이상 같은 객체가 아니다. 그래서 변경 시 새 객체를 반환한다. 불변이라는 특성 덕분에 사이드 이펙트가 없고, 멀티스레드 환경에서도 안전하다.

### VO를 적극 도입해야 하는 이유

원시 타입만 쓰는 코드를 보자.

```java
public class ShippingInfo {
    private String receiverName;
    private String receiverPhone;
    private String shippingAddress1;
    private String shippingAddress2;
    private String shippingZipcode;
}
```

여러 필드가 항상 같이 다닌다. 받는 사람 정보(`receiverName`, `receiverPhone`)와 주소(`address1`, `address2`, `zipcode`)는 의미 단위로 묶인다. 이걸 VO로 묶으면 훨씬 명확하다.

```java
public class ShippingInfo {
    private Receiver receiver;
    private Address address;
}

public class Receiver {
    private final String name;
    private final String phone;
}

public class Address {
    private final String address1;
    private final String address2;
    private final String zipcode;
}
```

더 나아가 VO는 **도메인 메서드**를 가질 수 있다. `Money.add()`, `Address.fullAddress()`, `Phone.format()` 같은 메서드가 VO 안에 있으면 관련 로직이 한 곳에 응집된다. 이게 VO의 진짜 가치다 - 단순한 데이터 묶음이 아니라 의미와 행위를 가진 객체가 된다.

### 식별자도 VO화하면 표현력이 좋아진다

```java
// String보다는
public class OrderNo {
    private final String number;

    public boolean is2ndGeneration() {
        return number.startsWith("N");
    }
}
```

식별자 자체도 의미가 있다면 VO로 표현하는 게 좋다. `String orderNo`로 다닐 때는 그저 문자열이지만, `OrderNo`라는 타입이 되면 도메인 개념이 코드에 드러난다.

### 판단 기준

> **같은 id면 같은 객체인가? → Entity**
> **값이 같으면 같은 객체인가? → VO**

VO는 반드시 불변이어야 한다. 모든 필드 final, setter 금지, 변경 시 새 객체 반환. 이게 깨지면 VO의 의미가 사라진다.

도입 비용은 거의 없고 효과는 크다. **항상 같이 다니는 필드가 있다면 일단 VO로 묶어보자.** VO는 적극적으로 활용할수록 도메인 모델이 풍부해진다.

---

## 3. 유비쿼터스 언어 - 같은 용어로 말하기

DDD의 시작점이라고 할 만한 개념이다. **도메인 전문가, 사용자, 개발자가 모두 같은 용어를 사용**하는 것이다.

도메인 지식 없이 코드를 짜면 잘못된 모델이 만들어진다.

```
도메인 전문가의 말 → 개발자의 해석 → 코드
        ↓                ↓             ↓
     원본 의미      왜곡된 의미       버그
```

이 격차를 줄이려면 **같은 단어를 쓰는 것**부터 시작해야 한다.

```java
// ❌ 개발자가 만든 용어
public enum OrderState {
    STEP1,    // 결제 대기
    STEP2,    // 준비 중
    STEP3,    // 배송 중
    STEP4     // 완료
}

// ✅ 도메인 용어 그대로
public enum OrderState {
    PAYMENT_WAITING, PREPARING, SHIPPED, DELIVERED, CANCELED
}
```

회의에서 "결제 대기 중인 주문"이라고 부르면, 코드에서도 `PAYMENT_WAITING`이어야 한다. 클래스명, 메서드명, 변수명 모두 일관되게 비즈니스 용어를 써야 한다.

이렇게 하면 의사소통 시 번역 단계가 사라지고, 요구사항 누락이 줄어든다. 코드를 통해 도메인을 이해할 수 있어 신규 입사자 온보딩 비용이 크게 감소한다.

당연한 얘기 같지만 실제로는 잘 안 지켜진다. 영문 용어를 임의로 만들거나, 비즈니스 용어와 다른 기술 용어를 쓰면서 점점 코드와 비즈니스가 멀어진다.

유비쿼터스 언어는 **모든 DDD 프로젝트의 필수 기반**이다. 도입 비용이 거의 없으면서 효과는 매우 크다. 가장 먼저 적용해야 할 개념이다.

---

## 4. 4계층 아키텍처 - 큰 그림 그리기

도메인 모델을 잘 만들었다고 끝이 아니다. 시스템 전체 구조도 정리되어야 한다. DDD는 다음 4계층을 제안한다.

```
표현 (Presentation)      → 사용자 요청/응답
        ↓
응용 (Application)        → 흐름 조율, 트랜잭션 관리
        ↓
도메인 (Domain)           → 비즈니스 규칙
        ↓
인프라 (Infrastructure)   → DB, 외부 API
```

각 계층의 책임을 한 줄로 정리하면:

```
표현      → 어떤 요청이 왔는가 (입출력)
응용      → 무엇을 어떤 순서로 할까 (흐름)
도메인    → 어떻게 처리할까 (비즈니스 규칙)
인프라    → 어떻게 저장할까 (기술 세부사항)
```

예시로 보자.

```java
// 1. 표현 - 요청/응답만
@RestController
public class OrderController {
    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        cancelOrderService.cancel(id);
        return ResponseEntity.ok().build();
    }
}

// 2. 응용 - 흐름 조율
@Service
@Transactional
public class CancelOrderService {
    public void cancel(Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        order.cancel();
    }
}

// 3. 도메인 - 비즈니스 규칙
public class Order {
    public void cancel() {
        if (!state.isCancelable()) throw new IllegalStateException();
        this.state = CANCELED;
    }
}

// 4. 인프라 - DB 접근
@Repository
public class JpaOrderRepository implements OrderRepository {
    // JPA 구현
}
```

### 계층 간 의존 규칙

규칙은 단순하다.

```
상위 계층 → 하위 계층 의존만 허용
역방향 의존 절대 금지 (도메인이 응용에 의존 X)
```

각 계층이 **알아야 할 것**과 **몰라야 할 것**이 명확하다. 표현 계층은 Facade를 알지만 도메인 내부를 몰라야 한다. 응용 계층은 도메인과 Repository를 알지만 HTTP나 다른 도메인 컨텍스트를 몰라야 한다. 도메인은 자기 자신만 알면 되고, Spring이나 JPA를 몰라야 한다. 인프라는 DB와 외부 시스템을 알지만 비즈니스 규칙을 몰라야 한다.

특히 중요한 건 **도메인이 Spring, JPA 같은 인프라를 몰라야 한다**는 점이다. 도메인이 외부 기술에 의존하면 인프라가 바뀔 때마다 도메인 코드도 바뀐다. 도메인은 비즈니스 규칙의 집이지, 기술의 집이 아니다.

이 계층 구조는 거의 모든 도메인 프로젝트에 적용된다. 협업하는 프로젝트, 장기 유지보수가 필요한 시스템에서는 필수다. 일회성 스크립트가 아니라면 처음부터 4계층 구조를 잡고 시작하는 게 좋다.

---

## 5. DIP (의존성 역전) - 도메인 순수성 지키기

4계층 아키텍처의 의존 규칙을 강제하는 핵심 도구가 DIP다.

문제 상황을 보자.

```java
// 도메인이 인프라에 의존
public class CalculateDiscountService {

    private DroolsRuleEngine ruleEngine;  // ← 인프라(룰 엔진)에 직접 의존

    public Money calculate(List<OrderLine> lines, String customerId) {
        // Drools 룰 엔진 호출 (인프라 세부사항)
    }
}
```

도메인이 Drools라는 특정 룰 엔진에 묶여있다. Drools를 다른 엔진으로 바꾸려면 도메인 코드까지 수정해야 한다. 테스트할 때는 Drools 환경이 필요하다.

DIP는 **의존성 방향을 뒤집어서** 이 문제를 해결한다.

```java
// 도메인이 인터페이스 정의 (도메인 영역)
public interface RuleDiscounter {
    Money applyRules(Customer customer, List<OrderLine> orderLines);
}

public class CalculateDiscountService {
    private RuleDiscounter ruleDiscounter;  // 추상화에 의존

    public Money calculate(List<OrderLine> lines, String customerId) {
        Customer customer = findCustomer(customerId);
        return ruleDiscounter.applyRules(customer, lines);
    }
}

// 인프라가 도메인 인터페이스를 구현 (인프라 영역)
@Component
public class DroolsRuleDiscounter implements RuleDiscounter {
    @Override
    public Money applyRules(Customer customer, List<OrderLine> orderLines) {
        // Drools 관련 코드
    }
}
```

이제 의존성 방향이 바뀌었다.

```
도메인 (고수준)
   ↑
   │ 의존 방향이 도메인을 향함
   │
인프라 (저수준) ← 도메인이 정의한 인터페이스를 구현
```

인프라 변경 시 도메인에 영향이 없다. Drools를 다른 룰 엔진으로 바꾸든 자체 구현으로 바꾸든, `RuleDiscounter`를 구현하기만 하면 된다. 테스트할 때는 Mock으로 대체할 수 있다.

핵심은 **인터페이스 위치**다. 인터페이스가 인프라에 있으면 DIP의 의미가 없다. 반드시 **인터페이스는 도메인에, 구현체는 인프라에** 두어야 한다.

```
❌ 인터페이스를 인프라에 두면 의미 없음
└── infrastructure
    ├── RuleDiscounter (인터페이스)
    └── DroolsRuleDiscounter (구현체)

✅ 인터페이스를 도메인에 둬야 의존성 역전 효과
├── domain
│   └── RuleDiscounter (인터페이스)
└── infrastructure
    └── DroolsRuleDiscounter (구현체)
```

DIP는 약간의 추상화 비용(인터페이스 클래스가 늘어남)을 받고 도메인의 순수성을 얻는 거래다. 도메인이 외부 기술과 분리되면 시스템 전체가 훨씬 유연해진다. 도메인 순수성이 중요하지 않은 단순 CRUD라면 굳이 적용할 필요 없지만, 도메인 단위 테스트가 중요하거나 인프라 교체 가능성이 있는 시스템(RDB → NoSQL 등)에서는 큰 가치를 발휘한다.

---

## 6. 애그리거트 - 객체 관계를 묶어서 다루기

도메인 객체가 늘어나면 관계가 복잡해진다.

```
Order
├── OrderLine
├── ShippingInfo
├── Receiver
├── Address
├── Orderer
├── PaymentInfo
└── ... (수십 개)
```

이 객체들을 개별적으로 다루면 객체 간 관계 파악이 어렵고, 어디까지가 한 단위인지 모호하다. 외부에서 자식 객체를 직접 조작할 수 있어서 일관성 유지가 어려워진다.

**애그리거트(Aggregate)** 는 관련된 객체들을 하나의 군집으로 묶는 개념이다.

```
[Order 애그리거트]
└── Order (애그리거트 루트) ⭐
    ├── OrderLine
    ├── ShippingInfo
    │   ├── Receiver
    │   └── Address
    ├── Orderer
    └── PaymentInfo
```

외부에서는 **애그리거트 루트(Aggregate Root)** 인 Order를 통해서만 접근한다. OrderLine, ShippingInfo 같은 자식 객체는 외부에서 직접 다루지 않는다.

### 왜 루트를 통해서만 접근해야 하는가

루트의 핵심 역할은 **일관성 유지**다.

```java
public class Order {

    public void changeShippingInfo(ShippingInfo newInfo) {
        verifyNotYetShipped();  // 검증
        this.shippingInfo = newInfo;
    }

    private void verifyNotYetShipped() {
        if (state != PAYMENT_WAITING && state != PREPARING) {
            throw new IllegalStateException("이미 배송 중");
        }
    }
}
```

루트를 통하지 않고 자식을 직접 조작하면 검증을 우회한다.

```java
// ❌ 위험! 검증 로직 우회
ShippingInfo info = order.getShippingInfo();
info.setAddress(newAddress);  // 배송 중이어도 변경됨!
```

이런 일을 막으려면 VO를 불변으로 만들어 setter 자체를 없애고, 컬렉션은 `Collections.unmodifiableList()` 로 반환하고, 변경은 반드시 루트의 메서드를 통해서만 하도록 강제해야 한다.

```java
public class Order {

    private List<OrderLine> orderLines = new ArrayList<>();

    // 외부에는 읽기 전용으로 노출
    public List<OrderLine> getOrderLines() {
        return Collections.unmodifiableList(orderLines);
    }

    // 변경은 루트의 메서드로만
    public void addOrderLine(OrderLineRequest request) {
        if (state != PAYMENT_WAITING) {
            throw new IllegalStateException("결제 대기 상태에서만 추가 가능");
        }
        OrderLine line = OrderLine.of(request);
        orderLines.add(line);
    }
}
```

물론 자바에서는 getter를 막을 수 없으므로 100% 강제는 불가능하다. 그래서 **VO를 불변으로 만들고 + 컬렉션은 Unmodifiable로 반환하고 + 컨벤션으로 강제**하는 게 현실적이다.

### Repository는 애그리거트 루트 단위

리포지터리도 애그리거트 단위로 만든다.

```java
// ✅ 애그리거트 루트
public interface OrderRepository {
    Optional<Order> findById(OrderNo orderNo);
    void save(Order order);
}

// ❌ 자식 객체용은 만들지 않음
// public interface OrderLineRepository { ... }
```

OrderLine을 조회하고 싶으면? 그 OrderLine을 가진 Order를 통해서 접근하면 된다. 이게 애그리거트의 핵심 원칙이다.

### 다른 애그리거트는 ID로만 참조

같은 애그리거트 안에서는 객체 참조를 쓰지만, **다른 애그리거트는 ID로만 참조**한다.

```java
// ❌ 다른 애그리거트를 직접 참조
public class Order {
    @ManyToOne
    private Member orderer;  // Member 애그리거트 직접 참조

    public void cancel() {
        orderer.usePoint(...);  // 다른 애그리거트 조작
    }
}

// ✅ ID로만 참조
public class Order {
    private MemberId ordererId;  // ID만 가짐
}
```

직접 참조의 문제는 한 트랜잭션에서 여러 애그리거트가 함께 변경될 위험이 있고, 즉시 로딩 시 연쇄 로딩으로 성능이 저하되며, 애그리거트 간 결합도가 높아진다는 점이다.

ID 참조는 애그리거트의 **독립성**을 지킨다. 나중에 마이크로서비스로 분리할 때도 자연스럽다.

### 애그리거트 도입 기준

도메인 객체 관계가 복잡하거나, 일관성 유지가 중요하거나, 여러 객체가 함께 생성/삭제되는 경우에 애그리거트로 명시한다. 매우 단순한 도메인이라면 굳이 명시할 필요까지는 없지만, "이 객체들은 한 단위로 다뤄야 한다"는 인식만 있어도 도움이 된다.

장점은 일관성 보장, 트랜잭션 경계 명확화, 캡슐화 강화다. 단점은 학습 곡선이 있고, 강한 일관성 제약 때문에 가끔 우회 설계(이벤트)가 필요하다는 점이다.

---

## 7. 트랜잭션 경계 - 강한 일관성 vs 최종 일관성

애그리거트의 중요한 원칙 중 하나가 **"한 트랜잭션 = 한 애그리거트"** 다.

```java
// ❌ 한 트랜잭션에 두 애그리거트 변경
@Transactional
public void placeOrder(...) {
    Order order = ...;
    Member member = ...;

    order.place();
    member.usePoint(...);  // 다른 애그리거트

    orderRepository.save(order);
    memberRepository.save(member);
}
```

왜 이걸 피해야 하는가?

애그리거트는 **"항상 일관된 상태를 유지해야 하는 단위"** 다. 트랜잭션이 그 일관성을 보장한다. 두 애그리거트를 한 트랜잭션에 묶는다는 건 잠금 범위가 커져 성능이 저하되고, 두 애그리거트의 독립성을 깨뜨리며, 나중에 마이크로서비스로 분리할 때 분산 트랜잭션 문제를 만든다.

게다가 정말로 두 애그리거트가 동시에 성공해야 하는 경우는 생각보다 드물다.

```
포인트 차감이 실패하면 주문도 실패해야 하나?  → 강한 일관성
주문은 성공하고 포인트는 나중에 처리해도 되나? → 최종 일관성
```

대부분의 비즈니스 흐름은 **최종 일관성으로 충분**하다.

### 최종 일관성으로 처리하는 방법

도메인 이벤트로 분리한다.

```java
// 주문 트랜잭션 - Order만 변경
@Transactional
public void placeOrder(...) {
    Order order = Order.place(...);
    orderRepository.save(order);
    eventPublisher.publish(new OrderPlacedEvent(order.getId(), pointsToUse));
}

// 포인트 차감 - 별도 트랜잭션
@EventListener
@Transactional
public void handle(OrderPlacedEvent event) {
    Member member = memberRepository.findById(event.getMemberId());
    member.usePoint(event.getPoints());
}
```

주문은 즉시 완료되고, 포인트 차감은 이벤트로 별도 처리된다. 둘은 결국 일관된 상태에 도달하지만, **즉시 일관성은 보장하지 않는다.**

### 정말 강한 일관성이 필요한 경우

가끔은 진짜로 두 작업이 함께 성공/실패해야 한다.

```
계좌 이체: A에서 출금 + B에 입금 동시 성공
재고 차감 + 결제: 오버셀링 방지
회계 정합성
```

이런 경우는 선택지가 세 가지다. 한 트랜잭션에 묶거나(실용적 타협), 애그리거트 경계를 재설계하거나(두 객체가 늘 함께 변경된다면 하나의 애그리거트일 수도 있다), 마이크로서비스 환경이면 Saga 패턴으로 보상 트랜잭션을 적용한다.

원칙은 단순하다.

> **"동시에 성공/실패해야 하는가?"** 라고 묻고,
> 대부분은 "아니오" 라는 답이 나온다 → 이벤트로 분리

---

## 8. 응용 서비스와 도메인 서비스 - 흐름과 규칙의 분리

응용 계층에는 두 종류의 서비스가 있다. 헷갈리기 쉬우니 명확히 정리하자.

### Application Service - 흐름 조율

응용 서비스의 역할은 **흐름을 조율하고 트랜잭션을 관리**하는 것이다.

```java
@Service
@Transactional
public class CancelOrderService {

    private final OrderRepository orderRepository;

    public void cancel(OrderNo orderNo) {
        Order order = orderRepository.findById(orderNo).orElseThrow();
        order.cancel();  // 도메인에 위임
    }
}
```

이게 전부다. 응용 서비스는 **비즈니스 규칙을 가지지 않는다.** 비밀번호 검증, 주문 취소 가능 여부 같은 규칙은 모두 도메인에 있다. 응용 서비스는 도메인 객체를 조회하고, 도메인 메서드를 호출하고, 트랜잭션을 시작/종료한다.

만약 응용 서비스에 if문이 잔뜩 들어가기 시작하면 신호다. 그 로직이 도메인에 있어야 하는데 응용 계층으로 새어 나온 것이다.

응용 서비스의 또 다른 책임은 **표현 영역과 도메인 영역의 다리** 역할이다. Controller가 도메인 객체를 직접 다루면 도메인이 외부에 노출된다. 응용 서비스를 통해 Command/Info DTO로 변환해서 주고받는다.

```
Request → Command → [도메인] → Info → Response
   ↑         ↑                   ↑        ↑
API 입력  응용 입력           응용 출력  API 출력
```

응용 서비스는 거의 모든 비즈니스 유스케이스에서 필요하다. 트랜잭션이 필요한 경우, 표현 계층이 도메인을 직접 호출하면 곤란한 경우 등 사실상 어디서나 쓰인다.

### Domain Service - 비즈니스 규칙

도메인 서비스는 **여러 애그리거트에 걸친 비즈니스 로직** 또는 **한 엔티티에 넣기 애매한 로직**을 담는다.

예를 들어 두 계좌 간 송금은 어디에 넣어야 할까?

```java
// Account 안에 넣기 - from이 to를 알아야 하나? 어색함
public class Account {
    public void transfer(Account to, Money amount) { ... }
}
```

어느 한쪽에 넣으면 그 객체가 다른 애그리거트를 알아야 한다. 이런 경우 도메인 서비스를 만든다.

```java
public class TransferService {

    public void transfer(Account from, Account to, Money amount) {
        if (!from.canTransfer(amount)) {
            throw new IllegalStateException("잔액 부족");
        }
        from.withdraw(amount);
        to.deposit(amount);
    }
}
```

도메인 서비스 안에는 **비즈니스 규칙**이 있다. 어느 계좌가 송금 가능한지, 어떤 순서로 처리하는지 등의 규칙이다.

또 다른 예는 **할인 정책 계산**이다.

```java
public class DiscountService {

    public Money calculateDiscount(Order order, Coupon coupon, Member member) {
        Money couponDiscount = coupon.calculateDiscount(order.getTotalAmount());
        Money memberDiscount = member.getGrade().getDiscount(order.getTotalAmount());

        // 두 할인 중 큰 쪽 적용 (비즈니스 규칙)
        return couponDiscount.isGreaterThan(memberDiscount) ? couponDiscount : memberDiscount;
    }
}
```

여러 도메인 객체(Order, Coupon, Member)에 걸친 로직이라 어느 하나에 넣기 어색하다.

### 응용 서비스 vs 도메인 서비스 구분

두 가지를 구분하는 가장 간단한 기준은 **"비즈니스 규칙이 있느냐"** 다. 응용 서비스는 흐름만 다루고, 도메인 서비스는 규칙을 다룬다. 응용 서비스는 Repository에 의존하지만, 도메인 서비스는 원칙적으로 Repository를 직접 호출하지 않는다(이미 로드된 도메인 객체를 받아 처리). 응용 서비스는 트랜잭션을 시작/관리하지만 도메인 서비스는 트랜잭션을 모른다.

```
@Service + Repository 의존 + 트랜잭션 → Application Service
순수 자바 + 도메인 객체만 받음 + 규칙 보유 → Domain Service
```

### Domain Service 남용 금지

도메인 서비스는 만들기 쉬워서 남용되기 쉽다. **엔티티에 넣을 수 있는 로직을 굳이 도메인 서비스로 빼는 것**은 잘못된 사용이다.

```java
// ❌ Order에 들어가야 할 로직을 Service로 분리
public class OrderCancelService {
    public void cancel(Order order) {
        if (!order.getStatus().isCancelable()) throw ...;
        order.setStatus(CANCELED);
    }
}

// ✅ Order 자기 자신이 처리할 수 있는 로직
public class Order {
    public void cancel() {
        if (!status.isCancelable()) throw ...;
        this.status = CANCELED;
    }
}
```

도메인 서비스를 만들기 전 **"엔티티나 VO에 넣을 수 없는지"** 먼저 고민해야 한다. 남용하면 도메인 모델이 빈약해지고 결국 트랜잭션 스크립트로 회귀한다.

### Facade - 여러 도메인 조율

단일 API 요청에서 여러 도메인을 다뤄야 할 때가 있다.

```
주문 생성 시:
  OrderService + PaymentService + NotificationService
  이 세 개를 묶어서 호출해야 함
```

Controller가 여러 응용 서비스를 직접 호출하기 시작하면 신호다. 이 조율 책임을 **Facade**로 분리한다.

```java
@Service
@Transactional
public class OrderFacade {

    private final OrderApplicationService orderApplicationService;
    private final PaymentApplicationService paymentApplicationService;
    private final NotificationApplicationService notificationApplicationService;

    public OrderInfo placeOrder(CreateOrderCommand command) {
        OrderInfo info = orderApplicationService.placeOrder(command);
        paymentApplicationService.processPayment(info.getOrderId(), command.getAmount());
        notificationApplicationService.sendOrderConfirm(info.getOrderId());
        return info;
    }
}
```

Facade도 응용 계층이다. 다만 단일 도메인이 아니라 **여러 도메인을 조율**한다.

```
Controller
    ↓
Facade                  ← 여러 도메인 조율 (응용 계층 상단)
    ↓
ApplicationService     ← 단일 도메인 흐름 (응용 계층 하단)
    ↓
Domain (Entity, Domain Service)
```

Facade를 두는 또 다른 효과는 **순환참조 방지**다. 각 응용 서비스는 자기 도메인만 알고, 다른 응용 서비스를 모른다. 조율은 Facade가 담당하므로 단방향 흐름이 유지된다.

---

## 9. 바운디드 컨텍스트 - 큰 도메인을 나누는 방법

여기서부터는 DDD의 **전략적 패턴**이다. 시스템이 커질 때 어떻게 나눌 것인가의 문제다.

### 같은 용어, 다른 의미

도메인이 커지면 **같은 용어가 컨텍스트마다 다른 의미**를 가진다. "상품(Product)"이라는 단어만 봐도:

```
카탈로그 컨텍스트 → "어떤 상품이 있는가" (진열/판매)
                    name, category, description, images

재고 컨텍스트     → "얼마나 있는가" (물류 관리)
                    productId, stockQuantity, warehouseLocation

주문 컨텍스트     → "얼마에 샀는가" (거래 기록)
                    productId, priceAtOrder, quantity

배송 컨텍스트     → "어떻게 보내는가" (물류 처리)
                    productId, weight, volume, packaging
```

모든 정보를 하나의 `Product`에 담으면 거대한 누더기 클래스가 된다.

```java
public class Product {
    // 카탈로그 필드들
    private String name;
    private Category category;
    private List<Image> images;
    // 재고 필드들
    private int stockQuantity;
    private String warehouseLocation;
    // 주문 필드들
    private Money price;
    // 배송 필드들
    private Weight weight;
    private Volume volume;
    // ... 수십 개의 필드
}
```

카탈로그 변경이 배송 로직에 영향을 주고, 각 팀이 같은 클래스를 수정하며 충돌하고, 어떤 필드가 어떤 목적인지 불명확해진다.

### 바운디드 컨텍스트는 다른 팀의 서비스

**바운디드 컨텍스트(Bounded Context)** 는 도메인 모델이 적용되는 명시적 경계다. 쉽게 말해 **"다른 팀의 서비스"** 라고 이해하면 된다.

```
카탈로그 팀 → 카탈로그 컨텍스트 (상품 등록/수정/조회)
주문 팀     → 주문 컨텍스트     (주문 생성/취소/조회)
배송 팀     → 배송 컨텍스트     (배송 처리/추적)
```

각 컨텍스트는 자기에게 필요한 필드만 가진 **독립된 Product 클래스**를 만든다.

```
[카탈로그]           [주문]              [배송]
Product              Product             Product
 - name               - id                - id
 - category           - price             - weight
 - images             - quantity          - volume
```

이렇게 분리하면 카탈로그 팀이 변경해도 배송 팀에 영향이 없고, 각 컨텍스트가 자기에게 맞는 아키텍처를 선택할 수 있다(단순 CRUD는 트랜잭션 스크립트, 복잡한 도메인은 DDD + 헥사고날 등). 변경 영향 범위가 명확해진다.

### 구현 단계

바운디드 컨텍스트를 코드로 표현하는 방식은 규모에 따라 다르다.

**1단계: 패키지 분리 (소규모 모놀리스)**

같은 프로젝트 안에서 패키지로 구분한다.

```
com.myshop
├── catalog
│   └── domain/Product   ← 카탈로그용
├── order
│   └── domain/Product   ← 주문용 (같은 이름, 다른 클래스)
└── shipping
    └── domain/Product   ← 배송용
```

같은 이름의 클래스가 여러 패키지에 존재할 수 있다. 통합은 직접 메서드 호출.

**2단계: 멀티 모듈**

Gradle/Maven 모듈로 분리해서 의존성을 명시적으로 관리한다. 모듈 경계를 컴파일 타임에 강제할 수 있다.

**3단계: 마이크로서비스 (대규모)**

각 컨텍스트가 별도 서버, 별도 DB로 분리. 통합은 REST API나 메시지 큐.

**처음부터 MSA로 갈 필요는 없다.** 경계만 잘 그어두면 나중에 분리하기 쉬워진다. 점진적 진화가 안전하다.

### 컨텍스트 간 통합

다른 이름으로 클래스를 분리해도 **비즈니스 흐름상 데이터 교환은 피할 수 없다.**

```
주문할 때 → 카탈로그에서 현재 가격 가져와야 함
주문 완료 → 재고 컨텍스트에 차감 알려야 함
```

통합 방법은 구조에 따라 다르다.

**모놀리스**: 직접 메서드 호출
```java
CatalogProduct catalogProduct = catalogProductRepository.findById(productId);
OrderProduct orderProduct = new OrderProduct(
    catalogProduct.getId(),
    catalogProduct.getPrice()  // 필요한 것만
);
```

**MSA**: REST API
```java
CatalogProductResponse response = catalogClient.getProduct(productId);
OrderProduct orderProduct = new OrderProduct(
    response.getId(),
    response.getPrice()
);
```

**MSA, 비동기**: 메시지 큐
```java
@KafkaListener(topics = "order-placed")
public void handle(OrderPlacedEvent event) {
    Inventory inventory = inventoryRepository.findByProductId(event.getProductId());
    inventory.decrease(event.getQuantity());
}
```

### 컨텍스트 간 관계 패턴

컨텍스트 간 관계에는 여러 패턴이 있다.

**공유 커널 (Shared Kernel)** 은 어디서나 의미가 동일한 기반 타입을 공유하는 패턴이다.

```
Money    → "금액" 어느 컨텍스트에서도 동일
MemberId → "회원 ID" 어디서나 동일
```

이런 건 컨텍스트마다 따로 만들 필요 없다. 하지만 **Product, Order 같은 핵심 도메인은 공유 커널에 넣으면 안 된다.** 컨텍스트마다 의미가 다르기 때문이다. 공유 커널의 단점은 변경 시 양쪽 팀 모두에 영향을 주므로 협의가 필요하다는 점이다.

**고객-공급자 (Customer-Supplier)** 는 사내 팀 간 협력 관계다. 공급자가 고객의 요구사항을 반영해준다.

```
주문 팀(고객): "회원 등급 API 만들어주세요"
회원 팀(공급자): "다음 스프린트에 넣을게요"
→ 협상 가능
```

핵심은 **고객의 요구가 공급자의 개발 계획에 반영**된다는 점이다.

**준수자 (Conformist)** 는 공급자가 고객 요구를 신경 쓰지 않고, 고객은 그냥 따를 수밖에 없는 관계다. 주로 **외부 시스템**(카카오 로그인, PG사, 레거시)이 여기 해당한다. 카카오에게 "필드명 바꿔주세요"라고 할 수 없다.

**ACL (Anti-Corruption Layer, 충돌 방지 계층)** 은 외부 모델이 내 도메인을 오염시키지 못하게 변환 계층을 두는 패턴이다. 준수자 관계에서는 **반드시 ACL을 적용**해야 한다.

```java
@Component
public class KakaoAuthAdapter {
    public Member toMember(KakaoLoginResponse response) {
        return Member.of(
            response.getId(),
            response.getKakaoAccount().getEmail()
            // 카카오 전용 필드는 버림
        );
    }
}
```

카카오 API가 바뀌면 Adapter만 수정하면 된다. Member 도메인은 영향 없다. ACL은 거의 모든 컨텍스트 통합에서 필수적이다.

**공개 호스트 (Open Host Service)** 는 API를 공개해서 여러 컨텍스트가 사용하게 하는 패턴이다. 뷔페처럼 모든 데이터를 차려놓으면 각 컨텍스트가 ACL로 필요한 것만 꺼내간다.

```
카탈로그 API (모든 정보 제공)
       ↓
   ACL로 변환
       ↓
주문 컨텍스트: 가격만 사용
재고 컨텍스트: 재고만 사용
배송 컨텍스트: 무게/부피만 사용
```

처음의 "뒤섞인 Product"와 다른 점은, **공개 호스트는 API 응답은 모든 정보를 제공하되 받는 쪽이 ACL로 자기 도메인 모델을 별도로 만든다**는 것이다. 각 컨텍스트의 독립성은 유지된다.

### 정리

바운디드 컨텍스트는 도메인이 커질 때 **필수 개념**이다. 단, 작은 프로젝트에서는 굳이 명시할 필요까지는 없다. 도메인이 한 사람의 머릿속에 다 들어간다면 컨텍스트는 하나여도 된다.

핵심 원칙: 핵심 도메인은 컨텍스트마다 독립 클래스, 공유 커널은 기반 타입(Money, Id)만, 경계 넘을 때는 항상 ACL, 외부 시스템은 반드시 ACL.

장점은 팀 독립성, 변경 영향 제한, 적합한 아키텍처 선택이지만, 단점도 명확하다. 통합 복잡도가 늘고, 데이터 동기화 이슈가 생기며, 같은 개념을 여러 컨텍스트에서 다른 모델로 표현해야 한다(코드 중복).

---

## 10. 도메인 이벤트 - 결합도 낮추는 방법

여러 시스템이 얽히면 강결합이 생긴다. 주문 취소 시 환불을 처리하는 예를 보자.

```java
@Service
@Transactional
public class CancelOrderService {

    private final RefundService refundService;

    public void cancel(OrderNo orderNo) {
        Order order = orderRepository.findById(orderNo).orElseThrow();
        order.cancel();
        refundService.refund(order.getPaymentId());  // 외부 시스템 호출
    }
}
```

문제는 여러 가지다. 외부 API에 의존하므로 환불 API가 느리면 주문 취소도 느려진다. 한 트랜잭션에 외부 호출이 있어서 환불 실패 시 주문 취소도 롤백된다(정말 그래야 하는가?). 두 작업이 강한 일관성을 요구하게 되고, 주문 도메인이 환불 시스템을 알아야 한다.

### 이벤트로 결합도 낮추기

도메인 이벤트는 **이미 발생한 사실**을 표현하는 객체다. 과거형으로 이름 짓는다.

```java
public class OrderCanceledEvent {
    private final OrderNo orderNo;
    private final PaymentId paymentId;
    private final LocalDateTime occurredOn;
}
```

도메인에서 이벤트를 발행하고, 별도 핸들러가 이벤트를 받아서 처리한다.

```java
public class Order {
    public void cancel() {
        verifyCancelable();
        this.state = OrderState.CANCELED;
        registerEvent(new OrderCanceledEvent(this.id, this.paymentId));
    }
}

@Component
public class RefundEventHandler {
    @EventListener
    public void handle(OrderCanceledEvent event) {
        refundService.refund(event.getPaymentId());
    }
}
```

이제 주문 도메인은 환불 시스템을 모른다(결합도 감소). 환불 외에 이메일, SMS, 통계 등 다른 핸들러를 자유롭게 추가할 수 있다. 환불 실패가 주문 취소에 영향 없게 처리할 수 있다.

### 동기 vs 비동기 - 무엇을 선택하나

이벤트 처리 방식을 결정하는 기준은:

> **"이벤트 처리가 메인 로직과 함께 성공/실패해야 하는가?"**

YES라면 동기 처리(`@EventListener`)다. 예를 들어 주문 후 적립금 차감은 반드시 함께 처리되어야 한다.

NO라면 비동기 처리다. 주문 후 이메일 발송은 이메일이 실패해도 주문은 성공해야 한다.

### 트랜잭션 시점 고려

동기 처리에서 주의할 점이 있다. 일반 `@EventListener`는 **같은 트랜잭션 안**에서 실행된다.

```java
@Transactional
public void cancel(OrderNo orderNo) {
    order.cancel();  // 이벤트 발행 → 핸들러 즉시 실행
    // 아직 메인 커밋 전!
}

@EventListener
public void handle(OrderCanceledEvent event) {
    refundService.refund(...);
    // 이 시점에 메인 트랜잭션이 롤백되면?
    // 환불은 이미 처리됨!
}
```

해결책은 `@TransactionalEventListener(AFTER_COMMIT)` 을 사용하는 것이다. 메인 트랜잭션 커밋이 성공한 후에만 핸들러가 실행된다.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handle(OrderCanceledEvent event) {
    refundService.refund(event.getPaymentId());
}
```

### 비동기 처리 방식

비동기는 여러 단계가 있다. **로컬 비동기 (`@Async`)** 는 같은 JVM의 별도 스레드에서 실행한다. 간단하지만 서버 재시작 시 미처리 이벤트 손실 위험이 있다.

**메시지 큐 (Kafka, RabbitMQ)** 는 외부 메시징 시스템을 활용한다. 안정적이고 MSA에 적합하지만 인프라 추가가 필요하다.

**Outbox 패턴** 은 DB와 이벤트 발행의 원자성을 보장한다. 가장 안전하지만 구현이 복잡하다. 메시지 발행 실패에 대비해 이벤트를 DB에 저장하고 별도 스케줄러가 메시지 큐로 발행한다. DB와 메시지 큐 사이의 데이터 정합성이 보장된다.

### 도메인 이벤트의 한계

이벤트 기반 설계는 만능이 아니다. 코드가 분산되어 흐름 파악이 어려워진다. "이 이벤트는 누가 받지?" 추적이 힘들고, 디버깅이 복잡하다. 일관성 보장도 어려워진다(최종 일관성을 받아들여야 함).

그래서 **모든 것을 이벤트로 만들 필요는 없다.** 동기 직접 호출로 충분한 건 굳이 이벤트로 분리하지 말자.

### 정리

도메인 이벤트는 **결합도를 낮추는 강력한 도구**다. 한 트랜잭션에서 여러 애그리거트 변경이 필요할 때, 외부 시스템 호출(환불, 이메일 등)이 있을 때, 메인 로직과 부수 처리를 분리하고 싶을 때, 컨텍스트 간 통합이 필요할 때 적극 고려한다.

핵심 원칙: 이벤트 이름은 과거형, 도메인 객체가 이벤트 발행, 메인 트랜잭션 커밋 후 처리 권장, 컨슈머는 멱등성 보장, 이벤트 카탈로그 문서화.

---

## 11. CQRS - 명령과 조회의 분리

CQRS는 DDD에 속한 패턴은 아니지만 자주 함께 쓰인다. 도메인 모델이 풍부해질수록 자연스럽게 마주치는 문제 때문이다.

### 단일 모델의 단점

지금까지 우리는 하나의 도메인 모델로 변경(Command)과 조회(Query)를 모두 처리해왔다.

```java
@Entity
public class Order {
    private OrderState state;
    private List<OrderLine> orderLines;
    private ShippingInfo shippingInfo;

    public void cancel() { ... }
    public void changeShippingInfo(...) { ... }
}
```

사실 단순 조회는 Lazy Loading + Info 변환으로 대부분 해결된다. 실제로 **단일 애그리거트 조회**라면 CQRS까지 갈 필요 없다.

문제는 **단일 애그리거트로 안 끝나는 조회**다.

```
주문 목록 페이지에 필요한 정보:
- 주문번호           (Order)
- 주문 상태          (Order)
- 주문자 이름        (Member - 다른 애그리거트!)
- 첫 상품 이미지     (Product - 다른 애그리거트!)
- 카테고리명         (Category - 다른 애그리거트!)
```

도메인 모델로 처리하면 N+1 폭발이다.

```java
List<Order> orders = orderRepository.findByMemberId(memberId);
return orders.stream().map(order -> {
    Member member = memberRepository.findById(order.getOrdererId());  // N+1
    Product product = productRepository.findById(order.getFirstProductId());  // N+1
    return OrderInfo.from(order, member, product);
}).toList();
```

게다가 도메인 모델과 조회 모델은 **목적 자체가 충돌**한다.

```
도메인 모델 (Command)            조회 모델 (Query)
- 비즈니스 규칙 중심              - 화면 표시 중심
- 트랜잭션 안전성                 - 빠른 응답 속도
- 정규화                          - 역정규화
- 캡슐화                          - 다양한 데이터 조합
```

한 모델로 둘 다 처리하려고 하면 Fetch Join이 늘어나고 Entity가 누더기가 된다. 단순 UI 변경(조회 필드 추가)이 핵심 결제/주문 로직이 포함된 클래스를 수정하게 만든다.

### CQRS - 책임 분리

CQRS(Command Query Responsibility Segregation)는 **명령과 조회의 책임을 분리**하는 패턴이다.

```
Command (CUD): 도메인 모델, 비즈니스 규칙, 정합성
Query (R):     조회 전용 DTO, 빠른 응답, 화면 최적화
```

각자 다른 모델을 쓴다.

```java
// Command - 도메인 모델
@Entity
public class Order {
    public void cancel() { ... }
}

// Query - 조회 전용 DTO
public class OrderSummary {
    private final Long orderId;
    private final String state;
    private final String ordererName;
    private final BigDecimal totalAmount;
}
```

서비스도 분리한다.

```java
@Service
@Transactional
public class OrderService {  // Command
    public void cancel(OrderNo orderNo) { ... }
}

@Service
@Transactional(readOnly = true)  // 읽기 전용 - 변경 감지 스킵, 성능 향상
public class OrderQueryService {  // Query
    public Page<OrderSummary> search(SearchCondition cond, Pageable pageable) { ... }
}
```

핵심 원칙은 **Service와 QueryService 간 직접 참조 금지**다. 두 서비스를 호출해야 하면 Facade를 통한다.

### 도입 임계치

**모든 도메인에 CQRS를 기계적으로 적용하면 개발 생산성이 박살 난다.**

도입 단계를 정리하면:

**초기 MVP**: 통합 CRUD 모델로 충분하다. TPS가 낮고 단순 CRUD일 때다.

**성장기**: 소스 코드 레벨 CQRS를 도입한다. 신호는 명확하다. 하나의 서비스 클래스 코드가 500줄을 돌파할 때, N+1 해결을 위해 Fetch Join이 스파게티처럼 꼬일 때, CUD로 발생한 쓰기 락이 무거운 R 때문에 유지되어 트래픽 병목이 올 때다.

**대규모**: 물리적 CQRS(Write DB / Read DB 분리 + Kafka)로 간다. 단일 DB의 CPU가 조회 쿼리 때문에 80% 이상 상시 도달할 때, 명령과 조회의 트래픽 비율이 1:9 이상으로 극단적으로 벌어질 때다. 이 단계는 동기화 지연(Eventual Consistency) 감수가 필요하다.

### 전략적 최소 설계

당장 트래픽이 크지 않더라도, **예약/결제/주문** 같이 기획 변경이 잦고 정합성이 중요한 **핵심 비즈니스 도메인**은 처음부터 소스 코드 레벨 CQRS를 적용하는 게 좋다.

나중에 통합 모델을 CQRS로 갈아엎으려면 꼬여있는 영속성 컨텍스트와 의존성을 다 끊어내야 하는데, 이 **전환 비용(Migration Cost)** 이 어마어마하다. 리팩토링 난이도가 최상에 달한다. 미리 분리해두는 게 결과적으로 싸다.

### 단계별 적용 흐름

```
1단계: 단일 모델 + Lazy + Info 변환
        - 대부분의 단순 조회는 여기서 충분

2단계: 소스 코드 레벨 CQRS
        - Service / QueryService 분리
        - Repository / QueryRepository 분리
        - 같은 DB 사용

3단계: Read Replica 활용 (동일 구조)
        - DB 자동 복제로 부하 분산

4단계: 물리적 CQRS + 역정규화
        - Write DB / Read DB 분리
        - 역정규화된 테이블로 조회 최적화
        - 이벤트로 동기화 (최종 일관성)
```

각 단계는 점진적으로 적용하면 된다. 한 번에 4단계로 갈 필요 없다.

물리적 CQRS에서 한 가지 중요한 구분이 있다. **동일 구조 복제**(Master/Slave)는 DB binlog로 자동 복제되어 애플리케이션이 신경 쓸 게 없다. 단순히 읽기 부하를 분산하는 효과지만 조회 시 여전히 JOIN이 필요하다. 반면 **역정규화 Read DB**는 구조 자체가 다르므로 DB 자동 복제가 불가능하고, 애플리케이션이 동기화 로직(이벤트, CDC, 배치)을 작성해야 한다. 그 대신 조회 시 조인 없이 단일 테이블로 끝나므로 폭발적인 성능 향상이 있다.

### CQRS의 장단점

장점은 명확하다. Command와 Query가 각자 최적화 가능해진다. Command는 비즈니스 정합성과 도메인 규칙에 집중하고, Query는 QueryDSL 등으로 화면에 딱 맞는 DTO 최적화 조회가 가능하다. 도메인 모델이 조회 책임에서 해방되어 단순해진다. CUD와 R의 트랜잭션 범위가 강제 분리되어 DB 락 유지 시간이 단축된다. UI 변경 시 Query 영역만 수정하면 되니 유지보수성이 극대화된다.

단점도 있다. 같은 데이터를 두 가지 모델로 표현하니 코드 중복(Order + OrderSummary)이 생긴다. 명령과 조회가 찢어지면서 코드의 흐름을 한눈에 파악하기 힘든 파편화 현상이 생긴다. 물리적 분리 시 동기화 복잡도가 늘어난다. 학습 곡선도 있다. 단순 CRUD 도메인에 적용하면 클래스 파일만 많아지는 오버엔지니어링이 된다.

CQRS는 큰 무기지만 신중하게 써야 한다. 핵심은 **"정말로 두 모델이 필요한가"** 를 먼저 묻는 것이다.

---

## 마무리 - DDD를 어떻게 시작할까

지금까지 DDD의 11가지 핵심 개념을 살펴봤다. 정리하면:

```
[기반]
1. 유비쿼터스 언어 - 같은 용어로 말하기
2. Entity와 VO - 객체 모델링의 기본

[도메인 표현]
3. 도메인 모델 패턴 - 비즈니스 규칙은 도메인에
4. 4계층 아키텍처 - 구조의 큰 그림
5. DIP - 도메인 순수성 지키기

[관계와 경계]
6. 애그리거트 - 객체 관계 정리
7. 트랜잭션 경계 - 강한 vs 최종 일관성

[서비스 계층]
8. 응용 서비스 vs 도메인 서비스 - 흐름과 규칙 분리
   (Facade로 여러 도메인 조율)

[큰 도메인 처리]
9. 바운디드 컨텍스트 - 도메인 분할
10. 도메인 이벤트 - 결합도 낮추기
11. CQRS - 명령과 조회 분리
```

### 학습과 적용 우선순위

모든 개념을 한 번에 적용할 필요는 없다. 가치 큰 것부터 점진적으로 도입한다.

**필수로 모든 프로젝트에 적용해야 할 것들**은 유비쿼터스 언어, Value Object, 도메인 모델 패턴, 4계층 아키텍처, Application Service의 역할이다. 이 다섯 가지만 잘 지켜도 코드 품질이 크게 올라간다. 도입 비용은 거의 없으면서 효과는 크다.

**중규모 이상에서 권장하는 것들**은 애그리거트와 애그리거트 루트, Repository(DIP), Domain Service, Facade 패턴이다. 도메인 객체 관계가 복잡해지고 여러 도메인을 조율해야 할 때 진가를 발휘한다.

**큰 도메인 / 복잡도가 높을 때 도입할 것들**은 Bounded Context, 도메인 이벤트, CQRS다. 한 사람의 머릿속에 도메인이 다 들어가지 않을 때, 결합도를 낮춰야 할 때, 조회와 명령의 충돌이 심해질 때 필요하다.

**대규모 시스템의 고급 패턴**은 Saga 패턴, 물리적 CQRS, Event Sourcing 등이다. 분산 환경에서 일관성과 성능을 모두 챙기려면 마주치게 된다.

### 프로젝트 설계 순서

신규 프로젝트를 시작할 때는 다음 순서로 결정한다.

먼저 **도메인 분석**이다. 비즈니스 요구사항을 분석하고, 도메인 전문가와 용어를 정리하고(유비쿼터스 언어), 핵심 도메인과 지원 도메인을 구분하고, 도메인 모델(Entity, VO)을 도출한다.

다음으로 **경계 설정**이다. 바운디드 컨텍스트를 식별하고, 분리 방식을 결정(패키지/모듈/서비스)하고, 컨텍스트 간 관계를 정의(ACL, 공개 호스트 등)하고, 컨텍스트 맵을 작성한다.

그 다음 **애그리거트 설계**다. 애그리거트 경계를 결정하고, 루트를 선정하고, 트랜잭션 경계가 애그리거트 단위인지 확인하고, 다른 애그리거트는 ID 참조로 검토하고, 일관성 요구(강한 vs 최종)를 결정한다.

이어서 **계층 구조 설정**이다. 4계층 아키텍처를 적용하고, Facade / ApplicationService / DomainService의 역할을 분담하고, Repository는 도메인에 인터페이스, 인프라에 구현(DIP)으로 두고, Command / Query 분리 여부를 결정한다(핵심 도메인은 권장).

마지막으로 **통합 전략과 점진적 발전 계획**이다. 컨텍스트 간 통합 방법(메서드 호출 / API / 이벤트)을 정하고, 도메인 이벤트 도입 여부와 동기/비동기 처리 기준을 명확히 한다. MVP는 단순하게 가되, 핵심 도메인은 처음부터 CQRS 베이스라인을 잡고, 트래픽/규모 증가에 따른 다음 단계 계획을 세워둔다.

### DDD의 본질을 잊지 말 것

마지막으로 강조하고 싶은 건 **DDD는 패턴이 아니라 사고방식**이라는 점이다.

첫째, **도메인이 먼저, 기술은 나중**이다. 비즈니스 모델링을 충분히 한 후에 기술을 결정한다.

둘째, **가치 큰 것부터 점진적으로** 도입한다. 유비쿼터스 언어, VO, 도메인 모델 패턴부터 시작하고, Aggregate나 Bounded Context 같은 큰 개념은 필요할 때 도입한다.

셋째, **도구가 아닌 사고방식**이다. Spring/JPA는 도구일 뿐이고, 다른 환경(Node.js, Python 등)에서도 동일 원리가 적용된다.

DDD의 어떤 패턴을 적용하느냐보다, **도메인을 잘 이해하고 표현하려는 노력**이 더 중요하다. 패턴은 그 노력을 도와주는 도구일 뿐이다.

다음 파트에서는 이 원리들을 **Spring/JPA로 구체적으로 어떻게 구현**하는지 다룬다.


---
---

# PART 2. Spring/JPA 구현 - 코드 템플릿


> *DDD Start!* 1~11장 중 **Spring/JPA 구현 디테일**을 모아서 정리
> 1부 [DDD 순수 개념 가이드]의 원리를 실제 코드로 어떻게 구현하는지 다룹니다.

이 문서는 **실무 적용 코드 템플릿, 핵심 어노테이션, 주의사항, 대안 비교** 위주로 정리되어 있습니다.

---

## 1. JPA Entity 매핑 패턴

### 1.1 기본 Entity 매핑

```java
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // 기본 생성자 protected
@Getter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)  // String으로 저장 (Ordinal X)
    @Column(nullable = false)
    private OrderState state;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    // 비즈니스 로직 (도메인 모델 패턴)
    public void cancel() {
        if (!state.isCancelable()) {
            throw new IllegalStateException("취소 불가");
        }
        this.state = OrderState.CANCELED;
    }
}
```

**핵심 어노테이션**:
- `@NoArgsConstructor(access = PROTECTED)` - JPA는 리플렉션으로 접근 가능, 외부 직접 생성 차단
- `@Enumerated(EnumType.STRING)` - Enum 순서 변경에 안전
- `@Column(nullable = false)` - DB 제약을 명시적으로

### 1.2 Value Object 매핑 (@Embeddable)

```java
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
public class Money {

    @Column(name = "amount")
    private BigDecimal amount;

    public Money(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("금액은 0 이상");
        }
        this.amount = amount;
    }

    // VO는 불변 - 새 객체 반환
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money multiply(int multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)));
    }
}
```

```java
@Entity
public class Order {

    @Embedded
    private Money totalAmount;

    @Embedded
    private ShippingInfo shippingInfo;
}
```

**핵심 원칙**:
- VO는 반드시 **불변** (모든 필드 final이 이상적, JPA 때문에 protected 빈 생성자 필요)
- `equals/hashCode` 구현 (값 기반 비교)
- 변경은 새 객체 반환

### 1.3 Value Object ID 매핑

```java
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
public class OrderNo implements Serializable {

    @Column(name = "order_number")
    private String number;

    public OrderNo(String number) {
        if (!isValidFormat(number)) {
            throw new IllegalArgumentException("주문번호 형식 오류");
        }
        this.number = number;
    }

    public boolean is2ndGeneration() {
        return number.startsWith("N");  // 식별자도 도메인 로직 가능
    }
}
```

```java
@Entity
public class Order {
    @EmbeddedId
    private OrderNo number;  // VO를 ID로 사용
}
```

### 1.4 컬렉션 매핑 - @Entity 권장

**@ElementCollection은 변경 시 전체 삭제 후 재삽입되는 치명적 단점이 있어, 실무에서는 @Entity로 매핑하는 게 일반적이다.**

```java
@Entity
public class Order {

    @OneToMany(
        mappedBy = "order",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @BatchSize(size = 100)  // N+1 방지
    private List<OrderLine> orderLines = new ArrayList<>();

    // 외부에서는 Unmodifiable로 반환
    public List<OrderLine> getOrderLines() {
        return Collections.unmodifiableList(orderLines);
    }

    // 변경은 메서드를 통해서만
    public void addOrderLine(OrderLineRequest request) {
        OrderLine line = OrderLine.of(request);
        line.assignOrder(this);
        this.orderLines.add(line);
    }
}
```

```java
@Entity
@Table(name = "order_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)  // 반드시 LAZY
    @JoinColumn(name = "order_id")
    private Order order;

    @Embedded
    private Money price;

    private int quantity;

    // 패키지 레벨 (default) - Order에서만 호출
    static OrderLine of(OrderLineRequest request) {
        OrderLine line = new OrderLine();
        line.price = request.getPrice();
        line.quantity = request.getQuantity();
        return line;
    }

    // 패키지 레벨 - 양방향 연관관계
    void assignOrder(Order order) {
        this.order = order;
    }
}
```

### 1.5 @ElementCollection이 적합한 경우

단순한 값 컬렉션에만 사용:

```java
// ✅ 적합 - 단순 태그
@ElementCollection
@CollectionTable(name = "member_tags")
@Column(name = "tag")
private Set<String> tags;

// ✅ 적합 - 즐겨찾기 ID
@ElementCollection
private Set<ProductId> favoriteProductIds;
```

### 1.6 매핑 결정 매트릭스

| 상황 | 어노테이션 | 비고 |
|---|---|---|
| 단순 컬럼 | `@Column` | 기본 |
| Enum | `@Enumerated(EnumType.STRING)` | Ordinal 금지 |
| VO 임베디드 | `@Embeddable` + `@Embedded` | 같은 테이블 |
| VO ID | `@Embeddable` + `@EmbeddedId` | 의미 있는 ID |
| VO ↔ 컬럼 변환 | `@Converter` | 단일 컬럼 변환 |
| 자식 엔티티 컬렉션 | `@OneToMany` + `@Entity` | **권장** |
| 단순 값 컬렉션 | `@ElementCollection` | 변경 빈도 낮을 때만 |
| 다른 애그리거트 참조 | VO ID 필드만 | `@ManyToOne` 금지 |

---

## 2. Repository 구현

### 2.1 Spring Data JPA 기본

```java
// 도메인 영역 - 인터페이스만
package com.shop.order.domain;

public interface OrderRepository extends JpaRepository<Order, OrderNo> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByOrdererIdOrderByOrderedAtDesc(MemberId ordererId);
}
```

**핵심 원칙**:
- 인터페이스는 **도메인 영역**에
- 구현체는 Spring Data JPA가 자동 생성
- 메서드 이름으로 쿼리 자동 생성 (`findByXxx`)

### 2.2 애그리거트 단위 Repository

```java
// ✅ 애그리거트 루트만
public interface OrderRepository extends JpaRepository<Order, OrderNo> { ... }

// ❌ 자식 엔티티용은 만들지 않음
// public interface OrderLineRepository ... { ... }
```

### 2.3 영속성 전파

```java
@Entity
public class Order {

    @OneToMany(
        mappedBy = "order",
        cascade = CascadeType.ALL,    // 영속성 전파
        orphanRemoval = true           // 고아 객체 자동 삭제
    )
    private List<OrderLine> orderLines;
}
```

- `cascade = ALL`: Order 저장/삭제 시 OrderLine도 함께
- `orphanRemoval = true`: 컬렉션에서 제거된 OrderLine은 DB에서도 삭제

---

## 3. 응용 계층 구현

### 3.1 Facade + ApplicationService 패턴

```java
// 1. Controller
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request) {
        OrderInfo info = orderFacade.placeOrder(request.toCommand());
        return ResponseEntity.ok(OrderResponse.from(info));
    }
}
```

```java
// 2. Facade - 여러 도메인 조율
@Service
@Transactional
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderApplicationService orderApplicationService;
    private final PaymentApplicationService paymentApplicationService;
    private final NotificationApplicationService notificationApplicationService;

    public OrderInfo placeOrder(CreateOrderCommand command) {
        OrderInfo info = orderApplicationService.placeOrder(command);
        paymentApplicationService.processPayment(info.getOrderId(), command.getAmount());
        notificationApplicationService.sendOrderConfirm(info.getOrderId());
        return info;
    }
}
```

```java
// 3. ApplicationService - 단일 도메인 흐름
@Service
@Transactional
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderRepository orderRepository;

    public OrderInfo placeOrder(CreateOrderCommand command) {
        Order order = Order.place(
            command.getUserId(),
            command.getOrderLines()
        );
        Order saved = orderRepository.save(order);
        return OrderInfo.from(saved);
    }
}
```

```java
// 4. Domain - 비즈니스 규칙
public class Order {
    public static Order place(Long userId, List<OrderLine> lines) {
        validateLines(lines);
        Order order = new Order();
        order.userId = userId;
        order.state = OrderState.PAYMENT_WAITING;
        lines.forEach(order::addOrderLine);
        return order;
    }
}
```

### 3.2 트랜잭션 관리

```java
// Command 서비스
@Service
@Transactional  // 클래스 레벨
public class OrderApplicationService { ... }

// Query 서비스
@Service
@Transactional(readOnly = true)  // 변경 감지 스킵, 성능 최적화
public class OrderQueryService { ... }
```

### 3.3 계층 간 데이터 변환

```
Request → Command → [도메인] → Info → Response
   ↑         ↑                   ↑        ↑
API 입력  응용 입력           응용 출력  API 출력
```

```java
// Request - JSON 역직렬화 대상
@Getter
public class OrderRequest {
    @NotNull private Long userId;
    @NotEmpty private List<OrderLineRequest> orderLines;
    private String requestId;  // API 추적용
    private String clientVersion;

    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(userId, orderLines);
    }
}

// Command - 도메인 작업용 (불변)
@Getter
@RequiredArgsConstructor
public class CreateOrderCommand {
    private final Long userId;
    private final List<OrderLineRequest> orderLines;
    // requestId, clientVersion 같은 API 전용 필드 없음
}

// Info - 응용 출력 (도메인 노출 차단)
@Getter
@RequiredArgsConstructor
public class OrderInfo {
    private final Long orderId;
    private final String state;
    private final BigDecimal totalAmount;

    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getState().name(),
            order.getTotalAmount().getAmount()
        );
    }
}

// Response - API 출력
@Getter
@RequiredArgsConstructor
public class OrderResponse {
    private final Long orderId;
    private final String orderStatus;
    private final BigDecimal amount;

    public static OrderResponse from(OrderInfo info) {
        return new OrderResponse(
            info.getOrderId(),
            info.getState(),
            info.getTotalAmount()
        );
    }
}
```

### 3.4 검증 위치

| 검증 종류 | 위치 | 방법 |
|---|---|---|
| **형식 검증** | 표현 영역 | `@Valid`, `@NotNull`, `@Email` 등 |
| **비즈니스 규칙** | 응용 또는 도메인 | Service 또는 Entity 메서드 |
| **도메인 불변식** | 도메인 영역 | Entity/VO 생성자 |

```java
// 형식 검증 (Bean Validation)
@PostMapping
public ResponseEntity<...> create(@RequestBody @Valid OrderRequest request) { ... }

// 비즈니스 규칙 검증
@Service
@Transactional
public class JoinService {
    public void join(JoinRequest req) {
        if (memberRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateEmailException();
        }
        // ...
    }
}

// 도메인 불변식
public class Money {
    public Money(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("금액은 0 이상");
        }
        this.amount = amount;
    }
}
```

---

## 4. 동시성 제어 (락)

### 4.1 선점 잠금 (Pessimistic Lock)

```java
public interface OrderRepository extends JpaRepository<Order, OrderNo> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findByIdForUpdate(OrderNo orderNo);
}
```

```java
@Service
public class StartShippingService {

    @Transactional(timeout = 5)  // DB 무관하게 동작하는 타임아웃
    public void startShipping(OrderNo orderNo) {
        try {
            Order order = orderRepository.findByIdForUpdate(orderNo)
                .orElseThrow();
            order.startShipping();
        } catch (TransactionTimedOutException e) {
            throw new OrderLockException("잠시 후 다시 시도");
        }
    }
}
```

**중요**: `@QueryHint`의 `lock.timeout` 은 MySQL에서 동작 안 함.
**`@Transactional(timeout)`** 이 DB 무관하게 가장 호환성 좋음.

### 4.2 비선점 잠금 (Optimistic Lock)

```java
@Entity
public class Order {

    @Version  // JPA가 자동 관리
    private long version;
}
```

```java
@Service
@Transactional
public class CancelOrderService {

    public void cancel(OrderNo orderNo) {
        try {
            Order order = orderRepository.findById(orderNo).orElseThrow();
            order.cancel();
            // 트랜잭션 커밋 시 version 체크
        } catch (OptimisticLockingFailureException e) {
            throw new OrderConflictException("다른 사용자가 수정 중");
        }
    }
}
```

### 4.3 강제 버전 증가 (자식 변경 시 루트도)

```java
@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
Optional<Order> findByIdOptimistic(OrderNo orderNo);
```

### 4.4 오프라인 선점 잠금

```java
// 도메인 인터페이스
public interface LockManager {
    LockId tryLock(String type, String id) throws LockException;
    void checkLock(LockId lockId) throws LockException;
    void releaseLock(LockId lockId);
    void extendLockExpiration(LockId lockId, long inc);
}
```

```java
// 락 테이블
CREATE TABLE locks (
    type            VARCHAR(255),
    id              VARCHAR(255),
    lock_id         VARCHAR(255),
    expiration_time DATETIME,
    PRIMARY KEY (type, id)
);
```

```java
@Component
@RequiredArgsConstructor
public class SpringLockManager implements LockManager {

    private final JdbcTemplate jdbcTemplate;
    private int lockTimeout = 5 * 60 * 1000;  // 5분

    @Override
    @Transactional
    public LockId tryLock(String type, String id) {
        // 만료되지 않은 락 체크
        List<Map<String, Object>> locks = jdbcTemplate.queryForList(
            "SELECT * FROM locks WHERE type = ? AND id = ? AND expiration_time > ?",
            type, id, new Timestamp(System.currentTimeMillis())
        );
        if (!locks.isEmpty()) {
            throw new AlreadyLockedException();
        }

        LockId lockId = new LockId(UUID.randomUUID().toString());
        Timestamp expTime = new Timestamp(System.currentTimeMillis() + lockTimeout);

        jdbcTemplate.update(
            "INSERT INTO locks (type, id, lock_id, expiration_time) VALUES (?, ?, ?, ?)",
            type, id, lockId.getValue(), expTime
        );
        return lockId;
    }
}

// 만료된 락 정리 스케줄러
@Component
@RequiredArgsConstructor
public class LockCleanupScheduler {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelay = 60_000)
    public void cleanExpiredLocks() {
        int deleted = jdbcTemplate.update(
            "DELETE FROM locks WHERE expiration_time < ?",
            new Timestamp(System.currentTimeMillis())
        );
        if (deleted > 0) log.info("만료된 락 {}개 정리", deleted);
    }
}
```

### 4.5 락 방식 비교

| | 선점 잠금 | 비선점 잠금 | 오프라인 선점 잠금 |
|---|---|---|---|
| 방식 | `FOR UPDATE` | `@Version` | 잠금 테이블 |
| 범위 | 트랜잭션 내 | 트랜잭션 내 | 트랜잭션 넘어서도 유지 |
| 충돌 감지 | 대기 (블로킹) | 커밋 시 예외 | 잠금 획득 시 예외 |
| 사용 | 짧은 작업, 충돌 빈번 | 충돌 드문 경우 | 조회 → 수정 → 저장 긴 흐름 |

---

## 5. 도메인 이벤트 처리

### 5.1 도메인에서 이벤트 발행 - AbstractAggregateRoot

```java
@Entity
public class Order extends AbstractAggregateRoot<Order> {

    public void cancel() {
        verifyCancelable();
        this.state = OrderState.CANCELED;
        registerEvent(new OrderCanceledEvent(this.id, this.paymentId));
    }
}
```

**save() 호출 시점에 이벤트 자동 발행**된다.

### 5.2 이벤트 핸들러

```java
@Component
public class OrderEventHandler {

    // 권장 패턴: 트랜잭션 커밋 후 + 별도 트랜잭션
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderCanceledEvent event) {
        refundService.refund(event.getPaymentId());
    }
}
```

### 5.3 비동기 이벤트 처리

```java
// 비동기 활성화
@Configuration
@EnableAsync
public class AsyncConfig { }

// 핸들러
@Component
public class OrderEventHandler {

    @Async  // 별도 스레드
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderCanceledEvent event) {
        refundService.refund(event.getPaymentId());
    }
}
```

**3개 어노테이션의 역할**:
- `@Async`: 별도 스레드 (응답 빠름)
- `@TransactionalEventListener(AFTER_COMMIT)`: 메인 커밋 후 (안전)
- `@Transactional(REQUIRES_NEW)`: 새 트랜잭션 (DB 작업 보장)

### 5.4 이벤트 처리 방식 선택

| 방식 | 어노테이션 | 적합한 경우 |
|---|---|---|
| 동기, 같은 트랜잭션 | `@EventListener` | 함께 성공/실패 필요 |
| 동기, 커밋 후 | `@TransactionalEventListener(AFTER_COMMIT)` | 메인 후 처리 |
| 비동기, 로컬 | `@Async + @TransactionalEventListener` | 단순 알림 |
| 비동기, 메시지 큐 | Kafka | MSA, 중요 이벤트 |
| 안정성 최우선 | Outbox 패턴 | 정합성 중요 |

---

## 6. Outbox 패턴 (메시지 큐 연동)

### 6.1 이벤트 저장소

```sql
CREATE TABLE event_store (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type      VARCHAR(255),
    payload         TEXT,
    published       BOOLEAN DEFAULT FALSE,
    published_at    DATETIME,
    created_at      DATETIME
);
```

### 6.2 Producer 측 - 같은 트랜잭션에 이벤트 저장

```java
@Service
@Transactional
@RequiredArgsConstructor
public class CancelOrderService {

    private final OrderRepository orderRepository;
    private final EventStoreRepository eventStore;

    public void cancel(OrderNo orderNo) {
        Order order = orderRepository.findById(orderNo).orElseThrow();
        order.cancel();

        // 같은 트랜잭션에 이벤트 저장 (원자성 보장)
        OrderCanceledEvent event = new OrderCanceledEvent(...);
        eventStore.save(EventEntity.from(event));
    }
}

// 별도 스케줄러가 메시지 큐로 발행
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final EventStoreRepository eventStore;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publishEvents() {
        List<EventEntity> unpublished = eventStore.findByPublishedFalse();
        for (EventEntity event : unpublished) {
            try {
                kafkaTemplate.send("events", event.getPayload());
                event.markAsPublished();
            } catch (Exception e) {
                log.error("발행 실패", e);  // 다음 스케줄에 재시도
            }
        }
    }
}
```

### 6.3 Consumer 측 - 멱등성 처리

```java
@Component
@RequiredArgsConstructor
public class OrderCanceledConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final RefundService refundService;

    @KafkaListener(topics = "order-canceled")
    public void handle(OrderCanceledEvent event) {
        // 멱등성 체크 (자기 DB에 처리 기록)
        if (processedEventRepository.existsByEventId(event.getId())) {
            return;
        }

        refundService.refund(event.getPaymentId());

        processedEventRepository.save(new ProcessedEvent(event.getId()));
    }
}
```

### 6.4 책임 경계

```
Producer 책임            Message Broker         Consumer 책임
─────────────────       ──────────────         ──────────────
DB + 이벤트 저장 원자성    메시지 보관/전달        메시지 수신
Kafka 발행 재시도                                멱등성 보장
event_store 관리                                재시도, DLQ
                                                자기 처리 기록

→ Producer는 "보냈다"까지, Consumer는 "처리했다"까지
→ event_store는 Producer 전용 (Consumer 접근 X)
```

---

## 7. 조회 최적화 (Spring Data JPA + QueryDSL)

### 7.1 단순 조회 - Spring Data JPA

```java
public interface OrderRepository extends JpaRepository<Order, OrderNo> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByOrdererIdOrderByOrderedAtDesc(MemberId ordererId);

    @Query("SELECT o FROM Order o WHERE o.state = :state")
    List<Order> findByState(@Param("state") OrderState state);
}
```

### 7.2 N+1 방지

```java
@Entity
public class Order {

    // 컬렉션은 LAZY + BatchSize
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<OrderLine> orderLines;

    // ToOne 관계도 명시적으로 LAZY (기본값이 EAGER라 위험)
    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;
}
```

### 7.3 동적 검색 - QueryDSL (권장)

```java
// DTO 정의
public class OrderSummary {
    private final Long orderId;
    private final String state;
    private final String ordererName;
    private final BigDecimal totalAmount;

    @QueryProjection  // QueryDSL이 생성자 매핑 자동화
    public OrderSummary(Long orderId, String state, String ordererName, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.state = state;
        this.ordererName = ordererName;
        this.totalAmount = totalAmount;
    }
}
```

```java
@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<OrderSummary> search(SearchCondition cond, Pageable pageable) {
        QOrder o = QOrder.order;
        QMember m = QMember.member;

        List<OrderSummary> content = queryFactory
            .select(new QOrderSummary(  // DTO 프로젝션
                o.id, o.state.stringValue(), m.name, o.totalAmount
            ))
            .from(o)
            .join(m).on(o.ordererId.eq(m.id))
            .where(
                ordererEq(cond.getOrdererId()),
                stateEq(cond.getState()),
                createdBetween(cond.getFrom(), cond.getTo())
            )
            .orderBy(toOrderSpecifier(pageable.getSort()))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory
            .select(o.count())
            .from(o)
            .where(ordererEq(cond.getOrdererId()), stateEq(cond.getState()))
            .fetchOne();

        return new PageImpl<>(content, pageable, total);
    }

    // 동적 조건 (null이면 자동 무시)
    private BooleanExpression ordererEq(String ordererId) {
        return StringUtils.hasText(ordererId)
            ? QOrder.order.ordererId.eq(ordererId) : null;
    }

    private BooleanExpression stateEq(OrderState state) {
        return state != null ? QOrder.order.state.eq(state) : null;
    }
}
```

### 7.4 페이징 주의사항

```java
// ❌ 1:N 컬렉션 + 페이징 - 메모리 페이징 발생
@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderLines")
Page<Order> findAll(Pageable pageable);

// ✅ BatchSize + 페이징
@OneToMany(mappedBy = "order")
@BatchSize(size = 100)
private List<OrderLine> orderLines;
```

### 7.5 조회 방법 비교

| 방법 | 적합한 경우 |
|---|---|
| Spring Data JPA 메서드 이름 | 단순 조회 (`findByXxx`) |
| `@Query` JPQL | 약간 복잡한 조회 |
| **QueryDSL + DTO 프로젝션** | **동적 쿼리, 복잡한 조인** |
| `@Subselect` | 통계/집계 뷰 |
| JdbcTemplate | 극한 성능 최적화 |

---

## 8. CQRS 구현 (소스 코드 레벨)

### 8.1 구조

```
Controller
    ↓
Facade
    ├─→ OrderService (Command)
    │       └─→ OrderRepository (Entity 반환)
    │
    └─→ OrderQueryService (Query)
            └─→ OrderQueryRepository (DTO 반환, QueryDSL)
```

### 8.2 Command 측

```java
// Command - 도메인 모델로 비즈니스 처리
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderInfo placeOrder(CreateOrderCommand command) {
        Order order = Order.place(...);
        Order saved = orderRepository.save(order);
        return OrderInfo.from(saved);
    }

    public void cancel(OrderNo orderNo) {
        Order order = orderRepository.findById(orderNo).orElseThrow();
        order.cancel();
    }
}

// Command Repository - Entity 반환
public interface OrderRepository extends JpaRepository<Order, OrderNo> {
    Optional<Order> findById(OrderNo orderNo);
}
```

### 8.3 Query 측

```java
// Query - DTO로 빠른 조회
@Service
@Transactional(readOnly = true)  // 변경 감지 스킵
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderQueryRepository orderQueryRepository;

    public Page<OrderSummary> search(SearchCondition cond, Pageable pageable) {
        return orderQueryRepository.search(cond, pageable);
    }
}

// Query Repository - DTO 반환 (위 7.3과 동일)
@Repository
public class OrderQueryRepository { ... }
```

### 8.4 Facade로 조율

```java
@Service
@Transactional
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;            // CUD
    private final OrderQueryService orderQueryService;  // R

    public OrderInfo placeOrder(CreateOrderCommand cmd) {
        return orderService.placeOrder(cmd);
    }

    public Page<OrderSummary> search(SearchCondition cond, Pageable pageable) {
        return orderQueryService.search(cond, pageable);
    }
}
```

**핵심 원칙**:
- `OrderService` 와 `OrderQueryService` 간 **직접 참조 금지**
- 두 서비스의 호출은 **Facade를 통해서만**

---

## 9. 물리적 CQRS (Write/Read DB 분리)

### 9.1 RoutingDataSource 설정

```java
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource writeDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:mysql://write-db:3306/myshop")
            .build();
    }

    @Bean
    public DataSource readDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:mysql://read-db:3306/myshop")
            .build();
    }

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("writeDataSource") DataSource writeDs,
            @Qualifier("readDataSource") DataSource readDs) {

        RoutingDataSource routing = new RoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("write", writeDs);
        dataSourceMap.put("read", readDs);

        routing.setTargetDataSources(dataSourceMap);
        routing.setDefaultTargetDataSource(writeDs);

        return routing;
    }
}
```

```java
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? "read" : "write";
    }
}
```

### 9.2 사용

```java
@Transactional  // readOnly = false → Write DB
public class OrderService { ... }

@Transactional(readOnly = true)  // → Read DB
public class OrderQueryService { ... }
```

### 9.3 역정규화 Read DB 동기화

```java
// Write 시점에 이벤트 발행
@Service
@Transactional
public class OrderService {

    public void placeOrder(...) {
        Order order = Order.place(...);
        orderRepository.save(order);  // Write DB
        eventPublisher.publish(new OrderPlacedEvent(order));
    }
}

// Read DB 동기화 (별도 트랜잭션)
@Component
public class ReadDbSyncHandler {

    @TransactionalEventListener(AFTER_COMMIT)
    @Transactional("readDbTransactionManager")
    public void syncToReadDb(OrderPlacedEvent event) {
        Member member = memberRepository.findById(event.getOrdererId());
        Product product = productRepository.findById(event.getFirstProductId());

        OrderSummary summary = new OrderSummary(
            event.getOrderId(),
            event.getState(),
            member.getName(),       // 역정규화 (회원 이름 복사)
            product.getName(),
            product.getImageUrl()
        );
        orderSummaryRepository.save(summary);
    }
}
```

---

## 10. 식별자 생성 패턴

### 10.1 4가지 방식

```java
// 1. Auto Increment (가장 흔함)
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

// 2. Sequence (Oracle, PostgreSQL)
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ord_seq")
@SequenceGenerator(name = "ord_seq", sequenceName = "order_seq")
private Long id;

// 3. UUID
@Id
private String id = UUID.randomUUID().toString();

// 4. 도메인 로직으로 생성 (의미 있는 ID)
public class OrderNoGenerator {
    public OrderNo generate() {
        String prefix = "ORD-";
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String sequence = nextSequence();
        return new OrderNo(prefix + yearMonth + "-" + sequence);
    }
}
```

### 10.2 선택 기준

| 방식 | 적합한 경우 |
|---|---|
| **Auto Increment** | 일반적인 경우, MySQL |
| **Sequence** | Oracle, PostgreSQL |
| **UUID** | 분산 환경, 충돌 방지 |
| **도메인 규칙** | 의미 있는 식별자 (주문번호 등) |

---

## 11. 권한 검사 패턴

### 11.1 URL 수준 권한 (Spring Security)

```java
@Configuration
public class SecurityConfig {
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
            .antMatchers("/admin/**").hasRole("ADMIN")
            .antMatchers("/orders/**").authenticated()
            .anyRequest().permitAll();
    }
}
```

### 11.2 기능 수준 권한 (응용 서비스)

```java
@Service
public class DeleteArticleService {

    public void delete(String userId, Long articleId) {
        Article article = articleRepository.findById(articleId).orElseThrow();

        if (!article.isWrittenBy(userId)) {
            throw new PermissionDeniedException();
        }
        articleRepository.delete(article);
    }
}
```

### 11.3 데이터 수준 권한 (도메인)

```java
public class Article {

    public void delete(String requesterId) {
        if (!writerId.equals(requesterId)) {
            throw new PermissionDeniedException();
        }
    }
}
```

---

## 12. 자주 쓰는 어노테이션 한눈에

### Entity 관련

```java
@Entity                                  // JPA 엔티티
@Table(name = "orders")                  // 테이블명 지정
@Embeddable                              // VO 임베디드
@Embedded                                // VO 필드
@EmbeddedId                              // VO ID
@Convert(converter = MoneyConverter.class) // 단일 컬럼 변환
@Enumerated(EnumType.STRING)             // Enum 문자열 저장
@NoArgsConstructor(access = PROTECTED)   // 기본 생성자 제한
@OneToMany(fetch = LAZY, cascade = ALL, orphanRemoval = true) // 자식 컬렉션
@ManyToOne(fetch = LAZY)                 // 단일 참조 (LAZY 명시 필수)
@BatchSize(size = 100)                   // N+1 방지
@Version                                 // 낙관적 락
```

### Service 관련

```java
@Service                                 // 빈 등록
@Transactional                           // 트랜잭션 (기본 REQUIRED)
@Transactional(readOnly = true)          // 읽기 전용 (변경 감지 스킵)
@Transactional(timeout = 5)              // 5초 타임아웃 (DB 무관)
@Transactional(propagation = REQUIRES_NEW) // 별도 트랜잭션
@RequiredArgsConstructor                 // 생성자 주입
```

### Repository 관련

```java
@Repository                              // 빈 등록 (선택)
@Lock(LockModeType.PESSIMISTIC_WRITE)   // 선점 잠금
@Query("...")                            // JPQL 쿼리
@QueryProjection                         // QueryDSL DTO 프로젝션
```

### 이벤트 관련

```java
@EventListener                           // 동기 이벤트 핸들러
@TransactionalEventListener(phase = AFTER_COMMIT) // 커밋 후 처리
@Async                                   // 비동기 실행
@EnableAsync                             // 비동기 활성화
@Scheduled(fixedDelay = 1000)            // 스케줄링
```

### 검증/보안

```java
@Valid                                   // 요청 검증
@NotNull, @NotEmpty, @Email, @Size       // Bean Validation
@PreAuthorize("hasRole('ADMIN')")        // 메서드 권한
@AuthenticationPrincipal                 // 인증 사용자 주입
```

---

## 13. 프로젝트 구조 템플릿

### 권장 패키지 구조

```
com.myshop.order
├── interfaces.api                ← 표현 계층
│   ├── OrderController
│   ├── OrderRequest
│   └── OrderResponse
│
├── application                   ← 응용 계층
│   ├── OrderFacade              ← 여러 도메인 조율
│   ├── OrderService             ← Command
│   ├── OrderQueryService        ← Query
│   ├── command
│   │   └── CreateOrderCommand
│   └── info
│       └── OrderInfo
│
├── domain                        ← 도메인 계층
│   ├── Order                    ← Entity
│   ├── OrderLine
│   ├── OrderState               ← Enum
│   ├── Money                    ← VO
│   ├── OrderNo                  ← VO ID
│   ├── OrderRepository          ← Port (인터페이스)
│   └── service
│       └── OrderPricingService  ← Domain Service
│
└── infrastructure                ← 인프라 계층
    ├── persistence
    │   └── OrderRepositoryImpl  ← Adapter
    └── external
        └── PaymentApiClient
```

### CQRS 적용 시 구조

```
application/
├── OrderFacade
├── command
│   ├── OrderService
│   └── CreateOrderCommand
└── query
    ├── OrderQueryService
    ├── OrderQueryRepository
    ├── condition
    │   └── OrderSearchCondition
    └── dto
        └── OrderSummary
```

---

## 14. 실무 의사결정 가이드

### 신규 프로젝트 시작 시

```
1. 도메인 분석 (1부 가이드 참고)
        ↓
2. 기본 구조 설정
   □ 4계층 아키텍처
   □ Facade + ApplicationService 패턴
   □ Repository (도메인 인터페이스 + 인프라 구현)
        ↓
3. Entity/VO 설계
   □ VO는 불변 (final, setter X)
   □ Entity는 도메인 모델 패턴 (비즈니스 로직 보유)
   □ 자식 컬렉션은 @OneToMany + @Entity
   □ ToOne 관계는 LAZY 명시
        ↓
4. 검증 위치
   □ 형식 검증: @Valid (표현 계층)
   □ 비즈니스 규칙: 응용 또는 도메인
        ↓
5. 핵심 도메인은 CQRS 베이스라인 적용
   □ Service / QueryService 분리
   □ Repository / QueryRepository 분리
```

### 트래픽 증가 시 발전 단계

```
[성능 문제 발생]
        ↓
1. @Transactional(readOnly = true) 최적화
        ↓
2. N+1 해결 (BatchSize, Fetch Join, QueryDSL)
        ↓
3. 동시성 제어 (필요한 곳만 락)
        ↓
4. 이벤트 도입 (트랜잭션 분리)
        ↓
5. CQRS 소스 코드 레벨 분리
        ↓
6. Read Replica 도입
        ↓
7. 물리적 CQRS + 역정규화
```

### 락 선택 가이드

```
충돌 빈번 + 짧은 작업          → 선점 잠금
충돌 드물 + 일반 업데이트       → 비선점 잠금 (@Version)
조회 → 수정 → 저장 긴 흐름       → 오프라인 선점 잠금
교착 상태 우려                  → 다수 락 자체를 회피 (이벤트 분리)
```

### 이벤트 처리 선택 가이드

```
같이 성공/실패 필요             → 동기 (@EventListener)
메인 후 처리 + 트랜잭션 안전      → @TransactionalEventListener(AFTER_COMMIT)
응답 빠름 필요                  → + @Async
서버 재시작 손실 방지            → 메시지 큐 (Kafka)
정합성 최우선                   → Outbox 패턴
```

---

## 마무리

### Spring/JPA 구현의 핵심 가치

```
1. 도메인 순수성 보호
   - DIP로 도메인이 인프라 모르게
   - VO는 불변, Entity는 행위 중심

2. 트랜잭션 명확성
   - 응용 계층에서 시작/관리
   - readOnly 명시로 성능 최적화

3. 조회 최적화
   - LAZY 기본 + BatchSize
   - QueryDSL + DTO 프로젝션

4. 동시성 안전성
   - 락 종류별 적절한 선택
   - 교착 상태 회피 (이벤트로 분리)

5. 결합도 감소
   - 도메인 이벤트
   - Outbox 패턴
```

### 1부와 2부의 관계

```
[1부] DDD 순수 개념 가이드
- 무엇을(What) 만들 것인가
- 왜(Why) 그렇게 만드는가
- 사고방식, 원리

[2부] Spring/JPA 구현 가이드 (현재)
- 어떻게(How) 구현할 것인가
- 어떤 도구를 쓸 것인가
- 코드 템플릿, 베스트 프랙티스
```

**1부 → 2부 순서로 활용**하면 좋습니다.

도메인을 먼저 설계하고, 그 다음에 Spring/JPA로 구현하는 흐름.
도구가 도메인을 좌우하지 않도록 주의.

---

학습하느라 고생 많으셨습니다. 이 두 문서를 프로젝트 설계 시 참고 자료로 활용하시면 좋습니다. 🎉
