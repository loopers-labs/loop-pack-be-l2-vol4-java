package com.loopers.support.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String LOGIN_ID_SCHEME = "X-Loopers-LoginId";
    private static final String LOGIN_PW_SCHEME = "X-Loopers-LoginPw";
    private static final String ADMIN_LDAP_SCHEME = "X-Loopers-Ldap";

    /**
     * 인증 헤더를 apiKey SecurityScheme 으로 등록해 Swagger UI 상단 Authorize 버튼에서 입력할 수 있게 한다.
     * <ul>
     *     <li>사용자 API: {@code X-Loopers-LoginId} / {@code X-Loopers-LoginPw} (전역 SecurityRequirement)</li>
     *     <li>관리자 API({@code /api-admin/**}): {@code X-Loopers-Ldap}</li>
     * </ul>
     * 각 SecurityScheme 의 description 에 예시 값을 넣어 Authorize 모달에서 바로 확인할 수 있게 한다.
     * (Swagger UI 보안 정책상 입력란 자체를 스펙으로 미리 채울 수는 없다.)
     */
    @Bean
    public OpenAPI loopersOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Loopers Commerce API")
                .description("사용자 API 는 X-Loopers-LoginId / X-Loopers-LoginPw, 관리자 API(/api-admin/**) 는 "
                    + "X-Loopers-Ldap 헤더로 인증합니다. 우측 상단 Authorize 에서 입력하세요.")
                .version("v1"))
            .components(new Components()
                .addSecuritySchemes(LOGIN_ID_SCHEME, headerApiKey(LOGIN_ID_SCHEME, "사용자 로그인 ID. 예시: usertest123"))
                .addSecuritySchemes(LOGIN_PW_SCHEME, headerApiKey(LOGIN_PW_SCHEME, "사용자 로그인 비밀번호. 예시: abc123!@#"))
                .addSecuritySchemes(ADMIN_LDAP_SCHEME, headerApiKey(ADMIN_LDAP_SCHEME, "관리자 LDAP 계정. 예시: loopers.admin")))
            .addSecurityItem(new SecurityRequirement()
                .addList(LOGIN_ID_SCHEME)
                .addList(LOGIN_PW_SCHEME));
    }

    private SecurityScheme headerApiKey(String headerName, String description) {
        return new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name(headerName)
            .description(description);
    }
}
