package com.dmg;

import com.dmg.filter.impl.TokenFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableEurekaClient
public class GateWayApplication {

    @Bean
    TokenFilter tokenFilter() {
        return new TokenFilter();
    }

    public static void main(String[] agrs) {
        SpringApplication.run(GateWayApplication.class, agrs);
    }
}
