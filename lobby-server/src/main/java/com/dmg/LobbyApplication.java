package com.dmg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class LobbyApplication {
    public static void main(String[] agrs) {
        SpringApplication.run(LobbyApplication.class,agrs);
    }
}
