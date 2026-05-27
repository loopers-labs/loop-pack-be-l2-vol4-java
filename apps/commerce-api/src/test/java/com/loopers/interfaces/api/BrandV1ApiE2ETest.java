package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class BrandV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/brands";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveBrand(String name, String description) {
        return brandRepository.save(BrandModel.of(BrandName.of(name), BrandDescription.of(description)));
    }

    private List<BrandV1Dto.BrandResponse> readContent(MvcResult mvcResult) throws Exception {
        ApiResponse<JsonNode> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        JsonNode content = response.data().get("content");
        return objectMapper.convertValue(content, new TypeReference<>() {});
    }

    @DisplayName("GET /api/v1/brands")
    @Nested
    class GetBrands {

        @DisplayName("keyword 없이 요청하면, 삭제되지 않은 전체 브랜드를 이름 오름차순으로 반환한다.")
        @Test
        void returnsAllNotDeleted_orderedByName() throws Exception {
            // given
            saveBrand("나이키", "스포츠 브랜드");
            saveBrand("아디다스", "독일 스포츠 브랜드");
            saveBrand("가나다", "기타 브랜드");
            BrandModel deleted = saveBrand("삭제됨", "삭제될 브랜드");
            deleted.delete();
            brandRepository.save(deleted);

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)
                                         .param("page", "0")
                                         .param("size", "10"))
                                         .andReturn();

            // then
            List<BrandV1Dto.BrandResponse> content = readContent(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(content).hasSize(3),
                    () -> assertThat(content).extracting(BrandV1Dto.BrandResponse::name)
                            .containsExactly("가나다", "나이키", "아디다스")
            );
        }

        @DisplayName("keyword를 주면, 이름에 부분 일치하는 브랜드만 반환한다.")
        @Test
        void returnsFilteredByName_whenQueryProvided() throws Exception {
            // given
            saveBrand("나이키", "스포츠 브랜드");
            saveBrand("나이스원", "기타 브랜드");
            saveBrand("아디다스", "독일 스포츠 브랜드");

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)
                                         .param("keyword", "나이")
                                         .param("page", "0")
                                         .param("size", "10"))
                                         .andReturn();

            // then
            List<BrandV1Dto.BrandResponse> content = readContent(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(content).hasSize(2),
                    () -> assertThat(content).extracting(BrandV1Dto.BrandResponse::name)
                            .containsExactly("나이스원", "나이키")
            );
        }

        @DisplayName("일치하는 브랜드가 없으면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmpty_whenNoMatch() throws Exception {
            // given
            saveBrand("나이키", "스포츠 브랜드");

            // when
            MvcResult mvcResult = mockMvc.perform(get(ENDPOINT)
                                         .param("keyword", "zzz")
                                         .param("page", "0")
                                         .param("size", "10"))
                                         .andReturn();

            // then
            List<BrandV1Dto.BrandResponse> content = readContent(mvcResult);
            assertAll(
                    () -> assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(content).isEmpty()
            );
        }
    }
}
