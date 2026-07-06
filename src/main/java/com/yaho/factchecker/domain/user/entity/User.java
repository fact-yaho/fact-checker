package com.yaho.factchecker.domain.user.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name= "p_user")
@Getter
@NoArgsConstructor
@AllArgsConstructor //
@Builder //
public class User {

    @Id
    @GeneratedValue
    private long id;

    @Column(nullable = false, unique = true ,length = 50 ) //중복 가입방지
    private String email;

    @Column(nullable = false, length = 255 )
    private String password;

    @Column(nullable = false, length = 50 )
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @Column(nullable = false,length = 50, unique = true)
    private String nickname;


    public  User(String email, String password, String name, Role role, String nickname) {

        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
        this.nickname = nickname;



    }


}
