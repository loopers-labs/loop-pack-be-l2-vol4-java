# 아키텍처 구성 전략

본 프로젝트는 4계층의 레이어드 아키텍처를 따르며 DIP를 준수한다.

- API request, response DTO와 응용 레이어의 DTO는 분리해 작성하도록 합니다.
- 패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징하는 형태로 작성합니다.

## 레이어별 책임

각 레이어는 다음과 같은 책임을 가집니다.

- **interfaces** (presentation 레이어): 외부 요청/응답 계약, 입력 검증, HTTP 표현
- **application** (application 레이어): 유스케이스 흐름, 트랜잭션 경계, 여러 도메인 조합
- **domain** (domain 레이어): 도메인 객체, 비즈니스 규칙, Repository 계약
- **infrastructure** (infrastructure 레이어): JPA, Redis, Kafka 등 기술 구현체
