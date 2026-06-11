# 02. 시퀀스 다이어그램

## 시퀀스다이어그램 표기 규칙

- **사전 조건 · 불변식 · 사후 조건은 해당 흐름에 *실재할 때만* 표기**한다 (없으면 누락이 아니라 그 조건이 없다는 뜻). **사전 조건은 진입점(Controller; 도메인 단위 다이어그램이면 진입 도메인)에, 사후 조건은 응답 직전에** 둔다 — 흐름 중간이 아니라 *경계*에 둬야 계약이 또렷하다. 불변식은 강제되는 지점(흐름 중간)에 둔다. 예: 조회(읽기)는 상태 변경이 없어 불변식을 표기하지 않는다.
- **예외**는 도메인이 `throw`하고 전역 핸들러(`@RestControllerAdvice`)가 HTTP 상태로 변환한다.
- **추상화 레벨은 다이어그램의 *의도*에 맞춘다. 참여자 수는 그 방아쇠다** — 참여자가 많아 핵심이 묻히면 *도메인 단위*로 올리고, 단일 도메인 흐름이면 *레이어드*(Controller/Service/Repository/DB)로 내린다. 단 둘이 충돌하면(참여자가 적어도 크로스 도메인 책임이 핵심이거나, 많아도 레이어 책임이 핵심) **의도가 이긴다**.
- **한 다이어그램 안에서는 추상화 레벨을 통일**한다. 레이어드 흐름에서는 *주 도메인*의 Repository→DB까지 표기하고, *다른 도메인의 협력 Service*는 블랙박스(Service 호출까지만)로 둔다 — 일부만 DB까지 내려가면 잘못된 강조가 생긴다. **단 협력 도메인의 DB 접근 자체가 흐름의 핵심이면(예: 상품 목록 조회의 재고 일괄 조회) 그 도메인도 Repository→DB까지 펼쳐 대칭을 맞춘다** — 핵심을 블랙박스로 가리면 오히려 잘못된 강조가 된다.
- 각 다이어그램 상단에 **추상화 레벨 라벨**(`도메인 단위` / `레이어드 아키텍처`)을 붙인다. 도메인 단위일 때는 도메인↔레이어 매핑도 한 줄 적는다.
- **01 요구사항의 상태 전이와 02 시퀀스의 종료 상태가 일치해야 한다** — 주문 생성 primary 다이어그램은 `status=CREATED`로 끝낸다. 결제 성공/실패(`SUCCEEDED`/`FAILED`) 분기는 본 라운드 미구현이므로 별도 *확장 다이어그램*으로 분리한다.

---

## 1. 상품 목록 조회
> 추상화 레벨: **레이어드 아키텍처**

```mermaid
sequenceDiagram
    actor C as 고객
    participant Ctl as ProductController
    participant F as ProductFacade
    participant PS as ProductService
    participant PR as ProductRepository
    participant SS as StockService
    participant SR as StockRepository
    participant DB as DB

    C->>+Ctl: 상품 목록 조회 요청
    Note over Ctl: 사전 조건 — 인증 불필요(공개 조회), 정렬키는 허용된 값(latest·price_asc·likes_desc)
    Ctl->>+F: search(brandId, sort, page, size)

    F->>+PS: search(brandId, sort, page, size)
    opt 잘못된 정렬키 / size 범위 초과
        PS-->>C: throw CoreException(BAD_REQUEST) → 전역 핸들러 400
    end
    PS->>+PR: 상품 목록 조회 (브랜드 필터 · 정렬 · 페이징 · soft delete 제외)
    PR->>+DB: SELECT
    DB-->>-PR: 결과
    PR-->>-PS: Page<ProductModel>
    PS-->>-F: Page<ProductModel>

    F->>+SS: getQuantities(productIds) — 현재 페이지 상품들의 재고 일괄 조회
    SS->>+SR: findByProductIdIn(productIds)
    SR->>+DB: SELECT ... WHERE product_id IN (...)
    DB-->>-SR: 재고 목록
    SR-->>-SS: List<StockModel>
    SS-->>-F: Map<productId, quantity>

    Note over F: 상품 + 재고 메모리 병합 → ProductInfo (구매 가능 여부 = 재고 > 0)
    F-->>-Ctl: Page<ProductInfo>
    Note over Ctl: 사후 조건 — 각 상품의 구매 가능 여부 포함 응답
    Ctl-->>-C: 200 OK
```

