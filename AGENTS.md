# Project Agent Instructions

## 기본 작업 원칙

이 저장소의 목적은 프로덕션 산출 자체보다 학습과 설계 고민의 기록에 있다.
작업할 때는 빠르게 정답을 확정하기보다 선택지, 트레이드오프, 판단 근거가 드러나도록 진행한다.

### 협업 방식

- 구현이나 설계 방향이 열려 있으면 최소 2개 이상의 선택지와 트레이드오프를 먼저 제시한다.
- 권장안을 제시할 수 있지만, 최종 결정은 사용자가 내린다.
- 사용자의 명시적 선택 없이 중요한 설계 결정을 코드나 문서에 확정 반영하지 않는다.
- 자명한 오타 수정, 단순 이동, 단순 포맷 변경은 바로 진행해도 된다.
- 결과만 말하지 말고 왜 그렇게 판단했는지 함께 설명한다.

## 핸드코딩 영역 규칙

새 기능 구현 시 핵심 구현 1개는 사용자가 직접 작성할 수 있도록 비워두는 것을 기본으로 한다.

- 구현 전 핸드코딩 후보를 먼저 제안하고 사용자와 합의한다.
- 보통 도메인 `Service`의 핵심 비즈니스 메서드, 도메인 `Entity`의 상태 전이/불변식 처리, 알고리즘성 코드가 후보가 된다.
- 합의된 영역에는 아래 표식을 남긴다.

```java
// TODO(hand-coded): 직접 구현할 영역
//   - 의도: <무엇을 해야 하는지 한 줄>
//   - 힌트: <필요시 1~2줄, 정답은 적지 말 것>
throw new UnsupportedOperationException("hand-coded zone");
```

- 사용자가 "이번엔 다 채워줘"라고 명시하면 전체 구현 가능하다.
- 사용자가 직접 작성한 코드를 리뷰할 때는 단점 지적 전에 선택의 근거를 먼저 확인한다.

## 개발 워크플로 규칙

기능 구현은 별도 지시가 없는 한 TDD 흐름을 따른다.

### TDD 사이클

- 🔴 Red: 요구사항을 만족하는 실패 테스트를 먼저 작성하고 실제 실패를 확인한다.
- 🟢 Green: 실패 테스트를 통과시키는 최소 구현만 작성한다.
- 🔵 Refactor: 모든 테스트가 통과한 상태에서 구조와 가독성을 정리한다.
- 한 사이클은 한 기능 단위로 제한한다.
- 테스트는 `Arrange`, `Act`, `Assert` 흐름이 드러나도록 작성한다.
- phase 전환 시 테스트 결과를 근거로 공유한다.

### 구현 우선순위

1. 실제로 동작하는 해결책을 우선한다.
2. 외부 입력 경계에서는 null-safety와 유효성 검증을 명확히 한다.
3. 테스트 가능한 구조로 설계한다.
4. 기존 코드 패턴을 먼저 확인하고 일관성을 유지한다.
5. 성능 최적화는 측정과 관찰 이후에 적용한다.

### 금지/주의

- 동작하지 않는 더미 구현이나 의미 없는 Mock 데이터로 기능을 채우지 않는다.
- Java 외부 경계값은 명시적으로 검증하고, 필요한 경우 `Optional`을 활용한다.
- `System.out.println`이나 디버그 print를 남기지 않는다. 필요하면 `log.*`를 사용한다.
- 불필요한 private 함수, 과한 추상화, 선제 최적화를 피한다.

## 프로젝트 구조 및 코드 컨벤션

### 주요 명령

항상 Gradle wrapper를 사용한다.

```bash
./gradlew build -x test
./gradlew build
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:test --tests "com.loopers.xxx.SomeTest"
./gradlew :apps:commerce-api:jacocoTestReport
./gradlew :apps:commerce-api:bootRun
```

