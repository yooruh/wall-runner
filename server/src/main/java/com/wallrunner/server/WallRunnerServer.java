package com.wallrunner.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 【模块】server
 * 【代号】X + Y
 * 【职责】Spring Boot 应用入口，扫描 com.wallrunner 下所有组件。
 * 【原则】仅作启动器，不含业务逻辑。
 */
@SpringBootApplication(scanBasePackages = {"com.wallrunner"})
public class WallRunnerServer {
    public static void main(String[] args) {
        SpringApplication.run(WallRunnerServer.class, args);
    }
}
