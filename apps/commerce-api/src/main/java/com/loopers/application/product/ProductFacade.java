package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        BrandModel brand = brandService.getActive(brandId);
        ProductModel product = productService.createProduct(name, description, price, brandId);
        StockModel stockModel = stockService.create(product.getId(), stock);
        return ProductInfo.from(product, stockModel.isAvailable(), brand);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getActive(id);
        StockModel stockModel = stockService.getByProductId(id);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, stockModel.isAvailable(), brand);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(Long brandId, ProductSortType sort, int page, int size) {
        List<ProductModel> products = productService.getAllActiveProducts(brandId, sort, page, size);
        Map<Long, StockModel> stockByProductId = stockByProductId(products);
        Map<Long, BrandModel> brandById = brandById(products);
        return products.stream()
            .map(product -> ProductInfo.from(
                product,
                stockByProductId.get(product.getId()).isAvailable(),
                brandById.get(product.getBrandId())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public ProductAdminInfo getProductForAdmin(Long id) {
        ProductModel product = productService.getProduct(id);
        StockModel stockModel = stockService.getByProductId(id);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductAdminInfo.from(product, stockModel, brand);
    }

    @Transactional(readOnly = true)
    public Page<ProductAdminInfo> getAllProductsForAdmin(Long brandId, int page, int size) {
        Page<ProductModel> products = productService.getAllProducts(brandId, PageRequest.of(page, size));
        Map<Long, StockModel> stockByProductId = stockByProductId(products.getContent());
        Map<Long, BrandModel> brandById = brandById(products.getContent());
        return products.map(product -> ProductAdminInfo.from(
            product,
            stockByProductId.get(product.getId()),
            brandById.get(product.getBrandId())
        ));
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, price);
        StockModel stockModel = stockService.getByProductId(id);
        stockModel.changeTo(stock);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, stockModel.isAvailable(), brand);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }

    private Map<Long, StockModel> stockByProductId(List<ProductModel> products) {
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        return stockService.getAllByProductIdIn(productIds).stream()
            .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));
    }

    private Map<Long, BrandModel> brandById(List<ProductModel> products) {
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).toList();
        return brandService.getAllByIdIn(brandIds).stream()
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
    }
}
