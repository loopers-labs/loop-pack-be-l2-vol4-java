package com.loopers.infrastructure.payment;

import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentGatewayRetryConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RetryAutoConfiguration.class))
            .withUserConfiguration(PaymentGatewayRetryConfig.class)
            .withPropertyValues(
                    "resilience4j.retry.instances.paymentGateway.max-attempts=3",
                    "resilience4j.retry.instances.paymentGateway.wait-duration=100ms",
                    "resilience4j.retry.instances.paymentGateway.enable-exponential-backoff=true",
                    "resilience4j.retry.instances.paymentGateway.exponential-backoff-multiplier=2",
                    "resilience4j.retry.instances.paymentGateway.exponential-max-wait-duration=300ms",
                    "resilience4j.retry.instances.paymentGateway.retry-exceptions[0]=feign.RetryableException"
            );

    @DisplayName("application.yml의 resilience4j.retry.instances.paymentGateway 설정으로부터, ")
    @Test
    void createsRetryBean_boundFromConfiguredProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Retry.class);

            Retry retry = context.getBean(Retry.class);
            RetryConfig retryConfig = retry.getRetryConfig();

            Request feignRequest = Request.create(
                    Request.HttpMethod.POST, "/api/v1/payments", Collections.emptyMap(), null, StandardCharsets.UTF_8, null
            );
            RetryableException retryableException =
                    new RetryableException(503, "PG 응답을 받지 못했습니다.", Request.HttpMethod.POST, (Long) null, feignRequest);

            assertThat(retryConfig.getMaxAttempts()).isEqualTo(3);
            assertThat(retryConfig.getExceptionPredicate().test(retryableException)).isTrue();
            assertThat(retryConfig.getExceptionPredicate().test(new RuntimeException("재시도 대상이 아님"))).isFalse();

            // 재시도가 모두 소진되면 MaxRetriesExceededException 등으로 감싸지 않고 원본 예외를 그대로 던진다.
            RetryableException result = assertThrows(RetryableException.class,
                    () -> Retry.decorateSupplier(retry, () -> { throw retryableException; }).get());
            assertThat(result).isSameAs(retryableException);
        });
    }
}