> ProductFacade가 ProductService(페이지 조회) + StockService(재고 일괄 조회)를 *조회 합성*한다 — Stock은 독립 애그리거트(D13)라 DB 조인 대신 **앱 병합**으로 경계를 지킨다. Product로 정렬·페이징을 먼저 끝내고 그 페이지의 `product_id`만 `IN`으로 조회해 N+1을 피한다. 조회 합성이라 Facade `@Transactional`은 없다(D17).

---

## 2. 좋아요 등록
> 추상화 레벨: **레이어드 아키텍처**

```mermaid
sequenceDiagram
    actor C as 고객
    participant Ctl as LikeController
    participant F as LikeFacade
    participant S as LikeService
    participant PS as ProductService
    participant LR as LikeRepository
    participant DB as DB

    C->>+Ctl: 좋아요 등록 요청 (POST /products/{productId}/likes)
    Note over Ctl: 사전 조건 — 사용자 인증 완료
    Ctl->>+F: like(userId, productId)
    Note over F: @Transactional — LikeService + ProductService 합성
    F->>+S: like(userId, productId)
    Note over S,LR: 불변식 — 좋아요는 사용자·상품당 최대 1개 (UNIQUE 제약)
    S->>+LR: 존재 확인 (existsByUserIdAndProductId)
    LR->>+DB: 조회
    DB-->>-LR: 존재 여부
    LR-->>-S: true / false
    alt 미존재 — 신규 등록
        S->>+LR: save(좋아요)
        LR->>+DB: 저장
        DB-->>-LR: ok
        LR-->>-S: 저장 완료
    else 이미 존재 — 멱등
        Note over S: 상태 변화 없음
    end
    S-->>-F: created (true=신규 / false=기존)
    alt created — 실제 반영
        F->>+PS: incrementLikeCount(productId)
        Note over PS: UPDATE like_count + 1 WHERE 미삭제 — 매칭 0건이면 NOT_FOUND
        PS-->>-F: ok / throw CoreException(NOT_FOUND) → 404
    else 멱등 — 카운터 불변
        F->>+PS: requireExists(productId)
        PS-->>-F: ok / throw CoreException(NOT_FOUND) → 404
    end
    F-->>-Ctl: 완료
    Note over Ctl: 사후 조건 — 사용자-상품 좋아요 관계 존재
    Ctl-->>-C: 200 OK
```

> 카운터 증감을 **LikeFacade 합성**에 둔 이유(D7) — `LikeService`는 like row 멱등 처리만 맡고(boolean 반환), `LikeFacade`가 그 결과로 *실제 반영될 때만* `ProductService`의 카운터를 증감한다. `Service↔Service 금지` 규약상 `LikeService`가 `ProductService`를 직접 부를 수 없어 합성 자리는 Facade뿐이고, 멱등 반영 여부에 따라 Facade에 분기가 생긴다(`Facade 분기 금지`와 충돌하나 두 규약 상충 시 `Service↔Service 금지`를 우선). `like_count`는 약한 일관성(D3).
> 동시 등록 레이스(두 요청이 동시에 `existsBy`를 통과)는 `UNIQUE(user_id, product_id)` 위반으로 한쪽이 실패한다 — 위반 예외 처리는 동시성 라운드에서 합류(D6).

---

## 3. 좋아요 취소
> 추상화 레벨: **레이어드 아키텍처**

