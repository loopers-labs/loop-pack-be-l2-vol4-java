# 트랜잭션 Self-Invocation 설계 QnA

> Spring `@Transactional`을 중심으로, Prisma `$transaction`과 비교하며 트랜잭션 개념과 self-invocation 문제까지 다룬다.

---

## Q1. 트랜잭션이란 무엇인가

**[질문]**
"트랜잭션(transaction)"이라는 단어를 본인 언어로 설명해보세요. 왜 필요한 개념이고, 트랜잭션 없이 여러 DB 작업을 하면 무엇이 문제가 되나요? 구체적인 시나리오 하나 들어서 설명해주시면 좋습니다.

**[답변]**
> 트랜잭션이란 작업의 정합성을 지켜주도록 하는 논리적인 작업의 단위야. 한가지의 논리적인 작업을 모두 성공하거나 실패하도록 작업에 데이터 정합성?을 지키게 해줌(정합성이 적절한가) 트랜잭션이 없으면 어떤건 성공하고 어떤건 실패해서 정합성이 안맞음. 주문은 처리했는데 만약 결제가 처리되지 않으면 문제가 발생하겠지?(구체적으로 무슨 문제지?)

**[후속 질문 1: "정합성" 단어가 적절한가 — ACID 매핑]**
트랜잭션의 보장 속성을 4가지로 나누는 모델(ACID)이 있는데, 본인이 말한 "모두 성공하거나 모두 실패"는 그 4가지 중 어디에 가장 가까운가요? 그리고 "정합성"이라는 단어는 사실 그 4개 중 다른 하나를 가리키는 표현인데, 의미가 어떻게 다르다고 생각하시나요?

**[후속 질문 2: 주문은 됐는데 결제가 안 된 상태의 구체적 문제]**
- 데이터 관점: 그때 DB 테이블들의 상태는 어떻게 되어 있나요?
- 사용자/회사 관점: 누가 손해를 보고, 누가 이득을 보나요? 그게 왜 "버그"인가요?

**[답변]**
> Atomic에 해당함. 정합성은 일관성 이런거니 consistency와 관련될거임 의미가 다른진 모르겟음 / DB의 상태가 맞지 않으니 옳지 않은 데이터 분석을 하거나 데이터 신뢰성을 믿지 못함. 사용자 입장에서는 돈 안내고도 상품을 받을 수 잇고 이는 곧 회사 손실

**[정리 — Atomicity vs Consistency]**
- Atomicity: "트랜잭션 실행 자체"에 대한 약속 — All or Nothing
- Consistency: "트랜잭션 전후 DB 상태"에 대한 약속 — 모든 제약(constraint, FK, NOT NULL, 비즈니스 룰)을 만족하는 상태로 유지됨
- 본인이 말한 시나리오는 "원자성이 깨졌기 때문에 → 결과적으로 일관된 상태가 깨진 것". 원인은 atomicity 위반, 증상은 inconsistent state.
- 데이터 관점 구체화: `orders`에는 `status=COMPLETED` 행이 있는데 `payments`에 대응 행이 없는 dangling 상태.

---

## Q2. 코드에서 트랜잭션을 어떻게 다루나

**[질문]**
개념은 이해했으니, 이제 코드에서 어떻게 트랜잭션을 시작/커밋/롤백하는지로 가봅시다.

본인이 써본 환경 기준으로:
1. **Spring(Java)**에서 트랜잭션을 다루는 방법은 어떤 것들이 있나요? 본인이 아는 방법 다 말해보세요.
2. **Node.js + Prisma**에서는 어떻게 트랜잭션을 묶나요?

두 환경에서 "트랜잭션 경계(boundary)를 어떻게 선언하는가"의 **스타일 차이**가 핵심입니다.

**[답변]**
> @transactional이랑 $transactional

**[후속 질문 — 코드 모양으로 그려보세요]**
이름만 말하면 모르는 것과 구분이 안 됨. 의사코드로 보여주세요.
1. Spring `OrderService.createOrder()`를 트랜잭션으로 묶을 때 코드가 어떻게 생겼나요?
2. Prisma에서 "주문 INSERT + 결제 INSERT" 두 개를 하나의 트랜잭션으로 묶을 때 코드가 어떻게 생겼나요?

힌트: 같은 "트랜잭션 묶기"인데 한쪽은 메서드에 **뭔가를 붙이고**, 다른 쪽은 코드 블록을 **뭔가로 감쌉니다**. 그 "붙이느냐 vs 감싸느냐"의 차이가 핵심.

