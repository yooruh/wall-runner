package com.wallrunner.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 应用入口。
 *
 * 职责：仅作启动器，不含业务逻辑。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.wallrunner"})
public class WallRunnerServer {
    public static void main(String[] args) {
        SpringApplication.run(WallRunnerServer.class, args);
    }
}
