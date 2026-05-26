package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.List;
import java.util.Map;

public class ProductCatalogService {

    public ProductDetail getProductDetail(ProductModel product, BrandModel brand) {
        return new ProductDetail(product, brand);
    }

    public List<ProductDetail> getProductDetails(List<ProductModel> products, Map<Long, BrandModel> brandsById) {
        return products.stream()
            .map(product -> getProductDetail(product, findBrand(product, brandsById)))
            .toList();
    }

    private BrandModel findBrand(ProductModel product, Map<Long, BrandModel> brandsById) {
        BrandModel brand = brandsById.get(product.getBrandId());
        if (brand == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다.");
        }
        return brand;
    }
}
