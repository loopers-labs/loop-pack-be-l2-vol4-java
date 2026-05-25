# 테스트 컨벤션 (공통)

## 책임
레이어 무관하게 모든 테스트 파일에 적용되는 단언 스타일·`@DisplayName` 규칙·에러 단언 범위·픽스처 패턴을 정의한다. 레이어별 세부 규칙은 각 레이어 `test.md`를 참조.

## 정식 참조
`apps/commerce-api/src/test/java/com/loopers/domain/user/LoginIdTest.java`
`apps/commerce-api/src/test/java/com/loopers/infrastructure/user/UserRepositoryIntegrationTest.java`
`apps/commerce-api/src/test/java/com/loopers/interfaces/api/UserV1ApiE2ETest.java`

## 핵심 규칙

### 3A 구조 & Mockito 스타일
- 모든 테스트는 레이어·종류와 무관하게 `// arrange` / `// act` / `// assert` 주석으로 세 단계를 구분한다. 이는 테스트 한정 규칙으로, 운영 코드의 장식 주석 금지(`code-style.md`)와 별개다.
- Mockito는 BDDMockito로 통일한다: 스텁은 `given(...).willReturn(...)`/`willThrow(...)`, 검증은 `then(...).should(...)`. 새 테스트에서 `when(...).thenReturn(...)`·`verify(...)`를 쓰지 않는다.

### 단언 스타일 — JUnit5 + AssertJ
- 단언은 AssertJ `assertThat(...)`을 기본으로 쓴다.
- 예외 단언은 `assertThatThrownBy(() -> ...).isInstanceOf(...).extracting("errorType").isEqualTo(...)` 체인.
- 다중 단언은 `assertAll(() -> ..., () -> ...)` 또는 AssertJ 체이닝(`assertThat(x).hasSize(60).startsWith("$2")`)으로 묶어 첫 실패에서 멈추지 않게 한다.
- `assertThrows`는 기본으로 사용하지 않는다.

### @DisplayName
- 도메인 의미 한국어 평서문으로 작성한다.
- 금지: 프레임워크·JPA 메서드명 노출(`existsByLoginId를 호출할 때`), 구체 Java 예외 클래스명 노출(`CoreException이 발생한다`, `DataIntegrityViolationException`).
- 허용: 도메인 계약인 `ErrorType` 값(`BAD_REQUEST`, `CONFLICT`, `NOT_FOUND`)은 결과 표현에 노출할 수 있다 — `extracting("errorType")` 단언과 일치하기 때문.
- 올바른 예: `"길이가 4자 미만이면 BAD_REQUEST 예외가 발생한다."`, `"이미 사용 중인 loginId로 재가입을 시도하면, 409 Conflict로 거절된다."`.
- 연관된 시나리오는 `@Nested` 클래스로 그룹화하고(`@Nested class SignUp`), 바깥 클래스와 각 케이스 모두에 `@DisplayName`을 단다. 반복되는 경계·누락 케이스는 `@ParameterizedTest`(`@ValueSource`/`@MethodSource`)로 데이터화한다.

### 에러 단언 범위
- **단위 테스트** (VO/Model/Service): `errorType`까지. `customMessage` 텍스트는 단언하지 않는다 — 문구 변경에 깨지는 취약한 테스트가 된다.
- **E2E 테스트**: 컨트랙트까지(`statusCode` + `meta.result`). `meta.message` 텍스트는 단언하지 않는다 — 단, 보안 목적의 부재 단언(`meta.message().doesNotContain(rawPassword)`)만 예외로 허용한다.

### 픽스처 헬퍼
- 픽스처 헬퍼는 "build + save"를 한 단위로 묶어 저장된 엔티티를 반환한다. 두 줄 반복(`build` + `save`)을 한 호출에 흡수해 테스트 본문이 도메인 시나리오만 드러내게 한다.
- **save 대상은 레이어에 따라 다르다**: 통합 테스트는 주입된 도메인 `*Repository`(구현체)를 통해 저장하고, E2E 테스트는 `*JpaRepository.save`를 직접 호출한다(다른 API를 거치지 않음).
- 예: `createUser(loginId, email)` → 저장된 `UserModel` 반환.

