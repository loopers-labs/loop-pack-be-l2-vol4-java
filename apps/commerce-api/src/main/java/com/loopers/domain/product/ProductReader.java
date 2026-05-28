package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.PageCriteria;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductReader {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductBrandProcessor productBrandProcessor;

    public ProductDetail getProduct(Long id) {
        ProductModel product = getProductModel(id);
        BrandModel brand = getBrand(product.getBrandId());
        return productBrandProcessor.getProductDetail(product, brand);
    }

    public List<ProductDetail> getAllProducts(Long brandId, String sort, Integer page, Integer size) {
        if (brandId != null) {
            getBrand(brandId);
        }

        ProductSort productSort = ProductSort.from(sort);
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        List<ProductModel> products = brandId == null
            ? productRepository.findAll(productSort, pageCriteria.page(), pageCriteria.size())
            : productRepository.findAllByBrandId(brandId, productSort, pageCriteria.page(), pageCriteria.size());

        List<Long> brandIds = productBrandProcessor.getBrandIds(products);
        List<BrandModel> brands = brandRepository.findAllByIds(brandIds);
        return productBrandProcessor.getProductDetails(products, brands);
    }

    ProductModel getProductModel(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    BrandModel getBrand(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }
}
