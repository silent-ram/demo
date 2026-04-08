package com.example.userservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestPassword {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // Generate hash for admin123
        String adminHash = encoder.encode("admin123");
        System.out.println("admin123 hash: " + adminHash);

        // Generate hash for operator123
        String operatorHash = encoder.encode("operator123");
        System.out.println("operator123 hash: " + operatorHash);

        // Verify existing hashes
        String storedAdminHash = "$2a$10$k0i8nyAZFbhz.X08BGZ/outq8HNJucilC0vPmoGVxky2yck5t12ui";
        String storedOperatorHash = "$2a$10$71GCmbm7.3VP/ItOKjHzruHbL.d.aW52AxYAm.sPyyoVE9G8mb8Mu";

        System.out.println("\nVerifying stored admin hash against admin123: " + encoder.matches("admin123", storedAdminHash));
        System.out.println("Verifying stored admin hash against operator123: " + encoder.matches("operator123", storedAdminHash));
        System.out.println("Verifying stored operator hash against admin123: " + encoder.matches("admin123", storedOperatorHash));
        System.out.println("Verifying stored operator hash against operator123: " + encoder.matches("operator123", storedOperatorHash));
    }
}
