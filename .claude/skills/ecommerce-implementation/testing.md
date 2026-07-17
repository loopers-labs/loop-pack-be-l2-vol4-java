# 테스트 지침

테스트 도구, Testcontainers, fixture 재사용, 실행 환경 기본 규칙은 `.claude/rules/code-conventions.md` 와 루트 `AGENTS.md` 를 따른다.

## 이커머스 우선 테스트 대상

- 회원가입 중복 검증
- 비밀번호 변경 정책
- 상품 등록 시 브랜드 존재 검증
- 상품 수정 시 브랜드 변경 불가
- 브랜드 삭제 시 상품 논리 삭제 처리
- 좋아요 중복 등록/취소 정책
- 주문 상품 스냅샷 저장
- 주문 시 재고 부족 실패
- 동시 주문에서 초과 판매 방지
- 결제 성공/실패 상태 전이
- 관리자 API 식별 정보 요구

## 실행 명령

```bash
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:compileJava
./gradlew build
```
