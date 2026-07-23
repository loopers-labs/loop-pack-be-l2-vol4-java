package com.loopers.tddstudy.interfaces.api.product;

import com.loopers.tddstudy.application.product.ProductService;
import com.loopers.tddstudy.application.ranking.RankingService;
import com.loopers.tddstudy.domain.product.Product;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final ProductService productService;
    private final RankingService rankingService;

    public ProductV1Controller(ProductService productService, RankingService rankingService) {
        this.productService = productService;
        this.rankingService = rankingService;
    }

    // GET /api/v1/products/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ProductV1Dto.ProductDetailResponse> getProduct(
            @PathVariable Long id,
            @RequestHeader("X-USER-ID") Long userId,
            @RequestHeader(value = "X-USER-ROLE", required = false, defaultValue = "USER") String userRole
    ) {
        Product product = productService.getById(id, userId, userRole);        // 캐시 O (상품 정보)
        Long rank = rankingService.getRank(LocalDate.now(ZONE), id);           // 캐시 X (오늘 순위, 매번)

        return ResponseEntity.ok(ProductV1Dto.ProductDetailResponse.of(product, rank));
    }
}
