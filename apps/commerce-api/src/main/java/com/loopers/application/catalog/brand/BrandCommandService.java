package com.loopers.application.catalog.brand;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.application.catalog.product.ProductCacheRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class BrandCommandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductCacheRepository productCacheRepository;

    @Transactional
    public BrandResult create(BrandCommand.Create command) {
        Brand brand = new Brand(command.name(), command.description());
        BrandResult result = BrandResult.from(brandRepository.save(brand));
        productCacheRepository.evictLists();
        return result;
    }

    @Transactional
    public BrandResult update(Long brandId, BrandCommand.Update command) {
        Brand brand = brandRepository.find(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));

        brand.update(command.name(), command.description());
        BrandResult result = BrandResult.from(brandRepository.save(brand));
        productRepository.findByBrandId(brandId)
            .forEach(product -> productCacheRepository.evictDetail(product.getId()));
        productCacheRepository.evictLists();
        return result;
    }

    @Transactional
    public void delete(Long brandId) {
        Brand brand = brandRepository.find(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));

        brand.delete();
        brandRepository.save(brand);

        productRepository.findByBrandId(brandId)
            .forEach(this::stopProduct);
        productCacheRepository.evictLists();
    }

    private void stopProduct(Product product) {
        product.stopSelling();
        productRepository.save(product);
        productCacheRepository.evictDetail(product.getId());
    }
}
