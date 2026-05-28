# 설계 문서 관리 규칙

## 작성 위치

- 기능 설계 문서, API 설계 문서, 도메인 설계 문서 등 실제 설계 산출물은 `.docs/design/` 하위에 작성한다.
- `.claude/skills/` 에는 설계 산출물을 직접 작성하지 않고, 설계 작성 방식과 참조 규칙만 둔다.

## 제출 파일 구조

- 요구사항 명세는 `.docs/design/01-requirements.md` 에 작성한다.
- 시퀀스 다이어그램 문서는 `.docs/design/02-sequence-diagrams.md` 에 작성한다.
- 클래스 다이어그램 문서는 `.docs/design/03-class-diagram.md` 에 작성한다.
- ERD 문서는 `.docs/design/04-erd.md` 에 작성한다.

## UML 작성 도구

- 시퀀스 다이어그램은 Mermaid `sequenceDiagram` 으로 작성한다.
- 클래스 다이어그램은 Mermaid `classDiagram` 으로 작성한다.
- ERD 는 Mermaid `erDiagram` 으로 작성한다.
- 렌더링 이미지가 필요한 경우 `.docs/design/assets/` 하위에 `svg` 또는 `png` 로 저장하고 Markdown 에서 참조한다.

## 파일명 규칙

- Markdown 설계 문서는 정렬 순서를 위해 `01-`, `02-`, `03-`, `04-` 접두어를 사용한다.

## Markdown 내 다이어그램 참조

- Mermaid 다이어그램은 각 설계 문서에 fenced code block 으로 직접 삽입한다.
- 렌더링 이미지가 있으면 다이어그램 섹션에 이미지를 함께 삽입한다.

예:

````markdown
## 주문 생성 시퀀스

```mermaid
sequenceDiagram
    actor Member as 회원
    participant API as Order API
    Member->>API: 주문 생성 요청
```
````

````markdown
## ERD

```mermaid
erDiagram
    MEMBER ||--o{ ORDER : places
```
````
