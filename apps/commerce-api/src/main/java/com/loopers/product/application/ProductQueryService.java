package com.loopers.product.application;

import com.loopers.brand.application.BrandReader;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.product.domain.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductQueryService {

    private final BrandReader brandReader;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;

    @Transactional(readOnly = true)
    public ProductResult.Detail getProduct(Long productId) {
        Product product = get(productId);
        int stockQuantity = getStock(productId).getQuantity();
        String brandName = brandReader.getName(product.getBrandId());
        return ProductResult.Detail.from(product, brandName, stockQuantity);
    }

    @Transactional(readOnly = true)
    public ProductResult.Page getProducts(ProductCommand.PageQuery query) {
        long offset = (long) query.page() * query.size();
        List<Product> products = productRepository.findAllOnSale(query.brandId(), query.sort(), offset, query.size());
        long totalCount = productRepository.countOnSale(query.brandId());

        List<ProductResult.Detail> content = products.isEmpty() ? List.of() : assembleDetails(products);
        return new ProductResult.Page(content, totalCount, query.page(), query.size());
    }

    private List<ProductResult.Detail> assembleDetails(List<Product> products) {
        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();

        Map<Long, Integer> stockByProductId = productStockRepository.findAllByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductStock::getProductId, ProductStock::getQuantity));
        Map<Long, String> brandNameById = brandReader.getNames(brandIds);

        return products.stream()
                .map(product -> {
                    if (!stockByProductId.containsKey(product.getId()) || !brandNameById.containsKey(product.getBrandId())) {
                        log.warn("상품 목록 보조 데이터 누락 productId={} brandId={} stockMissing={} brandMissing={}",
                                product.getId(), product.getBrandId(),
                                !stockByProductId.containsKey(product.getId()),
                                !brandNameById.containsKey(product.getBrandId()));
                    }
                    return ProductResult.Detail.from(
                            product,
                            brandNameById.get(product.getBrandId()),
                            stockByProductId.getOrDefault(product.getId(), 0));
                })
                .toList();
    }

    private Product get(Long productId) {
        return productRepository.findActiveById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductStock getStock(Long productId) {
        return productStockRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.STOCK_NOT_FOUND));
    }
}
