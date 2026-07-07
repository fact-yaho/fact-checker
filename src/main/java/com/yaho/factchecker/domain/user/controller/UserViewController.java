package com.yaho.factchecker.domain.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserViewController {

    //  http://localhost:8080/login
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // http://localhost:8080/signup
    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }
}