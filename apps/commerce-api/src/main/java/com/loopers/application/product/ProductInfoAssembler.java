package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
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
    private final StockService stockService;
    private final ProductStatsService productStatsService;

    public List<ProductInfo> toInfoList(List<ProductModel> products) {
        Set<Long> brandIds = products.stream()
                .map(ProductModel::getBrandId)
                .collect(Collectors.toSet());
        Set<Long> productIds = products.stream()
                .map(ProductModel::getId)
                .collect(Collectors.toSet());
        Map<Long, BrandModel> brandMap = brandService.getMapByIds(brandIds);
        Map<Long, StockModel> stockMap = stockService.getMapByProductIds(productIds);
        Map<Long, ProductStatsModel> statsMap = productStatsService.getMapByProductIds(productIds);
        return products.stream()
                .map(product -> ProductInfo.from(
                    product,
                    brandMap.get(product.getBrandId()),
                    stockMap.get(product.getId()),
                    statsMap.get(product.getId())
                ))
                .toList();
    }
}
