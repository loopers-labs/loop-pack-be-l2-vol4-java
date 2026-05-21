# 02. 시퀀스 다이어그램

### 시퀀스다이어그램 표기 규칙

- **사전 조건 · 불변식 · 사후 조건은 해당 흐름에 *실재할 때만* 표기**한다 (없으면 누락이 아니라 그 조건이 없다는 뜻). **사전 조건은 진입점(Controller; 도메인 단위 다이어그램이면 진입 도메인)에, 사후 조건은 응답 직전에** 둔다 — 흐름 중간이 아니라 *경계*에 둬야 계약이 또렷하다. 불변식은 강제되는 지점(흐름 중간)에 둔다. 예: 조회(읽기)는 상태 변경이 없어 불변식을 표기하지 않는다.
- **예외**는 도메인이 `throw`하고 전역 핸들러(`@RestControllerAdvice`)가 HTTP 상태로 변환한다.
- **추상화 레벨은 다이어그램의 *의도*에 맞춘다. 참여자 수는 그 방아쇠다** — 참여자가 많아 핵심이 묻히면 *도메인 단위*로 올리고, 단일 도메인 흐름이면 *레이어드*(Controller/Service/Repository/DB)로 내린다. 단 둘이 충돌하면(참여자가 적어도 크로스 도메인 책임이 핵심이거나, 많아도 레이어 책임이 핵심) **의도가 이긴다**.
- **한 다이어그램 안에서는 추상화 레벨을 통일**한다. 레이어드 흐름에서는 *주 도메인*의 Repository→DB까지 표기하고, *다른 도메인의 협력 Service*는 블랙박스(Service 호출까지만)로 둔다 — 일부만 DB까지 내려가면 잘못된 강조가 생긴다.
- 각 다이어그램 상단에 **추상화 레벨 라벨**(`도메인 단위` / `레이어드`)을 붙인다. 도메인 단위일 때는 도메인↔레이어 매핑도 한 줄 적는다.

---

## 1. 상품 목록 조회

> 추상화 레벨: **레이어드** (단일 도메인 조회) — ProductFacade는 1:1 위임

```mermaid
sequenceDiagram
    actor C as 고객
    participant Ctl as ProductController
    participant F as ProductFacade
    participant PS as ProductService
    participant PR as ProductRepository
    participant DB as DB

    C->>+Ctl: 상품 목록 조회 요청 (GET /products?brandId=&sort=likes_desc&page=0&size=20)
    Note over Ctl: 사전 조건 — 인증 불필요(공개 조회), 정렬키는 허용된 값(latest·price_asc·likes_desc)
    Ctl->>+F: search(brandId, sort, page, size)
    F->>+PS: search(brandId, sort, page, size) — 1:1 위임
    opt 잘못된 정렬키 / size 범위 초과
        PS-->>C: throw CoreException(BAD_REQUEST) → 전역 핸들러 400
    end
    PS->>+PR: 상품 목록 조회 (브랜드 필터 · 정렬 · 페이징 · soft delete 제외)
    PR->>+DB: 조회
    DB-->>-PR: 결과
    PR-->>-PS: Page<ProductModel>
    PS-->>-F: Page<ProductInfo>
    F-->>-Ctl: Page<ProductInfo>
    Note over Ctl: 사후 조건 — 구매 가능한 재고 응답
    Ctl-->>-C: 200 OK
```

---

## 2. 좋아요 등록

> 추상화 레벨: **레이어드** — LikeFacade는 1:1 위임. 주 도메인 Like는 LikeRepository→DB, 협력 Product는 ProductService 블랙박스(상품 조회 + like_count 증감)

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
    Note over Ctl: 사전 조건 — 사용자 인증 완료, 상품 존재
    Ctl->>+F: like(userId, productId)
    F->>+S: like(userId, productId) — 1:1 위임
    S->>+PS: getById(productId)
    PS-->>-S: 상품 / 없음
    opt 상품 미존재 / 삭제됨
        S-->>C: throw CoreException(NOT_FOUND) → 전역 핸들러 404
    end
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
        S->>PS: incrementLikeCount(productId)
    else 이미 존재 — 멱등
        Note over S: 상태 변화 없음 (카운터 불변)
    end
    S-->>-F: liked = true
    F-->>-Ctl: liked = true
    Note over Ctl: 사후 조건 — 사용자-상품 좋아요 관계 존재
    Ctl-->>-C: 200 OK
```

> 카운터 증감을 **LikeService 안**에 둔 이유(D7) — 등록·취소가 *실제 반영될 때만* 카운터를 바꾸는 **멱등 분기**가 핵심 유스케이스 흐름이라, Facade 분기 금지 규약상 Service에 둔다. `like_count`는 약한 일관성(D3)이라 Service↔Service 쓰기를 *이 카운터에 한해* 좁게 허용한다.
> 동시 등록 레이스(두 요청이 동시에 `existsBy`를 통과)는 `UNIQUE(user_id, product_id)` 위반으로 한쪽이 실패한다 — 위반 예외 처리는 동시성 라운드에서 합류(D6).

---

## 3. 좋아요 취소

> 추상화 레벨: **레이어드** — LikeFacade는 1:1 위임. 주 도메인 Like는 LikeRepository→DB, 협력 Product는 ProductService 블랙박스

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
    F->>+S: unlike(userId, productId) — 1:1 위임
    S->>+LR: 좋아요 삭제 (deleteByUserIdAndProductId)
    LR->>+DB: 삭제
    DB-->>-LR: 삭제 건수 (1 = 삭제 / 0 = 없음)
    LR-->>-S: 삭제 건수
    alt 실제 삭제됨 (1)
        S->>PS: decrementLikeCount(productId)
    else 없음 (0 — 멱등)
        Note over S: 상태 변화 없음
    end
    S-->>-F: liked = false
    F-->>-Ctl: liked = false
    Note over Ctl: 사후 조건 — 좋아요 관계 없음
    Ctl-->>-C: 200 OK
```

---

## 4. 주문 생성

> 추상화 레벨: **도메인 단위** (참여자 다수 · 크로스 도메인 협력) — 주문 도메인 = OrderFacade+OrderService, 상품/재고/결제 = 각 Service

```mermaid
sequenceDiagram
    actor C as 고객
    participant O as 주문 도메인
    participant P as 상품 도메인
    participant S as 재고 도메인
    participant Pay as 결제 도메인
    participant PG as 외부 PG

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
    Note over O: 사후 조건 — 주문 상태 확정, 실패 시 재고 원복
    deactivate O
```
