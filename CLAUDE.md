# CLAUDE.md

본 문서는 본 프로젝트(loop-pack-be-l2-vol4-java)에서 Claude 가 코드를 작성·리뷰할 때 준수해야 할 규칙을 정의한다. 부트캠프 학습 목적이 우선이며, 멘토 리뷰 효율을 위해 일관성을 유지한다.

---

## 1. 객체지향 / 도메인 모델링 전략

### 1.1 역할의 분리
- **도메인 객체(Entity/VO)** 는 비즈니스 규칙과 불변식(invariant)을 **자기 자신** 안에 캡슐화한다. setter 로 외부에서 상태를 흔드는 식의 빈약한(anemic) 모델을 만들지 않는다.
- **도메인 서비스(Domain Service)** 는 *상태 없이* 도메인 객체들 간의 협력을 조정한다. 한 객체에 귀속시키기 부자연스러운 로직(예: 두 객체의 비교, 조합)일 때만 사용한다.
- **애플리케이션 서비스(Facade)** 는 도메인 객체/서비스를 조립해 유스케이스 한 줄을 완성한다. 비즈니스 규칙을 직접 가지지 않는다.

### 1.2 규칙 배치의 의사결정 기준
- 같은 규칙이 여러 서비스에 흩어진다면 **도메인 객체로 끌어올린다**.
- 한 도메인의 상태만 바꾼다면 → 그 도메인 객체의 메서드.
- 여러 도메인을 가로질러야 한다면 → 도메인 서비스.
- "DB 조회 → 가공 → 저장" 형태의 유스케이스 조립이라면 → Facade.

### 1.3 작성 시 확인 사항
- 책임이 명확한가? (한 클래스에 하나의 변경 이유)
- 결합도가 낮은가? (외부 시스템에 직접 의존하지 않음 — Repository **인터페이스** 의존)
- 의도가 코드로 드러나는가? (메서드 이름이 비즈니스 용어)
- 개발자가 의도를 기록했는가? — 모호한 결정은 PR 본문 "Context & Decision" 에 남긴다.

---

## 2. 아키텍처 / 패키지 구성 전략

### 2.1 레이어드 아키텍처 + DIP
본 프로젝트는 4개 레이어로 구성된다. 의존성 방향은 항상 안쪽(도메인)을 향한다.

```
interfaces → application → domain ← infrastructure
```

- **interfaces**: HTTP(REST) Controller, Request/Response DTO. 외부 입출력 변환만.
- **application**: 도메인을 조합해 유스케이스를 제공하는 Facade. 응용 레이어 전용 DTO(`XxxInfo`, `XxxCriteria`).
- **domain**: Entity, Value Object, Domain Service, Repository **인터페이스**. 비즈니스 규칙의 본거지.
- **infrastructure**: Repository 구현체 (JPA, Redis 등). 도메인 인터페이스를 구현해 DIP 를 완성한다.

### 2.2 DTO 분리 원칙
- API 의 Request/Response DTO 와 응용 레이어의 DTO 는 **분리**한다.
- 응용 레이어가 인터페이스 레이어의 DTO 를 import 하지 않는다 (역방향 의존 차단).
- 변환은 Controller 와 Facade 의 경계에서 한다.

### 2.3 패키지 트리
계층 우선, 그 하위 도메인 별로 패키징한다.

```
com.loopers
├── interfaces.api.<domain>      (Controller, V1Dto)
├── application.<domain>         (Facade, Info, Criteria)
├── domain.<domain>              (Model, Repository, Service, VO)
└── infrastructure.<domain>      (RepositoryImpl, JpaRepository)
```

도메인 디렉토리 이름은 단수형(`product`, `brand`, `like`, `order`) 으로 통일한다.

### 2.4 Repository 의 분리
- 인터페이스: `domain.<x>.XxxRepository`
- 구현체: `infrastructure.<x>.XxxRepositoryImpl` (Spring Data JPA 등 활용)
- 도메인은 영속성 기술을 모른다 → 도메인 코드에 `@Entity`, `@Column` 외 JPA 어노테이션이 leak 되지 않게 한다 (영속성 매핑 자체는 BaseEntity 와 함께 도메인에 두는 본 프로젝트 컨벤션을 유지).

