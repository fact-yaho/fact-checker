package com.yaho.factchecker.domain.user.dto.response;
import com.yaho.factchecker.domain.user.entity.User;
import lombok.Getter;

@Getter
public class MyPageResponse {



    private  final String email;
    private  final String name;
    private final String nickname;

    public MyPageResponse(User user) {
        this.email = user.getEmail();
        this.name = user.getName();
        this.nickname = user.getNickname();
    }

    //추후 내가 질문한 정보 담을 공간
}
