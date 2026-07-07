package com.example.version_control_system;

import com.example.version_control_system.config.JwtProperties;
import com.example.version_control_system.config.MinioProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, MinioProperties.class})
@MapperScan("com.example.version_control_system.mapper")
public class VersionControlSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(VersionControlSystemApplication.class, args);
    }

}
