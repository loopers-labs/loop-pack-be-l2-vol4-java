package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.SortType;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final BrandService brandService;
    private final ProductService productService;
    private final StockService stockService;
    private final ProductInfoAssembler productInfoAssembler;

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getById(id);
        BrandModel brand = brandService.getById(product.getBrandId());
        return ProductInfo.from(product, brand);
    }

    public List<ProductInfo> getAllProducts(SortType sortType) {
        List<ProductModel> products = productService.findAll(sortType);
        return productInfoAssembler.toInfoList(products);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.delete(id);
        stockService.delete(id);
    }

    public Page<ProductInfo> getProductsByBrandId(Long brandId, Pageable pageable) {
        BrandModel brand = brandService.getById(brandId);
        return productService.findAllByBrandId(brandId, pageable)
            .map(p -> ProductInfo.from(p, brand));
    }

    public ProductAdminInfo getProductForAdmin(Long id) {
        ProductModel product = productService.getById(id);
        BrandModel brand = brandService.getById(product.getBrandId());
        StockModel stock = stockService.getByProductId(id);
        return ProductAdminInfo.from(product, brand, stock);
    }

    @Transactional
    public ProductAdminInfo createProductForAdmin(Long brandId, String name, BigDecimal price, Long stockQuantity) {
        BrandModel brand = brandService.getById(brandId);
        ProductModel product = productService.create(brandId, name, price);
        StockModel stock = stockService.create(product.getId(), stockQuantity);
        return ProductAdminInfo.from(product, brand, stock);
    }

    @Transactional
    public ProductAdminInfo updateProductForAdmin(Long id, String name, BigDecimal price, Long stockQuantity) {
        ProductModel product = productService.update(id, name, price);
        StockModel stock = stockService.update(id, stockQuantity);
        BrandModel brand = brandService.getById(product.getBrandId());
        return ProductAdminInfo.from(product, brand, stock);
    }
}
