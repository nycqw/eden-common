package com.eden.common.aspect.lock.handle;

import com.eden.common.condition.CuratorCondition;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/**
 * @author chenqw
 * @version 1.0
 * @since 2018/12/2
 */
@Conditional(CuratorCondition.class)
@Component
@Slf4j
public class CuratorDistributedLock implements InitializingBean {

    private final static String ROOT_PATH_LOCK = "rootlock";
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    @Autowired
    private CuratorFramework curatorFramework;

    /**
     * 获取分布式锁
     * 基于节点不可重复的原理，当节点存在时则会创建失败即获取锁失败
     * 使用临时节点可以避免死锁问题，当某个线程所在服务断开时zk会自动剔除该节点
     * 缺点是会出现羊群效应，当锁被释放后会出现所有线程同时去争抢锁
     */
    public void lock(String lockName) {
        String keyPath = "/" + ROOT_PATH_LOCK + "/" + lockName;
        while (true) {
            try {
                curatorFramework
                        .create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath(keyPath);
                log.info("获取锁成功-{}", Thread.currentThread().getName());
                break;
            } catch (Exception e) {
                try {
                    if (countDownLatch.getCount() <= 0) {
                        countDownLatch = new CountDownLatch(1);
                    }
                    countDownLatch.await();
                } catch (InterruptedException e1) {
                    log.error("中断异常", e);
                }
            }
        }
    }

    /**
     * 释放分布式锁
     * 删除节点，允许其他线程创建节点即获取到锁
     */
    public boolean unlock(String lockName) {
        try {
            String keyPath = "/" + ROOT_PATH_LOCK + "/" + lockName;
            if (curatorFramework.checkExists().forPath(keyPath) != null) {
                curatorFramework.delete().forPath(keyPath);
                log.info("释放锁成功-{}", Thread.currentThread().getName());
            }
        } catch (Exception e) {
            log.error("failed to release lock");
            return false;
        }
        return true;
    }

    /**
     * 创建 watcher 事件
     */
    private void addWatcher(String lockName) throws Exception {
        String keyPath;
        if (lockName.equals(ROOT_PATH_LOCK)) {
            keyPath = "/" + lockName;
        } else {
            keyPath = "/" + ROOT_PATH_LOCK + "/" + lockName;
        }
        // 监听根节点下的孩子节点
        final PathChildrenCache cache = new PathChildrenCache(curatorFramework, keyPath, false);
        cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
        cache.getListenable().addListener((client, event) -> {
            if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)) {
                String oldPath = event.getData().getPath();
                if (oldPath.contains(lockName)) {
                    //释放计数器，让当前的请求获取锁
                    countDownLatch.countDown();
                }
            }
        });
    }

    //创建父节点，并创建永久节点
    @Override
    public void afterPropertiesSet() {
        curatorFramework = curatorFramework.usingNamespace("lock-namespace");
        String path = "/" + ROOT_PATH_LOCK;
        try {
            if (curatorFramework.checkExists().forPath(path) == null) {
                curatorFramework.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath(path);
            }
            addWatcher(ROOT_PATH_LOCK);
            log.info("root path 的 watcher 事件创建成功");
        } catch (Exception e) {
            log.error("connect zookeeper fail，please check the log >> {}", e.getMessage(), e);
        }
    }
}