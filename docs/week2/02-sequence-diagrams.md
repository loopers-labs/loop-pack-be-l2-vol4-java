# 시퀀스 다이어그램

비자명한 결정이 있는 흐름만 골라, **도메인 협력**과 **구현 결정** 두 관점으로 그린다.

- **도메인 협력**: 누가 무엇을 책임지는가. 도메인 객체 어휘만 쓴다.
- **구현 결정**: 트랜잭션 경계와 레이어 흐름. 채택한 방식과 버린 대안을 함께 기록한다.

---

## 그릴 대상

| 흐름 | 이유 |
|---|---|
| 브랜드 삭제 | 연쇄 효과 + 멱등성 정책 |
| 좋아요 토글 | 멱등성 보장 위치 + like_count 갱신 주체 |
| 주문 생성 | 부분 주문 + 재고 분리 + 스냅샷 — 결정이 가장 많은 흐름 |

### 그리지 않은 흐름

| 흐름 | 이유 |
|---|---|
| 브랜드/상품 조회 · 등록 · 수정 | 단일 객체 CRUD, 화살표 방향이 자명 |
| 내 좋아요 목록 조회 | 권한 확인(403) 외에 객체 간 협력 없음 |
| 주문 조회 (회원 · 어드민) | 권한 분기 외 협력 없음 |
| 상품 삭제 | 단일 객체 soft delete, cascade 없음 |

---

## 1. 브랜드 삭제

### 도메인 협력

**왜 이 흐름인가**  
브랜드 삭제는 소속 상품 삭제를 연쇄 트리거한다. "브랜드 없는 상품은 없다"는 불변식의 주인이 누구인지, 그리고 이미 삭제된 브랜드에 재삭제 요청이 오면 어떻게 볼 것인지 결정해야 한다.

```mermaid
sequenceDiagram
    actor A as 어드민
    participant B as 브랜드
    participant P as 상품

    A->>+B: 브랜드 삭제 요청
    alt 존재하지 않거나 이미 삭제된 브랜드
        B-->>-A: 없음 (404)
    else 활성 브랜드
        B->>+P: 소속 상품 전체 삭제
        P-->>-B: 완료
        B->>B: 자신을 삭제
        B-->>-A: 삭제 완료
    end
```

**읽는 포인트**
- "브랜드 없는 상품" 불변식은 *브랜드*가 소유한다 — 브랜드가 직접 상품 삭제를 트리거하는 이유.
- 이미 삭제된 브랜드 재삭제는 **404**로 처리한다. 어드민 도구에서 존재하지 않는 리소스 조작 시도는 오류로 알려주는 것이 맞다고 판단했다. (참고: 멱등 성공으로 처리하는 방식도 있으나 요구사항에 명시된 정책을 따른다.)

### 구현 결정

**검증할 결정**  
Brand + Product 두 애그리거트를 한 TX 안에서 삭제한다. "한 TX = 한 애그리거트" 원칙의 의도적 예외다.

```mermaid
sequenceDiagram
    actor A as 어드민
    participant C as BrandController
    participant F as BrandFacade
    participant BS as BrandService
    participant PS as ProductService
    participant BR as BrandRepository
    participant PR as ProductRepository

    A->>+C: 브랜드 삭제 요청
    C->>+F: deleteBrand(brandId)
    Note over F: TX 시작
    F->>+BS: 브랜드 조회
    BS->>+BR: findById
    alt 없음 / 이미 삭제
        BR-->>-BS: null
        BS-->>-F: 없음
        F-->>-C: 404 Not Found
        C-->>-A: 404 Not Found
    else 활성 브랜드
        BR-->>-BS: brand
        BS-->>-F: brand
        F->>+PS: 소속 상품 일괄 soft delete
        PS->>+PR: softDeleteAllByBrandId
        PR-->>-PS: 완료
        PS-->>-F: 완료
        F->>+BS: 브랜드 soft delete
        BS->>+BR: softDelete
        BR-->>-BS: 완료
        BS-->>-F: 완료
        Note over F: TX 커밋
        F-->>-C: 200 OK
        C-->>-A: 200 OK
    end
```

**결정과 대안**
- **단일 TX 채택** — 상품 삭제 후 브랜드 삭제 실패 시 "상품 없는 활성 브랜드"가 남는 상황을 방지한다. 부분 실패가 아예 없다.
- 대안: 브랜드 삭제 이벤트 발행 → 상품 삭제 핸들러로 분리. TX 부담은 줄지만 어드민 응답 시점에 상품이 아직 삭제되지 않은 상태가 노출될 수 있다. 어드민 직후 재조회 시 불일치가 보여 채택하지 않았다.

---

