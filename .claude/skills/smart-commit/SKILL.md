---
name: smart-commit
description: 미커밋 변경사항을 의미 단위로 분석해 기능별 커밋을 순서대로 생성한다. "커밋 만들어줘", "기능별로 커밋해줘", "작업 마무리 커밋" 등 커밋 생성·정리 요청에서 사용.
---

# Smart Commit

## 개요

현재 변경사항(staged + unstaged + untracked)을 **기능 단위**로 의미적으로 분석하여, 적절한 커밋 타입과 메시지를 결정하고 커밋을 생성한다.

---

## 실행 절차

### 단계 1: 변경사항 파악

아래 명령을 모두 실행하여 전체 변경사항을 수집한다.

```bash
git status
git diff
git diff --cached
```

- `git status`: 전체 파일 상태 확인 (modified, untracked, deleted 등)
- `git diff`: unstaged 변경사항 (아직 stage하지 않은 내용)
- `git diff --cached`: 이미 staged된 변경사항
- `git status`에서 untracked 파일이 있으면 내용을 파악하여 커밋 그룹에 포함한다.


### 단계 2: 의미 단위로 그룹화

변경사항을 **변경의 목적과 의도** 기준으로 그룹화한다.

**그룹화 기준:**
- 파일 경로나 도메인 단위가 아닌, 작업의 "왜"와 "무엇을"을 기준으로 묶는다.
- 하나의 기능을 위해 여러 레이어(Controller, Service, Repository, DTO, 테스트 등)를 함께 변경했다면 하나의 커밋으로 묶는다.
- 성격이 다른 변경사항(예: 기능 추가 + 코드 포매팅)은 별도 커밋으로 분리한다.
- 커밋이 하나로 충분하면 굳이 나누지 않는다.
- 각 커밋 타입의 정확한 의미와 적용 기준은 아래 **커밋 메시지 규칙** 섹션을 참고한다.


### 단계 3: 커밋 타입 결정

아래 **커밋 메시지 규칙** 섹션에 정의된 타입과 각 타입의 의미를 기준으로 결정한다. 명시된 타입 외에는 사용하지 않는다.


### 단계 4: 커밋 계획 제시

사용자에게 아래 형식으로 커밋 계획을 보여주고, **반드시 승인을 받은 후에** 실행한다.

<예시>
```
[커밋 계획]

커밋 1: feat: {기능명} API 추가
  - src/main/java/{패키지}/presentation/{도메인}Controller.java
  - src/main/java/{패키지}/application/{도메인}Service.java
  - src/main/java/{패키지}/infrastructure/{도메인}Repository.java
  - src/main/java/{패키지}/application/dto/{도메인}CreateRequest.java

커밋 2: test: {기능명} 인수 테스트 추가
  - src/test/java/{패키지}/{도메인}AcceptanceTest.java
```

사용자가 계획을 수정하거나 파일 배치를 바꾸고 싶다면 반영 후 다시 제시한다.


### 단계 5: 커밋 실행

사용자 승인 후, 계획한 순서대로 각 그룹을 순서대로 처리한다.

각 그룹마다:
1. 해당 파일들만 명시적으로 stage한다.
2. 커밋 메시지를 작성하여 커밋한다.

```bash
git add <파일1> <파일2> ...
git commit -m "$(cat <<'EOF'
type: 커밋 메시지 내용
EOF
)"
```

**커밋 실패(pre-commit 훅 오류) 시:**
- 훅 오류 메시지를 사용자에게 그대로 보여준다.
- 원인을 파악하여 수정 방법을 제안한다.
- 수정 후 해당 파일을 다시 stage하고 커밋을 재시도한다.
- `--no-verify`로 훅을 건너뛰는 것은 금지한다.

---

## 커밋 메시지 규칙

```
{type}: {short summary}

- {details 1}
- {details 2}
- ...
```

- `type` : 작업 유형 (required)
    - `feat` : 새로운 기능 추가
    - `fix` : 버그 수정
    - `docs` : 문서 관련 (gitignore, README, 각종 템플릿 등)
    - `style` : 스타일 변경 (코드 포매팅, 들여쓰기 추가 등)
    - `refactor` : 코드 리팩토링
    - `test` : 테스트 코드 작성 및 수정
    - `build` : 최초 프로젝트 설정, 빌드 관련 파일 수정(build gradle 의존성 추가, yaml 파일 생성 등)
    - `chore` : 패키지 구조 변경, 파일 삭제, 기타 작업들
- `short summary` : 변경 내용 요약 (required)
    - 현재 시제로 작성
    - 마침표로 끝내지 않음
    - 동사보단 명사 형태로 작성
    - 한글로 작성
    - 어떤 것들을 작업했는지 가독성 있고 명확하게 작성
- `details` : 변경 내용 상세 (optional)
    - 왜 변경했는지 작성
    - 무엇을 변경했는지 작성
    - 어떻게 변경했는지 작성

---

## 제약 사항

- **사용자 승인 없이 커밋 실행 금지**: 계획을 제시하고 반드시 확인을 받은 후 실행한다.
- **`git add -A`, `git add .` 사용 금지**: 반드시 파일 경로를 명시하여 stage한다.
- **전체 테스트 실행 금지**
- **`--no-verify`, `--amend` 사용 금지**
- **커밋 메시지 Co-Authored-By 불필요**: 일반 커밋 메시지만 작성한다.
