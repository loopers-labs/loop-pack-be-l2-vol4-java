package com.loopers.application.product;

import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductDetailService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.SortOption;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final ProductDetailService productDetailService;
    private final StockService stockService;

    public ProductInfo getProductDetail(Long productId) {
        ProductDetail detail = productDetailService.getDetail(productId);
        StockModel stock = stockService.getByProductId(detail.product().getId());
        return ProductInfo.from(detail, stock.isAvailable());
    }

    public Page<ProductInfo> search(Long brandId, SortOption sort, Pageable pageable) {
        Page<ProductModel> products = productService.search(brandId, sort, pageable);
        List<Long> productIds = products.getContent().stream().map(ProductModel::getId).toList();
        Map<Long, Integer> quantities = stockService.getQuantities(productIds);
        return products.map(product ->
            ProductInfo.from(product, quantities.getOrDefault(product.getId(), 0) > 0)
        );
    }
}
