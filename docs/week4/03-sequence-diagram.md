# 03. 시퀀스 다이어그램 (Week 4 — Coupon)

---

## 목차

1. [쿠폰 발급](#1-쿠폰-발급)
2. [내 쿠폰 목록 조회](#2-내-쿠폰-목록-조회)
3. [쿠폰 적용 주문 — 정상 흐름](#3-쿠폰-적용-주문--정상-흐름)
4. [쿠폰 적용 주문 — 실패 흐름](#4-쿠폰-적용-주문--실패-흐름)
5. [쿠폰 동시 사용 — 낙관적 락 충돌](#5-쿠폰-동시-사용--낙관적-락-충돌)

---

## 1. 쿠폰 발급

사용자가 쿠폰 템플릿 ID를 지정해 발급을 요청합니다. 동일 쿠폰은 1인 1회만 발급됩니다.

```mermaid
sequenceDiagram
    actor 사용자
    participant CouponV1Controller
    participant UserCouponService
    participant CouponRepository
    participant UserCouponRepository

    사용자 ->> CouponV1Controller: POST /api/v1/coupons/{couponId}/issue

    CouponV1Controller ->> UserCouponService: issue(userId, couponId)

    UserCouponService ->> CouponRepository: findById(couponId)

    alt 쿠폰 없음 / 삭제됨
        CouponRepository -->> UserCouponService: empty
        UserCouponService -->> 사용자: 404 Not Found
    else 쿠폰 존재
        CouponRepository -->> UserCouponService: CouponModel

        alt 쿠폰 만료
            UserCouponService -->> 사용자: 400 Bad Request (만료된 쿠폰)
        else 유효한 쿠폰
            UserCouponService ->> UserCouponRepository: existsByUserIdAndCouponId(userId, couponId)

            alt 이미 발급됨
                UserCouponRepository -->> UserCouponService: true
                UserCouponService -->> 사용자: 409 Conflict (이미 발급받은 쿠폰)
            else 신규 발급
                UserCouponRepository -->> UserCouponService: false
                UserCouponService ->> UserCouponRepository: save(UserCouponModel{AVAILABLE})
                UserCouponRepository -->> UserCouponService: UserCouponModel
                UserCouponService -->> 사용자: 200 OK (UserCouponResponse)
            end
        end
    end
```

---

## 2. 내 쿠폰 목록 조회

발급된 쿠폰 목록을 페이지 단위로 반환합니다. EXPIRED 상태는 `expired_at` 기준으로 동적 계산합니다.

```mermaid
sequenceDiagram
    actor 사용자
    participant CouponV1Controller
    participant UserCouponService
    participant UserCouponRepository

    사용자 ->> CouponV1Controller: GET /api/v1/users/me/coupons?page=0&size=20

    CouponV1Controller ->> UserCouponService: getMyCoupons(userId, pageable)

    UserCouponService ->> UserCouponRepository: findAllByUserId(userId, pageable)
    Note over UserCouponRepository: @EntityGraph — coupon JOIN FETCH

    UserCouponRepository -->> UserCouponService: Page~UserCouponModel~

    Note over UserCouponService: 각 UserCouponModel.computedStatus() 호출<br/>USED → USED<br/>AVAILABLE + expired_at 지남 → EXPIRED<br/>AVAILABLE + 유효 → AVAILABLE

    UserCouponService -->> 사용자: 200 OK (Page~UserCouponResponse~)
```

---

## 3. 쿠폰 적용 주문 — 정상 흐름

쿠폰 ID를 포함해 주문하면 쿠폰 유효성 검증 → 재고 차감 → 쿠폰 사용 처리 → 금액 확정 순으로 진행됩니다.

```mermaid
sequenceDiagram
    actor 사용자
    participant OrderV1Controller
    participant OrderFacade
    participant UserCouponRepository
    participant ProductRepository
    participant StockRepository
    participant OrderDomainService
    participant OrderRepository

    사용자 ->> OrderV1Controller: POST /api/v1/orders<br/>{items:[...], couponId: 42}

    OrderV1Controller ->> OrderFacade: createOrder(command)

    %% 1. 쿠폰 사전 조회
    OrderFacade ->> UserCouponRepository: findByIdWithCoupon(couponId=42)
    UserCouponRepository -->> OrderFacade: UserCouponModel{userId=1, AVAILABLE}

    OrderFacade ->> OrderFacade: userId 소유권 확인

    %% 2. 상품·재고 조회
    OrderFacade ->> ProductRepository: findAllActiveByIds([...])
    ProductRepository -->> OrderFacade: List~ProductModel~

    OrderFacade ->> StockRepository: findByProductId (각 상품)
    StockRepository -->> OrderFacade: List~StockModel~

    %% 3. 재고 검증 및 차감
    OrderFacade ->> OrderDomainService: validateStocks(stockMap, quantityMap)
    OrderDomainService -->> OrderFacade: OK

    OrderFacade ->> OrderFacade: stock.decrease(qty) — dirty checking

    %% 4. 주문 조립
    OrderFacade ->> OrderDomainService: buildOrder(userId, products, quantityMap)
    Note over OrderDomainService: applyPricing(originalAmount, 0)
    OrderDomainService -->> OrderFacade: OrderModel{originalAmount=50000, discount=0}

    %% 5. 쿠폰 적용
    OrderFacade ->> OrderFacade: userCoupon.use()<br/>→ status=USED, @Version +1
    OrderFacade ->> OrderFacade: coupon.calculateDiscount(50000) = 5000
    OrderFacade ->> OrderFacade: order.applyPricing(50000, 5000)<br/>→ totalAmount=45000

    %% 6. 저장
    OrderFacade ->> OrderRepository: save(order)
    OrderRepository -->> OrderFacade: OrderModel (id=101)

    OrderFacade -->> 사용자: 200 OK<br/>{originalAmount:50000, discountAmount:5000, totalAmount:45000}
```

---

## 4. 쿠폰 적용 주문 — 실패 흐름

쿠폰 관련 실패 케이스별 분기입니다.

```mermaid
sequenceDiagram
    actor 사용자
    participant OrderFacade
    participant UserCouponRepository

    사용자 ->> OrderFacade: POST /api/v1/orders {couponId: 42}

    OrderFacade ->> UserCouponRepository: findByIdWithCoupon(42)

    alt 쿠폰 없음
        UserCouponRepository -->> OrderFacade: empty
        OrderFacade -->> 사용자: 404 Not Found
    else 타인 소유 쿠폰
        UserCouponRepository -->> OrderFacade: UserCouponModel{userId=99}
        OrderFacade ->> OrderFacade: userId 불일치
        OrderFacade -->> 사용자: 400 Bad Request
    else 이미 사용된 쿠폰
        UserCouponRepository -->> OrderFacade: UserCouponModel{status=USED}
        OrderFacade ->> OrderFacade: userCoupon.use() 예외
        OrderFacade -->> 사용자: 400 Bad Request (이미 사용된 쿠폰)
    else 만료된 쿠폰
        UserCouponRepository -->> OrderFacade: UserCouponModel{coupon.expiredAt 과거}
        OrderFacade ->> OrderFacade: userCoupon.use() 예외
        OrderFacade -->> 사용자: 400 Bad Request (만료된 쿠폰)
    else 최소 주문 금액 미충족
        OrderFacade ->> OrderFacade: coupon.calculateDiscount(amount) 예외
        OrderFacade -->> 사용자: 400 Bad Request (최소 주문 금액 미충족)
    end

    Note over OrderFacade: 모든 실패는 @Transactional 롤백<br/>재고 차감·쿠폰 사용 모두 원복
```

---

## 5. 쿠폰 동시 사용 — 낙관적 락 충돌

같은 사용자가 두 기기에서 동시에 동일 쿠폰으로 주문하는 시나리오입니다.

```mermaid
sequenceDiagram
    actor 기기A
    actor 기기B
    participant OrderFacade
    participant DB

    par 기기A 요청
        기기A ->> OrderFacade: createOrder{couponId=42}
        OrderFacade ->> DB: SELECT user_coupons WHERE id=42<br/>(version=0, status=AVAILABLE)
    and 기기B 요청
        기기B ->> OrderFacade: createOrder{couponId=42}
        OrderFacade ->> DB: SELECT user_coupons WHERE id=42<br/>(version=0, status=AVAILABLE)
    end

    Note over 기기A, DB: 두 트랜잭션이 같은 version=0을 읽음

    기기A ->> DB: UPDATE user_coupons SET status=USED, version=1<br/>WHERE id=42 AND version=0
    DB -->> 기기A: 1 row updated ✅

    기기B ->> DB: UPDATE user_coupons SET status=USED, version=1<br/>WHERE id=42 AND version=0
    DB -->> 기기B: 0 rows updated ❌ (version 불일치)

    Note over 기기B: OptimisticLockException<br/>→ 트랜잭션 롤백 (재고 차감도 원복)

    기기A -->> 기기A: 주문 성공 (totalAmount 반환)
    기기B -->> 기기B: 주문 실패 (이미 사용된 쿠폰)
```
