package com.loopers.support.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiConfig {

    @Bean
    public OperationCustomizer adminHeaderCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            RequestMapping classMapping = handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
            if (classMapping == null) {
                return operation;
            }

            boolean isAdminPath = classMapping.value().length > 0
                && classMapping.value()[0].startsWith("/api-admin");

            if (isAdminPath) {
                operation.addParametersItem(new Parameter()
                    .in("header")
                    .name("X-Loopers-Ldap")
                    .description("어드민 인증 헤더 (값: loopers.admin)")
                    .required(true)
                    .schema(new StringSchema()._default("loopers.admin"))
                );
            }

            return operation;
        };
    }
}
