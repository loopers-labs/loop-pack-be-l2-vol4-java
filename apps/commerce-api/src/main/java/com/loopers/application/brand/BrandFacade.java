package com.loopers.application.brand;

import com.loopers.application.common.PageCriteria;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final BrandService brandService = new BrandService();

    @Transactional
    public BrandInfo createBrand(String name, String description) {
        BrandModel brand = brandRepository.save(new BrandModel(name, description));
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long id) {
        return BrandInfo.from(getBrandModel(id));
    }

    @Transactional(readOnly = true)
    public List<BrandInfo> getBrands(Integer page, Integer size) {
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        return brandRepository.findAll(pageCriteria.page(), pageCriteria.size()).stream()
            .map(BrandInfo::from)
            .toList();
    }

    @Transactional
    public BrandInfo updateBrand(Long id, String name, String description) {
        BrandModel brand = getBrandModel(id);
        brand.update(name, description);
        return BrandInfo.from(brandRepository.save(brand));
    }

    @Transactional
    public void deleteBrand(Long id) {
        BrandModel brand = getBrandModel(id);
        List<ProductModel> products = productRepository.findAllByBrandId(id);
        brandService.deleteBrandWithProducts(brand, products);
        brandRepository.save(brand);
        products.forEach(productRepository::save);
    }

    private BrandModel getBrandModel(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }
}
