package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "회원 가입, 내 정보 조회, 비밀번호 변경 API")
public interface UserV1ApiSpec {

    String EXAMPLE_400_PASSWORD_POLICY = """
        {"meta":{"result":"FAIL","errorCode":"Bad Request","message":"비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 사용할 수 있습니다."},"data":null}
        """;
    String EXAMPLE_400_EMAIL_FORMAT = """
        {"meta":{"result":"FAIL","errorCode":"Bad Request","message":"이메일 형식이 올바르지 않습니다."},"data":null}
        """;
    String EXAMPLE_400_PASSWORD_SAME_AS_CURRENT = """
        {"meta":{"result":"FAIL","errorCode":"Bad Request","message":"새 비밀번호는 현재 비밀번호와 같을 수 없습니다."},"data":null}
        """;
    String EXAMPLE_401_UNAUTHENTICATED = """
        {"meta":{"result":"FAIL","errorCode":"Unauthorized","message":"인증이 필요합니다."},"data":null}
        """;
    String EXAMPLE_401_PASSWORD_MISMATCH = """
        {"meta":{"result":"FAIL","errorCode":"Unauthorized","message":"현재 비밀번호가 일치하지 않습니다."},"data":null}
        """;
    String EXAMPLE_409_DUPLICATE_LOGIN_ID = """
        {"meta":{"result":"FAIL","errorCode":"Conflict","message":"이미 가입된 로그인 ID 입니다."},"data":null}
        """;

    @Operation(
        summary = "회원 가입",
        description = """
            신규 회원을 가입시킵니다. 인증이 필요 없는 공개 엔드포인트입니다.

            검증 규칙:
            - 로그인 ID: 영문/숫자만 사용
            - 비밀번호: 8~16자, 영문 대소문자/숫자/특수문자 사용 가능, 생년월일 포함 불가
            - 이메일: `xx@yy.zz` 형식
            - 이름, 생년월일: 필수
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "입력 값 검증 실패",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "비밀번호 정책 위반", value = EXAMPLE_400_PASSWORD_POLICY),
                @ExampleObject(name = "이메일 형식 오류", value = EXAMPLE_400_EMAIL_FORMAT)
            })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 가입된 로그인 ID",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "중복 로그인 ID", value = EXAMPLE_409_DUPLICATE_LOGIN_ID)
            })
        )
    })
    ApiResponse<UserV1Dto.UserResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = """
            인증 헤더로 식별된 본인의 정보를 반환합니다.

            응답의 `name` 은 마지막 글자가 `*` 로 마스킹됩니다 (예: 김성호 → 김성*).
            """
    )
    @Parameters({
        @Parameter(in = ParameterIn.HEADER, name = "X-Loopers-LoginId", required = true, description = "로그인 ID", example = "loopers01"),
        @Parameter(in = ParameterIn.HEADER, name = "X-Loopers-LoginPw", required = true, description = "비밀번호", example = "Loopers!2026")
    })
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 헤더 누락 / 비밀번호 불일치 / 존재하지 않는 로그인 ID",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "인증 헤더 누락 또는 비밀번호 불일치 또는 존재하지 않는 로그인 ID", value = EXAMPLE_401_UNAUTHENTICATED)
            })
        )
    })
    ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @Parameter(hidden = true) Long userId
    );

    @Operation(
        summary = "비밀번호 변경",
        description = """
            인증된 본인의 비밀번호를 변경합니다.

            검증 규칙:
            - 바디의 `currentPassword` 는 저장된 비밀번호와 일치해야 함 (불일치 시 401)
            - `newPassword` 는 정책(8~16자, 영문/숫자/특수문자, 생년월일 미포함) 만족해야 함
            - `newPassword` 는 `currentPassword` 와 달라야 함
            """
    )
    @Parameters({
        @Parameter(in = ParameterIn.HEADER, name = "X-Loopers-LoginId", required = true, description = "로그인 ID", example = "loopers01"),
        @Parameter(in = ParameterIn.HEADER, name = "X-Loopers-LoginPw", required = true, description = "현재 비밀번호 (인증용)", example = "Loopers!2026")
    })
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "새 비밀번호 정책 위반 또는 현재 비밀번호와 동일",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "새 비밀번호 정책 위반", value = EXAMPLE_400_PASSWORD_POLICY),
                @ExampleObject(name = "현재 비밀번호와 동일", value = EXAMPLE_400_PASSWORD_SAME_AS_CURRENT)
            })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 헤더 누락 / 현재 비밀번호 불일치",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "인증 헤더 누락", value = EXAMPLE_401_UNAUTHENTICATED),
                @ExampleObject(name = "바디의 현재 비밀번호 불일치", value = EXAMPLE_401_PASSWORD_MISMATCH)
            })
        )
    })
    ApiResponse<Object> changePassword(
        @Parameter(hidden = true) Long userId,
        UserV1Dto.ChangePasswordRequest request
    );
}