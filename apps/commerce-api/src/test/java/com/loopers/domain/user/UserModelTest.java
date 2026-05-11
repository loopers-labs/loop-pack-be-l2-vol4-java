package com.loopers.domain.user;

import com.loopers.domain.example.ExampleModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserModelTest {
    //바텀업 (Bottom-Up, Inside-Out) 방식으로 구현을 시도

    @DisplayName("유효한 값으로 UserModel을 생성한다")
    @Test
    // 네이밍은 BDD 스타일 테스트 메서드 네이밍 컨벤션
    void createUserModel_withValidInput_success(){
        // AAA 패턴 (Arrange-Act-Assert)

        // Arrange (준비) - 테스트에 필요한 데이터/객체 세팅
        String id = "jse36855";
        String password = "jse6149";
        String name = "정태형";
        LocalDate birthday = LocalDate.of(1992, 6, 24);
        String email = "jse368554545@gmail.com";

        // Act (실행) - 실제로 테스트하려는 동작 수행
        UserModel userModel = new UserModel(id, password, name, birthday, email);

        // Assert (검증) - 결과가 기대한 대로인지 확인
        assertAll(
                () -> assertThat(userModel.getId()).isEqualTo(id),
                () -> assertThat(userModel.getName()).isEqualTo(name),
                () -> assertThat(userModel.getBirthday()).isEqualTo(birthday),
                () -> assertThat(userModel.getEmail()).isEqualTo(email),
                () -> assertThat(userModel.getPassword()).isNotBlank()
        );
    }

    @DisplayName("이름이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void throwsBadRequestException_whenNameIsNullOrBlank(String invalidName) {
        // arrange
        String id = "jse36855";
        String password = "jse6149";
        LocalDate birthday = LocalDate.of(1992, 6, 24);
        String email = "jse368554545@gmail.com";

        // act
        CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(id, password, invalidName, birthday, email)
        );

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
