package com.loopers.brand.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandModel create(String name, String description) {
        return brandRepository.save(new BrandModel(name, description));
    }

    public BrandModel get(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    public List<BrandModel> getAll() {
        return brandRepository.findAll();
    }

    public Map<Long, BrandModel> getMapByIds(Collection<Long> ids) {
        return brandRepository.findAllByIds(ids).stream()
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
    }

    public BrandModel update(Long id, String name, String description) {
        BrandModel brand = get(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    public void delete(Long id) {
        BrandModel brand = get(id);
        brand.delete();
        brandRepository.save(brand);
    }

    public void ensureExists(Long id) {
        if (!brandRepository.existsById(id)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다.");
        }
    }
}
