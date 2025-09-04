package com.cipher.server.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserChangeNicknameDTO {
    private String userId;
    private String nickname;
}
