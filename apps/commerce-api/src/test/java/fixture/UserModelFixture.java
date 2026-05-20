package fixture;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;

import java.time.LocalDate;

public record UserModelFixture(
    String loginId,
    String name,
    String password,
    LocalDate birth,
    String email
) {
    public static UserModelFixture defaults() {
        return new UserModelFixture(
            "testId", "테스터", "test_1234",
            LocalDate.of(1993, 3, 16),
            "test@test.com"
        );
    }

    public static UserModelFixture duplicate() {
        return new UserModelFixture(
            "duplicate", "테스터", "test_1234",
            LocalDate.of(1993, 3, 16),
            "test@test.com"
        );
    }

    public static UserModelFixture custom(String loginId, String name, String password, LocalDate birth, String email) {
        return new UserModelFixture(loginId, name, password, birth, email);
    }

    public UserModel toModel() {
        return UserModel.of(loginId, name, password, new BirthVO(birth), new EmailVO(email));
    }
}
