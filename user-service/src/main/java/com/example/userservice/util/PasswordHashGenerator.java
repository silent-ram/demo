package com.example.userservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 测试已有哈希是否能匹配
        String[] hashes = {
            "$2a$10$k0i8nyAZFbhz.X08BGZ/outq8HNJucilC0vPmoGVxky2yck5t12ui",
            "$2a$10$71GCmbm7.3VP/ItOKjHzruHbL.d.aW52AxYAm.sPyyoVE9G8mb8Mu",
            "$2a$10$4DoGRm9xhrwQgis0OjVWJ.3Re2wUut6.H2uqWhDtsJ9.7L3STaGpa",
            "$2a$10$eLTbSx/ZS3btdtOYaROfY.QKICuUYQw/wkmRiwM65EEa77pJTBq7y"
        };

        String password = "operator123";

        for (String hash : hashes) {
            boolean matches = encoder.matches(password, hash);
            System.out.println("Hash: " + hash);
            System.out.println("Matches 'operator123': " + matches);
            System.out.println();
        }
    }
}