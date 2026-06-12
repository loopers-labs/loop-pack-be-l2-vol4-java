package com.loopers.tddstudy.application.brand;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.brand.BrandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.loopers.tddstudy.application.product.ProductService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductService productService;

    public BrandService(BrandRepository brandRepository, ProductService productService) {
        this.brandRepository = brandRepository;
        this.productService = productService;
    }

    public Brand create(String name, String description, Long ownerId) {
        Brand brand = new Brand(name, description, ownerId);
        return brandRepository.save(brand);
    }

    public void publish(Long id) {
        Brand brand = findOrThrow(id);
        brand.publish();
        brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public Brand getById(Long id, Long userId, String userRole) {
        Brand brand = findOrThrow(id);
        if (!brand.isVisibleTo(userId, userRole)) {
            throw new IllegalArgumentException("브랜드를 찾을 수 없습니다.");
        }
        return brand;
    }

    @Transactional(readOnly = true)
    public List<Brand> getAll(Long userId, String userRole) {
        return brandRepository.findAll().stream()
                .filter(brand -> brand.isVisibleTo(userId, userRole))
                .collect(Collectors.toList());
    }

    public void update(Long id, String name, String description) {
        Brand brand = findOrThrow(id);
        brand.update(name, description);
        brandRepository.save(brand);
    }

    public void delete(Long id) {
        Brand brand = findOrThrow(id);
        productService.deleteAllByBrandId(id);
        brand.softDelete();
        brandRepository.save(brand);
    }

    private Brand findOrThrow(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("브랜드를 찾을 수 없습니다."));
    }
}
