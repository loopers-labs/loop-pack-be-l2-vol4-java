package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public Page<BrandModel> getList(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional
    public BrandModel create(String name) {
        validateDuplicateName(name);
        return brandRepository.save(new BrandModel(name));
    }

    @Transactional(readOnly = true)
    public List<BrandModel> getByIds(List<Long> ids) {
        return brandRepository.findAllByIds(ids);
    }

    @Transactional(readOnly = true)
    public BrandModel get(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional
    public BrandModel update(Long id, String name) {
        BrandModel brand = get(id);
        if (brandRepository.existsByNameAndIdNot(name, id)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
        brand.update(name);
        return brand;
    }

    private void validateDuplicateName(String name) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
    }

    @Transactional
    public void delete(Long id) {
        BrandModel brand = get(id);
        brand.delete();
    }
}
