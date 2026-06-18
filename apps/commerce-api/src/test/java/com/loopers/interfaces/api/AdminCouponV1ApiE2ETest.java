package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.interfaces.api.admin.coupon.AdminCouponV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCouponV1ApiE2ETest {

    private static final String ENDPOINT = "/api/admin/v1/coupons";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private LocalDateTime future() {
        return LocalDateTime.of(2999, 12, 31, 23, 59, 59);
    }

    private CouponModel seedCoupon() {
        return couponService.createCoupon("정액 1000원", CouponType.FIXED, 1000L, null, future(), 100);
    }

    private AdminCouponV1Dto.CouponResponse readCoupon(MvcResult mvcResult) throws Exception {
        ApiResponse<JsonNode> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), new TypeReference<>() {});
        return objectMapper.convertValue(response.data(), AdminCouponV1Dto.CouponResponse.class);
    }

    @DisplayName("POST /api/admin/v1/coupons")
    @Nested
    class Create {

        @DisplayName("정상 요청이면, 쿠폰 템플릿이 등록된다.")
        @Test
        void createsCoupon() throws Exception {
            // given
            AdminCouponV1Dto.CreateCouponRequest request = new AdminCouponV1Dto.CreateCouponRequest(
                    "신규가입 10% 할인", CouponType.RATE, 10L, 10000L, future(), 100);

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();

            // then
            AdminCouponV1Dto.CouponResponse coupon = readCoupon(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(coupon.id()).isNotNull(),
                    () -> assertThat(coupon.type()).isEqualTo(CouponType.RATE),
                    () -> assertThat(coupon.value()).isEqualTo(10L),
                    () -> assertThat(coupon.quantity()).isEqualTo(100)
            );
        }
    }

    @DisplayName("GET /api/admin/v1/coupons/{couponId}")
    @Nested
    class GetDetail {

        @DisplayName("등록된 쿠폰 템플릿 상세를 조회한다.")
        @Test
        void returnsDetail() throws Exception {
            // given
            CouponModel coupon = seedCoupon();

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT + "/" + coupon.getId())).andReturn();

            // then
            AdminCouponV1Dto.CouponResponse found = readCoupon(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(found.id()).isEqualTo(coupon.getId()),
                    () -> assertThat(found.quantity()).isEqualTo(100)
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, 404 상태를 응답한다.")
        @Test
        void returns404_whenMissing() throws Exception {
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT + "/99999")).andReturn();

            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @DisplayName("PUT /api/admin/v1/coupons/{couponId}")
    @Nested
    class Update {

        @DisplayName("쿠폰 템플릿이 수정된다.")
        @Test
        void updatesCoupon() throws Exception {
            // given
            CouponModel coupon = seedCoupon();
            AdminCouponV1Dto.UpdateCouponRequest request = new AdminCouponV1Dto.UpdateCouponRequest(
                    "수정된 쿠폰", CouponType.RATE, 20L, 5000L, future());

            // when
            MvcResult mvcResult = mockMvc.perform(put(ENDPOINT + "/" + coupon.getId())
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();

            // then
            AdminCouponV1Dto.CouponResponse updated = readCoupon(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(updated.name()).isEqualTo("수정된 쿠폰"),
                    () -> assertThat(updated.type()).isEqualTo(CouponType.RATE),
                    () -> assertThat(updated.value()).isEqualTo(20L)
            );
        }
    }

    @DisplayName("DELETE /api/admin/v1/coupons/{couponId}")
    @Nested
    class Delete {

        @DisplayName("쿠폰 템플릿이 삭제되어 이후 조회 시 404를 응답한다.")
        @Test
        void deletesCoupon() throws Exception {
            // given
            CouponModel coupon = seedCoupon();

            // when
            MvcResult deleteResult = mockMvc.perform(delete(ENDPOINT + "/" + coupon.getId())).andReturn();

            // then
            MvcResult getResult = mockMvc.perform(get(ENDPOINT + "/" + coupon.getId())).andReturn();
            assertAll(
                    () -> assertThat(deleteResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(getResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value())
            );
        }
    }

    @DisplayName("GET /api/admin/v1/coupons/{couponId}/issues")
    @Nested
    class GetIssues {

        @DisplayName("특정 쿠폰의 발급 내역이 조회된다.")
        @Test
        void returnsIssues() throws Exception {
            // given
            CouponModel coupon = seedCoupon();
            userCouponService.issue(1L, coupon.getId());
            userCouponService.issue(2L, coupon.getId());

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT + "/" + coupon.getId() + "/issues")).andReturn();

            // then
            ApiResponse<JsonNode> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(), new TypeReference<>() {});
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(response.data().get("totalElements").asInt()).isEqualTo(2)
            );
        }
    }
}