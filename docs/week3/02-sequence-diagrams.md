# 02. 시퀀스 다이어그램 — 쿠폰 (Coupons)

[`01-requirements.md`](./01-requirements.md) §6의 UC-13~20 흐름을 레이어별 참여자 기준으로 시각화한다. 표기 규칙은 [`../week2/02-sequence-diagrams.md`](../week2/02-sequence-diagrams.md) §0을 그대로 따른다(레이어/화살표/생략/공통 에러).

## 0. 이 문서의 참여자 (week2 §0.1 레이어에 쿠폰 도메인 추가)

| 약칭 | 클래스 | 레이어 | 책임 |
| --- | --- | --- | --- |
| `CCtrl` | `CouponV1Controller` | Interface (대고객) | 발급·내 쿠폰 조회 |
| `ACtrl` | `AdminCouponV1Controller` | Interface (Admin) | 템플릿 CRUD·발급 내역 |
| `CFac` | `CouponFacade` | Application | 대고객 유스케이스 조립 |
| `ACFac` | `AdminCouponFacade` | Application | Admin 유스케이스 조립 |
| `CSvc` | `CouponService` | Domain Service | 템플릿(Coupon) 생명주기·발급 가능 검증 |
| `UCSvc` | `UserCouponService` | Domain Service | 발급분(UserCoupon) 발급·선택·사용·원복 |
| `Cpn` | `CouponModel` | Domain Aggregate | 템플릿. 할인 계산(`calculateDiscount`) |
| `UCpn` | `UserCouponModel` | Domain Aggregate | 발급분. 상태 전이(`use`/`restore`) |
| `CRepo` | `CouponRepository` | Domain Repository | 템플릿 영속 |
| `UCRepo` | `UserCouponRepository` | Domain Repository | 발급분 영속 (락 조회 포함) |

> 주문 통합(UC-17~20)에서는 week2의 `OrderFacade`/`OrderService`/`OrderModel`/`PaymentGateway`가 함께 등장한다. 쿠폰 사용은 **주문 생성 트랜잭션 안**에서 `OrderService`가 `UserCouponService`를 호출하는 도메인 서비스 간 협력으로 그린다(week2 OrderService→ProductService 패턴과 동일).

---

## UC-13. 쿠폰 발급

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant CCtrl as CouponV1Controller
    participant CFac as CouponFacade
    participant UCSvc as UserCouponService
    participant CSvc as CouponService
    participant Cpn as CouponModel
    participant UCpn as UserCouponModel
    participant UCRepo as UserCouponRepository

    C->>CCtrl: POST /api/v1/coupons/{couponId}/issue
    CCtrl->>CFac: issue(userId, couponId)
    CFac->>UCSvc: issue(userId, couponId)
    UCSvc->>CSvc: getIssuableTemplate(couponId)
    Note over CSvc: 존재 + 활성(soft delete X) + 미만료 검증
    alt 미존재 / 비활성 / 만료
        CSvc-->>UCSvc: throw CoreException(NOT_FOUND | BAD_REQUEST)
        UCSvc-->>C: 404 / 400
    else 발급 가능
        CSvc-->>UCSvc: CouponModel
        UCSvc->>UCpn: issue(userId, couponId)
        Note over UCpn: status=AVAILABLE, issuedAt=now
        UCSvc->>UCRepo: save(userCoupon)
        UCRepo-->>UCSvc: UserCouponModel(id)
        UCSvc-->>CFac: UserCouponInfo
        CFac-->>CCtrl: UserCouponInfo
        CCtrl-->>C: 201 Created + UserCouponDto
    end