테스트는 순차 실행 설정을 유지한다. `maxParallelForks = 1`은 공유 Testcontainers 환경을 고려한 설정이므로 임의로 변경하지 않는다.

### 로컬 인프라

로컬 실행이나 통합 테스트에 외부 인프라가 필요하면 아래 compose를 사용한다.

```bash
docker-compose -f ./docker/infra-compose.yml up -d
docker-compose -f ./docker/monitoring-compose.yml up -d
```

- MySQL: `localhost:3306`, database/user/password는 `loopers` / `application` / `application`
- Redis: master `localhost:6379`, replica `localhost:6380`
- Kafka: host `localhost:19092`
- Grafana: `localhost:3000`, `admin/admin`

### 모듈 구조

- `apps/*`는 실행 가능한 Spring Boot 애플리케이션이다. BootJar는 app 모듈에서만 생성한다.
- `modules/*`는 재사용 가능한 `java-library` 모듈이며 특정 도메인에 의존하지 않는다.
- `supports/*`는 logging, monitoring, jackson 같은 보조 라이브러리 모듈이다.
- module/support 설정은 각 모듈의 `*.yml`을 app의 `spring.config.import`로 로딩한다.
- QueryDSL `Q*` 타입이 필요한 경우 annotation processor는 사용하는 app build file에 둔다.

### 레이어 구조

`commerce-api` 패키지는 아래 방향을 따른다.

```text
interfaces/api/<feature>
application/<feature>
domain/<feature>
infrastructure/<feature>
support/error
```

- Controller는 요청/응답 변환과 인증 사용자 전달을 담당하고 `ApiResponse<T>`를 반환한다.
- DTO는 `<Feature>Dto` 안에 API 행위와 버전 단위로 중첩한다. 예: `UserDto.Register.V1.Request`, `UserDto.Register.V1.Response`.
- DTO의 응답 변환은 `from(...)` 팩토리로 처리한다.
- Facade는 얇은 유스케이스 조율자이며 비즈니스 규칙을 직접 갖지 않는다.
- Domain Service는 도메인의 외부 진입점으로 사용한다.
- Domain Entity는 상태 변경과 도메인 규칙을 담당하며 JPA annotation을 갖지 않는다.
- Domain layer는 Spring Data나 infrastructure 구현체에 의존하지 않는다.
- 비즈니스 예외는 `CoreException(ErrorType.X, "한국어 메시지")`로 표현한다.

### Repository Adapter 규칙

- `domain/<feature>/<X>Repository`는 도메인 포트/interface다.
- `infrastructure/<feature>/<X>RepositoryImpl`은 도메인 포트 구현체이며 Spring Data Repository에 위임한다.
- `infrastructure/<feature>/<X>JpaRepository`는 Spring Data JPA 전용 interface다.
- Domain layer는 `org.springframework.data.*` 타입을 import하지 않는다.

### 레이어별 데이터 객체 네이밍

상세 기준은 `docs/common/layered-naming-conventions.md`를 따른다.

- `interfaces` 레이어의 API 요청/응답 객체는 `*Dto`를 사용한다.
  - 예: `ProductDto`, `UserDto.Register.V1.Request`
- `application` 레이어에서 외부 레이어로 전달하는 유스케이스 정보 객체는 `*Info`를 사용한다.
  - 예: `ProductInfo`, `OrderInfo`, `AuthenticatedUserInfo`
- `domain` 레이어에서는 무제한 자유 네이밍을 피하고, 역할이 드러나는 제한된 suffix를 사용한다.
  - suffix 없음: JPA annotation을 갖지 않는 순수 도메인 엔티티. 예: `Product`, `Brand`, `User`, `Order`
  - `*Command`: 도메인 동작 입력
  - `*Criteria`: 조회/필터/페이징 조건
  - `*Result`: 도메인 동작 결과
  - `*View`: 조회용 조합 결과 또는 read-only data carrier
  - `*Failure`: 실패 사유 항목
  - `*Policy`: 순수 정책
  - `*Processor`: 순수 처리/조합 로직
  - `*Service`: 도메인 진입점
  - `*Reader`: 조회 전용 Repository 접근
  - `*Writer`: 생성/수정/삭제 Repository 접근
