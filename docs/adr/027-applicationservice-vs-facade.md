# ADR-027: ApplicationService vs Facade — 왜 Facade라 부르는가

- 날짜: 2026-05-29
- 상태: 승인됨

---

## Introduction & Goals

- **Context / Background**:
  엄밀한 정의에서 ApplicationService와 Facade는 다른 계층이다.

  |  | ApplicationService | Facade |
  |--|---|---|
  | 조율 대상 | Entity, DomainService, Repository | 여러 ApplicationService |
  | 관심 범위 | 단일 도메인/애그리거트 유스케이스 | 여러 도메인을 가로지르는 유스케이스 |
  | 트랜잭션 | 단일 트랜잭션 경계 | 여러 트랜잭션 조율 (Saga/보상 포함) |
  | 비즈니스 규칙 | 없음 (도메인에 위임) | 없음 (하위에 위임) |

  이 프로젝트의 `XxxFacade`는 ApplicationService가 아닌 DomainService를 직접 호출한다. 엄밀히는 ApplicationService에 해당하는 구조다. 그럼에도 `Facade`라는 이름을 사용하고 있어, 그 근거를 명확히 정의할 필요가 생겼다.

- **Goals**:
  ApplicationService와 Facade의 개념적 차이를 인지한 상태에서, 이 프로젝트가 왜 `Facade`라는 명칭을 선택했는지 근거를 확립하고 계층 내 책임 범위를 정의한다.

---

## Detailed Design

### System Architecture

이 프로젝트의 실제 계층 구조:

```
Interfaces   (Controller)
    ↓
Application  (Facade)        ← DomainService를 직접 조율
    ↓
Domain       (Service)       ← 비즈니스 규칙, @Transactional 소유
    ↓
Infrastructure (Repository)
```

엄밀한 정의 기준으로 진짜 Facade라면 아래 구조여야 한다:

```
Interfaces   (Controller)
    ↓
Application  (Facade)             ← ApplicationService를 조율
    ↓
Application  (ApplicationService) ← DomainService를 조율
    ↓
Domain       (Service, Entity)      ← 비즈니스 규칙, @Transactional 소유
    ↓
Infrastructure (Repository)
    
```

이 프로젝트는 현재 규모에서 ApplicationService 위에 Facade를 별도로 두는 것을 과설계로 판단하여 두 계층을 하나로 합쳤다. 합쳐진 계층의 이름으로 `Facade`를 선택했다.

### Data Models

- Facade는 Controller로부터 요청 DTO를 받아 DomainService에 전달한다.
- DomainService의 반환값(`Model`)을 Application Layer DTO(`Info`)로 변환하여 Controller에 돌려준다.
- `Model` 객체는 Interfaces 계층으로 직접 노출하지 않는다.

### API Design

해당 없음 (계층 설계 결정).

### Constraints

- `XxxFacade`는 DomainService를 직접 호출한다. 다른 `XxxFacade`를 호출하지 않는다.
- Facade에 비즈니스 규칙을 작성하지 않는다. 도메인 규칙은 DomainService 또는 Model에 위임한다.
- `@Transactional`은 DomainService가 소유한다. Facade에 필요한 경우 반드시 ADR을 남긴다.
- 여러 도메인을 가로지르는 복잡한 유스케이스가 등장하면, ApplicationService 계층을 분리하고 그 위에 진짜 Facade를 두는 구조로 확장한다.

---

## Alternatives Considered

| 옵션 | Pros | Cons |
|------|------|------|
| `XxxApplicationService` 사용 | 엄밀한 DDD 용어와 일치. 현재 구조(DomainService 직접 호출)를 정확히 표현. | `XxxService`라는 이름이 DomainService와 충돌 위험. Controller에 단순 인터페이스를 제공한다는 구조적 의도가 드러나지 않음. |
| ApplicationService + Facade 두 계층 분리 | 엄밀한 정의에 부합. 향후 확장성 확보. | 현재 규모에서 과설계. 단일 도메인 유스케이스에 중간 계층이 하나 더 생겨 복잡도만 증가. |
| **선택: `XxxFacade` 사용** | Controller에 단순 인터페이스를 제공한다는 구조적 의도가 이름에 드러남. DomainService(`XxxService`)와 명칭이 명확히 구분됨. | 엄밀한 정의로는 ApplicationService에 가까운 구조임에도 Facade라 불러 개념적 혼동 가능성이 있음. |

**선택 근거:**

이 프로젝트에서 Application Layer의 핵심 역할은 "Controller가 도메인 복잡성을 알지 못하도록 단순한 진입점을 제공하는 것"이다. 이 의도는 GoF Facade 패턴이 정확히 설명한다.

현재 구조가 엄밀한 정의의 ApplicationService에 가깝다는 것을 인지하고 있다. 그러나 ApplicationService 위에 Facade를 추가하는 것은 현재 규모에서 과설계이며, `XxxApplicationService`라는 이름은 DomainService와의 구분을 모호하게 만든다.

따라서 두 계층을 하나로 합치되, 구조적 의도를 더 잘 표현하는 `Facade`를 명칭으로 선택한다.
