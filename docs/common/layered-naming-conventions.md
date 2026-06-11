# Layered Naming Conventions

## 목적

이 문서는 `commerce-api`의 레이어별 데이터 객체와 도메인 협력 객체 네이밍 기준을 정리한다.
목표는 클래스명을 열어보기 전에도 객체의 레이어와 역할을 추론할 수 있게 만드는 것이다.

## 기본 원칙

- 레이어별로 같은 역할의 객체는 같은 suffix를 사용한다.
- `Dto`는 API 요청/응답 계약에만 사용한다.
- Domain layer에서는 자유로운 이름을 무제한 허용하지 않고, 역할이 드러나는 제한된 suffix를 사용한다.
- Domain Entity는 JPA annotation을 갖지 않는 순수 객체로 두고, 도메인명을 그대로 사용한다.
- JPA 영속화 객체는 infrastructure layer에서 `*JpaEntity`로 둔다.
- 이름만으로 부족하면 객체의 위치까지 함께 본다. 예: `domain/product/ProductDetailView`

## 레이어별 데이터 객체 네이밍

| Layer | Suffix | 역할 | 예시 |
| --- | --- | --- | --- |
| `interfaces` | `*Dto` | API 요청/응답 계약 | `ProductDto`, `UserDto.Register.V1.Request` |
| `application` | `*Info` | 유스케이스 결과를 외부 레이어로 전달하는 정보 객체 | `ProductInfo`, `OrderInfo`, `AuthenticatedUserInfo` |
| `domain` | suffix 없음 | 식별자를 가지는 순수 도메인 엔티티 | `Product`, `Brand`, `User`, `Order` |
| `domain` | `*Command` | 도메인 동작 입력 | `OrderProductCommand` |
| `domain` | `*Criteria` | 조회/필터/페이징 조건 | `PageCriteria` |
| `domain` | `*Result` | 도메인 동작 결과 | `OrderResult` |
| `domain` | `*View` | 조회용 조합 결과 또는 read-only data carrier | `ProductDetailView` |
| `domain` | `*Failure` | 실패 사유 항목 | `OrderFailure` |
| `infrastructure` | `*JpaEntity` | JPA 영속화 전용 엔티티 | `ProductJpaEntity`, `BrandJpaEntity`, `UserJpaEntity`, `OrderJpaEntity` |

## Domain Service 협력 객체 네이밍

| Suffix | 역할 |
| --- | --- |
| `*Service` | 도메인 외부 진입점 |
| `*Reader` | 조회 전용 Repository 접근 |
| `*Writer` | 생성/수정/삭제 Repository 접근 |
| `*Policy` | Repository 없는 순수 정책 |
| `*Processor` | Repository 없는 순수 처리/조합 로직 |
| `*ProcessService` | 2개 이상의 도메인 객체를 Repository 없이 조합하는 도메인 진입점 |

## get/find 네이밍 규칙

- `get*` 메서드는 결과가 반드시 있어야 하는 조회를 의미한다.
- `get*` 메서드는 결과가 없으면 해당 메서드 내부에서 예외를 던진다.
- `find*` 메서드는 결과가 없을 수 있는 조회를 의미한다.
- `find*` 메서드는 직접 Not Found 예외를 던지지 않고 `Optional`, `null`, 빈 컬렉션 등으로 부재를 표현한다.
- Java 코드에서는 단건 부재 표현에 `null`보다 `Optional<T>`를 우선 사용한다.
- 컬렉션 조회는 결과가 없으면 `null`이 아니라 빈 컬렉션을 반환한다.

## 적용 예시

```text
Product + Brand
        ↓
ProductDetailView      // domain 조회 조합 결과
        ↓
ProductInfo            // application 유스케이스 정보 객체
        ↓
ProductDto.Get.V1.Response
```

## 판단 기준

### `*Dto`를 쓰는 경우

- HTTP request/response schema를 표현한다.
- API version이나 action에 종속된다.
- Controller 입출력 타입으로 사용된다.

### `*Info`를 쓰는 경우

- Application layer에서 Controller 또는 다른 상위 레이어로 전달하는 유스케이스 결과다.
- API version에는 종속되지 않는다.
- Domain 객체를 그대로 노출하지 않기 위한 application 경계 객체다.

### `*View`를 쓰는 경우

- Domain layer 내부에서 조회 결과를 조합한다.
- 여러 Domain Entity를 read-only로 묶어 전달한다.
- API 계약이나 application 응답 형식에는 종속되지 않는다.

### `*JpaEntity`를 쓰는 경우

- JPA annotation과 persistence schema를 표현한다.
- Infrastructure layer에 위치한다.
- Domain entity와 mapper 또는 factory method를 통해 변환한다.

## 리팩토링 기준

- Domain 조회 조합 객체가 `*Detail`, `*Summary`처럼 역할이 애매하면 `*View` suffix를 우선 검토한다.
- Application 객체가 특정 실행 결과를 넘어 여러 API에서 재사용되는 정보라면 `*Result`보다 `*Info`를 우선 검토한다.
- 새로운 suffix가 필요하면 기존 후보군으로 표현할 수 없는 이유를 먼저 문서화한다.
