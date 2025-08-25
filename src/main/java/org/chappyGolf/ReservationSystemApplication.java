package org.chappyGolf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ReservationSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReservationSystemApplication.class, args);
    }
}