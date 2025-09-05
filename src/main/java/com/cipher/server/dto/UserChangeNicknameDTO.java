package com.cipher.server.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
public class UserChangeNicknameDTO {
    private String userId;

    @Setter
    private String nickname;
}
