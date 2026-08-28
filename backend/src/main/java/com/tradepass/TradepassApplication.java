package com.tradepass;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@MapperScan("com.tradepass.mapper")
@SpringBootApplication
@EnableAsync
public class TradepassApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradepassApplication.class, args);
    }
}
