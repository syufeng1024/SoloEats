package com.soloeats.app;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan(basePackages = "com.soloeats", annotationClass = Mapper.class)
@SpringBootApplication(scanBasePackages = "com.soloeats")
public class SoloEatsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoloEatsApplication.class, args);
    }
}
