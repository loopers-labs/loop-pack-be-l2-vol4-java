# 코드 컨벤션

본 문서는 Java 21 / Spring Boot 3.4.x 기반으로 이 프로젝트에서 코드를 작성할 때 따를 상세 규칙을 정의합니다.
최상위 작업 규칙과 요약은 루트 `AGENTS.md` 를 우선 확인합니다.

## 기본 원칙

- 기존 모듈 구조와 패키지 레이어 책임을 우선한다.
- 변경 대상 모듈을 먼저 식별한 뒤 해당 모듈의 기존 코드 스타일을 따른다.
- Controller 는 요청/응답 변환, 입력 검증, HTTP 상태 표현에 집중한다.
- 비즈니스 규칙은 `domain` 또는 `application` 계층에 둔다.
- 인프라 구현 세부사항은 `infrastructure` 계층에 둔다.
- `modules/*` 와 `supports/*` 에는 특정 도메인의 비즈니스 로직을 두지 않는다.
- 새 추상화는 중복이나 복잡도를 실제로 줄일 때만 추가한다.

## 포맷팅

- Java 파일은 Google Java Format 기준으로 포맷한다.
- 수동 정렬, 컬럼 맞춤, 임의 줄바꿈보다 formatter 결과를 우선한다.
- import 는 사용하지 않는 항목을 제거하고 wildcard import 를 사용하지 않는다.
- 파일 끝에는 최종 개행을 유지한다.
- Kotlin / Gradle script / 기타 파일은 `.editorconfig` 를 따른다.

## Java 21 사용 규칙

- DTO, 조회 응답, 불변 값 객체에는 `record` 사용을 우선 고려한다.
- JPA Entity 에는 `record` 를 사용하지 않는다.
- Spring Bean, 설정 프로퍼티 클래스에는 `record` 사용 여부를 신중히 판단한다.
- 복잡한 분기에는 `switch expression` 사용을 허용한다.
- pattern matching 은 타입 분기가 명확해질 때 사용한다.
- `Optional` 은 반환 타입에서만 사용하고 필드, 파라미터, DTO 속성에는 사용하지 않는다.
- null 가능성이 있는 값은 명시적으로 검증하거나 도메인 규칙으로 방어한다.

## Spring Boot 3.4.x 규칙

- `javax.*` 대신 `jakarta.*` 패키지를 사용한다.
- 의존성 주입은 생성자 주입을 사용한다.
- 필드 주입(`@Autowired` 필드)은 사용하지 않는다.
- 설정 값 바인딩은 `@ConfigurationProperties` 를 우선 사용한다.
- Bean 등록이 필요한 경우 명확한 설정 클래스 또는 auto-configuration 성격의 모듈에 둔다.
- 트랜잭션 경계는 Facade/Application Service 또는 명확한 UseCase 단위에 둔다.
- 조회 전용 트랜잭션은 `@Transactional(readOnly = true)` 를 사용한다.
- 운영용 profile 설정(`dev`/`qa`/`prd`) 변경은 사용자 확인 후 진행한다.

## Lombok 규칙

- 생성자 주입에는 `@RequiredArgsConstructor` 를 우선 사용한다.
- 단순 getter 만 필요한 타입에는 `@Getter` 를 사용한다.
- 무분별한 `@Data` 사용은 피한다.
- Entity 에 공개 setter 를 열어두지 않는다.
- Entity 상태 변경은 의미 있는 도메인 메서드로 표현한다.
- `@Builder` 는 생성 인자가 많거나 테스트 픽스처 가독성이 좋아지는 경우에 사용한다.
- equals/hashCode 가 필요한 경우 Entity 식별자와 영속성 생명주기를 고려해 명시적으로 작성한다.

## 패키지 / 레이어 규칙

### interfaces

- `interfaces.api.<domain>` 하위에 Controller, DTO, Swagger Spec, Advice 를 둔다.
- Request/Response DTO 는 외부 API 계약으로 보고 domain model 과 분리한다.
- 입력 검증은 Bean Validation 애너테이션을 우선 사용한다.
- Controller 에서 domain model 을 직접 반환하지 않는다.
- 공통 응답 형식은 `ApiResponse` 를 따른다.

### application

- `application.<domain>` 하위에 Facade 와 Info 를 둔다.
- Facade 는 유스케이스 흐름, 트랜잭션 경계, 여러 도메인 서비스 조합을 담당한다.
- 외부로 반환하는 조회 결과는 `Info` 객체로 표현한다.
- 단순 위임만 반복되는 Facade 는 만들지 않는다.

### domain

- `domain.<domain>` 하위에 Model, Service, Repository 인터페이스를 둔다.
- 도메인 규칙은 Model 또는 Service 내부에 둔다.
- Repository 는 도메인 계약만 표현하고 JPA, Redis, Kafka 세부 타입에 의존하지 않는다.
- 도메인 계층은 `interfaces` 또는 `infrastructure` 계층에 의존하지 않는다.

### infrastructure

- `infrastructure.<domain>` 하위에 Repository 구현체와 JpaRepository 를 둔다.
- QueryDSL, JPA, Redis, Kafka 세부 구현은 이 계층에 한정한다.
- domain 계층의 Repository 계약을 구현한다.
- 외부 시스템 연동 예외는 도메인/애플리케이션에서 해석 가능한 형태로 변환한다.

## JPA 규칙

- Entity 는 `modules:jpa` 의 `BaseEntity` 사용을 우선 고려한다.
- Entity 기본 생성자는 `protected` 로 둔다.
- Entity 필드는 가능한 한 불변에 가깝게 유지하고, 변경은 도메인 메서드로 수행한다.
- 연관관계는 필요한 방향으로만 매핑한다.
- 컬렉션 필드는 외부에서 직접 수정하지 못하게 한다.
- N+1 가능성이 있는 조회는 fetch join, entity graph, QueryDSL 등을 검토한다.
- 복잡한 조건 조회나 동적 쿼리는 QueryDSL 사용을 고려한다.
- Entity 를 API 응답 DTO 로 직접 노출하지 않는다.

## 예외 처리

- 애플리케이션 오류는 `CoreException` 과 `ErrorType` 체계를 따른다.
- Controller 에서 임의의 예외 응답을 직접 만들지 않는다.
- 예외 메시지는 원인을 파악할 수 있게 작성하되 민감 정보를 포함하지 않는다.
- 외부 입력 검증 실패와 도메인 규칙 위반을 구분한다.

## 테스트 규칙

- 새 기능 또는 버그 수정에는 가능한 한 테스트를 추가한다.
- 단위 테스트는 JUnit 5 + Mockito 또는 springmockk 를 사용한다.
- 테스트 데이터 생성은 Instancio 사용을 우선 고려한다.
- 통합 테스트는 Testcontainers 기반 fixture 를 재사용한다.
- `modules:jpa`, `modules:redis`, `modules:kafka` 의 testFixtures 를 우선 활용한다.
- 테스트는 루트 Gradle 설정의 `spring.profiles.active=test`, `user.timezone=Asia/Seoul` 조건에서 동작해야 한다.
- 시간, 랜덤 값, 외부 I/O 에 의존하는 테스트는 재현 가능하게 작성한다.

