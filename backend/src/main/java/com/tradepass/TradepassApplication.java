package com.tradepass;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.tradepass.mapper")
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TradepassApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradepassApplication.class, args);
    }
}
