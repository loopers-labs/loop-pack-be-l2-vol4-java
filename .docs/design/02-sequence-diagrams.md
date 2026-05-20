# 02. 시퀀스 다이어그램

### 시퀀스다이어그램 표기 규칙

- **사전 조건 · 불변식 · 사후 조건은 해당 흐름에 *실재할 때만* 표기**한다 (없으면 누락이 아니라 그 조건이 없다는 뜻). 예: 좋아요 취소는 강제할 불변식이 없어 불변식이 없고, 조회는 상태 불변이라 셋 다 없다.
- **TX1 · TX2 · TX3** 은 트랜잭션 경계 — 외부 I/O(PG 호출)는 트랜잭션 밖.
- **예외**는 Service가 `throw`하고 전역 핸들러(`@RestControllerAdvice`)가 HTTP 상태로 변환한다.

---

## 1. 주문 생성

```mermaid
sequenceDiagram
    actor C as 고객
    participant Ord as 주문 도메인
    participant Prod as 상품 도메인
    participant Pay as 결제 도메인
    participant PG as 외부 PG

    C->>+Ord: POST /api/v1/orders (items)
    Note over Ord: 사전 조건 — 사용자 인증 완료, 모든 주문 상품 존재

    Note over Ord,Prod: TX1 — 주문 생성 + 재고 차감 (짧은 TX, PG 호출 없음)
    Ord->>+Prod: 상품 존재·가격 조회 (스냅샷용)
    Prod-->>-Ord: 상품 정보
    opt 상품 없음 / 삭제됨
        Ord-->>C: 404
    end
    Ord->>+Prod: 재고 차감 (상품 ID 정렬 후 순차 호출)
    Prod-->>-Ord: 차감 결과
    opt 재고 부족
        Ord-->>C: 409 CONFLICT
    end
    Note over Ord: 주문 저장 (items 스냅샷, status=CREATED) → orderId

    Note over Ord,PG: TX2 — 결제 도메인 내부 (PG 호출은 TX 밖)
    Ord->>+Pay: 결제 요청 (orderId, totalAmount)
    Note over Pay: 포인트 차감 → PG 호출 → Payment 저장 (usedPoint + pgAmount)
    Pay->>+PG: 결제 호출
    PG-->>-Pay: 결과
    Note over Pay: 실패 시 자체 보상 — 포인트 환원 + PG 취소
    Pay-->>-Ord: PaymentResult (status, reason)

    Note over Ord,Prod: TX3 — 결과 반영 (실패 시 재고 보상 + 주문 상태 확정)
    alt 결제 성공
        Ord-->>C: 200 OK (orderId, SUCCEEDED)
    else 결제 실패
        Ord->>+Prod: 재고 복원
        Prod-->>-Ord: ok
        Ord-->>C: 402 PAYMENT_FAILED
    end
    Note over Ord: 사후 조건 — 재고 차감 확정 + 주문 상태 확정 (SUCCEEDED 또는 FAILED)
    deactivate Ord
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
    Note over S,LR: 불변식 — (userId, productId) 좋아요는 최대 1개 (UNIQUE)
    S->>+LR: 좋아요 저장 (원자적)
    LR->>+DB: INSERT IGNORE
    DB-->>-LR: affected_rows (1 = 신규 / 0 = 이미 존재)
    LR-->>-S: 등록 처리 (멱등)
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
    participant LR as LikeRepository
    participant DB as DB

    C->>+Ctl: 좋아요 취소 요청 (DELETE /products/{productId}/likes)
    Ctl->>+S: unlike(userId, productId)
    Note over S: 사전 조건 — 사용자 인증 완료
    S->>+LR: 좋아요 삭제 (원자적)
    LR->>+DB: DELETE
    DB-->>-LR: affected_rows (1 = 삭제 / 0 = 없음)
    LR-->>-S: 취소 처리 (멱등)
    Note over S: 사후 조건 — 좋아요 관계 없음
    S-->>-Ctl: liked = false
    Ctl-->>-C: 200 OK
```
---

## 상품 조회 (목록 / 상세)
```mermaid
sequenceDiagram
    actor C as 고객
    participant Ctl as ProductController
    participant S as ProductService
    participant R as ProductRepository
    participant DB as DB

    alt 상품 목록 (GET /products?brandId=&sort=&page=&size=)
        C->>+Ctl: 목록 조회 요청
        Ctl->>+S: search(criteria)
        S->>+R: search(criteria)
        R->>+DB: 조회 (soft delete 제외 + brandId 필터 + sort(latest/price_asc/likes_desc) + 페이징)
        DB-->>-R: rows
        R-->>-S: Page<Product>
        S-->>-Ctl: Page<ProductInfo>
        Ctl-->>-C: 200 OK (Page)
    else 상품 상세 (GET /products/{productId})
        C->>+Ctl: 상세 조회 요청
        Ctl->>+S: getById(productId)
        S->>+R: findById (soft delete 제외)
        R->>+DB: 조회
        DB-->>-R: row / empty
        R-->>-S: Product / empty
        opt 없음 / 삭제됨
            S-->>C: throw CoreException(NOT_FOUND) → 전역 핸들러 404
        end
        S-->>-Ctl: ProductInfo
        Ctl-->>-C: 200 OK
    end
```

## 브랜드 조회
```mermaid
sequenceDiagram
    actor C as 고객
    participant Ctl as BrandController
    participant S as BrandService
    participant R as BrandRepository
    participant DB as DB

    C->>+Ctl: 브랜드 조회 요청 (GET /brands/{brandId})
    Ctl->>+S: getById(brandId)
    S->>+R: findById (soft delete 제외)
    R->>+DB: 조회
    DB-->>-R: row / empty
    R-->>-S: Brand / empty
    opt 없음 / 삭제됨
        S-->>C: throw CoreException(NOT_FOUND) → 전역 핸들러 404
    end
    S-->>-Ctl: BrandInfo
    Ctl-->>-C: 200 OK
```
