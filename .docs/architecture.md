# Architecture Decision

이 문서는 설계 보조 문서다. volume-2 제출 커밋에는 포함하지 않는다.

## 결정

이번 설계는 장기적으로 큰 서비스를 만든다는 전제로 도메인 우선 모듈러 모놀리스를 사용한다.

```text
commerce
  catalog
    interfaces
    application
    domain
    infrastructure

  ordering
    interfaces
    application
    domain
    infrastructure

  payment
    interfaces
    application
    domain
    infrastructure

  event
    application
    domain
    infrastructure
```

## 모듈 경계

| 모듈 | 포함 도메인 | 책임 |
| --- | --- | --- |
| `catalog` | `Brand`, `Product`, `ProductLike` | 상품 탐색, 상품 상태, 재고 수량, 좋아요 |
| `ordering` | `Order`, `OrderLine` | 주문 생성, 주문 상태, 주문 항목 스냅샷 |
| `payment` | `Payment`, `PaymentGateway` | 결제 요청, 결제 결과, 결제 실패/취소 처리 |
| `event` | `OrderEventOutbox`, `DataPlatformClient` | 주문 성공 이벤트 저장, 외부 데이터 플랫폼 전송 |

## 외부 경계

| 경계 | 이번 설계에서의 처리 | 이유 |
| --- | --- | --- |
| `identity` | `userId` 식별자만 참조 | 회원가입/회원 상세는 volume-2 설계 범위가 아니므로 내부 테이블과 필드는 설계하지 않는다. |

## 계층 책임

| 계층 | 책임 |
| --- | --- |
| `interfaces` | HTTP 요청/응답 변환, API DTO, 헤더 검증 |
| `application` | 유스케이스 조합, 모듈 간 협력, 트랜잭션 시작점 |
| `domain` | 도메인 모델, 상태 전이, 검증 규칙, repository interface |
| `infrastructure` | JPA 구현, 외부 API client, worker 구현 세부사항 |

## 의존 규칙

- 같은 모듈 내부에서는 `interfaces -> application -> domain` 방향으로 의존한다.
- `infrastructure`는 `domain`의 repository interface를 구현한다.
- 다른 모듈의 `infrastructure`를 직접 참조하지 않는다.
- 모듈 간 협력은 application 계층의 유스케이스 또는 명시적인 domain interface를 통해 연결한다.
- 외부 시스템 연동은 `infrastructure`에 둔다.

## 현재 코드와의 관계

현재 프로젝트는 `interfaces/api`, `application`, `domain`, `infrastructure`를 최상위 계층으로 두는 구조다. 구현은 나중에 진행하며, 이번 설계에서는 목표 구조를 먼저 확정한다.

목표 구조와 현재 구조가 다르므로 구현 단계에서는 두 선택지가 있다.

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| 현재 구조 유지 | 변경량이 작고 기존 코드와 충돌이 적다. | 서비스가 커질수록 한 도메인을 이해하기 위해 여러 최상위 패키지를 이동해야 한다. |
| 도메인 우선 구조로 점진 이전 | 도메인 응집도가 높고 장기 확장에 유리하다. | 초기 패키지 이동과 import 변경이 필요하다. |

이번 설계의 기준은 도메인 우선 구조이며, 구현 단계의 실제 이전 범위는 별도로 결정한다.
