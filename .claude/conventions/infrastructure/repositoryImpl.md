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
- 정렬은 도메인 enum(`ProductSortType`)을 `switch`로 매핑한다. 영속 컬럼으로 표현 가능한 정렬은 `Sort.by(...)`를 `PageRequest`에 실어 위임하고(VO 임베디드 컬럼은 `price.value`처럼 필드 경로), 영속 컬럼이 아닌 파생·집계 정렬(좋아요 수 등)은 `Sort`로 표현할 수 없으므로 `ORDER BY`를 박은 전용 `@Query` 메서드로 분기한다.
- 비즈니스 로직을 두지 않는다. 매핑·위임·`get*`의 NOT_FOUND 변환만 담당한다.

### JpaRepository
- `JpaRepository<XxxModel, Long>`을 `extends`하는 인터페이스다.
- 파생 쿼리(Derived Query)는 VO 필드 경로로 작성한다. VO 컴포넌트가 `value`이면 메서드명은 `findByLoginIdValue`, `existsByEmailValue`처럼 필드 경로 전체를 이어 붙인다.
- soft delete 도메인의 조회·존재 검사는 `...AndDeletedAtIsNull` 접미사로 살아있는 행만 거른다(`findByIdAndDeletedAtIsNull`, `findByLoginIdValueAndDeletedAtIsNull`, `existsByNameValueAndDeletedAtIsNull`). 단, 중복 방지처럼 삭제분까지 포함해 검사해야 하는 제약은 접미사를 붙이지 않는다(`existsByLoginIdValue`).
- 페이징 조회는 `Pageable`을 받아 `Page<XxxModel>`을 돌려준다(`findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable)`).
- 도메인 `Repository` 메서드명(`getActiveById(Long)`)과 JpaRepository 메서드명(`findByIdAndDeletedAtIsNull(Long)`)이 다를 때, `RepositoryImpl`이 그 이름 차이를 흡수한다.
- `@Query`는 파생 쿼리로 표현하기 어려운 경우에만 최소한으로 사용한다(조인·집계·생성자 projection 등). 생성자 projection은 `SELECT new <FQCN>(...)`로 도메인 read-model을 직접 생성하고, COUNT처럼 타입이 어긋날 수 있는 집계는 `CAST(... AS integer)`로 대상 타입에 맞춘다.
- **`@Param` 생략**: `@Query`의 `:name`과 메서드 인자 이름이 같으면 `@Param`을 생략한다(`findActiveSummaries(Long brandId, Pageable pageable)`). Spring Boot가 기본 적용하는 `-parameters` 컴파일 옵션으로 인자명이 바인딩되므로, 이름이 다를 때만 `@Param`을 명시한다.

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

// 정렬 — 영속 컬럼은 Sort.by, 집계·파생 정렬은 ORDER BY 전용 @Query로 분기
@Override
public Page<ProductSummary> findActiveSummaries(Long brandId, ProductSortType sort, int page, int size) {
    return switch (sort) {
        case LATEST -> productJpaRepository.findActiveSummaries(brandId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        case PRICE_ASC -> productJpaRepository.findActiveSummaries(brandId, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "price.value")));
        case LIKES_DESC -> productJpaRepository.findActiveSummariesOrderByLikeCount(brandId, PageRequest.of(page, size));  // 좋아요 수 집계는 Sort 불가
    };
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
