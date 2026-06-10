package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductDetailService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.SortOption;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품 + 브랜드 + 재고 조회 합성기. 고객/어드민 Facade가 공유한다.
 * 출력 변환(boolean available vs Integer quantity)만 각 Facade에서 처리.
 */
@RequiredArgsConstructor
@Component
public class ProductCompositionReader {

    private final ProductService productService;
    private final ProductDetailService productDetailService;
    private final StockService stockService;
    private final BrandService brandService;

    public ProductWithDeps getDetail(Long productId) {
        ProductDetail detail = productDetailService.getDetail(productId);
        StockModel stock = stockService.getByProductId(detail.product().getId());
        return new ProductWithDeps(detail.product(), detail.brand(), stock.getQuantity());
    }

    public Page<ProductWithDeps> search(Long brandId, SortOption sort, Pageable pageable) {
        Page<ProductModel> products = productService.search(brandId, sort, pageable);

        List<Long> productIds = products.getContent().stream().map(ProductModel::getId).toList();
        List<Long> brandIds = products.getContent().stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, Integer> quantities = stockService.getQuantities(productIds);
        Map<Long, BrandModel> brands = brandService.findAllByIds(brandIds).stream()
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));

        return products.map(p -> {
            BrandModel brand = brands.get(p.getBrandId());
            if (brand == null) {
                // 상품은 살아있는데 참조 brand가 사라진 상태 (예: soft-delete 비대칭) — 데이터 무결성 위반이므로 fail-fast.
                throw new CoreException(ErrorType.INTERNAL_ERROR,
                    "[productId=" + p.getId() + ", brandId=" + p.getBrandId() + "] 참조 브랜드를 찾을 수 없습니다.");
            }
            return new ProductWithDeps(p, brand, quantities.getOrDefault(p.getId(), 0));
        });
    }
}
