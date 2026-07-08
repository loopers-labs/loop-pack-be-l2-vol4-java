package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockService;
import com.loopers.domain.queue.EntryTokenStore;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.auth.AuthenticatedUserArgumentResolver;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String LOGIN_ID = "minbo";
    private static final String PASSWORD = "Test1234!";
    private static final String HEADER_ENTRY_TOKEN = "X-Entry-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductStockService productStockService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private EntryTokenStore entryTokenStore;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private void signUp() {
        userService.createUser(LOGIN_ID, PASSWORD, "민보", LocalDate.of(1991, 8, 21), "test@example.com");
    }

    private ProductModel saveProduct(String name, Long price, int stock) {
        return productService.createProduct(1L, name, name + " 설명", price, stock);
    }

    // 대기열 관문 통과용 유효 토큰을 발급한다(스케줄러 없이 직접 발급).
    private String issueEntryToken() {
        return entryTokenStore.issue(LOGIN_ID);
    }

    @SuppressWarnings("unchecked")
    private OrderV1Dto.OrderResponse readOrder(MvcResult mvcResult) throws Exception {
        ApiResponse<JsonNode> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        return objectMapper.convertValue(response.data(), OrderV1Dto.OrderResponse.class);
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class PlaceOrder {

        @DisplayName("정상 요청이면, 주문이 PENDING 상태로 생성되고 재고가 차감된다.")
        @Test
        void returnsPending_andDecreasesStock() throws Exception {
            // given
            signUp();
            String entryToken = issueEntryToken();
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.Item(product.getId(), 3)),
                    null
            );

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .header(HEADER_ENTRY_TOKEN, entryToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            OrderV1Dto.OrderResponse order = readOrder(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(order.status()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(order.totalAmount()).isEqualTo(30000L),
                    () -> assertThat(order.items()).hasSize(1),
                    () -> assertThat(order.items().get(0).quantity()).isEqualTo(3),
                    () -> assertThat(order.items().get(0).subtotal()).isEqualTo(30000L),
                    () -> assertThat(productStockService.getStock(product.getId()).getStock().value()).isEqualTo(7)
            );
        }

        @DisplayName("쿠폰을 적용해 주문하면, 할인이 반영되고 쿠폰은 USED 상태가 된다.")
        @Test
        void appliesCoupon_andMarksUsed() throws Exception {
            // given
            signUp();
            String entryToken = issueEntryToken();
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            CouponModel coupon = couponService.createCoupon("정액 3000원", CouponType.FIXED, 3000L, null,
                    LocalDateTime.of(2999, 12, 31, 23, 59, 59), null);
            Long userId = userService.getMyInfo(LOGIN_ID).getId();
            userCouponService.issue(userId, coupon.getId());
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.Item(product.getId(), 2)),
                    coupon.getId()
            );

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .header(HEADER_ENTRY_TOKEN, entryToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            OrderV1Dto.OrderResponse order = readOrder(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(order.totalAmount()).isEqualTo(20000L),
                    () -> assertThat(order.discountAmount()).isEqualTo(3000L),
                    () -> assertThat(order.finalAmount()).isEqualTo(17000L),
                    () -> assertThat(userCouponService.getMyCoupons(userId).get(0).isUsed()).isTrue()
            );
        }

        @DisplayName("같은 상품이 중복으로 들어오면, 수량이 합산되어 한 항목으로 처리된다.")
        @Test
        void mergesDuplicateItems() throws Exception {
            // given
            signUp();
            String entryToken = issueEntryToken();
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 2),
                    new OrderV1Dto.Item(product.getId(), 3)
            ), null);

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .header(HEADER_ENTRY_TOKEN, entryToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            OrderV1Dto.OrderResponse order = readOrder(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(order.items()).hasSize(1),
                    () -> assertThat(order.items().get(0).quantity()).isEqualTo(5),
                    () -> assertThat(productStockService.getStock(product.getId()).getStock().value()).isEqualTo(5)
            );
        }

        @DisplayName("재고보다 많은 수량을 주문하면, 409 상태를 응답한다.")
        @Test
        void returns409_whenInsufficientStock() throws Exception {
            // given
            signUp();
            String entryToken = issueEntryToken();
            ProductModel product = saveProduct("티셔츠", 10000L, 2);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 5)
            ), null);

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .header(HEADER_ENTRY_TOKEN, entryToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        }

        @DisplayName("존재하지 않는 상품을 주문하면, 404 상태를 응답한다.")
        @Test
        void returns404_whenProductMissing() throws Exception {
            // given
            signUp();
            String entryToken = issueEntryToken();
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(99999L, 1)
            ), null);

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .header(HEADER_ENTRY_TOKEN, entryToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }

        @DisplayName("입장 토큰이 없으면, 403 상태를 응답한다.")
        @Test
        void returns403_whenNoEntryToken() throws Exception {
            // given
            signUp();
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 1)
            ), null);

            // when — X-Entry-Token 헤더 없이 요청
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }

        @DisplayName("발급된 토큰과 일치하지 않는 토큰이면, 403 상태를 응답한다.")
        @Test
        void returns403_whenInvalidEntryToken() throws Exception {
            // given
            signUp();
            issueEntryToken(); // 유효 토큰은 존재하지만, 요청은 다른 값을 보낸다
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 1)
            ), null);

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .header(HEADER_ENTRY_TOKEN, "wrong-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }

        @DisplayName("유효한 토큰으로 주문에 성공하면, 토큰이 삭제된다.")
        @Test
        void deletesToken_afterSuccessfulOrder() throws Exception {
            // given
            signUp();
            String entryToken = issueEntryToken();
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 1)
            ), null);

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .header(HEADER_ENTRY_TOKEN, entryToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then — 주문 성공 후 토큰이 소비되어 사라진다
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(entryTokenStore.find(LOGIN_ID)).isEmpty()
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 상태를 응답한다.")
        @Test
        void returns401_whenNoAuth() throws Exception {
            // given
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 1)
            ), null);

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @DisplayName("주문 항목이 비어 있으면, 400 상태를 응답한다.")
        @Test
        void returns400_whenEmptyItems() throws Exception {
            // given
            signUp();
            String entryToken = issueEntryToken();
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(), null);

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .header(HEADER_ENTRY_TOKEN, entryToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }

        @DisplayName("수량이 0 이하면, 400 상태를 응답한다.")
        @Test
        void returns400_whenInvalidQuantity() throws Exception {
            // given
            signUp();
            String entryToken = issueEntryToken();
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 0)
            ), null);

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                            .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                            .header(HEADER_ENTRY_TOKEN, entryToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }
}