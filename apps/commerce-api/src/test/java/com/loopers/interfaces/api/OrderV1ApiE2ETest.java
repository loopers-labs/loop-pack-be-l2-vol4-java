package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.PaymentCommand;
import com.loopers.domain.order.PaymentGateway;
import com.loopers.domain.order.PaymentResult;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockService;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.auth.AuthenticatedUserArgumentResolver;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String LOGIN_ID = "minbo";
    private static final String PASSWORD = "Test1234!";

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
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private PaymentGateway paymentGateway;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void stubPaymentSuccess() {
        given(paymentGateway.requestPayment(any(PaymentCommand.class)))
                .willReturn(PaymentResult.success("tx-success"));
    }

    private void signUp() {
        userService.createUser(LOGIN_ID, PASSWORD, "민보", LocalDate.of(1991, 8, 21), "test@example.com");
    }

    private ProductModel saveProduct(String name, Long price, int stock) {
        return productService.createProduct(1L, name, name + " 설명", price, stock);
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

        @DisplayName("정상 요청이면, 주문이 PAID 상태로 확정되고 재고가 차감된다.")
        @Test
        void returnsPaid_andDecreasesStock() throws Exception {
            // given
            signUp();
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 3)
            ));

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();

            // then
            OrderV1Dto.OrderResponse order = readOrder(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(order.status()).isEqualTo(OrderStatus.PAID),
                    () -> assertThat(order.totalAmount()).isEqualTo(30000L),
                    () -> assertThat(order.items()).hasSize(1),
                    () -> assertThat(order.items().get(0).quantity()).isEqualTo(3),
                    () -> assertThat(order.items().get(0).subtotal()).isEqualTo(30000L),
                    () -> assertThat(productStockService.getStock(product.getId()).getStock().value()).isEqualTo(7)
            );
        }

        @DisplayName("같은 상품이 중복으로 들어오면, 수량이 합산되어 한 항목으로 처리된다.")
        @Test
        void mergesDuplicateItems() throws Exception {
            // given
            signUp();
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 2),
                    new OrderV1Dto.Item(product.getId(), 3)
            ));

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
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

        @DisplayName("결제가 실패하면, 재고가 복구되고 500 상태를 응답한다.")
        @Test
        void compensates_whenPaymentFails() throws Exception {
            // given
            signUp();
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            given(paymentGateway.requestPayment(any(PaymentCommand.class)))
                    .willReturn(PaymentResult.failure("declined"));
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 3)
            ));

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();

            // then
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                    () -> assertThat(productStockService.getStock(product.getId()).getStock().value()).isEqualTo(10)
            );
        }

        @DisplayName("재고보다 많은 수량을 주문하면, 409 상태를 응답한다.")
        @Test
        void returns409_whenInsufficientStock() throws Exception {
            // given
            signUp();
            ProductModel product = saveProduct("티셔츠", 10000L, 2);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 5)
            ));

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
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
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(99999L, 1)
            ));

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }

        @DisplayName("인증 헤더가 없으면, 401 상태를 응답한다.")
        @Test
        void returns401_whenNoAuth() throws Exception {
            // given
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 1)
            ));

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
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of());

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
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
            ProductModel product = saveProduct("티셔츠", 10000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                    new OrderV1Dto.Item(product.getId(), 0)
            ));

            // when
            MvcResult mvcResult = mockMvc.perform(post(ENDPOINT)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(objectMapper.writeValueAsString(request)))
                                         .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }
}