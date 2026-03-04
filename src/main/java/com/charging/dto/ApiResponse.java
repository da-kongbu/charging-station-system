package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一API响应封装类
 * 
 * 作用：规范前后端交互的数据格式，确保所有的 HTTP 响应都有统一的结构，
 * 包括状态码(code)、提示信息(message)、实际数据(data)和时间戳(timestamp)。
 * 
 * @param <T> data 字段携带的具体业务数据类型
 */
@Data // 自动生成 Getter, Setter, toString, equals 和 hashCode 方法
@Builder // 提供 Builder 模式（链式调用）实例化对象
@NoArgsConstructor // 自动生成无参构造函数
@AllArgsConstructor // 自动生成全参构造函数
@JsonInclude(JsonInclude.Include.NON_NULL) // 序列化为 JSON 时，如果属性值为 null，则不包含在 JSON 结果中
public class ApiResponse<T> {

    /**
     * 业务状态码 (例如：200 表示成功，400 表示请求参数错误，401 表示未登录)
     */
    private Integer code;

    /**
     * 响应的提示信息，通常用于前端展示给用户 (例如："操作成功", "用户名已存在")
     */
    private String message;

    /**
     * 实际承载的业务数据，类型由泛型 T 决定
     */
    private T data;

    /**
     * 响应生成的时间戳，方便前端处理和校验
     */
    private Long timestamp;

    /**
     * 快速构建响应成功，带有数据的 ApiResponse 对象
     *
     * @param data 要返回的业务数据
     * @param <T>  业务数据类型
     * @return 状态码为 200，信息为“操作成功”的 ApiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("操作成功")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 快速构建响应成功，自定义提示信息和数据的 ApiResponse 对象
     *
     * @param message 自定义成功提示信息
     * @param data    要返回的业务数据
     * @param <T>     业务数据类型
     * @return 状态码为 200 的 ApiResponse
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 基础错误响应构建方法
     *
     * @param code    自定义错误状态码
     * @param message 错误提示信息
     * @return 对应的错误 ApiResponse
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 快速构建系统内部错误 (500) 响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }

    /**
     * 快速构建客户端请求错误 (400) 响应 (例如：参数校验失败)
     */
    public static <T> ApiResponse<T> badRequest(String message) {
        return error(400, message);
    }

    /**
     * 快速构建未授权错误 (401) 响应 (例如：未登录，Token 失效)
     */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(401, message);
    }

    /**
     * 快速构建权限不足错误 (403) 响应
     */
    public static <T> ApiResponse<T> forbidden(String message) {
        return error(403, message);
    }

    /**
     * 快速构建资源未找到错误 (404) 响应
     */
    public static <T> ApiResponse<T> notFound(String message) {
        return error(404, message);
    }
}