---

## 3. 코드 작성 규칙

### 3.1 도메인 객체 작성 규칙
- `BaseEntity` 를 상속해 `id`, `createdAt`, `updatedAt`, `deletedAt` 을 공유한다.
- JPA 요구 사항인 기본 생성자는 `protected` 로 둔다.
- 생성자에서 모든 불변식을 검증한다. 부적합 시 `CoreException(ErrorType.BAD_REQUEST, "한글 메시지")` 을 던진다.
- setter 대신 의도가 드러나는 메서드 이름(`decreaseStock`, `incrementLikeCount`, `markPaid` …)을 노출한다.
- 외부에는 **getter 만** 노출하고, 상태 변경은 도메인 메서드를 통해서만 가능하게 한다.

### 3.2 예외 처리
- 비즈니스 규칙 위반은 `CoreException` 으로 통일. `ErrorType` 값으로 카테고리 표현.
- `BAD_REQUEST`: 입력값/불변식 위반.
- `NOT_FOUND`: 조회 대상 부재.
- `CONFLICT`: 중복/상태 충돌(예: 재고 부족, 동일 자원 중복 생성).
- 사용자에게 노출되는 메시지는 한국어, 도메인 용어를 사용한다.

### 3.3 트랜잭션 경계
- `XxxService` 의 public 메서드에 `@Transactional` 또는 `@Transactional(readOnly = true)` 를 명시한다.
- Facade 는 기본적으로 트랜잭션을 가지지 않는다 (도메인 서비스 호출 단위에서 경계가 잡힌다).
- 외부 API 호출(예: PG 결제)이 섞이는 유스케이스는 **트랜잭션을 쪼개** 보상 트랜잭션 패턴을 적용한다 (2주차 설계 문서 참고).

### 3.4 테스트 작성 규칙
- TDD 와 3A (Arrange / Act / Assert) 패턴을 따른다.
- `@DisplayName` 은 **한국어**, `@Nested` 로 시나리오를 그루핑한다.
- 도메인 모델/서비스 테스트는 **외부 의존성 없이** 단위 테스트로 구성한다. Repository 는 Fake 또는 Stub 으로 대체.
- 통합 테스트는 testcontainers + MySQL 로 별도 구성하되, 본 환경에서 Docker 호환성 문제로 막혀 있을 수 있다 (참고: `.codeguide/`).

### 3.5 금지 사항
- 가짜 코드(stub-only without intent), `null` 무분별 반환, `System.out.println` 디버깅 코드 commit.
- 미사용 변수/import, "TODO" 만 남기고 비워둔 메서드.
- 도메인 객체에서 영속성 기술 직접 호출 (Repository 의존 X).
- 비즈니스 규칙을 Controller 또는 Facade 에 두는 패턴.

---

## 4. 작업 흐름 / 협업 규칙

- **승인 후 작업**: 사용자가 큰 방향을 정하기 전까지는 제안 단계로 멈춘다 (단, 자율 진행이 명시된 경우는 예외).
- **우선순위**: 동작 → null 안전 → 테스트 → 일관성. 순서대로 만족시킨다.
- **커밋**: 의미 단위로 분할. 한 커밋 = 한 가지 변경 (도메인 1개 단위 권장).
- **PR**: `[<주차>] <설명> - 김도홍` 형식. `.github/pull_request_template.md` 자동 채움. 본문에 `## 💬 리뷰 포인트` 섹션 추가.

---

## 5. 본 주차(3주차) 범위

- **포함**: Product / Brand / Like / Order 도메인 모델·서비스, 도메인 서비스, Application Facade, 단위 테스트.
- **제외**: 결제 PG 연동 (후주차), 동시성 처리 정밀화 (4주차), 페이지네이션 keyset 변환 (성능 주차).
- 2주차 설계 문서(`docs/week2/`) 가 진위(authoritative). 코드는 설계를 따라간다.
