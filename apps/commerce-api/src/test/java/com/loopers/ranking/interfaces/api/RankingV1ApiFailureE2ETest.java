package com.loopers.ranking.interfaces.api;

import com.loopers.product.application.ProductListQuery;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.DailyRankingEntries;
import com.loopers.ranking.application.DailyRankingQuery;
import com.loopers.ranking.application.PublishedRankingQuery;
import com.loopers.shared.presentation.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiFailureE2ETest {

    private static final String ENDPOINT_RANKINGS = "/api/v1/rankings?date=20990714";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private DailyRankingQuery dailyRankingQuery;

    @MockitoBean
    private PublishedRankingQuery publishedRankingQuery;

    @MockitoBean
    private ProductListQuery productListQuery;

    @Autowired
    RankingV1ApiFailureE2ETest(
        TestRestTemplate testRestTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("Redis Ranking 조회에 실패하면 503과 재시도 안내를 반환한다")
        @Test
        void returnsServiceUnavailable_whenRedisRankingLookupFails() {
            // arrange
            when(dailyRankingQuery.findDaily(any(LocalDate.class), anyLong(), anyLong()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_RANKINGS,
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            ApiResponse<Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE),
                () -> assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5"),
                () -> assertThat(body.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(body.meta().errorCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase()),
                () -> assertThat(body.data()).isNull()
            );
        }

        @DisplayName("공개 Snapshot 조회에 실패하면 503과 재시도 안내를 반환한다")
        @Test
        void returnsServiceUnavailable_whenPublishedRankingLookupFails() {
            // arrange
            when(publishedRankingQuery.findLatestCompleted(
                RankingPeriod.WEEKLY,
                LocalDate.of(2099, 7, 14)
            )).thenThrow(new DataRetrievalFailureException("database down"));

            // act
            ResponseEntity<ApiResponse<Object>> response = requestRankings(
                ENDPOINT_RANKINGS + "&period=WEEKLY"
            );

            // assert
            assertServiceUnavailable(response);
        }

        @DisplayName("상품 정보 보강에 실패하면 503과 재시도 안내를 반환한다")
        @Test
        void returnsServiceUnavailable_whenProductEnrichmentFails() {
            // arrange
            when(dailyRankingQuery.findDaily(any(LocalDate.class), anyLong(), anyLong()))
                .thenReturn(new DailyRankingEntries(List.of(101L), 1));
            when(productListQuery.findVisibleProductsByIds(List.of(101L)))
                .thenThrow(new DataRetrievalFailureException("database down"));

            // act
            ResponseEntity<ApiResponse<Object>> response = requestRankings(ENDPOINT_RANKINGS);

            // assert
            assertServiceUnavailable(response);
        }
    }

    private ResponseEntity<ApiResponse<Object>> requestRankings(String endpoint) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(endpoint, HttpMethod.GET, null, responseType);
    }

    private void assertServiceUnavailable(ResponseEntity<ApiResponse<Object>> response) {
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE),
            () -> assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5"),
            () -> assertThat(body.meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
            () -> assertThat(body.meta().errorCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase()),
            () -> assertThat(body.data()).isNull()
        );
    }
}
