# Domain 테스트 컨벤션

## 책임
VO·Model·Service의 도메인 단위 테스트 작성 규칙. 외부 의존 없이 도메인 규칙 자체를 검증한다.

## 정식 참조
`apps/commerce-api/src/test/java/com/loopers/domain/user/LoginIdTest.java`
`apps/commerce-api/src/test/java/com/loopers/domain/user/UserModelTest.java`
`apps/commerce-api/src/test/java/com/loopers/domain/user/UserServiceTest.java`

## 핵심 규칙
- 클래스명 컨벤션: VO는 `*Test`(예: `LoginIdTest`), Model은 `*ModelTest`, Service는 `*ServiceTest`.
- 도메인 서비스 테스트(`*ServiceTest`)는 순수 단위다 — 실제 도메인 객체를 만들어 인자로 전달하고 협력 결과를 단언한다. Repository·트랜잭션이 없으므로 영속성 모킹이 필요 없다.
- port와 협력하는 Model 테스트는 클래스에 `@ExtendWith(MockitoExtension.class)`를 붙이고 `@Mock`으로 port(예: `PasswordEncrypter`)를 스텁한다. VO·도메인 서비스 테스트는 순수 단위(Mockito 불필요).
- 협력 객체에 대한 위임만 검증할 때는 `mock(...)` + `then(mock).should().메서드(...)`(BDDMockito)를 쓴다.
- 시나리오는 `@Nested` 클래스로 그룹화하고(`@Nested class SignUp`), 바깥 클래스와 각 케이스에 `@DisplayName`을 단다.
- 단언 스타일·3A 주석·BDDMockito·`@DisplayName` 작문·에러 단언 범위(`errorType`까지, `customMessage` 미단언)는 `common/testing.md`를 따른다.

## 핵심 발췌
```java
// VO 단위 테스트 — 3A
@DisplayName("길이가 4자 미만이면 BAD_REQUEST 예외가 발생한다.")
@ParameterizedTest
@ValueSource(strings = {"a", "abc"})
void throwsBadRequest_whenValueIsShorterThanMinLength(String value) {
    // arrange & act & assert
    assertThatThrownBy(() -> LoginId.from(value))
        .isInstanceOf(CoreException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.BAD_REQUEST);
}

// 도메인 서비스 단위 테스트 — 순수 (Mock 불필요)
@DisplayName("주문 라인들의 소계를 합산해 총액을 계산한다.")
@Test
void sumsLineSubtotals() {
    // arrange
    OrderPricingService orderPricingService = new OrderPricingService();
    List<OrderLine> orderLines = List.of(OrderLine.of(productId, 2), OrderLine.of(productId, 1));

    // act
    Money total = orderPricingService.calculateTotal(orderLines);

    // assert
    assertThat(total).isEqualTo(Money.of(30_000));
}
```

## do / don't
- ✅ `@DisplayName`은 도메인 의미 평서문으로 작성한다.
- ✅ 예외 단언은 `assertThatThrownBy` + `extracting("errorType")` 체인을 쓴다.
- ❌ `customMessage` 텍스트를 단언하지 않는다 (문구 변경에 취약).
- ❌ `assertThrows`를 기본으로 사용하지 않는다.
