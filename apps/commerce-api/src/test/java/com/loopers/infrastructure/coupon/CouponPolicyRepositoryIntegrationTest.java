package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponPolicy;
import com.loopers.domain.coupon.CouponPolicyRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CouponPolicyRepositoryIntegrationTest {

    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2099-12-31T23:59:59+09:00");

    private final CouponPolicyRepository couponPolicyRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CouponPolicyRepositoryIntegrationTest(
        CouponPolicyRepository couponPolicyRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponPolicyRepository = couponPolicyRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponPolicy newPolicy(String name) {
        return new CouponPolicy(name, CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);
    }

    @DisplayName("쿠폰 정책을 저장하고 단건 조회할 때, ")
    @Nested
    class SaveAndFindById {

        @DisplayName("저장한 쿠폰 정책을 id 로 조회하면, 저장한 값이 그대로 반환된다.")
        @Test
        void returnsSavedPolicy_whenFindById() {
            // given
            CouponPolicy saved = couponPolicyRepository.save(newPolicy("3천원 할인"));

            // when
            Optional<CouponPolicy> found = couponPolicyRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertAll(
                () -> assertThat(found.get().getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.get().getName()).isEqualTo("3천원 할인"),
                () -> assertThat(found.get().getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(found.get().getValue()).isEqualTo(3_000L),
                () -> assertThat(found.get().getMinOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(found.get().getExpiredAt()).isEqualTo(EXPIRED_AT)
            );
        }

        @DisplayName("최소 주문 금액이 null 인 정책도 저장·조회 시 null 이 그대로 보존된다.")
        @Test
        void preservesNullMinOrderAmount_whenSavedAndFound() {
            // given
            CouponPolicy policy = new CouponPolicy("무제한 10% 할인", CouponType.RATE, 10L, null, EXPIRED_AT);
            CouponPolicy saved = couponPolicyRepository.save(policy);

            // when
            Optional<CouponPolicy> found = couponPolicyRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertAll(
                () -> assertThat(found.get().getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(found.get().getMinOrderAmount()).isNull()
            );
        }

        @DisplayName("존재하지 않는 id 로 조회하면, 빈 Optional 을 반환한다.")
        @Test
        void returnsEmpty_whenIdDoesNotExist() {
            // given
            Long notExistingId = 999L;

            // when
            Optional<CouponPolicy> found = couponPolicyRepository.findById(notExistingId);

            // then
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("active 쿠폰 정책을 단건 조회할 때, ")
    @Nested
    class FindActiveById {

        @DisplayName("삭제되지 않은 정책을 조회하면, 해당 정책을 반환한다.")
        @Test
        void returnsPolicy_whenNotDeleted() {
            // given
            CouponPolicy saved = couponPolicyRepository.save(newPolicy("3천원 할인"));

            // when
            Optional<CouponPolicy> found = couponPolicyRepository.findActiveById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
        }

        @DisplayName("soft-deleted 된 정책을 조회하면, findById 로는 보이지만 findActiveById 로는 빈 Optional 을 반환한다.")
        @Test
        void returnsEmpty_whenSoftDeleted() {
            // given
            CouponPolicy saved = couponPolicyRepository.save(newPolicy("삭제될 쿠폰"));
            saved.delete();
            couponPolicyRepository.save(saved);

            // when
            Optional<CouponPolicy> active = couponPolicyRepository.findActiveById(saved.getId());
            Optional<CouponPolicy> any = couponPolicyRepository.findById(saved.getId());

            // then
            assertAll(
                () -> assertThat(active).isEmpty(),
                () -> assertThat(any).isPresent(),
                () -> assertThat(any.get().getDeletedAt()).isNotNull()
            );
        }
    }

    @DisplayName("쿠폰 정책을 페이징 조회할 때, ")
    @Nested
    class FindAll {

        @DisplayName("페이지 크기만큼 잘라서 전체 개수와 함께 반환한다.")
        @Test
        void returnsPagedPolicies_withTotalCount() {
            // given
            couponPolicyRepository.save(newPolicy("쿠폰1"));
            couponPolicyRepository.save(newPolicy("쿠폰2"));
            couponPolicyRepository.save(newPolicy("쿠폰3"));

            // when
            Page<CouponPolicy> page = couponPolicyRepository.findAll(PageRequest.of(0, 2));

            // then
            assertAll(
                () -> assertThat(page.getContent()).hasSize(2),
                () -> assertThat(page.getTotalElements()).isEqualTo(3L)
            );
        }
    }

    @DisplayName("여러 id 로 쿠폰 정책을 일괄 조회할 때, ")
    @Nested
    class FindAllByIdIn {

        @DisplayName("주어진 id 목록에 해당하는 쿠폰 정책만 반환한다.")
        @Test
        void returnsPoliciesMatchingGivenIds() {
            // given
            CouponPolicy first = couponPolicyRepository.save(newPolicy("쿠폰1"));
            CouponPolicy second = couponPolicyRepository.save(newPolicy("쿠폰2"));
            couponPolicyRepository.save(newPolicy("쿠폰3"));

            // when
            List<CouponPolicy> found = couponPolicyRepository.findAllByIdIn(List.of(first.getId(), second.getId()));

            // then
            assertThat(found).extracting(CouponPolicy::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        }
    }
}