- JPA 영속화 객체는 infrastructure layer에서 `*JpaEntity`로 둔다.
  - 예: `ProductJpaEntity`, `BrandJpaEntity`, `UserJpaEntity`, `OrderJpaEntity`
- 도메인 조회 조합 객체는 `*Dto` 대신 `*View`를 우선 고려한다.
  - 예: `ProductDetail`보다 `ProductDetailView`

### 도메인 서비스 책임 분리

- API의 진입점은 `*Controller`, Domain의 진입점은 `*Service`로 둔다.
- `*Service`는 외부에서 도메인 기능에 접근하는 진입점 역할을 우선한다.
- Repository 접근 책임은 아래 객체로 분리한다.
  - `*Reader`: 조회 전용 Repository 접근
  - `*Writer`: 생성/수정/삭제 Repository 접근
  - `*Policy`, `*Processor`, `*ProcessService`: Repository 없이 순수 규칙 처리
- 2개 이상의 도메인 객체를 조합하는 순수 객체는 `*CatalogService`처럼 범용적인 이름보다, 조합되는 도메인과 책임이 드러나는 이름을 사용한다.
  - 예: `ProductBrandProcessService`
- Facade에서 Repository를 직접 호출하지 않는다. Facade는 Transaction 경계와 유스케이스 호출에 집중한다.

### get/find 네이밍 규칙

- `get*` 메서드는 결과가 반드시 있어야 하는 조회를 의미한다.
- `get*` 메서드는 결과가 없으면 해당 메서드 내부에서 예외를 던진다.
  - 예: `getProduct`, `getBrand`, `getOrder`
- `find*` 메서드는 결과가 없을 수 있는 조회를 의미한다.
- `find*` 메서드는 직접 Not Found 예외를 던지지 않고 `Optional`, `null`, 빈 컬렉션 등으로 부재를 표현한다.
- Java 코드에서는 단건 부재 표현에 `null`보다 `Optional<T>`를 우선 사용한다.
- 컬렉션 조회는 결과가 없으면 `null`이 아니라 빈 컬렉션을 반환한다.

### 의존 방향 다이어그램 규칙

- Mermaid 구조도의 화살표는 반환 흐름이 아니라 참조/import 또는 호출 의존 방향만 표현한다.
- 양방향 화살표는 순환 참조 또는 다이어그램 표현 오류로 본다.
- 기본 레이어 의존 방향은 `interfaces -> application -> domain -> infrastructure`로 표현한다.
- `interfaces -> domain` 직접 참조는 상위 레이어가 하위 레이어를 참조하는 것이므로 허용한다.

### 에러 처리

- Error response 생성은 `ApiControllerAdvice`에서만 담당한다.
- 비즈니스 실패는 `CoreException(ErrorType.X, "한국어 메시지")`로 표현하고, 도메인에서 raw `RuntimeException`을 던지지 않는다.
- 새 에러 유형이 필요하면 `ErrorType` enum에 status, code, 기본 메시지를 추가한다.
- Controller나 Facade에서 임의의 `ResponseEntity` 에러 응답을 직접 만들지 않는다.

### 공통 설정

- 기본 timezone은 `Asia/Seoul` 기준으로 본다.
- profile은 `local`, `test`, `dev`, `qa`, `prd`를 사용한다.
- Swagger UI 경로는 `/swagger-ui.html`이다.
- `prd` profile에서는 springdoc API docs가 비활성화된다.
- CI 밖에서 빌드 version은 root Gradle 설정의 short git hash 기준으로 생성되므로, 의도 없이 수동 지정하지 않는다.

### 테스트와 샘플

