# CLAUDE.md

## 프로젝트 개요
Spring Boot 기반 멀티모듈 커머스 백엔드 (`loopers-java-spring-template`)

## 기술 스택
| 항목 | 버전 |
|------|------|
| Java | 21 (corretto-21) |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| QueryDSL | 5.1.0 |
| springdoc-openapi | 2.7.0 |
| MySQL | 8.0 |
| Redis (Lettuce) | 6.4.x |

## 모듈 구조
```
apps/
  commerce-api       # 메인 REST API 서버 (port: 8080, management: 8081)
  commerce-batch     # 배치 처리
  commerce-streamer  # Kafka 컨슈머
modules/
  jpa                # DataSource, Hibernate, QueryDSL 공통 설정
  redis              # Redis 공통 설정
  kafka              # Kafka 공통 설정
supports/
  jackson            # ObjectMapper 설정
  logging            # Logback 설정
  monitoring         # Actuator / Prometheus 설정
```

## 실행
```bash
# 앱 실행 전 포트 확인 (8080이 비어있어야 함)
./gradlew :apps:commerce-api:bootRun
```
> IntelliJ 사용 시 SDK / Gradle JVM 을 **corretto-21** 로 설정, Run Configuration에서 **Single instance** 활성화 필수

## 개발 규칙

### Workflow — 증강 코딩
- 방향성·주요 의사결정은 **제안만** 하고, 개발자 승인 후 수행
- 요청하지 않은 기능 구현·테스트 삭제 금지 (개발자 개입 필요)

### Workflow — 테스트 작성 원칙
- 모든 테스트는 **Arrange / Act / Assert** 3A 원칙으로 작성
- 테스트 설명은 **행동 중심**으로 간결하게 기술 (e.g. "null loginId로 생성 시 BAD_REQUEST 예외가 발생한다.")
- **Best case** (정상 흐름) 와 **Edge case** (경계값, 예외 상황, 누락 입력 등) 를 모두 꼼꼼히 작성

## 주의사항

### Never Do
- 실제 동작하지 않는 코드 / 불필요한 Mock 데이터 기반 구현 금지
- null-unsafe 코드 금지 → `Optional` 활용
- `System.out.println` 코드 잔존 금지

### Recommendation
- 실제 API 호출 기반 **E2E 테스트** 작성
- 완성된 API는 `.http/*.http` 에 분류하여 작성
- 재사용 가능한 객체 설계 및 성능 최적화 대안 제안

### Priority
1. 실제 동작하는 해결책
2. null-safety / thread-safety
3. 테스트 가능한 구조
4. 기존 코드 패턴과의 일관성


## 도메인 & 객체 설계 전략
- 도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공해야 합니다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높습니다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행합니다.

## 아키텍처, 패키지 구성 전략
- 본 프로젝트는 레이어드 아키텍처를 따르며, DIP (의존성 역전 원칙) 을 준수합니다.
- API request, response DTO와 응용 레이어의 DTO는 분리해 작성하도록 합니다.
- 패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징하는 형태로 작성합니다.
    - 예시
      > /interfaces/api (presentation 레이어 - API)
      /application/.. (application 레이어 - 도메인 레이어를 조합해 사용 가능한 기능을 제공)
      /domain/.. (domain 레이어 - 도메인 객체 및 엔티티, Repository 인터페이스가 위치)
      /infrastructure/.. (infrastructure 레이어 - JPA, Redis 등을 활용해 Repository 구현체를 제공)