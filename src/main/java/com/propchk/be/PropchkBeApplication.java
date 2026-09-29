package com.propchk.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PropchkBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropchkBeApplication.class, args);
    }

}
