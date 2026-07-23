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
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.infrastructure.ranking.ProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.ProductRankMonthlyModel;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
import com.loopers.infrastructure.ranking.ProductRankWeeklyModel;
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
import java.time.ZonedDateTime;
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
    private ProductRankWeeklyJpaRepository weeklyJpaRepository;

    @Autowired
    private ProductRankMonthlyJpaRepository monthlyJpaRepository;

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

    /** 배치가 적재했을 주간 MV 행을 흉내낸다. periodKey 는 조회와 같은 규칙으로 계산해 맞춘다. */
    private void seedWeeklyMv(LocalDate date, int rankNo, Long productId, double score) {
        weeklyJpaRepository.save(ProductRankWeeklyModel.of(
                RankingPeriod.WEEKLY.periodKey(date), rankNo, productId, score, ZonedDateTime.now()
        ));
    }

    private void seedMonthlyMv(LocalDate date, int rankNo, Long productId, double score) {
        monthlyJpaRepository.save(ProductRankMonthlyModel.of(
                RankingPeriod.MONTHLY.periodKey(date), rankNo, productId, score, ZonedDateTime.now()
        ));
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

    @DisplayName("period=DAILY 를 명시해도 기존 실시간(Redis) 랭킹 경로를 그대로 사용한다.")
    @Test
    void usesRedis_whenPeriodIsDaily() throws Exception {
        // given
        BrandModel brand = saveBrand("나이키");
        ProductModel realtime = saveProduct(brand.getId(), "실시간", 1000L);
        ProductModel weeklyOnly = saveProduct(brand.getId(), "주간전용", 2000L);
        LocalDate today = LocalDate.now();
        seedScore(today, realtime.getId(), 5.0);
        seedWeeklyMv(today, 1, weeklyOnly.getId(), 99.0);  // 주간 MV 가 있어도 일간 조회에는 쓰이지 않는다

        // when
        MvcResult mvcResult = mockMvc.perform(get(ENDPOINT).param("period", "DAILY")).andReturn();

        // then
        List<RankingV1Dto.RankingItemResponse> content = readContent(mvcResult);
        assertAll(
                () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(content).extracting(RankingV1Dto.RankingItemResponse::productId)
                        .containsExactly(realtime.getId()),
                () -> assertThat(content.getFirst().rank()).isEqualTo(1L),
                () -> assertThat(content.getFirst().score()).isCloseTo(5.0, within(1e-9))
        );
    }

    @DisplayName("period=WEEKLY 는 해당 주 MV 를 순위 오름차순으로 읽어 상품 정보를 Aggregation 한다.")
    @Test
    void returnsWeeklyRankingFromMv() throws Exception {
        // given
        BrandModel brand = saveBrand("나이키");
        ProductModel a = saveProduct(brand.getId(), "A", 1000L);
        ProductModel b = saveProduct(brand.getId(), "B", 2000L);
        ProductModel c = saveProduct(brand.getId(), "C", 3000L);
        LocalDate date = LocalDate.of(2026, 7, 23);
        seedWeeklyMv(date, 1, b.getId(), 30.0);
        seedWeeklyMv(date, 2, a.getId(), 20.0);
        seedMonthlyMv(date, 1, c.getId(), 99.0);  // 월간 MV 는 주간 조회에 섞이지 않는다

        // when
        MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)
                        .param("period", "WEEKLY")
                        .param("date", date.format(DateTimeFormatter.BASIC_ISO_DATE)))
                .andReturn();

        // then
        List<RankingV1Dto.RankingItemResponse> content = readContent(mvcResult);
        assertAll(
                () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(content).extracting(RankingV1Dto.RankingItemResponse::productId)
                        .containsExactly(b.getId(), a.getId()),
                () -> assertThat(content).extracting(RankingV1Dto.RankingItemResponse::rank)
                        .containsExactly(1L, 2L),
                () -> assertThat(content.getFirst().name()).isEqualTo("B"),
                () -> assertThat(content.getFirst().brand().name()).isEqualTo("나이키"),
                () -> assertThat(content.getFirst().likeCount()).isEqualTo(0L),
                () -> assertThat(content.getFirst().score()).isCloseTo(30.0, within(1e-9)),
                () -> assertThat(readTotalElements(mvcResult)).isEqualTo(2L)
        );
    }

    @DisplayName("period=MONTHLY 는 해당 달 MV 를 순위 오름차순으로 읽어 상품 정보를 Aggregation 한다.")
    @Test
    void returnsMonthlyRankingFromMv() throws Exception {
        // given
        BrandModel brand = saveBrand("나이키");
        ProductModel a = saveProduct(brand.getId(), "A", 1000L);
        ProductModel b = saveProduct(brand.getId(), "B", 2000L);
        LocalDate date = LocalDate.of(2026, 7, 23);
        seedMonthlyMv(date, 1, a.getId(), 50.0);
        seedMonthlyMv(date, 2, b.getId(), 40.0);

        // when
        MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)
                        .param("period", "MONTHLY")
                        .param("date", date.format(DateTimeFormatter.BASIC_ISO_DATE)))
                .andReturn();

        // then
        List<RankingV1Dto.RankingItemResponse> content = readContent(mvcResult);
        assertAll(
                () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(content).extracting(RankingV1Dto.RankingItemResponse::productId)
                        .containsExactly(a.getId(), b.getId()),
                () -> assertThat(content).extracting(RankingV1Dto.RankingItemResponse::rank)
                        .containsExactly(1L, 2L),
                () -> assertThat(content.getFirst().brand().name()).isEqualTo("나이키"),
                () -> assertThat(content.getFirst().score()).isCloseTo(50.0, within(1e-9)),
                () -> assertThat(readTotalElements(mvcResult)).isEqualTo(2L)
        );
    }

    @DisplayName("아직 집계되지 않은 기간(빈 MV)은 빈 페이지를 반환한다.")
    @Test
    void returnsEmpty_whenMvNotAggregated() throws Exception {
        // given
        LocalDate notAggregated = LocalDate.of(2026, 7, 23);

        // when
        MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)
                        .param("period", "WEEKLY")
                        .param("date", notAggregated.format(DateTimeFormatter.BASIC_ISO_DATE)))
                .andReturn();

        // then
        List<RankingV1Dto.RankingItemResponse> content = readContent(mvcResult);
        assertAll(
                () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(content).isEmpty(),
                () -> assertThat(readTotalElements(mvcResult)).isEqualTo(0L)
        );
    }

    @DisplayName("MV 에 있으나 삭제·부재인 상품은 응답에서 스킵하고 나머지 순위는 보존한다.")
    @Test
    void skipsDeletedOrAbsentProduct_inWeeklyMv() throws Exception {
        // given
        BrandModel brand = saveBrand("나이키");
        ProductModel deleted = saveProduct(brand.getId(), "삭제", 1000L);
        deleted.delete();
        productRepository.save(deleted);
        ProductModel alive = saveProduct(brand.getId(), "유지", 2000L);
        LocalDate date = LocalDate.of(2026, 7, 23);
        seedWeeklyMv(date, 1, deleted.getId(), 30.0);  // MV 1위지만 삭제됨
        seedWeeklyMv(date, 2, alive.getId(), 20.0);
        seedWeeklyMv(date, 3, 99999L, 10.0);           // 존재하지 않는 상품

        // when
        MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)
                        .param("period", "WEEKLY")
                        .param("date", date.format(DateTimeFormatter.BASIC_ISO_DATE)))
                .andReturn();

        // then
        List<RankingV1Dto.RankingItemResponse> content = readContent(mvcResult);
        assertAll(
                () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(content).extracting(RankingV1Dto.RankingItemResponse::productId)
                        .containsExactly(alive.getId()),
                () -> assertThat(content.getFirst().rank()).isEqualTo(2L)  // MV 의 rank_no(2위) 보존
        );
    }
}