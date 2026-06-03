# Architecture Decision

이 문서는 현재 3주차 구현의 아키텍처 기준 문서다. 제출 커밋에는 포함하지 않는다.

## 결정

이번 설계는 장기적으로 큰 서비스를 만든다는 전제로 5계층 우선 패키지 구조 안에 도메인 모듈 경계를 둔다.

```text
com.loopers
  interfaces
    api
      catalog
      ordering
  application
    catalog
    ordering
    payment
    event
  domain
    catalog
    ordering
    payment
    event
  infrastructure
    catalog
    ordering
    payment
    event
  support
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

- 같은 도메인 경계 내부에서는 `interfaces -> application -> domain` 방향으로 의존한다.
- `infrastructure`는 `domain`의 repository interface를 구현한다.
- 다른 도메인 경계의 `infrastructure`를 직접 참조하지 않는다.
- 모듈 간 협력은 application 계층의 유스케이스 또는 명시적인 domain interface를 통해 연결한다.
- 외부 시스템 연동은 `infrastructure`에 둔다.
- 도메인 레이어는 JPA, Spring, HTTP 같은 프레임워크 타입을 직접 사용하지 않는다.
- 영속성 객체는 `infrastructure`의 `*JpaEntity`로 분리하고, repository adapter가 도메인 엔티티와 JPA 엔티티를 매핑한다.
- 도메인 서비스는 상태를 가지지 않는 순수 객체로 두며, Spring bean 등록은 infrastructure configuration에서 처리한다.

## 구현 아키텍처 기준

현재 구현은 Onion/Hexagonal/CQRS 방향을 명시적으로 따른다.

| 관점 | 기준 |
| --- | --- |
| Onion | 도메인 엔티티와 VO가 중심이며, application/infrastructure가 바깥에서 의존한다. |
| Hexagonal | Repository, PaymentGateway, DataPlatformClient는 domain port이고 구현체는 infrastructure adapter다. |
| CQRS | command service와 query service를 분리해 변경 유스케이스와 조회 조합의 책임을 나눈다. |
| Persistence 분리 | `domain.catalog`, `domain.ordering`, `domain.payment`, `domain.event` 도메인 객체는 JPA 어노테이션을 갖지 않고, infrastructure JPA entity가 DB 스키마를 담당한다. |

## 현재 코드와의 관계

현재 구현은 기존 5계층 패키지를 유지하고, 3주차 구현 대상인 `catalog`, `ordering`, `payment`, `event`는 각 계층 하위 도메인 패키지로 둔다.

3주차 구현 대상 도메인은 순수 도메인 엔티티와 infrastructure JPA 엔티티를 분리한다. 기존 예제 코드의 JPA Entity 구조는 과제 핵심 범위가 아니므로 별도 리팩터링 대상에서 제외한다.
