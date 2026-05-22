# 주의사항

## 1. Never Do

- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것
- 커밋 메시지에 `Co-Authored-By: Claude` 등 AI 서명을 포함하지 말 것

---

## 2. Recommendation

- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성

---

## 3. Priority

1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지
