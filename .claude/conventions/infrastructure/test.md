# Infrastructure 레이어 테스트 컨벤션

## 책임
`RepositoryImpl`이 도메인 Repository 계약을 올바르게 이행하는지, DB 제약(UNIQUE 등)이 기대대로 동작하는지 실제 MySQL 컨테이너로 검증한다.

## 정식 참조
`apps/commerce-api/src/test/java/com/loopers/infrastructure/user/UserRepositoryIntegrationTest.java`

## 핵심 규칙
- 클래스명은 `*RepositoryIntegrationTest`로 한다.
- `@SpringBootTest`만 붙인다. `MySqlTestContainersConfig`가 MySQL 8.0 Testcontainers를 `static` 블록에서 시작하고 system property로 jdbc-url을 주입하므로 별도 설정 없이 컨테이너에 연결된다.
- `@Transactional`을 기본으로 붙이지 않는다. 없으면 각 `save()`가 즉시 commit·flush되어 UNIQUE 제약 위반이 `save()` 호출 지점에서 터지고 `assertThatThrownBy(() -> ...save(...))`가 그 자리에서 잡는다. `@Transactional`을 붙이면 테스트 전체가 롤백되는 단일 트랜잭션으로 묶여 flush가 DB까지 도달하지 않아, 제약 위반을 제자리에서 잡지 못한다.
- 매 `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()`를 호출해 테스트 간 격리한다.
- 검증 대상 계약은 도메인 `Repository` 인터페이스(`UserRepository`)다 — 그 메서드를 호출해 계약(인터페이스) 레벨에서 검증한다. 저장 결과를 DB에서 직접 되읽어 확인하거나(`findById`로 영속 상태 재확인) 픽스처를 구성할 때는 `XxxJpaRepository`를 함께 주입해도 된다. 단, 검증의 주체는 도메인 Repository이고 JpaRepository는 보조 수단이다.
- 픽스처 헬퍼(`createUser` 등)는 `UserModel` 생성 + `repository.save(...)` 를 한 단위로 묶어 저장된 엔티티를 반환한다. 테스트 본문에서 build·save 두 줄을 반복하지 않는다.
- 시나리오는 `@Nested` 클래스로 그룹화한다(`Save`, `ExistsByLoginId` 등). 단언 스타일·`@DisplayName` 작문은 `common/testing.md`를 따른다.
- 포트 어댑터처럼 DB가 필요 없는 인프라 구성요소는 컨테이너 없이 직접 `new`로 생성해 순수 단위로 테스트한다(클래스명 `*Test`, `@SpringBootTest` 미사용).

## 핵심 발췌
```java
@SpringBootTest
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;   // 보조 — 영속 상태 재확인·픽스처용

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel createUser(String rawLoginId, String rawEmail) {
        UserModel newUser = UserModel.builder()
            .rawLoginId(rawLoginId)
            .rawPassword("Kyle!2030")
            ...
            .passwordEncrypter(passwordEncrypter)
            .build();
        return userRepository.save(newUser);
    }

    @DisplayName("이미 사용 중인 로그인 ID로 가입을 시도하면 예외가 발생한다.")
    @Test
    void throwsException_whenLoginIdAlreadyExists() {
        createUser("kyleKim", "kyle@example.com");

        assertThatThrownBy(() -> createUser("kyleKim", "other@example.com"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

## do / don't
- ✅ 도메인 `Repository` 인터페이스를 주입받아 계약 레벨에서 검증한다. 영속 상태 재확인·픽스처 구성용으로 `JpaRepository`를 보조 주입하는 것은 허용한다.
- ✅ 픽스처 헬퍼는 build+save를 묶어 저장 엔티티를 반환한다.
- ✅ `@AfterEach`에서 `truncateAllTables()`로 격리한다.
- ❌ `@Transactional`을 통합 테스트 클래스에 기본으로 붙이지 않는다 — UNIQUE 제약 위반을 제자리에서 잡기 위해.
- ❌ 검증의 주체를 `JpaRepository`로 삼지 않는다 — 계약은 도메인 Repository로 검증하고, JpaRepository는 영속 확인·픽스처의 보조로만 쓴다.
