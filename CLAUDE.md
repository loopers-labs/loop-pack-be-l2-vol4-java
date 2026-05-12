# CLAUDE.md — loopers-java-spring-template

## 프로젝트 개요

Loopers에서 제공하는 Java + Spring Boot 기반 멀티 모듈 백엔드 템플릿 프로젝트입니다.

- **루트 프로젝트명**: `loopers-java-spring-template`
- **Group ID**: `com.loopers`
- **빌드 도구**: Gradle 8.13 (Kotlin DSL)
- **언어**: Java 21

---

## 기술 스택 및 버전

| 분류 | 라이브러리 | 버전 |
|---|---|---|
| Language | Java | 21 |
| Build | Gradle | 8.13 |
| Framework | Spring Boot | 3.4.4 |
| Framework | Spring Cloud Dependencies | 2024.0.1 |
| Framework | Spring Dependency Management | 1.1.7 |
| API 문서 | SpringDoc OpenAPI | 2.7.0 |
| DB | MySQL | 8.0 (Docker) |
| Cache | Redis | 7.0 (Docker, master/replica 구성) |
| Messaging | Kafka | 3.5.1 KRaft (Docker) |
| ORM | Spring Data JPA + QueryDSL | Spring Boot 관리 |
| Monitoring | Micrometer Prometheus + Grafana | Spring Boot 관리 |
| Tracing | Micrometer Tracing (Brave) | Spring Boot 관리 |
| Logging | Logback + Slack Appender | 1.6.1 |
| Test | Instancio JUnit | 5.0.2 |
| Test | SpringMockk | 4.0.2 |
| Test | Mockito | 5.14.0 |
| Test | Testcontainers (MySQL, Redis, Kafka) | Spring Boot 관리 |
| Lint | KtLint Plugin / KtLint | 12.1.2 / 1.0.1 |

---

## 모듈 구조

```
Root
├── apps/               # 실행 가능한 Spring Boot 애플리케이션 (BootJar 생성)
│   ├── commerce-api      # REST API 서버
│   ├── commerce-batch    # Spring Batch 배치 서버
│   └── commerce-streamer # Kafka Consumer 스트리밍 서버
├── modules/            # 도메인 독립적인 재사용 설정 모듈 (java-library)
│   ├── jpa               # Spring Data JPA + QueryDSL + MySQL
│   ├── redis             # Spring Data Redis
│   └── kafka             # Spring Kafka
└── supports/           # 부가 기능 add-on 모듈
    ├── jackson           # Jackson ObjectMapper 설정
    ├── logging           # Logback + Slack Appender + Tracing
    └── monitoring        # Actuator + Prometheus
```

### 모듈 규칙
- `apps`: BootJar 활성화, 직접 실행 가능. 다른 app 모듈 의존 금지.
- `modules`: `java-library` + `java-test-fixtures` 플러그인. 특정 도메인에 의존하지 않는 재사용 가능한 설정만 포함.
- `supports`: `java-library` 플러그인. 공통 횡단 관심사(로깅, 모니터링 등) 처리.

---

## commerce-api 레이어드 아키텍처

```
com.loopers
├── interfaces/api/     # REST 컨트롤러 (요청/응답 DTO 포함)
├── application/        # Facade 클래스 (유스케이스 오케스트레이션)
├── domain/             # 도메인 서비스, 도메인 모델, 리포지토리 인터페이스
├── infrastructure/     # 리포지토리 구현체, JPA Repository
└── support/error/      # 공통 예외 처리 (CoreException, ErrorType)
```

---

## 로컬 개발 환경

### 인프라 실행

```shell
# MySQL 8.0 / Redis 7.0 (master:6379, readonly:6380) / Kafka 3.5.1 (9092) / Kafka UI (9099)
docker-compose -f ./docker/infra-compose.yml up

# Prometheus + Grafana (http://localhost:3000, admin/admin)
docker-compose -f ./docker/monitoring-compose.yml up
```

### 활성 프로파일

| 프로파일 | 용도 |
|---|---|
| `local` | 로컬 개발 (기본값) |
| `test` | 테스트 (Testcontainers 사용) |
| `dev` | 개발 서버 |
| `qa` | QA 서버 |
| `prd` | 운영 서버 (Swagger UI 비활성화) |

---

## 테스트

```shell
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:jacocoTestReport
```

- `spring.profiles.active=test` 로 실행
- `user.timezone=Asia/Seoul` 적용
- Testcontainers로 실제 MySQL/Redis/Kafka 컨테이너를 띄워 통합 테스트 실행 (모킹 금지)
- `maxParallelForks = 1` (테스트 병렬 실행 비활성화)
- Instancio로 테스트 픽스처 데이터 생성

---

## 빌드

```shell
./gradlew build
./gradlew :apps:commerce-api:bootJar
```

- `apps/` 하위 모듈만 BootJar 생성
- `modules/`, `supports/` 는 일반 Jar만 생성
