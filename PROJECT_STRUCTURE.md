# 프로젝트 구조 가이드

멀티 모듈 프로젝트가 처음이라면 이 문서를 먼저 읽어라.

---

## 멀티 모듈이란?

하나의 Gradle 프로젝트 안에 여러 개의 독립적인 모듈(서브프로젝트)이 존재하는 구조다.
각 모듈은 별도로 빌드·테스트할 수 있고, 다른 모듈을 `implementation(project(":모듈명"))` 형태로 의존할 수 있다.

이 프로젝트는 모듈을 역할에 따라 3가지 그룹으로 나눈다.

```
Root
├── apps/        ← 실행되는 애플리케이션
├── modules/     ← 재사용 가능한 인프라 설정
└── supports/    ← 부가 기능 애드온
```

---

## 각 그룹의 역할

### apps — 실행 애플리케이션

`@SpringBootApplication`이 있는, 실제로 실행되는 서버들이다.
새로운 비즈니스 기능은 여기에 만든다.

| 모듈 | 역할 |
|---|---|
| `commerce-api` | 외부 클라이언트와 통신하는 REST API 서버 |
| `commerce-batch` | 정해진 시간에 대량 데이터를 처리하는 배치 서버 |
| `commerce-streamer` | Kafka 메시지를 구독해서 처리하는 이벤트 기반 서버 |

> **로그인 기능은 `commerce-api`에 만든다.**
> 로그인은 클라이언트(앱/웹)가 호출하는 REST API이기 때문이다.

### modules — 재사용 인프라 설정

DB, Redis, Kafka 같은 인프라 연결 설정을 담는다.
여러 앱이 공통으로 쓰는 설정을 여기에 모아두고, 필요한 앱에서 의존한다.
비즈니스 로직이나 도메인 코드는 절대 여기에 넣지 않는다.

| 모듈 | 역할 | testFixtures |
| --- | --- | --- |
| `jpa` | MySQL 연결, JPA·QueryDSL 설정, `BaseEntity` 제공 | `MySqlTestContainersConfig`, `DatabaseCleanUp` |
| `redis` | Redis 연결 설정 | `RedisTestContainersConfig`, `RedisCleanUp` |
| `kafka` | Kafka 연결 설정 | Kafka 테스트컨테이너 |

각 모듈은 `testFixtures`도 함께 제공한다. 앱에서 테스트할 때 실제 DB/Redis/Kafka 컨테이너를 띄울 수 있도록 Testcontainers 설정을 미리 만들어둔 것이다.

### supports — 부가 기능 애드온

도메인과 무관하게 모든 앱에 붙일 수 있는 공통 기능이다.

| 모듈 | 역할 |
|---|---|
| `jackson` | JSON 직렬화/역직렬화 커스텀 설정 |
| `logging` | 구조화 로그 설정 (Slack 알림 포함) |
| `monitoring` | Actuator·Prometheus 메트릭 설정 |

---

## apps 내부 계층 구조

`commerce-api`를 예시로 보면, 한 앱 안에서도 패키지를 4계층으로 나눈다.

```
com.loopers
├── interfaces/api/       ← 1. 외부 진입점 (Controller, DTO)
├── application/          ← 2. 유스케이스 조합 (Facade)
├── domain/               ← 3. 핵심 비즈니스 로직 (Service, Model, Repository 인터페이스)
└── infrastructure/       ← 4. 외부 기술 구현체 (JPA Repository 구현)
```

### 계층 간 호출 방향

```
[클라이언트]
    ↓ HTTP 요청
Controller          → DTO로 요청 받음
    ↓
Facade              → 여러 Service를 조합해 유스케이스 처리, Info 객체 반환
    ↓
Service             → 비즈니스 규칙 검증, Repository 인터페이스 호출
    ↓
Repository (인터페이스)
    ↑
RepositoryImpl      → Repository 인터페이스를 구현, JpaRepository 호출
    ↑
JpaRepository       → Spring Data JPA, 실제 DB 접근
```

**중요한 규칙:**
- Controller는 Facade만 호출한다. Service를 직접 호출하지 않는다.
- 각 계층은 바로 아래 계층만 알아야 한다. 계층을 건너뛰지 않는다.
- 비즈니스 오류는 `CoreException(ErrorType.XXX)`을 던진다. `ApiControllerAdvice`가 이를 HTTP 응답으로 변환한다.

---

## 로그인 기능을 만든다면?

`commerce-api` 안에서 아래 순서로 파일을 만들면 된다.

```
# 1. 도메인 — 핵심 비즈니스 객체와 규칙
domain/user/UserModel.java          ← User 엔티티 (BaseEntity 상속)
domain/user/UserRepository.java     ← 저장소 인터페이스
domain/user/UserService.java        ← 로그인 검증 로직

# 2. 인프라 — DB 실제 연결
infrastructure/user/UserJpaRepository.java    ← Spring Data JPA
infrastructure/user/UserRepositoryImpl.java   ← UserRepository 구현

# 3. 애플리케이션 — 유스케이스 조합
application/user/UserFacade.java    ← 로그인 흐름 조합, 토큰 발급 등
application/user/UserInfo.java      ← Controller에 넘길 결과 객체

# 4. 인터페이스 — HTTP 진입점
interfaces/api/user/UserV1Controller.java  ← POST /api/v1/users/login
interfaces/api/user/UserV1Dto.java         ← Request/Response DTO
interfaces/api/user/UserV1ApiSpec.java     ← Swagger 명세 인터페이스
```

---

## BaseEntity

모든 JPA 엔티티는 `modules/jpa`의 `BaseEntity`를 상속한다.
`id`, `createdAt`, `updatedAt`, `deletedAt`을 자동 관리하고, `delete()`/`restore()` 메서드를 제공한다.
논리 삭제(soft delete) 방식을 기본으로 사용한다.