## 2. 좋아요 토글 (등록 · 취소)

### 도메인 협력

**왜 이 흐름인가**  
"한 회원 - 한 상품 = 좋아요 1개" 불변식을 누가 지키는지, like_count를 *좋아요*가 계산할지 *상품*이 직접 소유할지 결정해야 한다.

```mermaid
sequenceDiagram
    actor M as 회원
    participant L as 좋아요
    participant P as 상품

    M->>+L: 좋아요 등록 요청
    alt 이미 좋아요한 상품
        L-->>-M: 변동 없이 성공
    else
        L->>L: 좋아요 기록
        L->>+P: 좋아요 수 +1
        P-->>-L: 완료
        L-->>-M: 등록 완료
    end

    M->>+L: 좋아요 취소 요청
    alt 좋아요한 적 없음
        L-->>-M: 변동 없이 성공
    else
        L->>L: 좋아요 해제
        L->>+P: 좋아요 수 -1
        P-->>-L: 완료
        L-->>-M: 취소 완료
    end
```

**읽는 포인트**
- 중복 방지 불변식의 주인은 *좋아요* — 상품은 자기 재고나 가격을 신경 쓰고, 중복 좋아요 여부는 모른다.
- like_count 갱신 주인은 *상품* — 자신의 인기 수치를 외부 집계에 맡기지 않는다.
- 등록/취소 모두 멱등: 이미 원하는 상태라면 그냥 성공 반환.

### 구현 결정

**검증할 결정**  
멱등성 보장을 애플리케이션 분기(exists 조회)로 할지 DB 제약(unique)으로 할지. like_count를 비정규화 컬럼에 둘지 매번 집계할지.

```mermaid
sequenceDiagram
    actor M as 회원
    participant C as LikeController
    participant F as LikeFacade
    participant LS as LikeService
    participant PS as ProductService
    participant LR as LikeRepository
    participant PR as ProductRepository

    Note over M,PR: 등록
    M->>+C: 좋아요 등록 요청
    C->>+F: addLike(userId, productId)
    Note over F: TX 시작
    F->>+LS: 좋아요 저장 시도
    LS->>+LR: save (DB unique 제약)
    alt 이미 좋아요 (unique 충돌)
        LR-->>-LS: 충돌
        LS-->>-F: 이미 존재
        Note over F: TX 커밋 — 수 변동 없음
        F-->>-C: 200 OK
        C-->>-M: 200 OK
    else 신규
        LR-->>-LS: 저장 완료
        LS-->>-F: 완료
        F->>+PS: like_count 증가
        PS->>+PR: increment
        PR-->>-PS: 완료
        PS-->>-F: 완료
        Note over F: TX 커밋
        F-->>-C: 200 OK
        C-->>-M: 200 OK
    end

    Note over M,PR: 취소
    M->>+C: 좋아요 취소 요청
    C->>+F: removeLike(userId, productId)
    Note over F: TX 시작
    F->>+LS: 좋아요 삭제
    LS->>+LR: deleteIfExists
    alt 삭제 행 없음
        LR-->>-LS: 0 rows
        LS-->>-F: 없음
        Note over F: TX 커밋 — 수 변동 없음
        F-->>-C: 200 OK
        C-->>-M: 200 OK
    else 삭제 성공
        LR-->>-LS: 1 row
        LS-->>-F: 완료
        F->>+PS: like_count 감소
        PS->>+PR: decrement
        PR-->>-PS: 완료
        PS-->>-F: 완료
        Note over F: TX 커밋
        F-->>-C: 200 OK
        C-->>-M: 200 OK
    end
```

**결정과 대안**
- **DB unique 제약으로 멱등성 보장** — exists 조회 후 save는 동시 요청 시 둘 다 exists=false를 읽고 둘 다 저장을 시도하는 race condition이 발생한다. DB 제약은 최후 방어선으로 항상 유효하다.
- **like_count 비정규화 컬럼 채택** — `sort=likes_desc` 정렬 시 집계 쿼리 없이 인덱스만으로 처리 가능. 동일 TX 내 갱신이라 정합성은 보장된다.
- 대안: likes 테이블 COUNT 집계 — 정합 고민이 사라지지만 인기순 정렬마다 전체 집계가 발생한다. likes_desc 정렬이 요구사항에 명시되어 있어 채택하지 않았다.

---

## 3. 주문 생성

### 도메인 협력

**왜 이 흐름인가**  
올-오어-낫싱 정책, 재고 분리, 스냅샷 책임 — 세 가지 비자명한 결정이 한 흐름에 묶여 있다. 특히 "하나라도 품절이면 전체 실패"는 트랜잭션 롤백으로 이미 차감된 재고를 어떻게 복구하는지가 핵심이다.