### E2E — TestRestTemplate 패턴
- 요청 본문: 프로덕션 DTO record. `Map<String, Object>` 사용 금지 — 컴파일 타임 안전·리팩터 안전.
- 응답 본문: 키 집합이 contract이면 `Map<String, Object>` + `containsOnlyKeys(...)`. 필드 값만 검증하면 typed record.
- 호출: `testRestTemplate.exchange(URL, METHOD, HttpEntity, ParameterizedTypeReference<ApiResponse<...>>)`. `postForEntity`는 제네릭 보존 불가로 사용하지 않는다.
- Content-Type: `jsonRequest(body)` 같은 헬퍼로 명시적으로 부착한다.
- fixture는 `*JpaRepository.save` 직접 사용. 다른 API를 통해 fixture를 생성하면 간접 의존성·오버헤드·데이터 제어 문제가 생긴다.
- DB 갱신 검증은 `findById(savedEntity.getId())`로 해당 엔티티만 조회한다. `findAll().get(0)` 회피.
- 헤더 값은 ASCII 한정. JDK HTTP 클라이언트가 non-ASCII 헤더를 `IllegalArgumentException`으로 거부한다.

## 핵심 발췌
```java
// 단위 테스트 — 예외 단언 (3A 주석)
@DisplayName("길이가 4자 미만이면 BAD_REQUEST 예외가 발생한다.")
@ParameterizedTest
@ValueSource(strings = {"a", "abc"})
void throwsBadRequest_whenValueIsShorterThanMinLength(String value) {
    // arrange, act & assert
    assertThatThrownBy(() -> LoginId.from(value))
        .isInstanceOf(CoreException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.BAD_REQUEST);
}

// 통합 테스트 — 픽스처 헬퍼 + 다중 단언
private UserModel createUser(String rawLoginId, String rawEmail) {
    UserModel newUser = UserModel.builder()
        .rawLoginId(rawLoginId)
        ...
        .build();
    return userRepository.save(newUser);
}

assertAll(
    () -> assertThat(userRepository.existsByLoginId("kyleKim")).isTrue(),
    () -> assertThat(userRepository.existsByLoginId("unknown99")).isFalse()
);

// E2E — exchange + ParameterizedTypeReference, 요청은 typed record
ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
    ENDPOINT_SIGN_UP,
    HttpMethod.POST,
    jsonRequest(new UserV1Dto.SignUpRequest("kylekim", "Kyle!2030", "김카일", birthDate, "kyle@example.com")),
    new ParameterizedTypeReference<>() {}
);
assertAll(
    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
    () -> assertThat(response.getBody().data()).containsOnlyKeys("userId", "loginId")
);
```

## do / don't
- ✅ 모든 테스트에 `// arrange`/`// act`/`// assert` 3A 주석을 단다.
- ✅ Mockito는 BDDMockito(`given().will*`, `then().should()`)로 쓴다.
- ❌ `when().thenReturn()`·`verify()`를 새로 쓰지 않는다.
- ✅ 단언은 `assertThat`, 예외는 `assertThatThrownBy` + `extracting("errorType")`.
- ✅ 다중 단언은 `assertAll` 또는 AssertJ 체이닝.
- ✅ `@DisplayName`은 도메인 의미 한국어 평서문. `ErrorType` 값 노출 허용.
- ✅ 단위 에러 단언은 `errorType`까지, E2E는 `statusCode + meta.result`까지.
- ✅ E2E 요청 본문은 typed record.
- ✅ fixture는 `*JpaRepository.save` 직접.
- ❌ `assertThrows`를 기본으로 사용하지 않는다.
- ❌ `customMessage` 텍스트를 단언하지 않는다.
- ❌ `@DisplayName`에 구체 Java 예외 클래스명(`DataIntegrityViolationException`)을 노출하지 않는다.
- ❌ E2E 요청에 `Map<String, Object>`를 사용하지 않는다.
- ❌ `postForEntity`를 사용하지 않는다 (제네릭 보존 불가).
- ❌ fixture를 다른 API 호출로 생성하지 않는다.
