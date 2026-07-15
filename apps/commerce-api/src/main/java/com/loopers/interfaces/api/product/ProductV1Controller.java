package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.StockInfo;
import com.loopers.infrastructure.ranking.RankingRedisStore;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자용 상품 API. 조회만 제공한다.
 * 등록/수정/삭제는 ProductAdminV1Controller에서 처리한다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductApplicationService productApplicationService;
    private final RankingRedisStore rankingRedisStore;

    @GetMapping
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        List<ProductInfo> infos = productApplicationService.getProducts(brandId, sort, page, size);
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProductDetail(
        @PathVariable Long productId
    ) {
        ProductInfo info = productApplicationService.getProductDetail(productId);
        // 랭킹은 항상 최신값이어야 하므로 상세 캐시와 분리해 매 요청 실시간 조회 후 병합한다.
        Integer rank = rankingRedisStore.findLiveRank(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info, rank));
    }

    /**
     * 재고만 조회하는 경량 엔드포인트 — 대기열 통과 후 품절 여부 확인용.
     * 상세 조회의 부가 작업(브랜드/좋아요 조립, 캐시, 조회 이벤트 발행)을 태우지 않는다.
     */
    @GetMapping("/{productId}/stock")
    public ApiResponse<ProductV1Dto.StockResponse> getStock(
        @PathVariable Long productId
    ) {
        StockInfo info = productApplicationService.getStock(productId);
        return ApiResponse.success(ProductV1Dto.StockResponse.from(info));
    }
}
