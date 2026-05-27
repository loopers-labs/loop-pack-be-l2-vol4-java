package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductInfoAssembler {

    private final BrandService brandService;

    public List<ProductInfo> toInfoList(List<ProductModel> products) {
        Set<Long> brandIds = products.stream()
                .map(ProductModel::getBrandId)
                .collect(Collectors.toSet());
        Map<Long, BrandModel> brandMap = brandService.getMapByIds(brandIds);
        return products.stream()
                .map(product -> ProductInfo.from(product, brandMap.get(product.getBrandId())))
                .toList();
    }
}
