# Ecommerce Requirement Modeling Skill

## Purpose

이 Skill은 이커머스 서비스의 요구사항을 분석하고, 도메인 모델링과 API 설계를 위한 기초 산출물을 작성하는 데 사용한다.
공통 규칙은 루트 `AGENTS.md`, `CLAUDE.md` 와 `.claude/rules/*` 를 우선 따르고, 이 문서는 요구사항 모델링 작업 방식과 세부 참조 지침을 보완한다.

## 사용 조건

- 이커머스 요구사항 원문을 분석해 설계 산출물로 정리할 때
- 유저 시나리오, 기능 목록, 유스케이스 흐름을 작성하거나 검토할 때
- DDD 기반 유비쿼터스 언어와 핵심 도메인 후보를 도출할 때
- user/admin API 를 분리하고 API 요구사항을 작성할 때
- 주문, 재고, 결제 정합성 규칙을 점검할 때
- 요구사항 정리 이후 도메인/데이터 모델링, ERD 설계가 필요할 때

## Workflow

1. 요구사항 원문을 읽고 서비스 흐름을 요약한다.
2. 사용자 시나리오를 기준으로 기능을 분류한다.
3. 유비쿼터스 언어를 도출한다.
4. 핵심 도메인 후보를 식별한다.
5. user/admin API 를 분리한다.
6. 주문, 재고, 결제 정합성 규칙을 점검한다.

## Output Rules

- 실제 설계 산출물은 `.docs/design/` 하위에 작성한다.
- `.claude/skills/` 에는 작업 방식과 참조 규칙만 작성한다.
- 설계 산출물 작성 위치, 표준 파일 구조, PlantUML 관리 규칙은 `.claude/rules/design-docs.md` 를 따른다.
- 구현 단계로 넘기기 전에 설계 산출물의 누락/불일치 항목을 확인 필요 항목으로 분리한다.

## 작업 전 확인

1. 변경 대상이 설계 문서이면 `.docs/design/` 하위 파일을 수정한다.
2. 변경 대상이 Skill 지침이면 `.claude/skills/ecommerce-requirement-modeling/` 하위 파일을 수정한다.
3. 구현 변경이 필요한 요청이면 `../ecommerce-implementation/SKILL.md` 를 따른다.

## 세부 지침

- [도메인 체크리스트](domain-checklist.md)
- [API 분류 가이드](api-classification-guide.md)
- [주문/재고/결제 정합성 가이드](order-inventory-payment-guide.md)
- [다이어그램 작성 패턴](diagram-patterns.md)
- [확인 질문 은행](clarification-question-bank.md)

## 구현 Skill 연결

- 요구사항 산출물 확정 후 구현 단계로 넘어가면 `../ecommerce-implementation/SKILL.md` 를 따른다.
