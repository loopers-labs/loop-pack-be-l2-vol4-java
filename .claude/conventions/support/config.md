# Support Config 컨벤션

## 책임
도메인과 무관한 횡단 MVC 설정을 모은 패키지. `ArgumentResolver`, `Interceptor`, CORS 등 Spring MVC 인프라 설정이 여기에 모인다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/support/config/WebMvcConfig.java`

## 핵심 규칙
- `support.config` 패키지는 도메인 무관 횡단 설정 전용이다. 특정 도메인 로직을 여기에 섞지 않는다.
- `WebMvcConfig`는 `WebMvcConfigurer`를 구현하고 `@Configuration` + `@RequiredArgsConstructor`로 선언한다.
- `ArgumentResolver`를 추가할 때는 `WebMvcConfig.addArgumentResolvers()`에 등록한다. 현재 `AuthenticatedUserArgumentResolver`가 등록돼 있다.
- `Interceptor`를 추가할 때도 `WebMvcConfig.addInterceptors()`에 등록한다.
- `ArgumentResolver`·`Interceptor` 구현체 자체는 `interfaces.api.auth` 또는 관련 패키지에 두고, `WebMvcConfig`는 등록만 담당한다.
- 등록된 `AuthenticatedUserArgumentResolver`는 컨트롤러의 `@LoginUser AuthenticatedUser` 파라미터를 해석해 인증 사용자(`userId`)를 주입한다 (인증 흐름 상세는 `common/architecture.md`).

## 핵심 발췌
```java
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthenticatedUserArgumentResolver authenticatedUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authenticatedUserArgumentResolver);
    }
}
```

## do / don't
- ✅ 횡단 MVC 설정은 모두 `support.config`에 모은다.
- ✅ 새 `ArgumentResolver`·`Interceptor`는 `WebMvcConfig`에 등록한다.
- ❌ 도메인별 비즈니스 로직이나 도메인 특화 설정을 `support.config`에 두지 않는다.
- ❌ 각 컨트롤러에서 `ArgumentResolver`를 개별 등록하지 않는다 — `WebMvcConfig` 단일 등록점.
