Git 상태 분석, diff 요약, 커밋 메시지 생성, PR 설명 초안을 작성합니다.

## 입력
`$ARGUMENTS` — 수행할 작업 키워드 (`commit`, `diff`, `pr`, `log`, `status`). 없으면 현재 변경사항 기반으로 모두 요약.

## 동작

### `status` / (인수 없음)
```
git status
git diff --stat
```
변경 파일 목록과 요약을 출력하고, 다음 권장 액션 제안.

### `diff`
```
git diff          # unstaged
git diff --cached # staged
```
변경 내용을 도메인 맥락(비즈니스 의도)으로 한국어 요약.
- 추가/삭제 이유를 추론하여 서술
- 잠재적 사이드이펙트·리스크 지적

### `commit`
Staged 변경사항을 분석하여 Conventional Commits 형식 메시지 초안 생성:

```
<type>(<scope>): <한국어 제목 — 50자 이내>

<body: 변경 이유 및 핵심 내용 — 선택>
```

- `type`: `feat` / `fix` / `refactor` / `test` / `docs` / `chore` / `perf`
- `scope`: 모듈명 (예: `commerce-api`, `jpa`, `redis`)
- 메시지 출력 후 실제 커밋은 **개발자가 직접 실행** (자동 커밋 금지)

### `pr`
```
git log main..HEAD --oneline
git diff main...HEAD --stat
```
PR 제목 + 본문 초안(한국어) 생성:
- **Summary**: 변경 목적 3줄 이내
- **Changes**: 주요 변경 파일과 이유
- **Test plan**: 검증 방법 체크리스트
- **주의사항**: 리뷰어가 집중해야 할 포인트

### `log`
```
git log --oneline -20
```
최근 20개 커밋을 타임라인으로 요약하고 작업 흐름 설명.

## 제약
- 실제 `git commit`, `git push`, `git reset` 등 **상태 변경 명령은 자동 실행 금지**
- 커맨드 출력 후 개발자 확인을 거쳐 실행하도록 안내
- force push, `--no-verify` 등 위험 옵션은 명시적 요청 없이 제안 금지
