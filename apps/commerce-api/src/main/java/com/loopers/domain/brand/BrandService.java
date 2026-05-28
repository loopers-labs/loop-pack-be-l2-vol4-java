package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandModel create(BrandModel brand) {
        if (brandRepository.existsByName(brand.getName())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 브랜드 이름입니다.");
        }
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public BrandModel getById(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[brandId = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<BrandModel> getAll(PageRequest pageRequest) {
        return brandRepository.findAll(pageRequest);
    }

    @Transactional
    public BrandModel update(Long id, String name) {
        BrandModel brand = brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[brandId = " + id + "] 브랜드를 찾을 수 없습니다."));

        if (!brand.getName().equals(name) && brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 브랜드 이름입니다.");
        }
        brand.update(name);
        return brand;
    }

    @Transactional
    public void delete(Long id) {
        BrandModel brand = brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[brandId = " + id + "] 브랜드를 찾을 수 없습니다."));
        brand.delete();
    }
}
