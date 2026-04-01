package com.example.userservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestPassword {
    public static void main(String[] args) {
        // 从数据库读取的密码哈希
        String storedHash = "$2a$10$k0i8nyAZFbhz.X08BGZ/outq8HNJucilC0vPmoGVxky2yck5t12ui";

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        System.out.println("Testing password 'operator123'");
        System.out.println("Stored hash: " + storedHash);
        System.out.println("Matches: " + encoder.matches("operator123", storedHash));

        // 测试 admin123
        String adminHashFromDb = "$2a$10$4DoGRm9xhrwQgis0OjVWJ.3Re2wUut6.H2uqWhDtsJ9.7L3STaGpa";
        System.out.println("\nTesting password 'admin123'");
        System.out.println("Stored hash: " + adminHashFromDb);
        System.out.println("Matches: " + encoder.matches("admin123", adminHashFromDb));
    }
}