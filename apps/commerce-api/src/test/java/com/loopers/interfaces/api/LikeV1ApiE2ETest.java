package com.loopers.interfaces.api;

import com.loopers.domain.like.LikeId;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductDescription;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductName;
import com.loopers.domain.product.ProductPrice;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.auth.AuthenticatedUserArgumentResolver;
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
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
class LikeV1ApiE2ETest {

    private static final String LOGIN_ID = "minbo@test.com";
    private static final String PASSWORD = "Test1234!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel signUp() {
        return userService.createUser(LOGIN_ID, PASSWORD, "민보", LocalDate.of(1991, 8, 21), "test@example.com");
    }

    private ProductModel saveProduct() {
        return productRepository.save(ProductModel.of(
                1L,
                ProductName.of("티셔츠"),
                ProductDescription.of("면 100%"),
                ProductPrice.of(10000L)
        ));
    }

    private String endpoint(Long productId) {
        return "/api/v1/products/" + productId + "/likes";
    }

    @DisplayName("PUT /api/v1/products/{productId}/likes")
    @Nested
    class Like {

        @DisplayName("정상 요청이면, 204 응답하고 좋아요가 등록된다.")
        @Test
        void returns204_andCreatesLike() throws Exception {
            // given
            UserModel user = signUp();
            ProductModel product = saveProduct();

            // when
            MvcResult mvcResult = mockMvc.perform(put(endpoint(product.getId()))
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            LikeModel found = likeRepository.find(LikeId.of(user.getId(), product.getId())).orElseThrow();
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value()),
                    () -> assertThat(found.isLiked()).isTrue()
            );
        }

        @DisplayName("이미 좋아요 상태에서 다시 요청해도, 204 응답하고 likedAt 시각이 유지된다.")
        @Test
        void isIdempotent() throws Exception {
            // given
            UserModel user = signUp();
            ProductModel product = saveProduct();
            mockMvc.perform(put(endpoint(product.getId()))
                   .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                   .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                   .andReturn();
            ZonedDateTime before = likeRepository.find(LikeId.of(user.getId(), product.getId()))
                    .orElseThrow().getLikedAt();

            // when
            MvcResult mvcResult = mockMvc.perform(put(endpoint(product.getId()))
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            ZonedDateTime after = likeRepository.find(LikeId.of(user.getId(), product.getId()))
                    .orElseThrow().getLikedAt();
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value()),
                    () -> assertThat(after).isEqualTo(before)
            );
        }

        @DisplayName("존재하지 않는 상품이면, 404 응답한다.")
        @Test
        void returns404_whenProductNotFound() throws Exception {
            // given
            signUp();

            // when
            MvcResult mvcResult = mockMvc.perform(put(endpoint(99999L))
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }

        @DisplayName("삭제된 상품이면, 404 응답한다.")
        @Test
        void returns404_whenProductSoftDeleted() throws Exception {
            // given
            signUp();
            ProductModel product = saveProduct();
            product.delete();
            productRepository.save(product);

            // when
            MvcResult mvcResult = mockMvc.perform(put(endpoint(product.getId()))
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class Unlike {

        @DisplayName("정상 요청이면, 204 응답하고 좋아요가 취소된다.")
        @Test
        void returns204_andCancelsLike() throws Exception {
            // given
            UserModel user = signUp();
            ProductModel product = saveProduct();
            mockMvc.perform(put(endpoint(product.getId()))
                   .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                   .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                   .andReturn();

            // when
            MvcResult mvcResult = mockMvc.perform(delete(endpoint(product.getId()))
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            LikeModel found = likeRepository.find(LikeId.of(user.getId(), product.getId())).orElseThrow();
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value()),
                    () -> assertThat(found.isLiked()).isFalse()
            );
        }

        @DisplayName("좋아요 행이 없는 상태에서 호출해도, 204 응답한다.")
        @Test
        void isIdempotent_whenNoRow() throws Exception {
            // given
            signUp();
            ProductModel product = saveProduct();

            // when
            MvcResult mvcResult = mockMvc.perform(delete(endpoint(product.getId()))
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
        }

        @DisplayName("존재하지 않는 상품이면, 404 응답한다.")
        @Test
        void returns404_whenProductNotFound() throws Exception {
            // given
            signUp();

            // when
            MvcResult mvcResult = mockMvc.perform(delete(endpoint(99999L))
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                                         .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD))
                                         .andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }
}