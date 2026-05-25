# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

루퍼스 4기 백엔드 학습용 Spring Boot 멀티모듈 템플릿 (`loopers-java-spring-template`, group `com.loopers`).

### 기술 스택 / 버전

| 영역 | 버전 |
| --- | --- |
| Java toolchain | **21** |
| Spring Boot | **3.4.4** |
| Spring Dependency Management | 1.1.7 |
| Spring Cloud BOM | 2024.0.1 |
| Spring Kafka | 부모 BOM 따름 |
| Spring Batch | 부모 BOM 따름 |
| QueryDSL | `com.querydsl:querydsl-jpa::jakarta` (APT) |
| Hibernate / Data JPA | Spring Boot 부모 BOM |
| MySQL Driver | `mysql-connector-j` (runtime), MySQL 서버 8.0 |
| Redis | `spring-boot-starter-data-redis`, 서버 Redis 7.0 (master + read replica) |
| Kafka | `spring-kafka` 3.5.1 (KRaft) |
| SpringDoc OpenAPI | 2.7.0 |
| Lombok | Spring Boot 부모 BOM |
| Micrometer Prometheus | Spring Boot 부모 BOM |
| Logback Slack Appender | 1.6.1 |
| JUnit Platform | Spring Boot 부모 BOM |
| Mockito | 5.14.0 |
| SpringMockK | 4.0.2 |
| Instancio | 5.0.2 |
| Testcontainers | MySQL / Redis / Kafka |

`gradle.properties`가 단일 진실의 원천이다 — 버전을 바꿀 때는 여기에서 바꾼다.

### 모듈 구성 (`settings.gradle.kts`)

```
apps/        ← 실행 가능한 @SpringBootApplication (BootJar = true, plain Jar = false)
  commerce-api       REST API 진입점 (구현 주 대상). Web + Actuator + SpringDoc.
  commerce-batch     Spring Batch 잡 실행기.
  commerce-streamer  Kafka 컨슈머 애플리케이션.
modules/     ← 재사용 가능한 설정 라이브러리 (java-library + java-test-fixtures)
  jpa     Data JPA, QueryDSL APT, Hikari DataSource, BaseEntity, Testcontainers(MySQL) fixture
  redis   Data Redis, master/replica 라우팅, Testcontainers(Redis) fixture
  kafka   Spring Kafka, Testcontainers(Kafka) fixture
supports/    ← 부가 기능 add-on
  jackson      JSR310 + Kotlin 모듈
  logging      logback + Slack appender 설정 (logback.xml)
  monitoring   Actuator + Prometheus + Micrometer Tracing (Brave)
```

루트 `build.gradle.kts`의 `subprojects` 블록이 모든 하위 모듈에 공통 의존성과 테스트 설정을 주입한다. `apps/`만 BootJar를 빌드하고, 나머지는 일반 jar를 빌드해 다른 모듈이 의존할 수 있게 한다.

### 설정 / 프로파일

`apps/commerce-api/src/main/resources/application.yml`이 모듈별 yml을 import한다 (`jpa.yml`, `redis.yml`, `logging.yml`, `monitoring.yml`). 프로파일: `local`(기본), `test`, `dev`, `qa`, `prd`.

- `local`/`test`: MySQL `ddl-auto: create`, Redis는 localhost 6379/6380으로 고정.
- `prd`/`qa`/`dev`: 호스트/계정은 환경변수(`MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PWD`, `REDIS_*_HOST/PORT`) 주입 전제.
- HikariCP prefix는 `datasource.mysql-jpa.main` — 다른 DataSource를 추가하려면 `DataSourceConfig`를 본떠 prefix만 바꾸면 된다.

### 테스트

- 루트 빌드 설정에서 `spring.profiles.active=test`, `user.timezone=Asia/Seoul`이 강제된다. `maxParallelForks=1`로 직렬 실행.
- `@SpringBootTest`는 자동으로 `MySqlTestContainersConfig`가 띄운 MySQL 8.0 컨테이너에 붙는다 (`static` 블록에서 시작하고 system property로 jdbc-url 주입).
- 테스트 작성 컨벤션(스타일·단언·픽스처·E2E 패턴·클래스명·격리)은 `.claude/conventions/`의 `common/testing.md`와 각 레이어 `test.md`를 따른다.

---

## 코드 컨벤션 (필수)

모든 코드 작업은 `.claude/conventions/` 를 준수한다. **작업 전 반드시 [`.claude/conventions/README.md`](.claude/conventions/README.md)** 를 읽고, 거기 레이어/타입별 인덱스에서 해당 파일을 찾아 참조한다. 각 파일은 책임·정식 참조(코드 원천)·핵심 규칙·발췌·do/don't로 구성되며, 규칙이 코드와 어긋나면 정식 참조 코드가 원천이다. User 도메인이 정식 참조 구현이므로 새 도메인은 그 레이어·네이밍을 그대로 본뜬다.

---

## 자주 쓰는 명령어

빌드/품질:
```bash
./gradlew build                    # 전체 빌드 + 테스트 + jacoco
./gradlew :apps:commerce-api:build # 단일 모듈 빌드
./gradlew clean
```

