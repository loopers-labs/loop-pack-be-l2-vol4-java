# 표현 계층 E2E 테스트 컨벤션

## 책임
실제 서버를 띄워 HTTP 요청·응답 전체 흐름을 검증하는 E2E 테스트. 컨트랙트(응답 구조·상태 코드·에러 코드)와 DB 상태 변화를 함께 확인한다.

## 정식 참조
`apps/commerce-api/src/test/java/com/loopers/interfaces/api/UserV1ApiE2ETest.java`,
`apps/commerce-api/src/test/java/com/loopers/interfaces/api/BrandAdminV1ApiE2ETest.java`(관리자 인증·페이징)

## 핵심 규칙

### 클래스 설정
- 클래스명은 `*V1ApiE2ETest`. `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`를 붙인다.
- `TestRestTemplate`을 `@Autowired`로 주입한다.
- 매 `@AfterEach`에서 `databaseCleanUp.truncateAllTables()`를 호출해 테스트 격리를 보장한다.

### 공통 규칙 (→ `common/testing.md`)
호출 방식(`exchange` + `ParameterizedTypeReference`, `postForEntity` 금지), 요청 본문 typed record(`Map` 금지), 응답 본문(키 집합은 `Map` + `containsOnlyKeys`, 값 검증은 typed record), 픽스처(build+save를 묶어 `*JpaRepository.save` 직접 호출, `findById(saved.getId())`로 검증), ASCII 헤더, 단언 스타일·`@DisplayName`은 `common/testing.md`를 따른다. 아래는 E2E 레이어 고유 규칙만 정리한다.

### 인증 헤더
- 사용자 인증이 필요한 호출은 `X-Loopers-LoginId`·`X-Loopers-LoginPw` 헤더를 헬퍼(예: `authJsonRequest(loginId, password, body)`)로 붙인다. 본문이 없는 GET은 헤더만 붙이는 변형(`authHeaders(loginId, password)`)을 쓴다. 값은 ASCII 한정.
- 관리자 API(`/api-admin/**`) 호출은 `X-Loopers-Ldap: loopers.admin` 헤더를 헬퍼로 붙인다(`LDAP_HEADER`/`ADMIN_LDAP` 상수). 헤더 누락·불일치로 403(`FORBIDDEN`)을 단언하는 케이스(`returnsForbidden_whenAdminHeaderIsMissing`)를 엔드포인트마다 함께 둔다.

### 테스트 구조
- API 엔드포인트별로 `@Nested` 클래스로 그룹화한다.
- 누락 필드·유효하지 않은 값 등 반복 케이스는 `@ParameterizedTest` + `@MethodSource`로 데이터화한다.
- 엔드포인트 URL은 `private static final String ENDPOINT_*` 상수로 둔다.

### 에러 단언
- 에러 응답 단언은 `statusCode` + `meta.result`(=`FAIL`)까지 검증한다(컨트랙트). `meta.message` 문구는 단언하지 않는다 — 문구 변경에 깨지는 빡빡한 테스트가 된다.
- 단, 보안 검증 목적의 **부재(absence) 단언**(`meta.message().doesNotContain(rawPassword)` — 응답에 민감 정보가 새지 않음 확인)만 예외로 허용한다. 메시지의 존재·내용을 단언하는 것(`contains`/`isEqualTo`)은 보안 목적이라도 금지.
- 실패 케이스는 DB 부작용이 없었음을 함께 단언한다(`userJpaRepository.findAll()`이 비어 있음, 또는 변경 전 값 유지).

## 핵심 발췌
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    @Autowired TestRestTemplate testRestTemplate;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Object> jsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void returnsCreated_whenSignUpRequestIsValid() {
        UserV1Dto.SignUpRequest requestBody = new UserV1Dto.SignUpRequest(
            "kylekim", "Kyle!2030", "김카일", LocalDate.of(1995, 3, 21), "kyle@example.com"
        );

        ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
            "/api/v1/users", HttpMethod.POST, jsonRequest(requestBody), responseType
        );

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
            () -> assertThat(response.getBody().data()).containsOnlyKeys("userId", "loginId")
        );
    }
}
```

## do / don't
- ✅ `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`, `@AfterEach`에서 `truncateAllTables()`로 격리.
- ✅ 사용자 인증 호출은 `X-Loopers-LoginId`·`X-Loopers-LoginPw`, 관리자 호출은 `X-Loopers-Ldap` 헤더 헬퍼로(값 ASCII). 관리자 헤더 누락 시 403 단언을 둔다.
- ✅ 엔드포인트별 `@Nested` 그룹화, 반복 케이스는 `@ParameterizedTest` + `@MethodSource`.
- ✅ 에러 단언은 statusCode + meta.result까지. 메시지 문구는 단언하지 않는다(보안 부재 단언 `doesNotContain`만 예외).
- ✅ 실패 케이스는 DB 부작용 부재도 함께 단언한다.
- ❌ 공통 규칙(`exchange`·typed record·`Map` 금지·픽스처·ASCII·`postForEntity` 금지)을 여기서 중복 정의하지 않는다 — `common/testing.md` 참조.
- ❌ `@Transactional` 롤백으로 격리하려 하지 않는다 — E2E는 RANDOM_PORT 별도 스레드라 롤백이 동작하지 않는다(통합 테스트 세부는 `infrastructure/test.md`).
