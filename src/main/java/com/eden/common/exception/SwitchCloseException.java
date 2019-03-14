package com.eden.common.exception;

/**
 * 开关关闭异常
 *
 * @author chenqw
 * @version 1.0
 * @since 2019/1/6
 */
public class SwitchCloseException extends RuntimeException {

    public SwitchCloseException(String message) {
        super("开关【" + message + "】关闭，请求失败");
    }
}
