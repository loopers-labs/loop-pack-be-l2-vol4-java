# Round 3 Quest Guide

## 1. 문서 목적

이 문서는 3주차 구현 과제를 AI가 정확히 이해하고 수행할 수 있도록 정리한 요구사항 문서다.

AI는 이 문서를 기준으로 다음 작업을 수행한다.

- 상품, 브랜드, 좋아요, 주문 기능의 도메인 모델을 구현한다.
- Entity, Value Object, Domain Service를 적절히 나누어 도메인 규칙을 코드로 표현한다.
- Onion/Hexagonal/CQRS 방향의 레이어드 아키텍처와 DIP를 적용해 테스트 가능한 구조를 만든다.
- 도메인 엔티티는 JPA/Spring 의존에서 분리하고, infrastructure에서 영속성 adapter를 제공한다.
- Application Layer는 도메인 객체를 조합하는 흐름에 집중하도록 경량으로 유지한다.
- 핵심 도메인 로직에 대한 단위 테스트와 예외/경계 케이스 테스트를 작성한다.

## 2. 전체 목표

### 2.1 Implementation Quest

3주차의 핵심 목표는 2주차 설계를 실제 구현 가능한 코드 구조로 옮기는 것이다.

| 목표 | 설명 |
| --- | --- |
| 도메인 모델링 | `Product`, `Brand`, `Like`, `Order`의 핵심 개념을 Entity, VO, Domain Service로 구현한다. |
| 아키텍처 적용 | Onion/Hexagonal/CQRS와 DIP를 적용해 도메인 로직이 외부 기술에 직접 의존하지 않게 한다. |
| Application Layer 구현 | Facade 또는 Application Service에서 도메인 객체를 조합해 유스케이스 흐름을 만든다. |
| 테스트 작성 | 도메인 규칙, 예외 흐름, 경계 조건을 단위 테스트로 검증한다. |

### 2.2 AI 작업 규칙 확장

원문 과제는 `CLAUDE.md`에 규칙을 추가하라고 안내한다.

이 저장소에서는 `AGENTS.md`가 AI 작업 규칙의 기준 문서이므로, 필요한 경우 아래 관점을 `AGENTS.md` 또는 현재 사용하는 AI 규칙 문서에 반영한다.

- 객체지향과 도메인 모델링 규칙
- 레이어드 아키텍처와 DIP 원칙
- 패키지 구성 방향
- DTO 분리 기준
- 도메인 로직과 애플리케이션 조립 책임의 경계

## 3. 구현 범위

### 3.1 포함 기능

| 도메인 | 구현 범위 |
| --- | --- |
| 상품 | 상품 정보, 가격, 재고, 정렬 조건, 재고 차감 규칙 |
| 브랜드 | 상품과 연결되는 브랜드 정보 |
| 좋아요 | 사용자와 상품 간 좋아요 등록/취소, 좋아요 수 조회 |
| 주문 | 여러 상품 주문, 주문 수량, 재고 차감, 예외 흐름 |
| 도메인 서비스 | 여러 도메인 객체가 협력해야 하는 핵심 규칙 |
| 애플리케이션 서비스 | 도메인 객체와 도메인 서비스를 조합하는 유스케이스 흐름 |
| 테스트 | 도메인 단위 테스트, 예외/경계 케이스 테스트, Fake/Stub 기반 테스트 |

### 3.2 대표 유스케이스

```text
ProductQueryService.getOnSaleProduct(productId, userId)
  -> Product 조회
  -> Brand 조회
  -> Like 수 또는 사용자 좋아요 여부 조회
  -> 상품 상세 응답 조합
```

단순 조회 응답 조합은 Application Layer에서 처리한다.

여러 도메인 객체의 비즈니스 규칙이 함께 검증되어야 하는 경우에는 Domain Service로 분리한다.

## 4. 도메인 모델링 원칙

### 4.1 객체 책임

