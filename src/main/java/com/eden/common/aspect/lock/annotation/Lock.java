package com.eden.common.aspect.lock.annotation;

import com.eden.common.aspect.lock.handle.LockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Lock {

    /**
     * 过期时间（毫秒）
     *
     * @return
     */
    long expire() default 3000L;

    /**
     * 获取锁超时时间（毫秒）
     *
     * @return
     */
    long timeout() default -1L;

    /**
     * 分布式锁类型
     *
     * @return
     */
    LockType type() default LockType.ZOOKEEPER_LOCK;
}
