## 1. 커밋 규칙
- volume-2 제출 커밋에는 `.docs/design` 디렉토리의 아래 4개 파일만 포함한다.
  - `.docs/design/01-requirements.md`
  - `.docs/design/02-sequence-diagrams.md`
  - `.docs/design/03-class-diagram.md`
  - `.docs/design/04-erd.md`
- `AGENTS.md`, `.codeguide/**`, 도메인 용어집, 구현 계획 문서는 제출 커밋에 포함하지 않는다.

## 2. 설계 진행 규칙
- 설계 문서를 수정하기 전 핵심 질문을 한 줄씩 묻고, 답변을 받은 뒤 반영한다.
- 도메인명, 상태명, API명, 메서드명은 도메인 용어집과 일치시킨다.
- 애매한 요구사항은 추측하지 말고 선택지와 영향도를 먼저 제시한다.

## 3. 아키텍처 결정
- 장기적으로 큰 서비스를 만든다는 전제로 도메인 우선 모듈러 모놀리스로 설계한다.
- 최상위 모듈 경계는 `catalog`, `ordering`, `payment`, `event`로 나눈다.
- 각 모듈 내부에는 `interfaces`, `application`, `domain`, `infrastructure` 계층을 둔다.
- 현재 구현 구조가 목표 구조와 다르면, 구현을 바로 바꾸지 말고 설계 문서에 목표 구조와 차이를 먼저 기록한다.

## 4. 문서 탐색 순서
- 이 파일이 1순위 진입점이다. 설계 작업을 시작하면 반드시 `AGENTS.md`를 먼저 읽는다.
- 그 다음 아래 순서로 문서를 읽고, 하위 문서의 내용이 충돌하면 `AGENTS.md`의 규칙을 우선한다.
  1. `.docs/README.md`
  2. `.docs/design-review.md`
  3. `.docs/worklog.md`
  4. `.docs/domain.md`
  5. `.docs/architecture.md`
  6. `.codeguide/loopers-2-week.md`
  7. `.codeguide/loopers-1-week.md`
  8. `.docs/design/01-requirements.md`
  9. `.docs/design/02-sequence-diagrams.md`
  10. `.docs/design/03-class-diagram.md`
  11. `.docs/design/04-erd.md`

## 5. 문서 역할
- `.docs/README.md`: 문서 지도와 탐색 목적을 정의한다.
- `.docs/design-review.md`: 빠진 설계 항목과 다음 질문 순서를 관리한다.
- `.docs/worklog.md`: 언제 중단되어도 이어가기 위한 현재 작업 상태를 관리한다.
- `.docs/domain.md`: 도메인명, 상태명, 모듈 소속, 구현 이름의 기준이다.
- `.docs/architecture.md`: 장기 아키텍처 결정과 모듈 경계를 정의한다.
- `.codeguide/loopers-2-week.md`: 이번 주차 과제 조건과 제출 기준이다.
- `.codeguide/loopers-1-week.md`: 기존 회원/인증 맥락을 확인할 때만 참조한다.
- `.docs/design/*`: volume-2 제출 대상 4개 문서다.

## 6. 문서 수정 규칙
- 제출 문서에 반영하기 전, 먼저 `.docs/design-review.md`의 빠진 항목을 갱신한다.
- 도메인명이나 상태명이 바뀌면 `.docs/domain.md`를 먼저 수정한 뒤 제출 문서에 반영한다.
- 아키텍처 경계가 바뀌면 `.docs/architecture.md`를 먼저 수정한 뒤 제출 문서에 반영한다.
- `.docs/design`에는 제출 대상 4개 파일 외의 파일을 추가하지 않는다.

## 7. 작업 지속성 규칙
- 매 작업 단위가 끝날 때 `.docs/worklog.md`를 갱신한다.
- `.docs/worklog.md`에는 현재 브랜치, 최근 결정, 수정 파일, 제출 포함/제외 파일, 다음 질문을 남긴다.
- 사용자가 언제 세션을 종료해도 다음 작업자는 `AGENTS.md -> .docs/README.md -> .docs/design-review.md -> .docs/worklog.md` 순서로 읽고 이어간다.
- 제출 문서 수정 전후에는 `.docs/worklog.md`에 어떤 제출 파일을 건드렸는지 기록한다.