- 가능하면 도메인 단위 테스트, 서비스/통합 테스트, API E2E 테스트의 역할을 구분한다.
- 신규 API를 추가하면 `http/<app>/**.http`에 JetBrains HTTP Client 형식의 요청 샘플을 정리한다.
- Testcontainers 기반 통합 테스트는 Docker 실행을 전제로 한다.
- `modules/jpa`, `modules/redis`, 필요 시 `modules/kafka`의 test fixtures를 활용한다.
- 공유 Testcontainers 환경을 고려해 테스트 병렬 실행 설정을 임의로 바꾸지 않는다.

### 작업 제외 파일

- `.omc/`, `.codeguide/`, `.claude/`는 agent tooling state이므로 일반 기능 작업 커밋에 섞지 않는다.
- OS 생성 파일(`.DS_Store` 등)은 커밋하지 않는다.

## 커밋 메시지 작성 규칙

커밋 메시지는 Conventional Commit 접두어를 유지하고, 메시지 본문은 한국어로 명확하게 작성한다.

### 형식

```text
type: 작업 대상 + 명확한 동사
```

### 원칙

- `type`은 `feat`, `fix`, `docs`, `refactor`, `test`, `chore` 등 기존 커밋 컨벤션을 따른다.
- 콜론 뒤 메시지는 한국어로 작성한다.
- 메시지만 보고 어떤 작업인지 알 수 있어야 한다.
- 단순 명사형보다 작업 대상과 동사가 드러나는 문장을 사용한다.
- 여러 산출물을 나눠 커밋할 때는 각 커밋이 독립적으로 설명되도록 작성한다.

### 예시

```text
feat: 주문 생성 기능 구현
refactor: 장바구니 엔티티 리팩토링
test: 주문 생성 테스트 코드 추가
docs: 2주차 요구사항 문서 작성
docs: 2주차 시퀀스 다이어그램 문서 작성
docs: 관리자 및 비기능 요구사항 반영
```

## PR Note 작성 규칙

PR 메시지나 PR 노트 작성을 요청받으면 `obsidian-notes/pr-notes` 하위에 문서로 정리한다.
기본 포맷은 `obsidian-notes/pr-notes/volume-1-user-pr.md`를 기준으로 맞춘다.

### 파일명

- 형식: `volume-n-작업요약-pr.md`
- 예시: `volume-2-design-pr.md`

### 문서 구조

```markdown
---
date: YYYY-MM-DD
type: pr-note
volume: volume-n
title: 작업 내용 요약
---

# [volume-n] 작업 내용 요약

## 📌 Summary

- 배경:
- 목표:
- 결과:

## 💬 리뷰 포인트

- 과제를 수행하면서 실제로 고민한 방향성/설계/의사결정 수준의 질문을 작성한다.
- 각 리뷰 포인트는 `- `로 시작하는 bullet 형식으로 작성한다.
- 리뷰 포인트는 3개 이내로 작성한다.
- 질문은 구체적으로 작성하고, AI가 자동 생성한 듯한 일반론은 피한다.

## 🧭 Context & Decision

### 문제 정의

- 현재 동작/제약:
- 문제(또는 리스크):
- 성공 기준(완료 정의):

### 선택지와 결정

- 고려한 대안:
  - A:
  - B:
- 최종 결정:
- 트레이드오프:
- 추후 개선 여지(있다면):

## 🤔 고민한 점 / 막혔던 부분

## 🙋 기타
```

### 작성 톤

- 커밋/PR 제목은 `[volume-n] 작업 내용 요약` 형식을 따른다.
- `💬 리뷰 포인트`는 반드시 채운다.
- 단정적인 표현보다 과제를 수행하며 실제로 고민한 판단 근거와 트레이드오프를 중심으로 작성한다.
- 부정적인 자기평가보다 설계상 고민과 선택 기준을 드러내는 표현을 사용한다.
