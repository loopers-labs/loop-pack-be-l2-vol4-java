package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        StockModel stock = stockService.getByProductId(id);
        return ProductInfo.from(product, brand, stock);
    }

    public Page<ProductInfo> getProducts(Long brandId, Pageable pageable) {
        Page<ProductModel> productPage = productService.findProducts(brandId, pageable);
        List<ProductInfo> infos = productInfoAssembler.toInfoList(productPage.getContent());
        return new PageImpl<>(infos, pageable, productPage.getTotalElements());
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.delete(id);
        stockService.delete(id);
    }

    public Page<ProductInfo> getProductsByBrandId(Long brandId, Pageable pageable) {
        BrandModel brand = brandService.getById(brandId);
        Page<ProductModel> productPage = productService.findAllByBrandId(brandId, pageable);
        Set<Long> productIds = productPage.getContent().stream().map(ProductModel::getId).collect(Collectors.toSet());
        Map<Long, StockModel> stockMap = stockService.getMapByProductIds(productIds);
        List<ProductInfo> infos = productPage.getContent().stream()
            .map(p -> ProductInfo.from(p, brand, stockMap.get(p.getId())))
            .toList();
        return new PageImpl<>(infos, pageable, productPage.getTotalElements());
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
