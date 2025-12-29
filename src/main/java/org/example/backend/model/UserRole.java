package org.example.backend.model;

public enum UserRole {
    USER, ADMIN;

    public static User.UserRole fromString(String role) {
        switch (role) {
            case "ROLE_USER":
                return User.UserRole.USER;
            case "ROLE_ADMIN":
                return User.UserRole.ADMIN;
            default:
                throw new IllegalArgumentException("Invalid role: " + role);
        }
    }
}