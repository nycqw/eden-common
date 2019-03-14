package com.eden.common.aspect.lock.handle;

/**
 * @author chenqw
 * @version 1.0
 * @since 2018/12/2
 */
public enum LockType {

    /**
     * 基于redis的分布式锁
     */
    REDIS_LOCK,

    /**
     * 基于zookeeper的分布式锁
     */
    ZOOKEEPER_LOCK
}
