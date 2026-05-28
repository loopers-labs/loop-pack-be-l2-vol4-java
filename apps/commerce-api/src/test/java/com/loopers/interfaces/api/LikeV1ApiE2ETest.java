package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LikeV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products/{productId}/likes";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Nike", null));
        ProductModel product = productRepository.save(
                new ProductModel(brand.getId(), "운동화", null, Money.of(1000L), Quantity.of(10), null));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Void> withUser(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Loopers-UserId", String.valueOf(userId));
        return new HttpEntity<>(headers);
    }

    @DisplayName("좋아요를 두 번 등록해도 likeCount 는 1 이다. (멱등)")
    @Test
    void likeIsIdempotent() {
        ParameterizedTypeReference<ApiResponse<Object>> type = new ParameterizedTypeReference<>() {};

        testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, withUser(1L), type, productId);
        ResponseEntity<ApiResponse<Object>> second =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, withUser(1L), type, productId);

        assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(productRepository.findById(productId).get().getLikeCount()).isEqualTo(1);
    }

    @DisplayName("좋아요 취소 후 likeCount 는 0 이다.")
    @Test
    void unlikeDecreasesCount() {
        ParameterizedTypeReference<ApiResponse<Object>> type = new ParameterizedTypeReference<>() {};

        testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, withUser(1L), type, productId);
        testRestTemplate.exchange(ENDPOINT, HttpMethod.DELETE, withUser(1L), type, productId);

        assertThat(productRepository.findById(productId).get().getLikeCount()).isEqualTo(0);
    }
}