```

**에러 분기**
- 존재하지 않는 템플릿 → `NOT_FOUND` (§7.2)
- 만료 시각 경과 / soft delete된 템플릿 → `BAD_REQUEST` (§9 Q2)
- 중복 발급은 막지 않음 — 같은 사용자가 같은 템플릿을 또 발급받으면 새 행 생성 (§9 Q4)

---

## UC-14. 내 쿠폰 목록 조회

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant CCtrl as CouponV1Controller
    participant CFac as CouponFacade
    participant UCSvc as UserCouponService
    participant UCRepo as UserCouponRepository
    participant CRepo as CouponRepository

    C->>CCtrl: GET /api/v1/users/me/coupons?page&size
    CCtrl->>CFac: getMyCoupons(userId, page, size)
    CFac->>UCSvc: getMyCoupons(userId, page, size)
    UCSvc->>UCRepo: findByUserId(userId, page, size)
    UCRepo-->>UCSvc: List~UserCouponModel~
    UCSvc->>CRepo: findByIds(templateIds)
    Note over UCSvc: 템플릿 배치 조회 (N+1 회피) — 이름/만료시각 조합
    CRepo-->>UCSvc: List~CouponModel~
    loop 각 발급분
        UCSvc->>UCpn: resolveStatus(now)
        Note over UCpn: USED면 USED / 아니면 만료시각<now → EXPIRED / 그 외 AVAILABLE
    end
    UCSvc-->>CFac: List~UserCouponView~ (상태 파생)
    CFac-->>CCtrl: List~UserCouponInfo~
    CCtrl-->>C: 200 OK + List~UserCouponDto~
```

> 상태(AVAILABLE/USED/EXPIRED)는 저장값이 아니라 조회 시점에 파생한다(§7.5). 만료 판정에 템플릿의 `expiredAt`이 필요하므로 발급분 + 템플릿을 배치 조합한다.

---

## UC-15. 쿠폰 템플릿 등록 (Admin)

```mermaid
sequenceDiagram
    actor A as Client(Admin)
    participant ACtrl as AdminCouponV1Controller
    participant ACFac as AdminCouponFacade
    participant CSvc as CouponService
    participant Cpn as CouponModel
    participant CRepo as CouponRepository

    A->>ACtrl: POST /api-admin/v1/coupons\n{name,type,value,minOrderAmount?,expiredAt}
    ACtrl->>ACFac: register(command)
    ACFac->>CSvc: register(name,type,value,minOrderAmount,expiredAt)
    CSvc->>Cpn: new CouponModel(...)
    Note over Cpn: type∈{FIXED,RATE}, value>0,\nRATE면 value≤100, expiredAt 필수 검증
    alt 검증 실패
        Cpn-->>CSvc: throw CoreException(BAD_REQUEST)
        CSvc-->>A: 400
    else 정상
        CSvc->>CRepo: save(coupon)
        CRepo-->>CSvc: CouponModel(id)
        CSvc-->>ACFac: CouponModel
        ACFac-->>ACtrl: CouponInfo
        ACtrl-->>A: 201 Created + CouponDto
    end
```

> 목록(`GET /coupons`)·상세(`GET /coupons/{id}`)·수정(`PUT`)·삭제(`DELETE`, soft delete)는 week2 Brand 관리와 동형이라 다이어그램 생략. 수정은 `CouponModel.update(...)`(동일 검증 재사용), 삭제는 `delete()`(deletedAt=now).

---

## UC-16. 특정 쿠폰 발급 내역 조회 (Admin)

```mermaid
sequenceDiagram
    actor A as Client(Admin)
    participant ACtrl as AdminCouponV1Controller
    participant ACFac as AdminCouponFacade
    participant UCSvc as UserCouponService
    participant UCRepo as UserCouponRepository

    A->>ACtrl: GET /api-admin/v1/coupons/{couponId}/issues?page&size
    ACtrl->>ACFac: getIssues(couponId, page, size)
    ACFac->>UCSvc: findByCouponId(couponId, page, size)
    UCSvc->>UCRepo: findByCouponId(couponId, page, size)
    UCRepo-->>UCSvc: List~UserCouponModel~
    UCSvc-->>ACFac: List~UserCouponInfo~ (누구에게/언제/상태)
    ACFac-->>ACtrl: List~UserCouponInfo~
    ACtrl-->>A: 200 OK + List~IssueDto~
```

---

## UC-17. 쿠폰을 적용한 주문 (성공)

