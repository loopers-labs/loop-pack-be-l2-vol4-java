package com.loopers.domain.brand;

import com.loopers.domain.product.ProductModel;

import java.util.List;

public class BrandService {

    public void deleteBrandWithProducts(BrandModel brand, List<ProductModel> products) {
        brand.delete();
        products.forEach(ProductModel::delete);
    }
}
