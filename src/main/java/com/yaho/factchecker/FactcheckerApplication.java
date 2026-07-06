package com.yaho.factchecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class FactcheckerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FactcheckerApplication.class, args);
    }

}
