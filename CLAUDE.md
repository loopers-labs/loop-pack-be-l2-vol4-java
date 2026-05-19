# CLAUDE.md

## 프로젝트
부트캠프 학습용 커머스 도메인 멀티 모듈 템플릿 (Gradle Java DSL).

## 핵심 스택
- Java 21 / Spring Boot 3.4.4 / Spring Cloud 2024.0.1
- JPA + QueryDSL(jakarta), Lombok, SpringDoc 2.7.0
- 테스트: JUnit Platform, Mockito 5.14, springmockk 4.0.2, Instancio 5.0.2, Testcontainers (MySQL)
- 상세 버전: `gradle.properties` / `build.gradle.kts`
- 활성 프로필: 기본 `local`, 테스트 `test` (`Asia/Seoul`)

## 모듈 (`settings.gradle.kts`)
- `apps/{commerce-api, commerce-batch, commerce-streamer}` — 1주차 작업 대상은 `commerce-api`
- `modules/{jpa, redis, kafka}` — 인프라 설정
- `supports/{jackson, logging, monitoring}` — 부가 모듈

## commerce-api 패키지 계층 (`com.loopers`)
- `interfaces.api.<domain>` — Controller / ApiSpec / Dto
- `application.<domain>` — Facade / Info (유스케이스 + 트랜잭션 경계)
- `domain.<domain>` — Model / Service / Repository(IF)
- `infrastructure.<domain>` — JpaRepository / RepositoryImpl
- `support.error` — CoreException / ErrorType

## 공통 컨벤션
- 엔티티는 `BaseEntity` 상속 (id/createdAt/updatedAt/deletedAt 자동).
- 도메인 검증 실패: `CoreException(ErrorType.BAD_REQUEST, ...)`.
- 응답 래핑 `ApiResponse<T>`, 예외 변환 `ApiControllerAdvice`.
- 컨트롤러는 `*V1ApiSpec` implements (Swagger 어노테이션 분리).

## 개발 Workflow
**증강 코딩**: AI 는 방향성·결정에 *제안만* 한다. 임의 판단/테스트 삭제 금지.

**TDD (Red → Green → Refactor)**, 모든 테스트는 3A (Arrange-Act-Assert).
1. Red: 실패하는 테스트 먼저.
2. Green: 통과시키는 최소 코드 (오버엔지니어링 금지).
3. Refactor: 테스트 통과 유지하며 정리 (unused import, 응집).

## 커밋 규칙
작업 단위로 분리, 전체 1커밋 금지.
prefix: `feat:` / `fix:` / `refactor:` / `docs:` / `test:`.

## Never Do
- 실제 동작 안 하는 코드 / Mock 데이터로 흉내내기.
- null-safety 누락 (Optional 활용).
- `System.out.println` 잔존 (SLF4J 사용).

## Priority
1. 동작하는 해결책 → 2. null/thread-safety → 3. 테스트 가능한 구조 → 4. 기존 패턴 일관성.

## 권장
- E2E 는 실제 API 호출로 검증.
- 완성 API 는 `apps/commerce-api/.http/*.http` 시나리오 정리.

## 빠른 명령
- 테스트: `./gradlew :apps:commerce-api:test`
- 특정: `./gradlew :apps:commerce-api:test --tests "*UserModelTest"`
- 인프라: `docker-compose -f ./docker/infra-compose.yml up` (상세는 `README.md`)