```mermaid
sequenceDiagram
    actor C as 고객
    participant Ctl as LikeController
    participant F as LikeFacade
    participant S as LikeService
    participant PS as ProductService
    participant LR as LikeRepository
    participant DB as DB

    C->>+Ctl: 좋아요 취소 요청 (DELETE /products/{productId}/likes)
    Note over Ctl: 사전 조건 — 사용자 인증 완료
    Ctl->>+F: unlike(userId, productId)
    Note over F: @Transactional — LikeService + ProductService 합성
    F->>+S: unlike(userId, productId)
    S->>+LR: 좋아요 삭제 (deleteByUserIdAndProductId)
    LR->>+DB: 삭제
    DB-->>-LR: 삭제 건수 (1 = 삭제 / 0 = 없음)
    LR-->>-S: 삭제 건수
    S-->>-F: deleted (true=삭제됨 / false=없음)
    alt deleted — 실제 반영
        F->>+PS: decrementLikeCount(productId)
        PS-->>-F: ok
    else 멱등 — 카운터 불변
        F->>+PS: requireExists(productId)
        PS-->>-F: ok / throw CoreException(NOT_FOUND) → 404
    end
    F-->>-Ctl: 완료
    Note over Ctl: 사후 조건 — 좋아요 관계 없음
    Ctl-->>-C: 200 OK
```

---

## 4. 주문 생성

> 추상화 레벨: **도메인 단위** (참여자 다수 · 크로스 도메인 협력) — 주문 도메인 = OrderFacade+OrderService, 상품/재고 = 각 Service

```mermaid
sequenceDiagram
    actor C as 고객
    participant O as 주문 도메인
    participant P as 상품 도메인
    participant S as 재고 도메인

    C->>+O: 주문 요청 (items)
    Note over O: 사전 조건 — 인증 완료, 주문 상품 모두 존재

    O->>+P: 상품 조회 (스냅샷용)
    P-->>-O: 상품 정보
    opt 상품 없음 / 삭제됨
        O-->>C: 404 NOT_FOUND
    end
    O->>+S: 재고 차감
    S-->>-O: 차감 결과
    opt 재고 부족
        O-->>C: 409 CONFLICT
    end
    Note over O: 주문 저장 (스냅샷, status=CREATED)
    O-->>-C: 201 CREATED
    Note over O: 사후 조건 — 주문 status=CREATED (결제는 다음 라운드)
```

> 본 라운드 구현 범위는 **주문 생성·재고 차감까지**이며 주문은 `CREATED`로 종료한다(01 §4.4). 결제 호출과 성공/실패 분기는 아래 *5. 주문 결제*로 분리했다.

---

## 5. 주문 결제 (다음 라운드 확장)

> 추상화 레벨: **도메인 단위** (다음 라운드 확장) — 주문/재고/결제 = 각 Service. **본 라운드 미구현 — 흐름 합의용**

```mermaid
sequenceDiagram
    actor C as 고객
    participant O as 주문 도메인
    participant S as 재고 도메인
    participant Pay as 결제 도메인
    participant PG as 외부 PG

    Note over C,PG: ⚠️ 다음 라운드 확장 — 진입 시점에 주문 status=CREATED (4. 주문 생성 이후)

    O->>+Pay: 결제 요청 (orderId, amount)
    Pay->>+PG: 결제 호출
    PG-->>-Pay: 결과
    Note over Pay: 결제 내역 저장
    Pay-->>-O: 결제 결과 (성공/실패)

    alt 결제 성공
        Note over O: status=SUCCEEDED
        O-->>C: 200 OK
    else 결제 실패
        O->>+S: 재고 복원
        S-->>-O: ok
        Note over O: status=FAILED
        O-->>C: 502 PAYMENT_FAILED
    end
    Note over O: 사후 조건 — 주문 상태 확정(SUCCEEDED/FAILED), 실패 시 재고 원복
```