- 도메인 객체는 비즈니스 규칙을 캡슐화한다.
- 재고 음수 방지, 주문 수량 검증, 주문 가능 여부 같은 규칙은 도메인 내부에 둔다.
- 여러 서비스에 반복되는 규칙은 도메인 객체 또는 Domain Service로 이동할 가능성이 높다.
- Application Service는 도메인 로직을 직접 구현하지 않고, 도메인 객체와 서비스를 조합한다.

### 4.2 Entity, VO, Domain Service 기준

| 구분 | 사용 기준 |
| --- | --- |
| Entity | 식별자를 가지고 생명주기가 있는 객체. 예: `Product`, `Brand`, `Order`, `OrderLine`, `Like` |
| Value Object | 값 자체가 의미를 가지며 불변으로 다루는 객체. 예: `Money`, `Quantity`, `Stock`, `ProductName` |
| Domain Service | 특정 Entity 하나에 넣기 어렵고 여러 도메인 객체의 협력이 필요한 규칙. 예: 주문 생성, 재고 차감 정책 |
| Application Service | 트랜잭션 경계, 조회 조합, 외부 포트 호출, 유스케이스 흐름 제어 |

## 5. 아키텍처 원칙

### 5.1 기본 구조

```text
Interfaces -> Application -> Domain <- Infrastructure
```

- 핵심 비즈니스 로직은 Domain Layer에 둔다.
- Repository Interface는 Domain Layer에 정의한다.
- Repository 구현체는 Infrastructure Layer에 둔다.
- Application Layer는 도메인 객체를 조합하고 유스케이스 흐름을 orchestration 한다.
- API request/response DTO와 Application Layer DTO는 분리한다.
- Domain Layer는 JPA, Spring, HTTP 타입을 직접 사용하지 않는다.
- Infrastructure Layer는 `*JpaEntity`와 repository adapter로 영속성 세부사항을 담당한다.
- 도메인 서비스는 순수 객체로 두고, Spring bean 등록은 infrastructure configuration에서 처리한다.

### 5.2 패키지 구성 기준

이 저장소의 구현 패키지는 기존 5계층을 먼저 유지하고, 각 계층 하위에 도메인 모듈 경계를 둔다.

