# 레이어링 지침

패키지/레이어 책임과 DTO 기본 원칙은 `.claude/rules/code-conventions.md` 를 따른다.

## 이커머스 적용 체크리스트

- 사용자 API 는 `interfaces.api.<domain>` 하위에 둔다.
- 관리자 API 는 `interfaces.api.admin.<domain>` 하위에 둔다.
- 사용자 API 와 관리자 API 는 Controller, Request/Response DTO 를 분리한다.
- 주문 생성처럼 여러 도메인이 결합되는 유스케이스는 `application.order` 의 Facade 에 흐름을 둔다.
- 결제 요청과 주문 상태 변경이 결합되는 유스케이스는 `application.payment` 또는 기존 도메인 흐름에 맞춰 책임을 분리한다.
