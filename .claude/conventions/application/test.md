# Application(Facade) 테스트 컨벤션

## 책임
Facade가 Repository·도메인 서비스·도메인 객체를 조합하는 유스케이스 흐름을 단위 테스트로 검증한다. 트랜잭션·영속성 접근이 Facade에 있으므로 Facade는 단위 테스트 대상이다.

## 정식 참조
`apps/commerce-api/src/test/java/com/loopers/application/user/UserFacadeTest.java`,
`apps/commerce-api/src/test/java/com/loopers/application/brand/BrandFacadeTest.java`

## 핵심 규칙
- 클래스명은 `*FacadeTest`. `@ExtendWith(MockitoExtension.class)` + `@InjectMocks`로 Facade를 구성한다.
- Repository는 `@Mock`(BDDMockito)으로 스텁한다. 도메인 서비스는 무상태 POJO이므로 모킹하지 않고 실제 객체를 주입한다.
- 검증 대상: 유스케이스 흐름(조회 → 위임 → 저장 순서·조건), `Info` 변환 결과, 예외 흐름(NOT_FOUND/CONFLICT 등 `errorType`).
- `@SpringBootTest`로 전체 컨텍스트를 띄우지 않는다 — 순수 단위 테스트로 충분하다.
- 단언 스타일·3A 주석·BDDMockito·`@DisplayName`·에러 단언 범위·`@Nested`는 `common/testing.md`를 따른다.

## do / don't
- ✅ Repository는 `@Mock`, 도메인 서비스는 실제 객체.
- ✅ 흐름·`Info` 변환·예외(`errorType`)를 검증한다.
- ❌ 무상태 도메인 서비스를 모킹하지 않는다.
- ❌ Facade 테스트에서 도메인 규칙(불변식·계산)을 재검증하지 않는다 — 도메인 단위 테스트 책임.
