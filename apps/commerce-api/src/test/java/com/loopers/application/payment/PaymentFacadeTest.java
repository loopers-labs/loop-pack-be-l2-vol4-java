package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers = com.loopers.testcontainers.RedisTestContainersConfig.class)
class PaymentFacadeTest {

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RedisTemplate<String, String> defaultRedisTemplate;

    @SpyBean
    private PaymentGateway paymentGateway;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        // Redis 정리
        var keys = defaultRedisTemplate.keys("payment_retry:*");
        if (keys != null && !keys.isEmpty()) {
            defaultRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("결제 요청 시 결제가 READY 상태로 저장되고 Redis에 TTL 10초인 retry 이력 키가 생성되며, PG사 통신에 성공하면 APPROVED로 변경된다.")
    void processPayment_Success_ShouldSaveApproved() {
        // given
        Long orderId = 1L;
        BigDecimal amount = new BigDecimal("5000");
        PaymentMethod method = PaymentMethod.CARD;

        // when
        Long paymentId = paymentFacade.processPayment(orderId, method, amount);

        // then
        PaymentModel payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getAmount()).isEqualByComparingTo(amount);

        // 성공 시 Redis retry 키는 삭제되어야 함
        String redisKey = "payment_retry:" + paymentId;
        Boolean hasKey = defaultRedisTemplate.hasKey(redisKey);
        assertThat(hasKey).isFalse();
    }

    @Test
    @DisplayName("PG API 호출 시 예외(Timeout 등)가 발생하면, 트랜잭션이 롤백되지 않고 결제가 READY 상태를 유지하며 Redis에 retry 키가 존재한다.")
    void processPayment_PgTimeout_ShouldKeepReadyStatus() {
        // given
        Long orderId = 2L;
        BigDecimal amount = new BigDecimal("10000");
        PaymentMethod method = PaymentMethod.CARD;

        // PG사 호출 시 강제로 Timeout 예외 발생시킴
        Mockito.doThrow(new CoreException(ErrorType.INTERNAL_ERROR, "PG Gateway Timeout"))
                .when(paymentGateway).requestPayment(Mockito.eq(orderId), Mockito.any(), Mockito.any());

        // when
        Long paymentId = paymentFacade.processPayment(orderId, method, amount);

        // then
        PaymentModel payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);

        // Redis에 retry count=0 키가 유지되어야 함
        String redisKey = "payment_retry:" + paymentId;
        String countVal = defaultRedisTemplate.opsForValue().get(redisKey);
        assertThat(countVal).isEqualTo("0");

        // TTL이 10초 근처로 설정되어 있는지 검증 (보통 즉시 조회하므로 0보다 큼)
        Long ttl = defaultRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(10);
    }
}
