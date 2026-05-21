# Claude Code 가이드

이 문서는 `loopers-java-spring-template` 리포지토리에서 Claude Code(및 호환 AI 에이전트)가 작업할 때 참고하는 진입 파일입니다.

- 공통 에이전트 작업 규칙(언어·코드 컨벤션·빌드·테스트·Git 정책 등)은 [`AGENTS.md`](AGENTS.md) 에 정의되어 있습니다.
- 정적인 레퍼런스(기술 스택 표·모듈 트리·외부 문서 링크)는 [`.claude/rules/`](.claude/rules) 하위 파일로 분리되어 있습니다.

Claude Code 는 아래 import 를 통해 위 문서들을 함께 컨텍스트에 로드합니다.

@AGENTS.md
@.claude/rules/tech-stack.md
@.claude/rules/module-structure.md
@.claude/rules/references.md

## Claude Code 전용 지침

(현재는 추가 지침 없음 — Claude 한정으로 따로 적용할 규칙이 생기면 이 섹션에 추가합니다.)
