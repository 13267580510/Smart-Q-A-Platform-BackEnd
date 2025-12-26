package org.example.backend.model;

public enum ResponseStatus {
    // 成功状态码
    SUCCESS(200, "成功"),
    CREATED(201, "创建成功"),
    ACCEPTED(202, "已接受"),

    // 客户端错误状态码
    BAD_REQUEST(400, "错误的请求"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "方法不允许"),
    CONFLICT(409, "资源冲突"),

    // 服务器错误状态码
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // 自定义业务状态码
    PARAMETER_ERROR(1001, "参数错误"),
    DATA_NOT_EXIST(1002, "数据不存在"),
    DATA_DUPLICATE(1003, "数据已存在"),
    OPERATION_FAILED(1004, "操作失败"),
    BUSINESS_ERROR(1005, "业务处理异常");

    private final int code;
    private final String message;

    ResponseStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    // 根据状态码获取对应的枚举
    public static ResponseStatus fromCode(int code) {
        for (ResponseStatus status : ResponseStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        // 默认返回未知错误
        return INTERNAL_SERVER_ERROR;
    }

    // 重写toString方法，方便日志记录和调试
    @Override
    public String toString() {
        return String.format("Status{code=%d, message='%s'}", code, message);
    }
}