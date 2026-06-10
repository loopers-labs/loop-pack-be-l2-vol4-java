---
name: analyze-query
description:
  대상이 되는 코드 범위를 탐색하고, Spring @Transactional, JPA, QueryDSL 기반의 코드에 대해 트랜잭션 범위, 영속성 컨텍스트, 쿼리 실행 시점 관점에서 분석한다.
  
  특히 다음을 중점적으로 점검한다.
  - 트랜잭션이 불필요하게 크게 잡혀 있지는 않은지
  - 조회/쓰기 로직이 하나의 트랜잭션에 혼합되어 있지는 않은지
  - JPA의 지연 로딩, flush 타이밍, 변경 감지로 인해
    의도치 않은 쿼리 또는 락이 발생할 가능성은 없는지

  단순한 정답 제시가 아니라, 현재 구조의 의도와 trade-off를 드러내고 개선 가능 지점을 선택적으로 판단할 수 있도록 돕는다.
---

### 📌 Analysis Scope
이 스킬은 아래 대상에 대해 분석한다.
- @Transactional 이 선언된 클래스 / 메서드
- Service / Facade / Application Layer 코드
- JPA Entity, Repository, QueryDSL 사용 코드
- 하나의 유즈케이스(요청 흐름) 단위
> 컨트롤러 → 서비스 → 레포지토리 전체 흐름을 기준으로 분석하며 특정 메서드만 떼어내어 판단하지 않는다.

### 🔍 Analysis Checklist
#### 1. Transaction Boundary 분석
다음을 순서대로 확인한다.
- 트랜잭션 시작 지점은 어디인가?
    - Service / Facade / 그 외 계층?
- 트랜잭션이 실제로 필요한 작업은 무엇인가?
    - 상태 변경 (쓰기)
    - 단순 조회
- 트랜잭션 내부에서 수행되는 작업 나열
    - 외부 API 호출
    - 복잡한 조회(QueryDSL)
    - 반복문 기반 처리

**출력 예시**
```markdown
- 현재 트랜잭션 범위:
OrderFacade.placeOrder()
  ├─ 유저 검증
  ├─ 상품 조회
  ├─ 주문 생성
  ├─ 결제 요청
  └─ 재고 차감

- 트랜잭션이 필요한 핵심 작업:
- 주문 생성
- 재고 차감
```

#### 2. 불필요하게 큰 트랜잭션 식별
아래 패턴이 존재하는지 점검한다.
- Controller 에서 Transactional 이 사용되고 있음
- 읽기 전용 로직이 쓰기 트랜잭션에 포함됨
- 외부 시스템 호출이 트랜잭션 내부에 포함됨
- 트랜잭션 내부에서 대량 조회 / 복잡한 QueryDSL 실행
- 상태 변경 이후에도 트랜잭션이 길게 유지됨

**문제 후보 예시**
- 결제 API 호출이 트랜잭션 내부에 포함되어 있음
- 주문 생성 이후 추천 상품 조회 로직까지 동일 트랜잭션에 포함됨

#### 3. JPA / 영속성 컨텍스트 관점 분석
다음을 중심으로 분석한다.
- Entity 변경이 언제 flush 되는지
- 조회용 Entity가 변경 감지 대상이 되는지
- 지연 로딩으로 인해 트랜잭션 후반에 쿼리가 발생할 가능성
- @Transactional(readOnly = true) 미적용 여부

**체크리스트 예시**
```markdown
- 단순 조회인데 Entity 반환 후 변경 가능성 존재?
- DTO Projection 대신 Entity 조회 사용 여부
- QueryDSL 조회 결과가 영속성 컨텍스트에 포함되는지
```

#### 4. Improvement Proposal (선택적 제안)
개선안은 강제하지 않고 선택지로 제시한다.
- 트랜잭션 분리
    - 조회 → 쓰기 분리
    - Facade에서 orchestration, Service는 최소 트랜잭션
- `@Transactional(readOnly = true)` 적용
- DTO Projection (읽기 전용 모델) 도입
- 외부 호출 / 이벤트 발행을 트랜잭션 외부로 이동
- Application Service / Domain Service 책임 재조정

**개선안 예시**
```markdown
[개선안 1]
- 주문 생성과 결제 요청을 분리
- 주문 생성까지만 트랜잭션 유지
- 결제 요청은 트랜잭션 종료 후 수행

[고려 사항]
- 결제 실패 시 주문 상태 관리 필요
- 보상 트랜잭션 또는 상태 전이 설계 필요
```