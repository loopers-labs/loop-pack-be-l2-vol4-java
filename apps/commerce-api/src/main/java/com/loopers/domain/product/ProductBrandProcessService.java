package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductBrandProcessService {

    public ProductDetailView getProductDetailView(Product product, Brand brand) {
        return new ProductDetailView(product, brand);
    }

    public List<Long> getBrandIds(List<Product> products) {
        return products.stream()
            .map(Product::getBrandId)
            .distinct()
            .toList();
    }

    public List<ProductDetailView> getProductDetailViews(List<Product> products, List<Brand> brands) {
        Map<Long, Brand> brandsById = brands.stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));

        return products.stream()
            .map(product -> getProductDetailView(product, getBrand(product, brandsById)))
            .toList();
    }

    private Brand getBrand(Product product, Map<Long, Brand> brandsById) {
        Brand brand = brandsById.get(product.getBrandId());
        if (brand == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다.");
        }
        return brand;
    }
}