테스트:
```bash
./gradlew test                                                   # 모든 모듈 테스트
./gradlew :apps:commerce-api:test                                # 단일 모듈
./gradlew :apps:commerce-api:test --tests "ExampleV1ApiE2ETest"  # 단일 클래스
./gradlew :apps:commerce-api:test --tests "*ApiE2ETest.Get.*"    # 중첩 클래스/메서드 패턴
./gradlew :apps:commerce-api:jacocoTestReport                    # 커버리지 XML 리포트
```

애플리케이션 실행:
```bash
./gradlew :apps:commerce-api:bootRun           # 기본 local 프로파일
SPRING_PROFILES_ACTIVE=dev ./gradlew :apps:commerce-api:bootRun
```

로컬 인프라 (실행 전 반드시 띄울 것):
```bash
docker-compose -f ./docker/infra-compose.yml up        # MySQL(3306), Redis master(6379)/replica(6380), Kafka(9092/19092), Kafka UI(9099)
docker-compose -f ./docker/monitoring-compose.yml up   # Grafana(3000, admin/admin) + Prometheus
```

문서/엔드포인트:
- Swagger UI: `http://localhost:8080/swagger-ui.html` (`local`/`dev`/`qa`만 활성, `prd`는 비활성)
- Actuator: `http://localhost:8081/actuator/health`, `/actuator/prometheus` (관리 포트 8081)

---

## 작업 원칙 (항상 필수로 따를 것)

> **트레이드오프:** 이 원칙들은 속도보다 신중함을 우선한다. 사소한 작업에는 판단력을 발휘하라.

### 1. 코드를 짜기 전에 생각하라

**가정하지 말고, 혼란을 숨기지 말고, 트레이드오프를 드러내라.**

구현 전에:
- 가정을 명시적으로 말하라. 불확실하면 묻는다.
- 해석이 여러 가지 가능하다면 모두 제시한다 — 조용히 하나를 고르지 않는다.
- 더 단순한 접근이 있으면 말한다. 정당한 이유가 있을 땐 반박한다.
- 불분명한 게 있으면 멈춰라. 무엇이 헷갈리는지 짚고, 묻는다.

### 2. 단순함이 우선이다

**문제를 해결하는 최소한의 코드. 추측성 코드 금지.**

- 요청되지 않은 기능을 덧붙이지 않는다.
- 1회용 코드에 추상화를 두지 않는다.
- 요청되지 않은 "유연성"이나 "설정 가능성"을 만들지 않는다.
- 일어날 수 없는 시나리오에 대한 예외 처리를 넣지 않는다.
- 200줄을 썼는데 50줄로 가능하다면, 다시 쓴다.

스스로 물어라: "시니어 엔지니어가 보면 이거 과하다고 할까?" 그렇다면 단순화한다.

### 3. 외과적으로 변경하라

**필요한 곳만 손댄다. 본인이 만든 흔적만 정리한다.**

기존 코드를 수정할 때:
- 주변 코드, 주석, 포매팅을 "개선"하지 않는다.
- 부서지지 않은 것을 리팩터링하지 않는다.
- 본인 취향과 다르더라도 기존 스타일에 맞춘다.
- 무관한 데드 코드를 발견했다면 — 삭제하지 말고 언급만 한다.

본인 변경이 고아를 만든 경우:
- 본인 변경 때문에 사용처가 사라진 import/변수/함수만 정리한다.
- 요청받지 않은 이상, 기존부터 있던 데드 코드는 제거하지 않는다.

테스트: 변경된 모든 줄이 사용자의 요청까지 직접 거슬러 올라갈 수 있어야 한다.

### 4. 목표 주도로 실행하라

**성공 기준을 정의하라. 검증될 때까지 루프를 돌려라.**

작업을 검증 가능한 목표로 바꿔라:
- "검증 추가" → "잘못된 입력에 대한 테스트를 쓰고, 통과시킨다"
- "버그 수정" → "버그를 재현하는 테스트를 쓰고, 통과시킨다"
- "X 리팩터" → "리팩터 전후로 모든 테스트가 통과함을 확인한다"

여러 단계 작업이라면 짧은 계획을 명시한다:
```
1. [단계] → 검증: [확인 방법]
2. [단계] → 검증: [확인 방법]
3. [단계] → 검증: [확인 방법]
```

강한 성공 기준은 독립적인 루프를 가능하게 한다. 약한 기준("작동하게 만들어")은 끊임없는 질의응답을 부른다.

**이 원칙들이 작동하고 있다는 신호:** diff에 불필요한 변경이 줄어든다 / 과설계로 인한 재작성이 줄어든다 / 실수 후가 아니라 구현 전에 명확화 질문이 나온다.

> 코드 표현 스타일(매직넘버 상수화·String.format·에러 메시지 분리·장식 주석 금지·정적 팩토리/변수/메서드 네이밍)은 `.claude/conventions/common/{naming, code-style}.md`를 따른다.

---

## 스킬 자동 트리거

**커밋 작업은 항상 `smart-commit` 스킬을 거친다.** Claude는 `git commit`을 직접 호출하지 않는다 — 커밋 단위 분리, 메시지 작성, 커밋 전 게이트(테스트·경고 체크)는 모두 smart-commit이 담당한다.
