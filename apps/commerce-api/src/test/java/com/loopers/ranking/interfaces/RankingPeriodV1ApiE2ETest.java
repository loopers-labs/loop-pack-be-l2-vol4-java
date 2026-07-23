package com.loopers.ranking.interfaces;

import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.ranking.domain.WeeklyProductRankModel;
import com.loopers.ranking.infrastructure.WeeklyProductRankJpaRepository;
import com.loopers.support.response.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingPeriodV1ApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private WeeklyProductRankJpaRepository weeklyRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final ParameterizedTypeReference<ApiResponse<List<RankingV1Dto.RankingResponse>>> RESPONSE_TYPE =
        new ParameterizedTypeReference<>() {};

    @DisplayName("GET /api/v1/rankings?period=weekly")
    @Nested
    class Weekly {

        @DisplayName("주간 MV에 적재된 랭킹을 저장된 rank 순서로 상품정보와 함께 반환한다.")
        @Test
        void returnsWeeklyRanking_fromMv() {
            // arrange
            ProductModel a = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            ProductModel b = productJpaRepository.save(new ProductModel("조던1", "나이키 농구화", 200000L, null));
            weeklyRepository.save(new WeeklyProductRankModel(b.getId(), 1, 9.0));
            weeklyRepository.save(new WeeklyProductRankModel(a.getId(), 2, 3.0));

            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange("/api/v1/rankings?period=weekly&page=1&size=20",
                    HttpMethod.GET, new HttpEntity<>(null), RESPONSE_TYPE);

            // assert
            List<RankingV1Dto.RankingResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data).hasSize(2),
                () -> assertThat(data.get(0).rank()).isEqualTo(1L),
                () -> assertThat(data.get(0).productId()).isEqualTo(b.getId()),
                () -> assertThat(data.get(0).name()).isEqualTo("조던1"),
                () -> assertThat(data.get(1).rank()).isEqualTo(2L),
                () -> assertThat(data.get(1).productId()).isEqualTo(a.getId())
            );
        }

        @DisplayName("page/size로 페이징하면 해당 구간(저장된 rank 순)만 반환된다.")
        @Test
        void paginates_byStoredRank() {
            // arrange — rank 1,2,3
            ProductModel a = productJpaRepository.save(new ProductModel("A", "1위", 1000L, null));
            ProductModel b = productJpaRepository.save(new ProductModel("B", "2위", 1000L, null));
            ProductModel c = productJpaRepository.save(new ProductModel("C", "3위", 1000L, null));
            weeklyRepository.save(new WeeklyProductRankModel(a.getId(), 1, 9.0));
            weeklyRepository.save(new WeeklyProductRankModel(b.getId(), 2, 5.0));
            weeklyRepository.save(new WeeklyProductRankModel(c.getId(), 3, 1.0));

            // act — size=1, page=2 → rank 2 (b)
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange("/api/v1/rankings?period=weekly&page=2&size=1",
                    HttpMethod.GET, new HttpEntity<>(null), RESPONSE_TYPE);

            // assert
            List<RankingV1Dto.RankingResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(data).hasSize(1),
                () -> assertThat(data.get(0).productId()).isEqualTo(b.getId()),
                () -> assertThat(data.get(0).rank()).isEqualTo(2L)
            );
        }
    }

    @DisplayName("GET /api/v1/rankings — period 검증")
    @Nested
    class InvalidPeriod {

        @DisplayName("지원하지 않는 period면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenPeriodIsInvalid() {
            // act
            ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange("/api/v1/rankings?period=yearly",
                    HttpMethod.GET, new HttpEntity<>(null), RESPONSE_TYPE);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
