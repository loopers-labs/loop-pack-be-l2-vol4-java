package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductDescription;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductName;
import com.loopers.domain.product.ProductPrice;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class RankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
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

    private void seedScore(LocalDate date, Long productId, double score) {
        String key = "ranking:all:" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
        redisTemplate.opsForZSet().add(key, String.valueOf(productId), score);
    }

    private List<RankingV1Dto.RankingItemResponse> readContent(MvcResult mvcResult) throws Exception {
        ApiResponse<JsonNode> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        JsonNode content = response.data().get("content");
        return objectMapper.convertValue(content, new TypeReference<>() {});
    }

    private long readTotalElements(MvcResult mvcResult) throws Exception {
        ApiResponse<JsonNode> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        return response.data().get("totalElements").asLong();
    }

    @DisplayName("score 내림차순으로 상품 정보(브랜드·좋아요·점수)를 Aggregation 해 반환한다.")
    @Test
    void returnsRankedWithAggregatedInfo() throws Exception {
        // given
        BrandModel brand = saveBrand("나이키");
        ProductModel a = saveProduct(brand.getId(), "A", 1000L);
        ProductModel b = saveProduct(brand.getId(), "B", 2000L);
        ProductModel c = saveProduct(brand.getId(), "C", 3000L);
        LocalDate today = LocalDate.now();
        seedScore(today, a.getId(), 2.0);
        seedScore(today, b.getId(), 1.0);
        seedScore(today, c.getId(), 3.0);

        // when
        MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)).andReturn();

        // then
        List<RankingV1Dto.RankingItemResponse> content = readContent(mvcResult);
        assertAll(
                () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(content).extracting(RankingV1Dto.RankingItemResponse::productId)
                        .containsExactly(c.getId(), a.getId(), b.getId()),
                () -> assertThat(content).extracting(RankingV1Dto.RankingItemResponse::rank)
                        .containsExactly(1L, 2L, 3L),
                () -> assertThat(content.get(0).name()).isEqualTo("C"),
                () -> assertThat(content.get(0).brand().name()).isEqualTo("나이키"),
                () -> assertThat(content.get(0).likeCount()).isEqualTo(0L),
                () -> assertThat(content.get(0).score()).isCloseTo(3.0, within(1e-9))
        );
    }

    @DisplayName("page/size 로 구간을 조회하고 total 은 ZCARD(전체 상품 수)다.")
    @Test
    void paginates() throws Exception {
        // given
        BrandModel brand = saveBrand("나이키");
        ProductModel a = saveProduct(brand.getId(), "A", 1000L);
        ProductModel b = saveProduct(brand.getId(), "B", 2000L);
        ProductModel c = saveProduct(brand.getId(), "C", 3000L);
        LocalDate today = LocalDate.now();
        seedScore(today, a.getId(), 3.0);
        seedScore(today, b.getId(), 2.0);
        seedScore(today, c.getId(), 1.0);

        // when
        MvcResult firstPage = mockMvc.perform(get(ENDPOINT).param("size", "2").param("page", "0")).andReturn();
        MvcResult secondPage = mockMvc.perform(get(ENDPOINT).param("size", "2").param("page", "1")).andReturn();

        // then
        List<RankingV1Dto.RankingItemResponse> first = readContent(firstPage);
        List<RankingV1Dto.RankingItemResponse> second = readContent(secondPage);
        assertAll(
                () -> assertThat(first).extracting(RankingV1Dto.RankingItemResponse::productId)
                        .containsExactly(a.getId(), b.getId()),
                () -> assertThat(first).extracting(RankingV1Dto.RankingItemResponse::rank)
                        .containsExactly(1L, 2L),
                () -> assertThat(second).extracting(RankingV1Dto.RankingItemResponse::productId)
                        .containsExactly(c.getId()),
                () -> assertThat(second).extracting(RankingV1Dto.RankingItemResponse::rank)
                        .containsExactly(3L),
                () -> assertThat(readTotalElements(firstPage)).isEqualTo(3L)
        );
    }

    @DisplayName("랭킹이 비어있으면(콜드 스타트) 빈 페이지를 반환한다.")
    @Test
    void returnsEmpty_whenNoRanking() throws Exception {
        // given
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // when
        MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)
                        .param("date", yesterday.format(DateTimeFormatter.BASIC_ISO_DATE)))
                .andReturn();

        // then
        List<RankingV1Dto.RankingItemResponse> content = readContent(mvcResult);
        assertAll(
                () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(content).isEmpty(),
                () -> assertThat(readTotalElements(mvcResult)).isEqualTo(0L)
        );
    }

    @DisplayName("ZSET 에 있으나 삭제·부재인 상품은 응답에서 스킵하고 나머지 순위는 보존한다.")
    @Test
    void skipsDeletedOrAbsentProduct() throws Exception {
        // given
        BrandModel brand = saveBrand("나이키");
        ProductModel deleted = saveProduct(brand.getId(), "삭제", 1000L);
        deleted.delete();
        productRepository.save(deleted);
        ProductModel alive = saveProduct(brand.getId(), "유지", 2000L);
        LocalDate today = LocalDate.now();
        seedScore(today, deleted.getId(), 3.0);  // ZSET 1위지만 삭제됨
        seedScore(today, alive.getId(), 2.0);    // ZSET 2위
        seedScore(today, 99999L, 1.0);           // 존재하지 않는 상품

        // when
        MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)).andReturn();

        // then
        List<RankingV1Dto.RankingItemResponse> content = readContent(mvcResult);
        assertAll(
                () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(content).extracting(RankingV1Dto.RankingItemResponse::productId)
                        .containsExactly(alive.getId()),
                () -> assertThat(content.getFirst().rank()).isEqualTo(2L)  // ZSET 위치(2위) 보존
        );
    }
}