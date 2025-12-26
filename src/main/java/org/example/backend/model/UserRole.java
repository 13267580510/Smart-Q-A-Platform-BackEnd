package org.example.backend.model;

public enum UserRole {
    USER, ADMIN;

    public static UserRole fromString(String role) {
        switch (role) {
            case "ROLE_USER":
                return USER;
            case "ROLE_ADMIN":
                return ADMIN;
            default:
                throw new IllegalArgumentException("Invalid role: " + role);
        }
    }
}