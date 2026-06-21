# Round 1 Legacy Reference

이 문서는 현재 3주차 구현의 기준 문서가 아니다. 기존 회원/인증 맥락이 필요할 때만 참고한다.

## 현재 사용 기준

- 3주차 구현에서는 회원 도메인을 새로 설계하거나 구현하지 않는다.
- `User`는 기존 `identity` 경계의 외부 식별자로만 다룬다.
- 대고객 API는 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더를 받아 사용자 식별 입력으로 사용한다.
- 회원가입, 내 정보 조회, 비밀번호 변경은 이번 구현 범위가 아니다.

## 남겨둘 맥락

| 항목 | 현재 3주차에서의 의미 |
| --- | --- |
| 회원가입 | 현재 구현 대상 아님 |
| 내 정보 조회 | 현재 구현 대상 아님 |
| 비밀번호 변경 | 현재 구현 대상 아님 |
| 인증 헤더 | 대고객 API의 사용자 식별 입력으로만 사용 |
| 테스트 원칙 | Arrange, Act, Assert 구조는 계속 따른다 |

## 참고 원칙

- 기존 회원 기능을 수정해야 하는 명시적 요청이 있을 때만 이 문서를 다시 본다.
- 현재 `catalog`, `ordering`, `payment`, `event` 도메인 구현 판단은 `.codeguide/loopers-3-week.md`, `.docs/domain.md`, `.docs/architecture.md`를 우선한다.
- `CLAUDE.md` 작성 요구는 현재 저장소에서는 `AGENTS.md` 규칙으로 대체한다.