핵심 흐름. 쿠폰 사용은 **재고 차감과 같은 트랜잭션**에서 일어나고, 결제(PG)는 트랜잭션 밖에서 최종 결제 금액으로 진행한다(week2 §7.6 패턴 그대로).

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant OCtrl as OrderV1Controller
    participant OFac as OrderFacade
    participant OSvc as OrderService
    participant UCSvc as UserCouponService
    participant Cpn as CouponModel
    participant UCpn as UserCouponModel
    participant O as OrderModel
    participant PG as PaymentGateway

    C->>OCtrl: POST /api/v1/orders\n{items, couponId(=템플릿)}
    OCtrl->>OFac: placeOrder(userId, method, lines, couponId)

    rect rgb(235,245,255)
    Note over OSvc,UCpn: ① 주문 생성 트랜잭션 [tx]
    OFac->>OSvc: placeOrderPending(userId, method, lines, couponId)
    OSvc->>O: 항목 스냅샷 + 재고차감 + calculateTotals()
    Note over O: originalAmount = 라인 합계
    opt couponId != null
        OSvc->>UCSvc: useForOrder(userId, couponId, originalAmount)
        UCSvc->>UCRepo: 사용가능 발급분 선택 (가장 먼저 발급)
        Note over UCSvc,UCRepo: 동시성 제어 지점 → UC-20
        UCSvc->>Cpn: calculateDiscount(originalAmount)
        Note over Cpn: FIXED: min(value,amount)\nRATE: floor(amount*value/100)\nminOrderAmount 미달 시 BAD_REQUEST
        Cpn-->>UCSvc: discountAmount
        UCSvc->>UCpn: use()
        Note over UCpn: AVAILABLE → USED, usedAt=now
        UCSvc-->>OSvc: AppliedCoupon(userCouponId, discountAmount)
    end
    OSvc->>O: applyDiscount(discountAmount)
    Note over O: finalAmount = originalAmount - discountAmount\n(originalAmount/discountAmount/finalAmount 스냅샷)
    OSvc-->>OFac: OrderModel(PENDING)
    end

    OFac->>PG: pay(orderId, finalAmount, method)
    Note over OFac,PG: ② 결제 [tx 밖] — 최종 결제 금액 청구
    PG-->>OFac: SUCCESS

    rect rgb(235,255,235)
    Note over OSvc: ③ 결과 반영 [tx]
    OFac->>OSvc: markPaid(orderId)
    OSvc->>O: markPaid()
    end
    OFac-->>OCtrl: OrderInfo (원금/할인/최종)
    OCtrl-->>C: 201 Created + OrderDto
```

---

## UC-18. 쿠폰을 적용한 주문 (실패 — 잘못된 쿠폰)

검증 실패는 ① 트랜잭션 안에서 발생하므로 **재고 차감을 포함한 전체가 롤백**된다(원자성).

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant OFac as OrderFacade
    participant OSvc as OrderService
    participant UCSvc as UserCouponService

    C->>OFac: placeOrder(userId, method, lines, couponId)
    rect rgb(255,238,238)
    Note over OSvc,UCSvc: 주문 생성 트랜잭션 [tx] — 실패 시 전체 롤백
    OFac->>OSvc: placeOrderPending(..., couponId)
    OSvc->>OSvc: 재고 차감 (선행)
    OSvc->>UCSvc: useForOrder(userId, couponId, originalAmount)
    alt 사용 가능 발급분 없음 (미보유/전부 USED/EXPIRED)
        UCSvc-->>OSvc: throw CoreException(NOT_FOUND)
    else 타 유저 소유
        UCSvc-->>OSvc: throw CoreException(NOT_FOUND)
        Note over UCSvc: 존재/소유 노출 방지 (§2 격리)
    else minOrderAmount 미달
        UCSvc-->>OSvc: throw CoreException(BAD_REQUEST)
    end
    Note over OSvc: 예외 전파 → @Transactional 롤백\n→ 재고 차감 원복, 쿠폰 USED 미반영
    end
    OFac-->>C: 404 / 400 (주문 실패)
```

> 결제(PG)는 호출되지 않는다. 검증이 트랜잭션 안 선행 단계라 PG 이전에 종결된다.

---

## UC-19. 쿠폰을 적용한 주문 (결제 실패 — 원복)

