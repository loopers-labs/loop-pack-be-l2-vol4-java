package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final StockService stockService;
    private final BrandService brandService;

    @Transactional
    public ProductInfo createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        brandService.getActive(brandId);
        ProductModel product = productService.createProduct(name, description, price, brandId);
        StockModel stockModel = stockService.create(product.getId(), stock);
        return ProductInfo.from(product, stockModel);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        StockModel stockModel = stockService.getByProductId(id);
        return ProductInfo.from(product, stockModel);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(ProductSortType sort, int page, int size) {
        List<ProductModel> products = productService.getAllProducts(sort, page, size);
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        Map<Long, StockModel> stockByProductId = stockService.getAllByProductIdIn(productIds).stream()
            .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));
        return products.stream()
            .map(product -> ProductInfo.from(product, stockByProductId.get(product.getId())))
            .toList();
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, price);
        StockModel stockModel = stockService.getByProductId(id);
        stockModel.changeTo(stock);
        return ProductInfo.from(product, stockModel);
    }

    @Transactional
    public void deleteProduct(Long id) {
        stockService.deleteByProductId(id);
        productService.deleteProduct(id);
    }
}
