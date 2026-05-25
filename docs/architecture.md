# 아키텍처 전략 및 패키지 구성

## 레이어드 아키텍처 (commerce-api)

```
com.loopers
├── interfaces/api/         # 진입점 — HTTP 요청/응답만 처리
├── application/            # 유스케이스 오케스트레이션 (Facade)
├── domain/                 # 핵심 비즈니스 로직
│   ├── {domain}/           # 도메인별 패키지 (user, product, order, ...)
│   │   ├── model/          # 도메인 모델 (Entity, VO)
│   │   ├── service/        # 도메인 서비스
│   │   └── repository/     # 리포지토리 인터페이스
│   └── ...
├── infrastructure/         # 외부 의존성 구현체
│   └── {domain}/
│       ├── persistence/    # JpaRepository, QueryDSL
│       └── ...
└── support/
    └── error/              # CoreException, ErrorType
```

## 레이어 규칙

### interfaces/api
- 역할: HTTP 요청 수신 → Facade 호출 → HTTP 응답 반환
- 허용: `@RestController`, `@RequestMapping`, Request/Response DTO
- 금지: 비즈니스 로직, 도메인 객체 직접 조작, Repository 직접 주입

### application (Facade)
- 역할: 여러 도메인 서비스를 조합해 단일 유스케이스를 완성
- 허용: 트랜잭션 경계 정의(`@Transactional`), 여러 도메인 서비스 조합
- 금지: 비즈니스 규칙 직접 구현, Repository 직접 주입

### domain
- 역할: 순수 비즈니스 로직. 외부 프레임워크 의존 최소화
- 허용: `@Service`, `@Entity`, `@Embeddable`, Repository 인터페이스
- 금지: `@Controller`, `@RestController`, JPA 구현 세부사항 노출

### infrastructure
- 역할: domain의 Repository 인터페이스를 구현. 영속성 기술 캡슐화
- 허용: `JpaRepository`, QueryDSL `QEntity`, `@Repository`
- 금지: 비즈니스 로직

## 의존 방향

```
interfaces → application → domain ← infrastructure
```

- 단방향 의존. 역방향(domain → application 등) 금지.
- domain은 infrastructure를 모른다.

## 패키지 명명 규칙

| 클래스 유형 | 패키지 위치 | 네이밍 예시 |
|---|---|---|
| REST 컨트롤러 | `interfaces/api/{domain}/` | `UserController` |
| Request DTO | `interfaces/api/{domain}/` | `UserCreateRequest` |
| Response DTO | `interfaces/api/{domain}/` | `UserResponse` |
| Facade | `application/{domain}/` | `UserFacade` |
| 도메인 서비스 | `domain/{domain}/service/` | `UserService` |
| 도메인 모델 | `domain/{domain}/model/` | `User` (Entity) |
| Value Object | `domain/{domain}/model/` | `Email`, `PhoneNumber` |
| 리포지토리 인터페이스 | `domain/{domain}/repository/` | `UserRepository` |
| JPA 리포지토리 | `infrastructure/{domain}/persistence/` | `UserJpaRepository` |
| 리포지토리 구현체 | `infrastructure/{domain}/persistence/` | `UserRepositoryImpl` |

## Entity 설계 원칙

- `BaseEntity` 상속: `createdAt`, `updatedAt` 자동 관리
- PK: `@GeneratedValue(strategy = IDENTITY)` (Auto Increment)
- 소프트 딜리트: `deletedAt` 필드 + `@Where(clause = "deleted_at IS NULL")`
- 생성자: `protected` 기본 생성자 + `public` 정적 팩토리 메서드(`create(...)`)
- Setter 금지: 상태 변경은 의미 있는 메서드로만

## 예외 처리

- 모든 비즈니스 예외는 `CoreException(ErrorType)` 사용
- `ErrorType`: 에러 코드, HTTP 상태, 메시지를 enum으로 관리
- Controller Advice에서 `CoreException` 일괄 처리
