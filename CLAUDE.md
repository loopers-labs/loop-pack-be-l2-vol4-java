# CLAUDE.md

## 프로젝트 개요

Loopers에서 제공하는 Spring + Java 멀티 모듈 템플릿 프로젝트입니다.
커머스 도메인을 기반으로 API 서버, 배치, 스트리머(Kafka Consumer) 세 개의 실행 가능한 애플리케이션을 포함합니다.

---

## 기술 스택 및 버전

| 항목 | 버전 |
|------|------|
| Java | 21 (LTS) |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| Spring Dependency Management | 1.1.7 |
| Kotlin (Gradle 스크립트) | 2.0.20 |
| JPA / QueryDSL | Spring Boot BOM (Jakarta) |
| MySQL | 8.0 (Docker) |
| Redis | 7.0 (Master + Replica, Docker) |
| Kafka | 3.5.1 / KRaft 모드 (Docker, Bitnami) |
| Prometheus + Grafana | Docker (모니터링) |

### 주요 라이브러리 버전

| 라이브러리 | 버전 |
|-----------|------|
| springdoc-openapi | 2.7.0 |
| springmockk | 4.0.2 |
| mockito-core | 5.14.0 |
| instancio-junit | 5.0.2 |
| logback-slack-appender | 1.6.1 |
| ktlint-plugin | 12.1.2 / ktlint 1.0.1 |

---

## 모듈 구조

```
Root (loopers-java-spring-template)
├── apps/                   # 실행 가능한 SpringBootApplication
│   ├── commerce-api        # REST API 서버 (Web MVC + Swagger)
│   ├── commerce-batch      # Spring Batch 서버
│   └── commerce-streamer   # Kafka Consumer 서버
├── modules/                # 재사용 가능한 인프라 설정 모듈
│   ├── jpa                 # JPA + QueryDSL + MySQL
│   ├── redis               # Spring Data Redis (Master/Replica)
│   └── kafka               # Spring Kafka
└── supports/               # 부가 기능 add-on 모듈
    ├── jackson             # Jackson 커스텀 설정
    ├── logging             # Logback + Slack Appender
    └── monitoring          # Micrometer + Prometheus
```

### 모듈별 역할

#### apps
- **`commerce-api`**: Web MVC 기반 REST API. Swagger(SpringDoc OpenAPI) 제공. `jpa`, `redis`, `jackson`, `logging`, `monitoring` 모듈 의존.
- **`commerce-batch`**: Spring Batch 기반 배치 처리. `jpa`, `redis`, `jackson`, `logging`, `monitoring` 모듈 의존.
- **`commerce-streamer`**: Kafka Consumer 기반 이벤트 스트리밍. `jpa`, `redis`, `kafka`, `jackson`, `logging`, `monitoring` 모듈 의존.

#### modules
- **`jpa`**: `spring-boot-starter-data-jpa`, `querydsl-jpa (jakarta)`, `mysql-connector-j`, Testcontainers MySQL 제공. `java-test-fixtures` 플러그인으로 `DatabaseCleanUp`, `MySqlTestContainersConfig` 공유.
- **`redis`**: `spring-boot-starter-data-redis`. Testcontainers Redis 제공. `RedisCleanUp`, `RedisTestContainersConfig` 공유.
- **`kafka`**: `spring-kafka`. Testcontainers Kafka 제공. KafkaTestContainersConfig 공유.

#### supports
- **`jackson`**: `jackson-module-kotlin`, `jackson-datatype-jsr310` 커스텀 설정.
- **`logging`**: Logback + Slack Appender, Actuator 포함.
- **`monitoring`**: Actuator + Micrometer Prometheus Registry.

---

## 패키지 레이어 구조 (commerce-api 기준)

```
com.loopers
├── interfaces/api/         # Controller, DTO, ApiSpec (OpenAPI), ControllerAdvice
├── application/            # Facade (유스케이스 조합), Info (응답 변환 객체)
├── domain/                 # 도메인 모델, 서비스, 리포지토리 인터페이스
└── infrastructure/         # JPA 리포지토리 구현체
```

- **Controller → Facade → DomainService** 순으로 흐름.
- `ErrorType` enum으로 HTTP 상태 코드와 에러 메시지를 통합 관리.
- `CoreException`으로 도메인 규칙 위반 처리.

---

## 로컬 개발 환경

### 인프라 실행 (Docker)

```shell
# MySQL 8, Redis 7 (Master + Replica), Kafka 3.5.1, Kafka UI
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 (Prometheus + Grafana)
docker-compose -f ./docker/monitoring-compose.yml up
# Grafana: http://localhost:3000 (admin/admin)
```

### 포트 정보

| 서비스 | 포트 |
|--------|------|
| MySQL | 3306 |
| Redis Master | 6379 |
| Redis Replica | 6380 |
| Kafka | 9092 (내부), 19092 (호스트) |
| Kafka UI | 9099 |
| Grafana | 3000 |

### 기본 프로필

- 로컬 실행: `local` 프로필 (기본값)
- 테스트: `test` 프로필 (Testcontainers 사용)

---

## 빌드 및 테스트

```shell
# 전체 빌드
./gradlew build

# 테스트 실행
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test

# JaCoCo 리포트 생성
./gradlew jacocoTestReport

# 특정 앱 실행
./gradlew :apps:commerce-api:bootRun
```

### 테스트 규칙

- 테스트는 단일 스레드로 실행 (`maxParallelForks = 1`)
- 시스템 타임존: `Asia/Seoul`
- Testcontainers로 실제 MySQL, Redis, Kafka를 사용한 통합 테스트
- `testFixtures`를 통해 `DatabaseCleanUp`, `RedisCleanUp` 등을 테스트 간 공유

---

## CI / 자동화

- **GitHub Actions** (`main.yml`): PR 생성/재오픈 시 `qodo-ai/pr-agent` 실행 (코드 리뷰 자동화, `OPENAI_KEY` 필요)
- **Auto Assign** (`auto_assign.yml`): PR 자동 담당자 지정
- **PR 템플릿**: `.github/pull_request_template.md`

---

## 주요 설정 파일 위치

| 파일 | 역할 |
|------|------|
| `gradle.properties` | 버전 관리 (Spring, Kotlin, 라이브러리) |
| `settings.gradle.kts` | 모듈 등록, 플러그인 리포지토리 |
| `build.gradle.kts` (루트) | 공통 의존성, Java 21 툴체인, JaCoCo |
| `modules/jpa/src/main/resources/jpa.yml` | JPA/DataSource 설정 |
| `modules/redis/src/main/resources/redis.yml` | Redis 설정 |
| `modules/kafka/src/main/resources/kafka.yml` | Kafka 설정 |
| `supports/logging/src/main/resources/logging.yml` | Logback 설정 |
| `supports/monitoring/src/main/resources/monitoring.yml` | Actuator/Prometheus 설정 |
| `docker/infra-compose.yml` | 로컬 인프라 (MySQL, Redis, Kafka) |
| `docker/monitoring-compose.yml` | 모니터링 (Prometheus, Grafana) |

___

## 개발 규칙

### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)

#### 1. Red Phase : 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성
- 테스트 예시

#### 2. Green Phase : 테스트를 통과하는 코드 작성
- Red Phase 의 테스트가 모두 통과할 수 있는 코드 작성
- 오버엔지니어링 금지

#### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

___

## 주의사항

### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이요한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것

### 2. Recommendation
- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성

### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지