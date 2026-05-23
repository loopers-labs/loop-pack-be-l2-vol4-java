# Docs Index

이 문서는 설계 보조 문서다. volume-2 제출 커밋에는 포함하지 않는다.

## 진입점

- 1순위 진입점은 `AGENTS.md`다.
- 이 문서는 `AGENTS.md`에서 연결되는 문서 지도 역할만 한다.

## 탐색 순서

| 순서 | 문서 | 목적 |
| --- | --- | --- |
| 1 | `AGENTS.md` | 최상위 작업 규칙, 커밋 규칙, 탐색 순서 |
| 2 | `.docs/design-review.md` | 빠진 설계 항목, 다음 질문 순서 |
| 3 | `.docs/worklog.md` | 현재 작업 상태와 재개 지점 |
| 4 | `.docs/domain.md` | 도메인 용어, 상태명, 모듈 소속 |
| 5 | `.docs/architecture.md` | 도메인 우선 모듈러 모놀리스 결정 |
| 6 | `.docs/dto-spec.md` | API DTO 계약과 페이지 응답 형태 |
| 7 | `.codeguide/loopers-2-week.md` | 이번 주차 제출 조건 |
| 8 | `.codeguide/loopers-1-week.md` | 기존 회원/인증 맥락 |
| 9 | `.codeguide/service.md` | 전체 서비스 API 요구사항 기준 |
| 10 | `.docs/design/01-requirements.md` | 제출용 요구사항 분석 |
| 11 | `.docs/design/02-sequence-diagrams.md` | 제출용 시퀀스 다이어그램 |
| 12 | `.docs/design/03-class-diagram.md` | 제출용 클래스 설계 |
| 13 | `.docs/design/04-erd.md` | 제출용 ERD |

## 제출 문서

volume-2 제출 커밋에는 아래 4개 파일만 포함한다.

- `.docs/design/01-requirements.md`
- `.docs/design/02-sequence-diagrams.md`
- `.docs/design/03-class-diagram.md`
- `.docs/design/04-erd.md`

## 보조 문서

아래 문서는 설계 진행과 AI 탐색을 돕기 위한 보조 문서이며 제출 커밋에는 포함하지 않는다.

- `AGENTS.md`
- `.docs/README.md`
- `.docs/design-review.md`
- `.docs/worklog.md`
- `.docs/domain.md`
- `.docs/architecture.md`
- `.docs/dto-spec.md`
- `.codeguide/loopers-1-week.md`
- `.codeguide/loopers-2-week.md`
- `.codeguide/service.md`

## 탐색 규칙

- 설계 누락을 찾을 때는 `.docs/design-review.md`를 먼저 본다.
- 재개 위치를 찾을 때는 `.docs/worklog.md`를 먼저 본다.
- 이름이 흔들릴 때는 `.docs/domain.md`를 먼저 본다.
- 구조가 흔들릴 때는 `.docs/architecture.md`를 먼저 본다.
- DTO 모양이 흔들릴 때는 `.docs/dto-spec.md`를 먼저 본다.
- 과제 제출 기준이 흔들릴 때는 `.codeguide/loopers-2-week.md`를 먼저 본다.
- 회원/인증 맥락이 필요할 때만 `.codeguide/loopers-1-week.md`를 본다.
- 전체 서비스 API 기준이 흔들릴 때는 `.codeguide/service.md`를 본다.
