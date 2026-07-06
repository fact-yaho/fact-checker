package com.yaho.factchecker.domain.user.controller;

import com.yaho.factchecker.domain.user.dto.request.LoginRequest;
import com.yaho.factchecker.domain.user.dto.response.LoginResponse;
import com.yaho.factchecker.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;


    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request){

        LoginResponse response = userService.login(request);

        return ResponseEntity.ok(response);

    }



}