검증·사용 처리·재고 차감까지 끝났으나 PG가 실패한 경우. 재고와 함께 **쿠폰도 원복**한다.

```mermaid
sequenceDiagram
    participant OFac as OrderFacade
    participant OSvc as OrderService
    participant PSvc as ProductService
    participant UCSvc as UserCouponService
    participant UCpn as UserCouponModel
    participant PG as PaymentGateway

    OFac->>OSvc: placeOrderPending(...) [tx]
    Note over OSvc: 재고 차감 + 쿠폰 use()(USED) 완료, PENDING
    OFac->>PG: pay(orderId, finalAmount, method)
    PG-->>OFac: FAILED

    rect rgb(255,238,238)
    Note over OSvc,UCpn: 결제 실패 처리 [tx]
    OFac->>OSvc: markFailed(orderId, reason)
    OSvc->>PSvc: restoreStock(productId, qty) (항목별)
    opt 쿠폰 적용된 주문
        OSvc->>UCSvc: restore(userCouponId)
        UCSvc->>UCpn: restore()
        Note over UCpn: USED → AVAILABLE, usedAt=null
    end
    OSvc->>OSvc: order.markFailed(reason)
    end
    OFac-->>OFac: OrderInfo(FAILED)
```

> TIMEOUT(결제 지연)이면 week2와 동일하게 주문은 PENDING 유지, 쿠폰도 USED인 채로 둔다(추후 재확인). 원복은 FAILED 확정 시에만.

---

## UC-20. 동일 쿠폰 동시 사용 — 동시성 제어 (volume-4 핵심)

두 주문이 같은 발급 쿠폰을 거의 동시에 `use()`하려는 경합. **정확히 한 건만 성공**해야 한다. 두 가지 제어 전략을 구현·비교한다(트레이드오프는 [`03-class-diagram.md`](./03-class-diagram.md) §5 / `analyze-query` 스킬 분석으로 정리).

### 20-A. 낙관적 락 (@Version)

```mermaid
sequenceDiagram
    participant T1 as 주문 Tx1
    participant T2 as 주문 Tx2
    participant UCRepo as UserCouponRepository
    participant DB as DB(user_coupon, version)

    par 동시 진입
        T1->>UCRepo: findAvailable(userId, couponId)
        UCRepo->>DB: SELECT ... (version=5)
        DB-->>T1: UserCoupon(v5, AVAILABLE)
    and
        T2->>UCRepo: findAvailable(userId, couponId)
        UCRepo->>DB: SELECT ... (version=5)
        DB-->>T2: UserCoupon(v5, AVAILABLE)
    end
    T1->>DB: UPDATE ... SET status=USED, version=6 WHERE id=? AND version=5
    DB-->>T1: 1 row → commit ✅
    T2->>DB: UPDATE ... SET status=USED, version=6 WHERE id=? AND version=5
    DB-->>T2: 0 row → OptimisticLockException ❌
    Note over T2: 주문 Tx2 롤백 (재고/쿠폰 미반영) → 주문 실패
```

### 20-B. 비관적 락 (SELECT ... FOR UPDATE)

```mermaid
sequenceDiagram
    participant T1 as 주문 Tx1
    participant T2 as 주문 Tx2
    participant UCRepo as UserCouponRepository
    participant DB as DB(user_coupon)

    T1->>UCRepo: findAvailableForUpdate(userId, couponId)
    UCRepo->>DB: SELECT ... FOR UPDATE
    Note over DB: T1이 행 잠금 획득
    DB-->>T1: UserCoupon(AVAILABLE)
    T2->>UCRepo: findAvailableForUpdate(userId, couponId)
    UCRepo->>DB: SELECT ... FOR UPDATE
    Note over T2,DB: 🔒 T1 커밋까지 대기 (block)
    T1->>DB: UPDATE status=USED → commit ✅
    DB-->>T2: 잠금 해제 후 조회 → status=USED
    Note over T2: 사용 가능 발급분 없음 → CoreException → 주문 실패 ❌
```

**두 전략의 공통 보장**: 한 발급분은 정확히 한 주문에만 USED 된다. 차이(충돌 시점·대기 vs 재시도·처리량)는 03 §5에서 비교한다.
