package com.loopers.domain.brand;

import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<BrandModel> getList(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional
    public BrandModel create(String name) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
        return brandRepository.save(new BrandModel(name));
    }

    @Transactional(readOnly = true)
    public BrandModel get(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional
    public BrandModel update(Long id, String name) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
        BrandModel brand = get(id);
        brand.update(name);
        return brand;
    }

    @Transactional
    public void delete(Long id) {
        BrandModel brand = get(id);
        brand.delete();
        productRepository.suspendAllByBrandId(id);
    }
}
