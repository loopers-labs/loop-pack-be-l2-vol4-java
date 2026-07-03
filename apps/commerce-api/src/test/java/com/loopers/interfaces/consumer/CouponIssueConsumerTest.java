package com.loopers.interfaces.consumer;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class CouponIssueConsumerTest {

    @Autowired
    private CouponIssueConsumer couponIssueConsumer;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰 발급 요청을 처리할 때,")
    @Nested
    class Process {

        @DisplayName("정상 요청이면 UserCoupon이 생성된다.")
        @Test
        void process_issuesCoupon_whenValid() throws Exception {
            // arrange
            var template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("선착순 쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7), 100L)
            );
            String payload = "{\"memberId\":1,\"templateId\":" + template.getId() + "}";

            // act
            couponIssueConsumer.process("event-1", payload);

            // assert
            assertThat(userCouponJpaRepository.countByTemplateId(template.getId())).isEqualTo(1);
        }

        @DisplayName("수량이 초과되면 UserCoupon이 생성되지 않는다.")
        @Test
        void process_skips_whenSoldOut() throws Exception {
            // arrange
            var template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("선착순 쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7), 1L)
            );
            couponIssueConsumer.process("event-1", "{\"memberId\":1,\"templateId\":" + template.getId() + "}");

            // act
            couponIssueConsumer.process("event-2", "{\"memberId\":2,\"templateId\":" + template.getId() + "}");

            // assert
            assertThat(userCouponJpaRepository.countByTemplateId(template.getId())).isEqualTo(1);
        }

        @DisplayName("동일 회원이 같은 쿠폰을 중복 요청하면 한 번만 발급된다.")
        @Test
        void process_skips_whenDuplicateRequest() throws Exception {
            // arrange
            var template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("선착순 쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7), 100L)
            );
            String payload = "{\"memberId\":1,\"templateId\":" + template.getId() + "}";

            // act
            couponIssueConsumer.process("event-1", payload);
            couponIssueConsumer.process("event-2", payload);

            // assert
            assertThat(userCouponJpaRepository.countByTemplateId(template.getId())).isEqualTo(1);
        }

        @DisplayName("200명이 100개 한도 선착순 쿠폰을 요청하면 100개만 발급된다.")
        @Test
        void process_issues_onlyUpToTotalCount() throws Exception {
            // arrange
            int totalCount = 100;
            int requestCount = 200;
            var template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("선착순 쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7), (long) totalCount)
            );

            // act - key=templateId로 단일 파티션에서 순차 처리되는 것을 시뮬레이션
            for (int i = 0; i < requestCount; i++) {
                String payload = "{\"memberId\":" + (i + 1) + ",\"templateId\":" + template.getId() + "}";
                couponIssueConsumer.process("event-" + i, payload);
            }

            // assert
            assertThat(userCouponJpaRepository.countByTemplateId(template.getId())).isEqualTo(totalCount);
        }
    }
}