(디테일: Prisma는 `$transactional`이 아니라 `$transaction` — 메서드입니다.)

**[답변]**
> transactional { service.createOrder payService.createPay} 노드도 비슷하게

**[후속 질문 — 부정확한 부분 두 군데]**

1. Spring 쪽: `transactional { ... }` 블록은 `@Transactional` 어노테이션의 본 모습이 아님 (오히려 `TransactionTemplate` 같은 명령형 패턴). 진짜 `@Transactional`은 메서드에 붙이는 어노테이션. 다시 그려보세요 — Controller가 OrderService.createOrder()를 어떻게 부르는지까지 포함해서. 호출하는 쪽이 트랜잭션 존재를 알아야 하나요?

2. Prisma 쪽: `$transaction`은 콜백이 **인자 하나**를 받음 (`tx`). 그 인자가 뭐고, 안 쓰고 그냥 `prisma.order.create(...)`라고 쓰면 트랜잭션에 포함되나요?

**[답변]**
> 둘다 모르겟누 호출하는쪽이 알아야하지 않아? js는 내가 잘 몰라서 콜백이 구체적으로 머고 뭐가 다르다는거지

**[정리 — Spring vs Prisma 트랜잭션 스타일]**

**Spring `@Transactional` (선언적, AOP/프록시 기반)**
```java
@Service
class OrderService {
    @Transactional
    public void createOrder() {
        orderRepo.save(order);
        payService.createPay();
    }
}

// Controller
orderService.createOrder();  // 호출하는 쪽은 트랜잭션 존재를 모름
```
- Spring이 OrderService 빈을 만들 때 프록시 객체로 감싸서 컨테이너에 등록
- Controller가 주입받은 orderService는 진짜 객체가 아닌 프록시
- 프록시가 메서드 호출을 가로채서: BEGIN → 진짜 메서드 실행 → COMMIT/ROLLBACK
- 호출하는 쪽이 트랜잭션 존재를 몰라도 되는 이유: 프록시에 가로채진 줄도 모르고 그냥 메서드 호출만 함

**Prisma `$transaction` (명시적, 콜백 기반)**
```javascript
await prisma.$transaction(async (tx) => {
  await tx.order.create({ data: ... });    // tx로 호출 → 트랜잭션 안
  await tx.payment.create({ data: ... });  // tx로 호출 → 트랜잭션 안
  // prisma.payment.create(...) 라고 쓰면 → 트랜잭션 밖에서 별도 connection
});
```
- 콜백(함수에 함수를 인자로 넘기는 것)을 받음
- 콜백 실행 시 Prisma가 `tx`(트랜잭션 클라이언트)를 인자로 전달
- `tx`로 호출한 쿼리만 트랜잭션 안에 포함

**본질적 차이**
| 항목 | Spring @Transactional | Prisma $transaction |
|---|---|---|
| 경계 선언 | 메서드 어노테이션 | 함수 호출 + 콜백 |
| 어느 쿼리가 트랜잭션 안인가 | 코드에 안 드러남 (ThreadLocal로 묶음) | 코드에 드러남 (`tx`로 호출한 것만) |
| 호출하는 쪽 | 트랜잭션 존재 모름 | 명시적으로 `$transaction(...)` 호출 |
| 스타일 | 선언적, AOP(프록시) | 명령형, 콜백 |

---

## Q3. Self-Invocation 문제의 클래식 케이스

**[질문]**
같은 OrderService 안에 두 메서드가 있다고 가정.

```java
@Service
class OrderService {
    public void method1() {
        // 트랜잭션 없음
        this.method2();   // 같은 클래스 내부에서 자기 자신의 메서드 호출
    }

    @Transactional
    public void method2() {
        // 트랜잭션 있어야 함
        orderRepo.save(order);
    }
}
```

Controller가 `orderService.method1()`을 호출했어요.

이때 `method2`는 트랜잭션 안에서 실행될까요, 안 될까요? 그리고 그 이유는?

(힌트: 프록시 그림을 머릿속에 그려보고 답해주세요. 누가 `method2` 호출을 가로채야 트랜잭션이 시작되는데, 지금 그 호출을 누가 하고 있죠?)

**[답변]**
> 프록시를 안거쳐서 트랜잭션 안걸림

