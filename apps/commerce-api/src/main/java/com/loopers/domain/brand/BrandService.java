package com.loopers.domain.brand;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional
    public BrandModel createBrand(String name, String description) {
        BrandModel brand = new BrandModel(name, description);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long brandId) {
        return brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<BrandModel> getBrands(int page, int size) {
        return brandRepository.findAll(page, size);
    }

    @Transactional
    public BrandModel updateBrand(Long brandId, String name, String description) {
        BrandModel brand = getBrand(brandId);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        BrandModel brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));

        List<ProductModel> products = productRepository.findAllByBrandId(brandId);
        for (ProductModel product : products) {
            product.delete();
            productRepository.save(product);
        }

        brand.delete();
        brandRepository.save(brand);
    }
}
