package com.loopers.interfaces.api;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberRepository;
import com.loopers.interfaces.api.member.MemberV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MemberV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/member";

    private final TestRestTemplate testRestTemplate;
    private final MemberRepository memberRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            MemberRepository memberRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberRepository = memberRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/member")
    @Nested
    class Create {

        @DisplayName("형식에 맞는 맴버 Request 가 들어오면, 맴버를 생성하고 Response Ok 와 맴버info 를 반환한다.")
        @Test
        void createMember_whenMemberInfoProvided() {
            // arrange
            MemberV1Dto.CreateMemberRequest request = new MemberV1Dto.CreateMemberRequest(
                    "testuser", "Password1!", "test@example.com", "김테스트", "19940201"
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.CreateMemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.CreateMemberResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().userId()).isEqualTo(request.userId()),
                    () -> assertThat(response.getBody().data().userName()).isEqualTo(request.userName())
            );
        }

        @DisplayName("이미 존재하는 userId 로 가입을 시도하면,Conflict 를 반환한다.")
        @Test
        void throwsConflict_whenDuplicateUserIdProvided() {
            // arrange
            memberRepository.save(new MemberModel("testuser", "Password1!", "test@example.com", "김테스트", "19940201"));
            MemberV1Dto.CreateMemberRequest request = new MemberV1Dto.CreateMemberRequest(
                    "testuser", "Password2@", "other@example.com", "박테스트", "19950301"
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.CreateMemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.CreateMemberResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        }
    }

    @DisplayName("GET /api/v1/member/{id}")
    @Nested
    class Get {

        @DisplayName("맴버의 id를 주면, 해당 맴버의 로그인ID, 이름(마지막 글자를 마스킹 *), 생년월일, 이메일 을 반환한다.")
        @Test
        void returnMemberInfo_whenValidIdIsProvided() {
            // arrange
            MemberModel member = memberRepository.save(
                    new MemberModel("testuser", "Password1!", "test@example.com", "김테스트", "19940201")
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberInfoResponse>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + member.getId(), HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().userId()).isEqualTo(member.getUserId()),
                    () -> assertThat(response.getBody().data().userName()).isEqualTo(member.getMaskedUsername()),
                    () -> assertThat(response.getBody().data().birthDate()).isEqualTo(member.getBirthDate()),
                    () -> assertThat(response.getBody().data().email()).isEqualTo(member.getEmail())
            );
        }

        @DisplayName("존재하지 않는 id 를 주면, 404 Not Found 를 반환한다.")
        @Test
        void throwsNotFound_whenInvalidIdIsProvided() {
            // arrange
            Long invalidId = -1L;

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberInfoResponse>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + invalidId, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }

    @DisplayName("PATCH /api/v1/member/{id}")
    @Nested
    class Update {

        @DisplayName("유효한 새로운 비밀번호가 들어오면, 비밀번호를 변경하고 Response Ok 를 반환한다.")
        @Test
        void updateMemberPassword_whenValidPasswordIsProvided() {
            // arrange
            MemberModel member = memberRepository.save(
                    new MemberModel("testuser", "Password1!", "test@example.com", "김테스트", "19940201")
            );
            MemberV1Dto.UpdatePasswordRequest request = new MemberV1Dto.UpdatePasswordRequest("Password1!", "NewPass2@");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + member.getId(), HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, 400 Bad Request 를 반환한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordIsWrong() {
            // arrange
            MemberModel member = memberRepository.save(
                    new MemberModel("testuser", "Password1!", "test@example.com", "김테스트", "19940201")
            );
            MemberV1Dto.UpdatePasswordRequest request = new MemberV1Dto.UpdatePasswordRequest("WrongPass1!", "NewPass2@");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + member.getId(), HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }
    }
}
