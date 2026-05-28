package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductDescription;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductName;
import com.loopers.domain.product.ProductPrice;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class ProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveBrand(String name) {
        return brandRepository.save(BrandModel.of(BrandName.of(name), BrandDescription.of(name + " 설명")));
    }

    private ProductModel saveProduct(Long brandId, String name, Long price) {
        return productRepository.save(ProductModel.of(
                brandId,
                ProductName.of(name),
                ProductDescription.of(name + " 설명"),
                ProductPrice.of(price)
        ));
    }

    private List<ProductV1Dto.ProductSummaryResponse> readContent(MvcResult mvcResult) throws Exception {
        ApiResponse<JsonNode> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        JsonNode content = response.data().get("content");
        return objectMapper.convertValue(content, new TypeReference<>() {});
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("sort 미지정이면, 최신순으로 반환한다.")
        @Test
        void returnsLatestFirst_whenSortNotGiven() throws Exception {
            // given
            BrandModel brand = saveBrand("나이키");
            saveProduct(brand.getId(), "A", 1000L);
            saveProduct(brand.getId(), "B", 2000L);
            ProductModel last = saveProduct(brand.getId(), "C", 3000L);

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)).andReturn();

            // then
            List<ProductV1Dto.ProductSummaryResponse> content = readContent(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(content).hasSize(3),
                    () -> assertThat(content.get(0).id()).isEqualTo(last.getId())
            );
        }

        @DisplayName("sort=PRICE_ASC면, 가격 오름차순으로 반환한다.")
        @Test
        void returnsByPriceAsc() throws Exception {
            // given
            BrandModel brand = saveBrand("나이키");
            saveProduct(brand.getId(), "B", 2000L);
            saveProduct(brand.getId(), "C", 3000L);
            saveProduct(brand.getId(), "A", 1000L);

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT).param("sort", "PRICE_ASC")).andReturn();

            // then
            List<ProductV1Dto.ProductSummaryResponse> content = readContent(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(content).extracting(ProductV1Dto.ProductSummaryResponse::price)
                            .containsExactly(1000L, 2000L, 3000L)
            );
        }

        @DisplayName("sort=PRICE_DESC면, 가격 내림차순으로 반환한다.")
        @Test
        void returnsByPriceDesc() throws Exception {
            // given
            BrandModel brand = saveBrand("나이키");
            saveProduct(brand.getId(), "B", 2000L);
            saveProduct(brand.getId(), "C", 3000L);
            saveProduct(brand.getId(), "A", 1000L);

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT).param("sort", "PRICE_DESC")).andReturn();

            // then
            List<ProductV1Dto.ProductSummaryResponse> content = readContent(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(content).extracting(ProductV1Dto.ProductSummaryResponse::price)
                            .containsExactly(3000L, 2000L, 1000L)
            );
        }

        @DisplayName("brandId 필터로 해당 브랜드 상품만 반환한다.")
        @Test
        void filtersByBrandId() throws Exception {
            // given
            BrandModel brand1 = saveBrand("나이키");
            BrandModel brand2 = saveBrand("아디다스");
            saveProduct(brand1.getId(), "A", 1000L);
            saveProduct(brand2.getId(), "B", 2000L);

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)
                            .param("brandId", brand2.getId().toString()))
                    .andReturn();

            // then
            List<ProductV1Dto.ProductSummaryResponse> content = readContent(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(content).hasSize(1),
                    () -> assertThat(content.get(0).brandId()).isEqualTo(brand2.getId())
            );
        }

        @DisplayName("삭제된 상품은 목록에서 제외된다.")
        @Test
        void excludesSoftDeleted() throws Exception {
            // given
            BrandModel brand = saveBrand("나이키");
            saveProduct(brand.getId(), "유지", 1000L);
            ProductModel deleted = saveProduct(brand.getId(), "삭제", 2000L);
            deleted.delete();
            productRepository.save(deleted);

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)).andReturn();

            // then
            List<ProductV1Dto.ProductSummaryResponse> content = readContent(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(content).hasSize(1),
                    () -> assertThat(content.get(0).name()).isEqualTo("유지")
            );
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProductDetail {

        @DisplayName("존재하면, 상품과 브랜드 정보를 함께 반환한다.")
        @Test
        void returnsDetail() throws Exception {
            // given
            BrandModel brand = saveBrand("나이키");
            ProductModel product = saveProduct(brand.getId(), "티셔츠", 10000L);

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT + "/" + product.getId())).andReturn();

            // then
            ApiResponse<JsonNode> response = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );
            ProductV1Dto.ProductDetailResponse detail =
                    objectMapper.convertValue(response.data(), ProductV1Dto.ProductDetailResponse.class);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(detail.id()).isEqualTo(product.getId()),
                    () -> assertThat(detail.name()).isEqualTo("티셔츠"),
                    () -> assertThat(detail.price()).isEqualTo(10000L),
                    () -> assertThat(detail.brand().id()).isEqualTo(brand.getId()),
                    () -> assertThat(detail.brand().name()).isEqualTo("나이키")
            );
        }

        @DisplayName("존재하지 않으면, 404 응답한다.")
        @Test
        void returns404_whenNotExists() throws Exception {
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT + "/99999")).andReturn();

            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }

        @DisplayName("삭제된 상품이면, 404 응답한다.")
        @Test
        void returns404_whenSoftDeleted() throws Exception {
            // given
            BrandModel brand = saveBrand("나이키");
            ProductModel product = saveProduct(brand.getId(), "티셔츠", 10000L);
            product.delete();
            productRepository.save(product);

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT + "/" + product.getId())).andReturn();

            // then
            assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }
}