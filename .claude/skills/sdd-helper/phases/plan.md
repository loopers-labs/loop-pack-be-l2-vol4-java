# plan 단계 — 기술 설계 결정 (implement 컨텍스트)

spec.md를 읽고 이 시나리오를 어떻게 구현할지 기술적으로 결정한다. 이 문서는 implement 단계에서 따르는 컨텍스트가 된다 — 파일 맵과 설계 결정이 명확할수록 구현이 정확해진다.

## 절차

1. 시나리오 폴더의 `spec.md`를 읽는다.
2. `templates/plan-template.md`를 같은 폴더에 `plan.md`로 복사한다.
3. 아래를 채운다.
4. 다 채우면 사용자에게 설계 결정을 요약 보고하고 gate.

## 채울 내용

- **요약**: 시나리오 + 기술 접근 한두 문장.
- **기술 컨텍스트**: 대부분 고정(Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api). 시나리오별 추가 의존성만 적는다.
- **컨벤션·결정 점검**: 우리의 헌법 = CLAUDE.md + 소스 문서의 핵심 결정(그리고 `.claude/conventions/`가 생성되어 있으면 그 문서들). 이 시나리오에 걸리는 항목을 점검 체크리스트로 적는다:
  - 호출 방향 interfaces → application → domain → infrastructure 준수.
  - 검증은 VO `from()`에 단일화(DTO Bean Validation 금지).
  - soft/hard delete 정책, 동시성(원자적 갱신 등), 스냅샷, 멱등 처리 등 해당되는 결정.
  - 인증이 필요하면 `@LoginUser AuthenticatedUser` 시그니처.
- **레이어별 설계 결정 & 파일 맵**: `plan-template.md`의 레이어별 파일 맵(interfaces/application/domain/infrastructure)을 채운다 — 만들거나 손댈 클래스를 구체 경로와 함께 나열한다. 각 클래스에 엔드포인트 시그니처(HTTP 메서드·경로·요청/응답), 매핑할 `ErrorType`, 영속화·조회 방식(페이지네이션·정렬·집계)을 적는다.
- **복잡도 트래킹**: 컨벤션·기본 패턴을 벗어나는 결정이 있으면 이유와 함께 적는다. 없으면 비운다.

## 주의

- 기존 User 도메인이 Example 패턴의 참조 구현이다 — 새 도메인은 그 레이어·네이밍을 그대로 본뜬다.
- 과설계 금지: 시나리오가 요구하지 않는 유연성·추상화를 넣지 않는다.
