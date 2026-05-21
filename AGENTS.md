# 공통 AI 작업 지침

본 문서는 본 리포지토리에서 작업하는 모든 AI 에이전트(Claude Code, Codex, Cursor 등)가 공통으로 따라야 하는 규칙을 정의합니다.


## Common Rules

- 답변은 한국어로 작성한다.
- 작업 전 변경 대상 모듈을 먼저 식별한다.
- 변경 전 간단한 수정 계획을 먼저 설명한다.
- 불확실한 내용은 추측하지 말고 확인 필요 항목으로 구분한다.
- 공통 모듈 수정 시 영향받는 모듈을 함께 설명한다.

## Project Overview

- **이름**: `loopers-java-spring-template` (group `com.loopers`)
- **구성**: `commerce-api`, `commerce-streamer`, `commerce-batch` 세 개의 Spring Boot 앱 + 재사용 인프라 모듈(`jpa`, `redis`, `kafka`) + add-on(`jackson`, `logging`, `monitoring`).
- 상세 트리 / 패키지 컨벤션: [`.claude/rules/module-structure.md`](.claude/rules/module-structure.md)
- 기술 스택 / 버전 표: [`.claude/rules/tech-stack.md`](.claude/rules/tech-stack.md)
- 상세 코드 컨벤션: [`.claude/rules/code-conventions.md`](.claude/rules/code-conventions.md)
- 외부 문서·접속 정보: [`.claude/rules/references.md`](.claude/rules/references.md)

## 멀티 모듈 작업 규칙

- 디렉터리별 책임을 지킨다.
  - `apps/*` — 실행 가능한 Spring Boot 애플리케이션. 
  - `modules/*` — 특정 도메인에 종속되지 않는 reusable configuration (JPA, Redis, Kafka). 도메인 코드 금지.
  - `supports/*` — 로깅/모니터링/직렬화 등 add-on. 비즈니스 로직 금지.
- 새 모듈은 `settings.gradle.kts` 의 `include(...)` 에 등록하고, 루트 `build.gradle.kts` 의 `subprojects` 블록이 제공하는 공통 설정을 그대로 상속받도록 작성한다.
- `apps/*` 만 `bootJar` 가 활성, `modules/*`·`supports/*` 는 일반 `Jar`. 이 구분을 깨지 않는다.

## 코드 컨벤션

- Java 21, Spring Boot 3.4.x 기준 문법을 사용한다 (record, pattern matching, switch expression 등 허용).
- Java 파일은 Google Java Format 기준으로 포맷한다.
- Lombok 사용을 전제로 한다 (`implementation` + `annotationProcessor`). getter/setter 보일러플레이트보다 `@Getter`, `@Builder`, `@RequiredArgsConstructor` 등을 우선 고려한다.
- 패키지 컨벤션 (`apps/*` 기준):
  - `interfaces.api.<domain>` — Controller / DTO / Swagger Spec / Advice
  - `application.<domain>` — Facade / Info (UseCase, 트랜잭션 경계)
  - `domain.<domain>` — Model / Service / Repository 인터페이스
  - `infrastructure.<domain>` — Repository 구현체, Jpa/Querydsl repository
- Controller 는 얇게, 비즈니스 규칙은 Service/Facade 에 둔다.
- 상세 코드 작성 규칙은 [`.claude/rules/code-conventions.md`](.claude/rules/code-conventions.md)를 따른다.

## 테스트 규칙

- 새 기능/버그 수정 시 가능한 한 테스트를 함께 추가한다.
- 통합 테스트는 Testcontainers (MySQL/Redis/Kafka) 를 사용한다 — mock 으로 대체하지 않는다 (인프라 호환성 검증 목적).
- 테스트는 다음 환경에서 동작해야 한다 (루트 `build.gradle.kts` `tasks.test`):
  - JUnit Platform (`useJUnitPlatform()`)
  - `maxParallelForks = 1` (직렬 실행)
  - `user.timezone = Asia/Seoul`
  - `spring.profiles.active = test`
  - JVM 옵션 `-Xshare:off`
- 단위 테스트는 JUnit 5 + Mockito 또는 springmockk, 데이터 픽스처는 Instancio 를 우선 고려한다.
- `modules:jpa`, `modules:redis`, `modules:kafka` 의 `testFixtures` (DatabaseCleanUp, RedisCleanUp, TestContainersConfig 등) 를 재사용한다.

## 빌드 / 실행 / 테스트 명령

```bash
# 로컬 인프라 기동
docker-compose -f ./docker/infra-compose.yml up
# (선택) 모니터링 스택
docker-compose -f ./docker/monitoring-compose.yml up

# 컴파일 확인
./gradlew :apps:<app>:compileJava

# 모듈 단위 테스트
./gradlew :apps:<app>:test
./gradlew :modules:<module>:test

# 전체 빌드 (테스트 포함)
./gradlew build

# 특정 앱 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-streamer:bootRun
./gradlew :apps:commerce-batch:bootRun -Pjob.name=<JOB_NAME>

# 커버리지 리포트 (XML)
./gradlew jacocoTestReport
```

Testcontainers 사용 테스트는 Docker 데몬이 실행 중이어야 한다.

## Git / PR

- 새 커밋을 만드는 것이 원칙이며, 이미 푸시된 커밋의 `--amend` / `force-push` 는 사용자가 명시적으로 지시했을 때만 수행한다.
- PR 본문은 `.github/pull_request_template.md` 양식을 따른다.
- `.github/workflows/main.yml` 에서 qodo-ai PR-Agent 가 자동 동작하므로 PR 제목/설명은 명확한 한국어 문장으로 작성한다.
- 민감한 파일(`.env`, 키 파일 등)은 절대 스테이징하지 않는다.

## 위험한 작업 / 확인이 필요한 작업

다음 작업은 반드시 사용자 승인 후에만 수행한다.

- 원격 브랜치에 push / force-push
- `git reset --hard`, 브랜치 삭제, untracked 파일 일괄 삭제
- 의존성 버전(`gradle.properties`) 변경, Spring Boot/Spring Cloud 메이저 업그레이드
- `docker/*-compose.yml` 의 포트/볼륨 변경
- 운영용 설정 파일(`application.yml` 의 `dev`/`qa`/`prd` 섹션) 수정
