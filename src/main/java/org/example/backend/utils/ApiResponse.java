package org.example.backend.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse {
    private int status;
    private boolean success;
    private String message;
    private Object data;

    public static ApiResponse success(int status,String message) {
        return new ApiResponse(status,true, message, null);
    }

    public static ApiResponse success(int status,String message, Object data) {
        return new ApiResponse(status,true, message, data);
    }

    public static ApiResponse error(int status ,String message) {
        return new ApiResponse(status,false, message, null);
    }
}