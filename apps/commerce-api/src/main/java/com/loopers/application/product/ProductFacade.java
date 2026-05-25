package com.loopers.application.product;

import com.loopers.application.brand.BrandFinder;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductDetailAssembler;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.SortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final BrandFinder brandFinder;
    private final ProductService productService;
    private final ProductFinder productFinder;

    public ProductInfo createProduct(Long brandId, String name, BigDecimal price, Long stock) {
        BrandModel brand = brandFinder.getById(brandId);
        ProductModel product = productService.create(brandId, name, price, stock);
        return ProductInfo.from(ProductDetailAssembler.assemble(product, brand));
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productFinder.getById(id);
        BrandModel brand = brandFinder.getById(product.getBrandId());
        return ProductInfo.from(ProductDetailAssembler.assemble(product, brand));
    }

    public List<ProductInfo> getAllProducts(SortType sortType) {
        List<ProductModel> products = productFinder.getAll(sortType);

        Set<Long> brandIds = products.stream()
                .map(ProductModel::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, BrandModel> brandMap = brandFinder.getMapByIds(brandIds);

        return ProductDetailAssembler.assembleAll(products, brandMap).stream()
                .map(ProductInfo::from)
                .toList();
    }

    public ProductInfo updateProduct(Long id, String name, BigDecimal price, Long stock) {
        ProductModel product = productService.update(id, name, price, stock);
        BrandModel brand = brandFinder.getById(product.getBrandId());
        return ProductInfo.from(ProductDetailAssembler.assemble(product, brand));
    }

    public void deleteProduct(Long id) {
        productService.delete(id);
    }
}