```mermaid
sequenceDiagram
    actor M as 회원
    participant O as 주문
    participant S as 재고
    participant OI as 주문 항목

    M->>+O: 상품·수량 목록으로 주문 요청
    loop 각 상품
        O->>+S: 재고 차감 요청(수량)
        alt 재고 부족 / 상품 없음 / 삭제된 상품
            S-->>-O: 차감 거부
            Note over O: 주문 전체 실패<br/>(이미 차감된 재고는 원복)
        else 재고 충분
            S-->>-O: 차감 완료 + 차감 시점 상품 정보
            O->>+OI: 주문 시점 정보 보존 (상품명·가격·브랜드명)
            OI-->>-O: 완료
        end
    end
    O->>O: 총액 계산
    O-->>-M: 주문 확정
```

**읽는 포인트**
- 재고 차감 권한은 *재고* — 상품 정보와 재고를 분리해 상품 수정과 재고 차감이 서로 블로킹되지 않는다.
- 올-오어-낫싱: 하나라도 차감 거부되면 주문 전체가 실패하고, 이미 차감된 재고는 원복된다. B2C에서 사용자 동의 없이 결제 금액이 바뀌어서는 안 되기 때문이다.
- 스냅샷 주인은 *주문 항목* — 이후 상품 가격·이름 변경과 완전히 격리된다.

### 구현 결정

**검증할 결정**  
단일 TX 안에서 재고 차감 실패 시 이미 차감된 항목을 어떻게 원복할지. 재고를 원자적으로 차감하는 방식. 스냅샷을 언제 저장할지.

```mermaid
sequenceDiagram
    actor M as 회원
    participant C as OrderController
    participant F as OrderFacade
    participant SS as StockService
    participant OS as OrderService
    participant SR as StockRepository
    participant OR as OrderRepository
    participant SN as SnapshotRepository

    M->>+C: 주문 요청 (상품·수량 목록)
    C->>+F: createOrder(userId, items)
    Note over F: TX 시작
    loop 각 항목
        F->>+SS: 재고 차감 시도
        SS->>+SR: UPDATE stocks SET quantity = quantity - ? WHERE quantity >= ?
        alt 차감 실패 (재고 부족 / 없음 / 삭제)
            SR-->>-SS: 0 rows affected
            SS-->>-F: 차감 실패
        else 차감 성공
            SR-->>-SS: 1 row affected
            SS-->>-F: 차감 완료 + 상품 스냅샷 정보
        end
    end
    alt 차감 실패 항목 있음
        Note over F: TX 롤백 — 이미 차감된 항목 자동 원복
        F-->>-C: 400 Bad Request (품절 상품 식별 정보 포함)
        C-->>-M: 400 Bad Request
    else 전체 차감 성공
        F->>+OS: 주문 생성 (항목 · 총액)
        OS->>+OR: 주문 저장
        OR-->>-OS: 완료
        OS->>+SN: 스냅샷 저장 (상품명·가격·브랜드명)
        SN-->>-OS: 완료
        OS-->>-F: 완료
        Note over F: TX 커밋
        F-->>-C: 200 OK
        C-->>-M: 200 OK
    end
```

**결정과 대안**
- **TX 롤백으로 재고 원복** — 차감 실패 시 예외를 던져 TX를 롤백하면 이미 차감된 항목도 자동으로 원복된다. 수동으로 "이미 차감된 항목을 다시 더해주는" 보상 로직이 필요 없다.
- **원자적 재고 차감 채택** — UPDATE 문에 `WHERE quantity >= ?` 조건을 포함해 영향 행이 0이면 실패로 처리한다. SELECT 후 UPDATE는 동시 요청 시 두 요청이 모두 재고 있음을 읽고 둘 다 차감해 음수가 될 수 있다.
- 대안 1: 비관적 락(SELECT FOR UPDATE) — 직관적이나 다항목 주문에서 락 순서 차이로 데드락 가능. 재고 테이블을 분리한 이유(블로킹 분리)와도 상충한다.
- 대안 2: 낙관적 락(version 컬럼) + 재시도 — 경합이 적을 때 유리하나 핫상품에서 재시도 폭증. 현 범위에서 과설계.
- **스냅샷은 OrderService 내부, 동일 TX** — 차감 성공 시 SS가 상품 정보를 함께 반환하므로 별도 조회 없이 일관된 데이터로 저장 가능. 주문 저장과 스냅샷 저장이 같은 TX이므로 둘 중 하나 실패 시 모두 롤백된다.

---

## 참고

- [01-requirements.md](./01-requirements.md) — 도메인 정책, 유스케이스 본문, 비기능 요구사항
