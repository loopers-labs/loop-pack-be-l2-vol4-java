package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.auth.AuthenticatedUserArgumentResolver;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class CouponV1ApiE2ETest {

    private static final String LOGIN_ID = "minbo";
    private static final String PASSWORD = "Test1234!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void signUp() {
        userService.createUser(LOGIN_ID, PASSWORD, "민보", LocalDate.of(1991, 8, 21), "test@example.com");
    }

    private CouponModel seedCoupon() {
        return couponService.createCoupon("정액 1000원", CouponType.FIXED, 1000L, null,
                LocalDateTime.of(2999, 12, 31, 23, 59, 59), null);
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("정상 요청이면, 쿠폰이 발급되고 AVAILABLE 상태로 반환된다.")
        @Test
        void issuesCoupon() throws Exception {
            // given
            signUp();
            CouponModel coupon = seedCoupon();

            // when
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/coupons/" + coupon.getId() + "/issue")
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            ApiResponse<JsonNode> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(), new TypeReference<>() {});
            CouponV1Dto.MyCouponResponse coupons = objectMapper.convertValue(response.data(), CouponV1Dto.MyCouponResponse.class);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(coupons.couponId()).isEqualTo(coupon.getId()),
                    () -> assertThat(coupons.status()).isEqualTo(UserCouponStatus.AVAILABLE)
            );
        }

        @DisplayName("같은 쿠폰을 두 번 발급하면, 409 상태를 응답한다.")
        @Test
        void returns409_whenAlreadyIssued() throws Exception {
            // given
            signUp();
            CouponModel coupon = seedCoupon();
            mockMvc.perform(post("/api/v1/coupons/" + coupon.getId() + "/issue")
                    .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                    .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD));

            // when
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/coupons/" + coupon.getId() + "/issue")
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        }

        @DisplayName("존재하지 않는 쿠폰을 발급하면, 404 상태를 응답한다.")
        @Test
        void returns404_whenCouponMissing() throws Exception {
            // given
            signUp();

            // when
            MvcResult mvcResult = mockMvc.perform(post("/api/v1/coupons/99999/issue")
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    class GetMyCoupons {

        @DisplayName("발급받은 쿠폰들이 상태와 함께 조회된다.")
        @Test
        void returnsMyCoupons() throws Exception {
            // given
            signUp();
            CouponModel coupon = seedCoupon();
            mockMvc.perform(post("/api/v1/coupons/" + coupon.getId() + "/issue")
                    .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                    .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD));

            // when
            MvcResult mvcResult = mockMvc.perform(get("/api/v1/users/me/coupons")
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            ApiResponse<List<CouponV1Dto.MyCouponResponse>> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(), new TypeReference<>() {});
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(response.data()).hasSize(1),
                    () -> assertThat(response.data().get(0).couponId()).isEqualTo(coupon.getId())
            );
        }
    }
}