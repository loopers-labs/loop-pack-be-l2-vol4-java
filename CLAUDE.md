# CLAUDE.md

## 로컬 환경 설정

> 새 컴퓨터에서 프로젝트를 처음 실행할 때 참고

### 필수 설치
- JDK 21
- MySQL

### MySQL 설정
```sql
CREATE DATABASE loopers_test;
CREATE USER 'application'@'localhost' IDENTIFIED BY 'application';
GRANT ALL PRIVILEGES ON loopers_test.* TO 'application'@'localhost';
```

### Windows 환경변수 설정
Docker 없이 로컬 MySQL로 테스트를 실행하기 위해 필요하다.
```
TESTCONTAINERS_FALLBACK_ENABLED=true
```
설정 방법: 시스템 환경변수 → 새로 만들기 → 변수명/값 입력

### 테스트 실행
```bash
TESTCONTAINERS_FALLBACK_ENABLED=true ./gradlew :apps:commerce-api:test
```

---

## 개발 규칙

### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.
- **커밋 금지** : git commit 은 항상 개발자가 직접 수행한다. AI 는 어떠한 경우에도 커밋하지 않는다.
- **작업 완료 후** : 작업이 끝나면 무엇을 했는지 간단히 설명하고, 다음 단계를 제안한 뒤 개발자의 확인을 받고 진행한다.

### 스터디 세션 Workflow
- 테스트 케이스 목록은 **표(table) 형식**으로 제시, 사용자 승인 후 진행
- **RED 후 반드시 HARD STOP** — 사용자가 직접 테스트 실행해 실패 확인
- GREEN은 사용자 승인 후에만 진행
- GREEN 완료 후 → `/design-qna` 스킬 실행 → 결과를 `MyStudy/week3/03-concepts-qna.md`에 Q번호 이어서 기록
- 세션 마무리 → `/study-log` 스킬 실행 → 결과를 `MyStudy/week3/02-design-decisions.md`에 추가

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)

#### 1. Red Phase : 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성

#### 2. Green Phase : 테스트를 통과하는 코드 작성
- Red Phase 의 테스트가 모두 통과할 수 있는 코드 작성
- 오버엔지니어링 금지

#### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

---

## 주의사항

### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
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

---

## 요구사항 분석 접근법

요구사항을 분석할 때 반드시 다음 흐름을 따른다.

### 1. 요구사항을 그대로 믿지 말고, 문제 상황으로 다시 설명한다.
- "무엇을 만들까?"가 아니라 "지금 어떤 문제가 있고, 그걸 왜 해결하려는가?"로 재해석한다.
- 사용자 관점 / 비즈니스 관점 / 시스템 관점을 분리해서 정리한다.

### 2. 애매한 요구사항을 숨기지 말고 드러낸다.
- 정책 질문 (기준 시점, 성공/실패 조건, 예외 처리 규칙)
- 경계 질문 (어디까지가 한 책임인가, 어디서 분리되는가)
- 확장 질문 (나중에 바뀔 가능성이 있는가)

### 3. 다이어그램은 항상 이유 → 다이어그램 → 해석 순서로 제시한다.
- Mermaid 문법 사용
- 시퀀스 다이어그램: 책임 분리, 호출 순서, 트랜잭션 경계
- 클래스 다이어그램: 도메인 책임, 의존 방향, 응집도
- ERD: 영속성 구조, 관계의 주인, 정규화 여부

### 4. 설계의 잠재 리스크를 반드시 언급한다.
- 해결책은 정답처럼 말하지 않고 선택지로 제시한다.

---

## 도메인 & 객체 설계 전략
- 도메인 객체는 비즈니스 규칙을 캡슐화해야 한다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해 기능을 제공해야 한다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행한다.

## 아키텍처 & 패키지 구성 전략
- 레이어드 아키텍처를 따르며, DIP (의존성 역전 원칙) 을 준수한다.
- API request/response DTO와 응용 레이어의 DTO는 분리해 작성한다.
- 패키징 전략: 4개 레이어 패키지 하위에 도메인별로 패키징한다.

```
/interfaces/api       # presentation 레이어 - API
/application/..       # application 레이어 - ApplicationService, Facade, Info DTO
/domain/..            # domain 레이어 - Model, Repository 인터페이스 (DomainService 필요 시 포함)
/infrastructure/..    # infrastructure 레이어 - JPA, Redis 등 Repository 구현체
```

### 서비스 명칭 및 책임 구분

| 명칭 | 위치 | 책임 | Repository 의존 |
|------|------|------|----------------|
| `XDomainService` | `domain/..` | 순수 도메인 로직 (POJO). 여러 도메인 객체에 걸친 비즈니스 규칙 | ❌ |
| `XService` | `application/..` | 단일 애그리거트 유스케이스 처리. Repository를 통한 영속성 조작 | ✅ |
| `XFacade` | `application/..` | 여러 Service를 조합해 기능 제공. Controller가 호출하는 진입점 | ❌ (Service 통해 간접 사용) |

**의존 방향:**
```
interfaces → application(Facade → Service) → domain(Model, Repository 인터페이스) ← infrastructure
```

---

## 설계 문서 참조 가이드

상황에 따라 아래 문서를 읽고 작업한다. 불필요한 문서는 읽지 않는다.

| 상황 | 참조 파일 |
|------|-----------|
| 요구사항 분석, 도메인 규칙, API 목록, Soft Delete 정책 확인 | `.docs/design/01-requirements.md` |
| 호출 흐름, 트랜잭션 경계, 인증 흐름 설계 | `.docs/design/02-sequence-diagrams.md` |
| 클래스 구조, 도메인 객체 책임, 의존 방향 설계 | `.docs/design/03-class-diagram.md` |
| DB 스키마, 인덱스 전략, 제약조건 설계 | `.docs/design/04-erd.md` |