```text
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

프로젝트의 `AGENTS.md` 또는 기존 구조가 더 구체적인 모듈 경계를 정의한다면 그 규칙을 우선한다.

### 5.3 아키텍처 적용 기준

| 관점 | 적용 기준 |
| --- | --- |
| Onion | 도메인 엔티티, VO, 도메인 서비스가 중심이다. |
| Hexagonal | Repository, PaymentGateway, DataPlatformClient는 domain port이고 infrastructure가 adapter를 제공한다. |
| CQRS | command service와 query service를 분리해 변경 흐름과 조회 조합을 나눈다. |
| Persistence 분리 | Domain entity와 JPA entity를 분리하고 repository adapter에서 매핑한다. |

## 6. 도메인별 구현 체크리스트

### 6.1 Product / Brand

- [ ] 상품 정보 객체는 브랜드 정보와 좋아요 수를 함께 제공할 수 있다.
- [ ] 상품 목록은 정렬 조건 `latest`, `price_asc`, `likes_desc`를 고려한다.
- [ ] 상품은 재고를 가지고 있으며 주문 시 차감할 수 있다.
- [ ] 재고가 음수가 되는 상황은 도메인 레벨에서 차단한다.
- [ ] 재고 차감 실패 시 명확한 예외를 발생시킨다.

### 6.2 Like

- [ ] 좋아요는 사용자와 상품 간의 관계로 별도 도메인으로 분리한다.
- [ ] 상품 상세 또는 목록 조회에서 좋아요 수를 함께 제공한다.
- [ ] 좋아요 등록 흐름을 단위 테스트로 검증한다.
- [ ] 좋아요 취소 흐름을 단위 테스트로 검증한다.
- [ ] 중복 등록 또는 중복 취소 같은 경계 케이스를 검증한다.

### 6.3 Order

- [ ] 주문은 여러 상품을 포함할 수 있다.
- [ ] 주문 항목은 상품 ID와 주문 수량을 명시한다.
- [ ] 주문 시 상품 재고 차감을 수행한다.
- [ ] 재고 부족 예외 흐름을 고려한다.
- [ ] 정상 주문과 예외 주문 흐름을 모두 단위 테스트로 검증한다.

### 6.4 Domain Service

- [ ] 도메인 간 협력 로직은 Domain Service에 위치시킨다.
- [ ] Domain Service는 상태를 가지지 않고 도메인 객체의 협력을 조정한다.
- [ ] 복합 유스케이스 흐름은 Application Layer에 두고, 핵심 규칙은 Domain Service 또는 Entity에 위임한다.
- [ ] 단순 조회 응답 조합과 비즈니스 규칙 검증을 구분한다.

### 6.5 Architecture

- [ ] Application Layer는 도메인 객체를 조합하는 흐름에 집중한다.
- [ ] 핵심 비즈니스 로직은 Entity, VO, Domain Service에 위치한다.
- [ ] Repository Interface와 구현체가 분리되어 있다.
- [ ] Infrastructure 의존성이 Domain Layer로 역류하지 않는다.
- [ ] Domain entity가 JPA/Spring 어노테이션을 갖지 않는다.
- [ ] Infrastructure `*JpaEntity`와 domain entity의 매핑 위치가 adapter에 한정되어 있다.
- [ ] 테스트는 외부 의존성을 Fake 또는 Stub으로 대체할 수 있다.

## 7. 테스트 기준

### 7.1 필수 테스트

| 영역 | 테스트 대상 |
| --- | --- |
| 상품 | 재고 차감, 재고 부족, 음수 재고 방지 |
| 좋아요 | 등록, 취소, 중복 요청, 좋아요 수 반영 |
| 주문 | 정상 주문, 여러 상품 주문, 재고 부족 |
| 도메인 서비스 | 여러 도메인 객체 협력 규칙 |
| 애플리케이션 서비스 | 도메인 객체 조합 흐름, Fake/Stub 기반 외부 의존성 분리 |

### 7.2 테스트 작성 원칙

- 도메인 규칙은 가능한 한 순수 단위 테스트로 검증한다.
- 외부 저장소, 외부 API, 프레임워크 의존성은 Fake 또는 Stub으로 분리한다.
- 성공 케이스만 작성하지 말고 예외와 경계 케이스를 포함한다.
- 테스트 이름은 어떤 규칙을 검증하는지 드러나야 한다.

## 8. AI 작업 순서

AI는 구현을 시작할 때 다음 순서로 진행한다.

1. `AGENTS.md`와 이 문서를 읽고 현재 작업 규칙을 확인한다.
2. 기존 구현 구조와 패키지를 확인한다.
3. 도메인 용어, 상태명, API명, 메서드명이 기존 문서와 충돌하지 않는지 확인한다.
4. 책임 경계가 애매한 부분은 구현 전에 질문한다.
5. Entity와 VO를 먼저 구현한다.
6. 여러 도메인 객체의 협력이 필요한 규칙을 Domain Service로 분리한다.
7. Application Layer에서 유스케이스 흐름을 조립한다.
8. Repository Interface와 구현체를 분리한다.
9. 핵심 도메인 로직과 예외 흐름에 대한 테스트를 작성한다.
10. 변경 파일과 테스트 결과를 정리한다.

## 9. 완료 기준

- [ ] `Product`, `Brand`, `Like`, `Order` 관련 핵심 도메인 모델이 구현되어 있다.
- [ ] 도메인 규칙이 Application Service에 흩어져 있지 않다.
- [ ] Domain Layer가 Infrastructure 구현체에 직접 의존하지 않는다.
- [ ] Domain Layer가 JPA/Spring/HTTP 타입에 직접 의존하지 않는다.
- [ ] API DTO와 Application DTO가 분리되어 있다.
- [ ] 주요 예외 흐름이 코드와 테스트에 반영되어 있다.
- [ ] 단위 테스트로 핵심 비즈니스 규칙을 검증한다.
- [ ] 패키지 구조가 레이어와 도메인 기준을 드러낸다.
