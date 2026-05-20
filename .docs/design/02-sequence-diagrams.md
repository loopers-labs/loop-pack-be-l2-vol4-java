# 02. 시퀀스 다이어그램

### 시퀀스다이어그램 표기 규칙

- **사전 조건 · 불변식 · 사후 조건은 해당 흐름에 *실재할 때만* 표기**한다 (없으면 누락이 아니라 그 조건이 없다는 뜻). 예: 좋아요 취소는 강제할 불변식이 없어 불변식을 표기하지 않는다.
- **TX1 · TX2 · TX3** 은 트랜잭션 경계 — 외부 I/O(PG 호출)는 트랜잭션 밖.
- **예외**는 Service가 `throw`하고 전역 핸들러(`@RestControllerAdvice`)가 HTTP 상태로 변환한다.

---

## 1. 주문 생성

```mermaid
sequenceDiagram
    actor C as 고객
    participant Ctl as OrderController
    participant F as OrderFacade
    participant PS as ProductService
    participant SS as StockService
    participant OS as OrderService
    participant Pay as PaymentService
    participant PG as 외부 PG

    C->>+Ctl: POST /api/v1/orders (items)
    Ctl->>+F: placeOrder(userId, items)
    Note over F: 사전 조건 — 사용자 인증 완료, 모든 주문 상품 존재

    Note over F,OS: TX1 — 주문 생성 + 재고 차감 (짧은 TX, PG 호출 없음)
    F->>+PS: 상품 존재·가격 조회 (스냅샷용)
    PS-->>-F: 상품 정보
    opt 상품 없음 / 삭제됨
        F-->>C: 404 NOT_FOUND
    end
    F->>+SS: 재고 차감 (상품 ID 정렬 후 순차 호출)
    SS-->>-F: 차감 결과
    opt 재고 부족
        F-->>C: 409 CONFLICT
    end
    F->>+OS: 주문 저장 (items 스냅샷, status=CREATED)
    OS-->>-F: orderId

    Note over F,PG: ▼ 이하 결제 흐름 — 본 라운드 미구현, 향후 흐름 표현만 ▼
    Note over F,PG: TX2 — 결제 도메인 내부 (PG 호출은 TX 밖)
    F->>+Pay: 결제 요청 (orderId, totalAmount)
    Note over Pay: PG 호출 → Payment 저장 (지불 사실 보존)
    Pay->>+PG: 결제 호출
    PG-->>-Pay: 결과
    Note over Pay: 실패 시 자체 보상 — PG 취소
    Pay-->>-F: PaymentResult (status, reason)

    Note over F,SS: TX3 — 결과 반영 (실패 시 재고 보상 + 주문 상태 확정)
    alt 결제 성공
        F->>OS: 주문 상태 SUCCEEDED 확정
        F-->>C: 200 OK (orderId, SUCCEEDED)
    else 결제 실패
        F->>+SS: 재고 복원
        SS-->>-F: ok
        F->>OS: 주문 상태 FAILED 확정
        F-->>C: 402 PAYMENT_FAILED
    end
    Note over F: 사후 조건(본 라운드) — 주문 CREATED + 재고 차감 확정
    deactivate F
    deactivate Ctl
```

## 의도적 단순화

- **결제 도메인 모델·테이블·정책은 정의하지 않음**
- **동시성 처리는 시퀀스 표현 수준에서만**.

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
    Note over S,LR: 불변식 — (userId, productId) 좋아요는 최대 1개 (UNIQUE 최종 방어선)
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
