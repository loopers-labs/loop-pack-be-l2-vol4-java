# RepositoryImpl / JpaRepository 컨벤션

## 책임
도메인 `Repository` 인터페이스를 구현하는 infrastructure 어댑터. Spring Data JPA(`JpaRepository`)에 위임해 도메인이 JPA에 직접 의존하지 않도록 격리한다. 도메인 port의 인프라 구현체(예: `BcryptPasswordEncrypter`)도 이 레이어에 둔다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/infrastructure/user/UserRepositoryImpl.java`,
`apps/commerce-api/src/main/java/com/loopers/infrastructure/user/UserJpaRepository.java`,
`apps/commerce-api/src/main/java/com/loopers/infrastructure/user/BcryptPasswordEncrypter.java`

## 핵심 규칙

### RepositoryImpl
- `@Component` + `@RequiredArgsConstructor`로 선언한다.
- 도메인 `Repository` 인터페이스를 `implements`한다.
- `XxxJpaRepository`를 유일한 필드로 주입받고, 모든 메서드를 그 파생 쿼리 메서드로 위임한다.
- 비즈니스 로직을 두지 않는다. 매핑·위임만 담당한다.

### JpaRepository
- `JpaRepository<XxxModel, Long>`을 `extends`하는 인터페이스다.
- 파생 쿼리(Derived Query)는 VO 필드 경로로 작성한다. VO 컴포넌트가 `value`이면 메서드명은 `findByLoginIdValue`, `existsByEmailValue`처럼 필드 경로 전체를 이어 붙인다.
- 도메인 `Repository` 메서드명(`findByLoginId(String)`)과 JpaRepository 메서드명(`findByLoginIdValue(String)`)이 다를 때, `RepositoryImpl`이 그 이름 차이를 흡수한다.
- `@Query`는 파생 쿼리로 표현하기 어려운 경우에만 최소한으로 사용한다.

### 도메인 port 구현체
- 도메인이 정의한 port 인터페이스(예: `PasswordEncrypter`)의 구현체는 infrastructure 패키지에 둔다.
- `@Component`로 등록하고 port 인터페이스만 구현한다. 구현 기술(BCrypt 등)에 대한 의존은 이 클래스 안에 가둔다.

## 핵심 발췌
```java
// RepositoryImpl — 도메인 인터페이스 구현, JpaRepository에 위임
@Component
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public UserModel save(UserModel user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<UserModel> findById(Long id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<UserModel> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginIdValue(loginId);  // 이름 차이 흡수
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return userJpaRepository.existsByLoginIdValue(loginId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmailValue(email);
    }
}

// JpaRepository — VO 필드 경로로 파생 쿼리
public interface UserJpaRepository extends JpaRepository<UserModel, Long> {

    Optional<UserModel> findByLoginIdValue(String value);   // loginId.value
    boolean existsByLoginIdValue(String value);
    boolean existsByEmailValue(String value);               // email.value
}

// 도메인 port 구현체
@Component
public class BcryptPasswordEncrypter implements PasswordEncrypter {

    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Override
    public String encrypt(String rawPassword) {
        return bCryptPasswordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encryptedPassword) {
        return bCryptPasswordEncoder.matches(rawPassword, encryptedPassword);
    }
}
```

## do / don't
- ✅ `RepositoryImpl`이 도메인 `Repository` 인터페이스를 구현한다 — 도메인은 JPA를 모른다.
- ✅ JpaRepository 파생 쿼리는 VO 필드 경로(`*Value`)로 작성한다.
- ✅ 도메인 port 구현체는 infrastructure 패키지에 둔다.
- ❌ 도메인 Service가 `JpaRepository`를 직접 주입받지 않는다.
- ❌ `RepositoryImpl`에 비즈니스 로직(검증, 충돌 검사 등)을 두지 않는다.
- ❌ `@Query`를 파생 쿼리로 충분한 곳에 무분별하게 쓰지 않는다.