**[후속 질문 — `this`가 누구인가]**
Controller가 `orderService.method1()`을 호출할 때를 분해:
1. Controller가 주입받은 orderService = 프록시 객체
2. 프록시가 method1() 호출을 받음
3. method1에는 @Transactional이 없음 → 프록시는 그냥 진짜 객체의 method1을 호출
4. 진짜 객체의 method1 안에서 `this.method2()` 실행

4번에서 `this`는 누구를 가리키나요? 그게 왜 프록시를 우회하게 되는 거죠?

**[답변]**
> 그때는 진짜 OrderService아냐?

**[정리 — self-invocation의 본질]**
```
Controller → 프록시(OrderService) → 진짜 OrderService 객체
                                      └── method1() 실행 중...
                                      └── this.method2()
                                           ↑
                                  this = 진짜 객체 자기 자신
                                  → 프록시를 우회하고 직접 호출
                                  → @Transactional 무시됨
```
프록시는 외부에서 들어오는 호출만 가로챌 수 있음. 객체 내부의 `this` 호출은 프록시가 끼어들 틈이 없음.

---

## Q4. 해결 방법

**[질문]**
이 문제를 어떻게 해결할 수 있을까요? 본인이 떠올릴 수 있는 방법을 다 말해보세요.

(힌트: 핵심은 "`method2`가 프록시를 거치게 만들기". 어떻게 하면 프록시를 거치게 만들 수 있을까요?)

**[답변]**
> 이전에 프록시를 거쳤다면 프록시를 다시 거치게끔 한다..? 이게 기술적으로 될지는 모르겠음

**[후속 질문 — 두 갈래로 추측 유도]**

갈래 A (프록시를 다시 거치게 만들기):
- 프록시는 Spring 컨테이너가 만들어서 빈으로 등록
- 진짜 객체가 그 프록시를 손에 넣을 수만 있다면 self.method2() 가능
- 어떻게 손에 넣을 수 있을까요? (DI 관점)

갈래 B (self-invocation 자체를 피하기):
- 더 단순한 발상: 내부 호출이 문제라면, 내부 호출이 아니게 만들면 됨
- method1과 method2가 "같은 클래스 내부 호출 관계"가 아니게 되려면?

**[답변]**
> 스프링이 빈으로 등록해놨을테니 그 프록시 빈을 호출해서 주입받아 사용하자. / 프록시가 두개 모두 각각 호출하도록? 아 이런 사고는 어떻게 하는거야?

**[메타 — "이런 사고는 어떻게 하는가"]**
본인이 정확히 추론한 이유는 본질을 정확한 그림으로 그렸기 때문. "프록시는 외부 호출만 가로챈다. this는 진짜 객체라 프록시를 우회한다." 이 그림이 선명하면 해결책은 그림이 알려줌:
- "외부 호출만 가로챈다" → 외부에서 호출하게 만들면 됨 (B: 메서드 분리)
- "this는 진짜 객체라 못 가로챈다" → this 말고 프록시를 변수로 들고 있으면 됨 (A: self-injection)

해결책이 안 떠오를 땐 본질 그림이 흐릿한 것. 그림을 선명하게 만드는 질문을 반복하면 됨.

**[실제 코드 모양]**

A. Self-injection:
```java
@Service
class OrderService {
    private final OrderService self;
    public OrderService(@Lazy OrderService self) { this.self = self; }
    public void method1() { self.method2(); }
    @Transactional public void method2() { ... }
}
```

B. 메서드 분리:
```java
@Service
class OrderService {
    private final OrderTxService orderTxService;
    public void method1() { orderTxService.method2(); }
}
@Service
class OrderTxService {
    @Transactional public void method2() { ... }
}
```

---

## Q5. 트레이드오프 — 어느 쪽을 선택할 것인가

**[질문]**
A(self-injection)와 B(메서드 분리), 기본 선택은 어느 쪽인가요? 이유는?

생각해볼 포인트:
- 다른 사람이 봤을 때 어느 쪽이 의도가 명확한가?
- 어느 쪽이 self-invocation 함정을 구조적으로 차단하는가?
- 각 방법의 단점은?

**[답변]**
> B로 할것 같아. Lazy하게 하면 프록시 객체 기반으로 동작하는거기때문에 신뢰 할 수 없음(근데 구체적으로 왜 프록시라고 신뢰할 수 없는지는 대답이 어렵네) 메서드 분리가 깔끔할듯

