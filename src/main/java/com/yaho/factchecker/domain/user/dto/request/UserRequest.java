package com.yaho.factchecker.domain.user.dto.request;


import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserRequest {

    private String email;
    private String password;
    private String name;
    private String nickname;


}
