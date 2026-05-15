대상 클래스/메서드에 대해 단위 테스트, 통합 테스트, E2E 테스트를 작성합니다.

## 환경
- Java 21 (corretto-21), Spring Boot 3.4.4
- 테스트 프레임워크: JUnit 5, Mockito, Spring Boot Test
- 통합 테스트: Testcontainers (MySQL 8.0, Redis)
- E2E: MockMvc 또는 실제 HTTP (`http/*.http`)

## 입력
`$ARGUMENTS` — 테스트 대상 파일 경로 또는 클래스명. 없으면 현재 컨텍스트(열려 있는 파일, 최근 변경)에서 추론.

## 절차

1. **대상 파악**: `$ARGUMENTS`가 있으면 해당 클래스를 읽고, 없으면 최근 수정 파일을 확인.
2. **테스트 종류 결정**:
   - 순수 도메인 로직 → 단위 테스트 (Mockito, `@ExtendWith(MockitoExtension.class)`)
   - Repository / JPA 쿼리 → `@DataJpaTest` + Testcontainers MySQL
   - Service 계층 → `@SpringBootTest` + Testcontainers (DB, Redis)
   - Controller → `@WebMvcTest` + MockMvc
   - 전체 흐름 → E2E: `@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers + `http/*.http` 파일
3. **테스트 작성** (3A 원칙 필수):
   - `// Arrange` / `// Act` / `// Assert` 주석으로 구획
   - `@DisplayName`은 행동 중심으로 간결하게 기술 (e.g. `"null loginId로 생성 시 BAD_REQUEST 예외가 발생한다."`)
   - Best case + Edge case (경계값, null 입력, 예외) 모두 커버
4. **E2E 작성 시**: `http/` 디렉터리에 `.http` 파일도 함께 생성

## 제약
- 불필요한 Mock 데이터 기반 구현 금지 — 실제 동작 가능한 테스트만 작성
- `System.out.println` 잔존 금지
- `Optional` 사용으로 null-unsafe 코드 방지
- 요청하지 않은 프로덕션 코드 수정 금지
