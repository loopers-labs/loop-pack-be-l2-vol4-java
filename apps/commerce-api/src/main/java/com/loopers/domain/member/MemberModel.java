package com.loopers.domain.member;


import com.loopers.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import org.antlr.v4.runtime.misc.NotNull;


@Entity
@Table(name="member")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MemberModel extends BaseEntity {

  @NotNull
  @Schema(description = "유저 아이디")
  private String userId;

  @NotNull
  @Schema(description = "유저 패스워드")
  private String password;

  @NotNull
  @Schema(description = "유저 이메일")
  private String email;

  @NotNull
  @Schema(description = "유저이름")
  private String username;

}
