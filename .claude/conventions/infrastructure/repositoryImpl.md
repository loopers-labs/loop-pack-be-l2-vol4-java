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
- 존재 보장 조회(`get*`)는 `JpaRepository`가 돌려준 `Optional`을 `orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ...))`로 풀어 "없으면 예외"를 이 레이어에서 책임진다. `find*`는 `Optional`을 그대로 위임한다(find\*/get\* 구분은 `domain/repository.md`).
- 페이징 조회는 도메인 인터페이스의 `int page, int size`를 `PageRequest.of(page, size)`로 변환해 `Pageable` 파생 쿼리에 위임한다.
- 비즈니스 로직을 두지 않는다. 매핑·위임·`get*`의 NOT_FOUND 변환만 담당한다.

### JpaRepository
- `JpaRepository<XxxModel, Long>`을 `extends`하는 인터페이스다.
- 파생 쿼리(Derived Query)는 VO 필드 경로로 작성한다. VO 컴포넌트가 `value`이면 메서드명은 `findByLoginIdValue`, `existsByEmailValue`처럼 필드 경로 전체를 이어 붙인다.
- soft delete 도메인의 조회·존재 검사는 `...AndDeletedAtIsNull` 접미사로 살아있는 행만 거른다(`findByIdAndDeletedAtIsNull`, `findByLoginIdValueAndDeletedAtIsNull`, `existsByNameValueAndDeletedAtIsNull`). 단, 중복 방지처럼 삭제분까지 포함해 검사해야 하는 제약은 접미사를 붙이지 않는다(`existsByLoginIdValue`).
- 페이징 조회는 `Pageable`을 받아 `Page<XxxModel>`을 돌려준다(`findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable)`).
- 도메인 `Repository` 메서드명(`getActiveById(Long)`)과 JpaRepository 메서드명(`findByIdAndDeletedAtIsNull(Long)`)이 다를 때, `RepositoryImpl`이 그 이름 차이를 흡수한다.
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
    public UserModel getActiveById(Long id) {                    // get* — 없으면 NOT_FOUND
        return userJpaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원이 존재하지 않습니다."));
    }

    @Override
    public Optional<UserModel> findActiveByLoginId(String loginId) {   // find* — Optional 그대로
        return userJpaRepository.findByLoginIdValueAndDeletedAtIsNull(loginId);  // 이름 차이 흡수
    }

    @Override
    public boolean existsByLoginId(String loginId) {             // 중복 제약 — 삭제분 포함
        return userJpaRepository.existsByLoginIdValue(loginId);
    }
}

// JpaRepository — VO 필드 경로 + soft delete 필터(...AndDeletedAtIsNull)
public interface UserJpaRepository extends JpaRepository<UserModel, Long> {

    Optional<UserModel> findByIdAndDeletedAtIsNull(Long id);
    Optional<UserModel> findByLoginIdValueAndDeletedAtIsNull(String value);  // loginId.value
    boolean existsByLoginIdValue(String value);                              // 삭제분 포함
}

// 페이징 — RepositoryImpl이 page/size를 PageRequest로 변환, JpaRepository는 Pageable 수신
@Override
public Page<BrandModel> findActiveByPage(int page, int size) {
    return brandJpaRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(PageRequest.of(page, size));
}

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {
    Page<BrandModel> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);
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
- ✅ `get*`의 NOT_FOUND 변환(`orElseThrow`)은 `RepositoryImpl`이 책임진다 — `find*`는 `Optional` 그대로 위임.
- ✅ soft delete 도메인의 조회는 `...AndDeletedAtIsNull`로 살아있는 행만 거른다(중복 제약은 삭제분 포함).
- ✅ 도메인 port 구현체는 infrastructure 패키지에 둔다.
- ❌ 도메인 Service가 `JpaRepository`를 직접 주입받지 않는다.
- ❌ `RepositoryImpl`에 비즈니스 로직(검증, 충돌 검사 등)을 두지 않는다.
- ❌ `@Query`를 파생 쿼리로 충분한 곳에 무분별하게 쓰지 않는다.
