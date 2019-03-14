package com.eden.common.aspect.lock.handle;

import com.eden.common.condition.RedisCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 分布式锁需要满足以下四个条件：
 * 1、互斥性。在任意时刻，只有一个客户端能持有锁。
 * 2、不会发生死锁。即使有一个客户端在持有锁的期间崩溃而没有主动解锁，也能保证后续其他客户端能加锁。
 * 3、具有容错性。只要大部分的Redis节点正常运行，客户端就可以加锁和解锁。
 * 4、解铃还须系铃人。加锁和解锁必须是同一个客户端，客户端自己不能把别人加的锁给解了。
 * <p/>
 * 使用redis来实现分布式锁，是基于redis的单线程特性来保证线程安全，基于redis的高并发性能来保证高可用。
 * 缺点是无法优雅的进行锁等待，需要通过阻塞的方式来进行等待，会一直占有cpu资源
 * <p/>
 * 锁过期时间不能设置过短，否则会造成锁提前结束，造成分布式锁失效
 *
 * @author chenqw
 * @version 1.0
 * @since 2018/12/1
 */
@Conditional(RedisCondition.class)
@Component
@Slf4j
public class RedisDistributedLock {

    private final int DEFAULT_WAIT_TIME = 10;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 加锁
     *
     * @param lockName   锁名称
     * @param identifier 锁标记
     * @param expire     过期时间（持有锁最长时间）（毫秒）
     * @return
     */
    public boolean lock(String lockName, String identifier, Long expire) {
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        RedisConnection connection = connectionFactory.getConnection();
        while (true) {
            if (tryLock(identifier, lockName, expire, connectionFactory, connection))
                return true;
        }
    }

    /**
     * 可设置超时时间的锁
     *
     * @param lockName   锁名称
     * @param identifier 锁标记
     * @param expire     过期时间（持有锁最长时间）（毫秒）
     * @param timeout    获取锁的超时时间
     * @return 是否成功
     */
    public boolean lockWithTimeout(String lockName, String identifier, long expire, long timeout) {
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        RedisConnection connection = connectionFactory.getConnection();
        long end = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < end) {
            if (tryLock(identifier, lockName, expire, connectionFactory, connection))
                return true;
        }
        RedisConnectionUtils.releaseConnection(connection, connectionFactory);
        return false;
    }

    /**
     * 尝试获取锁
     */
    private boolean tryLock(String identifier, String lockName, Long expire, RedisConnectionFactory connectionFactory, RedisConnection connection) {
        int lockExpire = (int) (expire / 1000);
        // 1、只有一个客户端能获取到锁
        if (connection.setNX(lockName.getBytes(), identifier.getBytes())) {
            // 2.1、若在这里程序突然崩溃，则无法设置过期时间，将发生死锁
            connection.expire(lockName.getBytes(), lockExpire);
            RedisConnectionUtils.releaseConnection(connection, connectionFactory);
            log.info("获取锁成功-{}", Thread.currentThread().getName());
            return true;
        }

        // 2.2、解决2.1处可能出现的死锁问题
        // 当出现锁没有设置过期时间时，由其他线程来为其设置过期时间
        if (connection.ttl(lockName.getBytes()) == -1) {
            connection.expire(lockName.getBytes(), lockExpire);
        }

        // 延迟再次尝试获取锁
        try {
            Thread.sleep(DEFAULT_WAIT_TIME);
        } catch (InterruptedException e) {
            log.warn("获取到分布式锁：线程中断！");
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /**
     * 释放锁
     *
     * @param lockName   锁的key
     * @param identifier 释放锁的标识
     * @return 是否成功
     */
    public void unLock(String lockName, String identifier) {
        if (identifier == null || "".equals(identifier)) {
            return;
        }
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        RedisConnection connection = connectionFactory.getConnection();
        while (true) {
            try {
                // 监视lock，准备开始事务
                connection.watch(lockName.getBytes());

                byte[] valueBytes = connection.get(lockName.getBytes());
                if (valueBytes == null) {
                    log.info("自动释放锁成功-{}", Thread.currentThread().getName());
                    connection.unwatch();
                    break;
                }
                // 4、加锁和解锁必须是同一个客户端
                if (identifier.equals(new String(valueBytes))) {
                    connection.multi();
                    // 释放锁
                    connection.del(lockName.getBytes());
                    List<Object> results = connection.exec();
                    if (results == null) {
                        continue;
                    }
                    log.info("释放锁成功-{}", Thread.currentThread().getName());
                }

                connection.unwatch();
                break;
            } catch (Exception e) {
                log.warn("释放锁异常", e);
            }
        }
        RedisConnectionUtils.releaseConnection(connection, connectionFactory);
    }
}
