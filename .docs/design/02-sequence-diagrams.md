# 02. 시퀀스 다이어그램

### 시퀀스다이어그램 표기 규칙

- **사전 조건 · 불변식 · 사후 조건은 해당 흐름에 *실재할 때만* 표기**한다 (없으면 누락이 아니라 그 조건이 없다는 뜻). 예: 좋아요 취소는 강제할 불변식이 없어 불변식을 표기하지 않는다.
- **예외**는 도메인이 `throw`하고 전역 핸들러(`@RestControllerAdvice`)가 HTTP 상태로 변환한다.
- **복잡도가 높아지면 추상화 단계를 올린다** — participant가 많아 핵심 흐름이 묻히면 *도메인 단위*(주문/상품/재고 도메인 등) 또는 *레이어드 아키텍처 단위*(Controller/Facade/Service/Repository)로 표기한다. 단, **한 다이어그램 안에서는 모든 요소를 같은 추상화 레벨로 통일**한다 — 일부 요소만 Repository·SQL까지 내려가면 흐름이 난잡해지고 잘못된 강조가 생긴다.

---

## 1. 주문 생성

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
        O-->>C: 402 PAYMENT_FAILED
    end
    Note over O: 사후 조건 — 주문 상태 확정, 실패 시 재고 원복
    deactivate O
```

---

## 좋아요 등록

```mermaid
sequenceDiagram
    actor C as 고객
    participant Ctl as LikeController
    participant S as LikeService
    participant PR as ProductRepository
    participant LR as LikeRepository
    participant DB as DB

    C->>+Ctl: 좋아요 등록 요청 (POST /products/{productId}/likes)
    Ctl->>+S: like(userId, productId)
    Note over S: 사전 조건 — 사용자 인증 완료, 상품 존재
    S->>+PR: 상품 존재 확인
    PR->>+DB: 상품 조회 (soft delete 제외)
    DB-->>-PR: 조회 결과
    PR-->>-S: 상품 / empty
    opt 상품 미존재 / 삭제됨
        S-->>C: throw CoreException(NOT_FOUND) → 전역 핸들러 404
    end
    Note over S,LR: 불변식 — 좋아요는 최대 1개
    S->>+LR: existsByUserIdAndProductId(userId, productId)
    LR->>+DB: SELECT
    DB-->>-LR: 존재 여부
    LR-->>-S: true / false
    alt 미존재
        S->>+LR: save(좋아요)
        LR->>+DB: INSERT
        DB-->>-LR: ok
        LR-->>-S: 저장 완료
        S->>+PR: 상품 like_count +1
        PR->>+DB: UPDATE
        DB-->>-PR: ok
        PR-->>-S: 갱신 완료
    else 이미 존재 (멱등 — 아무것도 하지 않음)
        Note over S: 상태 변화 없음 (카운터 불변)
    end
    Note over S: 사후 조건 — 사용자-상품 좋아요 관계 존재
    S-->>-Ctl: liked = true
    Ctl-->>-C: 200 OK
```

## 좋아요 취소

```mermaid
sequenceDiagram
    actor C as 고객
    participant Ctl as LikeController
    participant S as LikeService
    participant PR as ProductRepository
    participant LR as LikeRepository
    participant DB as DB

    C->>+Ctl: 좋아요 취소 요청 (DELETE /products/{productId}/likes)
    Ctl->>+S: unlike(userId, productId)
    Note over S: 사전 조건 — 사용자 인증 완료
    S->>+LR: 좋아요 삭제
    LR->>+DB: DELETE
    DB-->>-LR: affected_rows (1 = 삭제 / 0 = 없음)
    LR-->>-S: 삭제 건수
    alt 실제 삭제됨 (1)
        S->>+PR: 상품 like_count -1
        PR->>+DB: UPDATE
        DB-->>-PR: ok
        PR-->>-S: 갱신 완료
    else 없음 (0 — 멱등)
        Note over S: 상태 변화 없음
    end
    Note over S: 사후 조건 — 좋아요 관계 없음
    S-->>-Ctl: liked = false
    Ctl-->>-C: 200 OK
```
