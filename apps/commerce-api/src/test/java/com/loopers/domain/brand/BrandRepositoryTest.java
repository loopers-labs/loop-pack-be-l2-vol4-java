package com.loopers.domain.brand;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BrandRepositoryTest {

    @Autowired private BrandRepository brandRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String DEFAULT_NAME = "나이키";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("중복된 이름으로 저장하면, DataIntegrityViolationException 이 발생한다.")
    @Test
    void throwsException_whenDuplicateNameIsInserted() {
        brandRepository.save(new BrandModel(DEFAULT_NAME));

        assertThrows(DataIntegrityViolationException.class, () ->
                brandRepository.save(new BrandModel(DEFAULT_NAME))
        );
    }
}
