package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public BrandModel getById(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<BrandModel> findAllByIds(Collection<Long> ids) {
        return brandRepository.findAllByIds(ids);
    }

    @Transactional(readOnly = true)
    public Page<BrandModel> search(Pageable pageable) {
        return brandRepository.search(pageable);
    }
}
