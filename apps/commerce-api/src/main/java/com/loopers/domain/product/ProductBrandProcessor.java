package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductBrandProcessor {

    public ProductDetailView getProductDetailView(ProductModel product, BrandModel brand) {
        return new ProductDetailView(product, brand);
    }

    public List<Long> getBrandIds(List<ProductModel> products) {
        return products.stream()
            .map(ProductModel::getBrandId)
            .distinct()
            .toList();
    }

    public List<ProductDetailView> getProductDetailViews(List<ProductModel> products, List<BrandModel> brands) {
        Map<Long, BrandModel> brandsById = brands.stream()
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));

        return products.stream()
            .map(product -> getProductDetailView(product, getBrand(product, brandsById)))
            .toList();
    }

    private BrandModel getBrand(ProductModel product, Map<Long, BrandModel> brandsById) {
        BrandModel brand = brandsById.get(product.getBrandId());
        if (brand == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다.");
        }
        return brand;
    }
}
