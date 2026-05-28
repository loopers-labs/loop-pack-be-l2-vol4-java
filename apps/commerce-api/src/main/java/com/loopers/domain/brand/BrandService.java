package com.loopers.domain.brand;

import com.loopers.domain.common.PageCriteria;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public BrandModel createBrand(String name, String description) {
        return brandRepository.save(new BrandModel(name, description));
    }

    public BrandModel getBrand(Long id) {
        return getBrandModel(id);
    }

    public List<BrandModel> getBrands(Integer page, Integer size) {
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        return brandRepository.findAll(pageCriteria.page(), pageCriteria.size());
    }

    public BrandModel updateBrand(Long id, String name, String description) {
        BrandModel brand = getBrandModel(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    public void deleteBrand(Long id) {
        BrandModel brand = getBrandModel(id);
        List<ProductModel> products = productRepository.findAllByBrandId(id);
        deleteBrandWithProducts(brand, products);
        brandRepository.save(brand);
        products.forEach(productRepository::save);
    }

    public void deleteBrandWithProducts(BrandModel brand, List<ProductModel> products) {
        brand.delete();
        products.forEach(ProductModel::delete);
    }

    private BrandModel getBrandModel(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }
}