**[정리 — Self-injection의 진짜 단점]**
프록시 자체는 트랜잭션 관점에서 신뢰할 수 있음. "프록시라서 못 믿겠다"는 직관은 빗나간 것. 진짜 단점은:

1. 의도 모호함 — "왜 자기 자신을 주입받지?" 트릭처럼 보임
2. `@Lazy`를 써야 한다는 사실 자체가 코드 스멜
3. 설계 신호를 숨김 — method1과 method2가 트랜잭션 경계가 다르다는 건 "책임이 다르다"는 신호. B는 그 신호를 코드 구조로 드러냄
4. 전염성 — 새 메서드 추가 시 또 self.를 붙여야 함

**B가 권장되는 진짜 이유**: 트랜잭션 경계 = 클래스 경계로 맞추면, self-invocation 함정 자체가 구조적으로 사라짐. 함정을 우회하는 게 아니라 함정이 존재할 수 없는 구조를 만드는 것.

---

## Q5 보충 — 프록시의 정체

**[추가 질문]**
> 잠깐 이거 먼저 구체적으로 프록시가 어케 들어온다는거야? tx 프록시 인건가..? OrderService가 tx인거야..? 혼란스럽네

**[설명 — 프록시의 정체]**

1. Spring이 빈 등록할 때 실제로 하는 일:
   `OrderService` 클래스 안에 메서드 하나라도 `@Transactional`이 붙어 있으면, Spring은 OrderService 타입의 프록시 객체를 만들어서 컨테이너에 등록.

```
[Spring Container에 등록되는 것]
  orderService 빈 = OrderService$$Proxy 인스턴스
                     ├── 타입: OrderService를 상속한 클래스 (CGLIB)
                     ├── 내부에 진짜 OrderService 인스턴스 보유 (target)
                     └── 메서드 호출 가로채는 로직
```

2. CGLIB 프록시 의사코드 (Spring이 런타임에 만드는 클래스):
```java
class OrderService$$Proxy extends OrderService {
    private OrderService target;
    private TransactionInterceptor txAdvice;

    @Override
    public void method1() {
        target.method1();  // @Transactional 없음 → 그냥 위임
    }

    @Override
    public void method2() {
        // @Transactional 있음 → 트랜잭션 advice 추가
        txAdvice.invoke(() -> target.method2());
    }
}
```

핵심:
- 타입은 `OrderService` (다형성을 위해 상속)
- 역할은 "트랜잭션 advice를 추가한 wrapper"
- "tx 프록시"이든 "OrderService 프록시"이든 같은 객체를 가리킴

3. Self-injection에서 프록시가 `self`에 들어오는 과정:
- `@Autowired OrderService self` → 컨테이너에서 OrderService 타입의 빈 찾음
- 컨테이너에 등록된 OrderService 빈 = 프록시
- 그래서 `self`에는 프록시가 들어옴

핵심 구분:
- `this` = 진짜 OrderService 인스턴스 자기 자신
- `self` = 컨테이너에서 가져온 프록시 객체 (= 자기 자신을 감싼 wrapper)

```
[진짜 OrderService 인스턴스]  ← 객체 1개
       ↑
       │ wrap
       │
[프록시 객체]  ← 객체 1개 (컨테이너 빈으로 등록)
       │
       └─ 내부 필드 self ──→ [프록시 객체 자기 자신을 가리킴]
```

메모리에는 객체가 두 개(진짜 1개 + 프록시 1개). 진짜 객체 안의 `self` 필드가 자기를 감싼 프록시를 가리키게 만드는 게 self-injection의 트릭.

---

## Q6. 실무 함정 — REQUIRES_NEW 시나리오

**[질문]**
상황:
- 주문 처리 중 외부 API 호출 로그를 무조건 남겨야 함 (주문이 롤백되어도 로그는 남아야)
- 로그 저장 메서드를 REQUIRES_NEW로 별도 트랜잭션 처리

```java
@Service
class OrderService {

    @Transactional
    public void createOrder() {
        // ... 주문 저장 ...
        try {
            externalApi.call();
        } catch (Exception e) {
            this.saveLog(e);  // 같은 클래스 내부 호출
            throw e;          // 주문 자체는 롤백
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(Exception e) {
        logRepo.save(...);   // 별도 트랜잭션으로 무조건 커밋되어야 함
    }
}
```

1. createOrder()에서 예외 발생 시, 로그는 DB에 남을까요? 왜?
2. 남는다고 답했다면, 진짜로 남나요? (self-invocation 그림 다시 떠올려보세요)
