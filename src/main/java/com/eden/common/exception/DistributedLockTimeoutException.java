package com.eden.common.exception;

/**
 * @author chenqw
 * @version 1.0
 * @since 2018/12/4
 */
public class DistributedLockTimeoutException extends RuntimeException {

    public DistributedLockTimeoutException(String message) {
        super(message);
    }
}
