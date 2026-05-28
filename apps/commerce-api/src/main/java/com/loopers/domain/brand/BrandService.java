package com.loopers.domain.brand;

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

    @Transactional
    public BrandEntity create(String name, String description) {
        brandRepository.findByName(name).ifPresent(b -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        });
        return brandRepository.save(new BrandEntity(name, description));
    }

    @Transactional(readOnly = true)
    public BrandEntity getBrand(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<BrandEntity> getBrands(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional
    public BrandEntity update(Long id, String name, String description) {
        BrandEntity brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

        brandRepository.findByName(name)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
                });

        brand.update(name, description);
        return brandRepository.save(brand);
    }

    @Transactional
    public void delete(Long id) {
        BrandEntity brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        brand.delete();
        brandRepository.save(brand);
    }
}
