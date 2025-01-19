package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
@Component
public class RedisSnowflake {
    //1736919212650L
    private static final long twepoch = 1640995200000L;

    // 每部分的位数
    private static final long WORKER_ID_BITS = 5L;       // 机器 ID
    private static final long DATACENTER_ID_BITS = 5L;   // 数据中心 ID
    private static final long SEQUENCE_BITS = 12L;       // 序列号

    // 最大值
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long MAX_DATACENTER_ID = (1L << DATACENTER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

    // 位移
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private long workerId;       // 机器 ID
    private long datacenterId;   // 数据中心 ID
    private long sequence = 0L;  // 当前毫秒内的序列号
    private long lastTimestamp = -1L; // 上次生成 ID 的时间戳

    public void setWorkerId(long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("Worker ID out of bounds.");
        }
        this.workerId = workerId;
    }

    public void setDatacenterId(long datacenterId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("Datacenter ID out of bounds.");
        }
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long timestamp = timeGen();

        // 如果当前时间小于上次时间戳，抛出异常
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }

        // 如果是同一毫秒内
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;

            // 如果序列号达到最大值，等待下一毫秒
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 时间戳变化，重置序列号
            sequence = 0L;
        }

        // 更新上次时间戳
        lastTimestamp = timestamp;

        // 拼接 ID
        return ((timestamp - twepoch) << TIMESTAMP_LEFT_SHIFT) // 时间戳部分
                | (datacenterId << DATACENTER_ID_SHIFT)        // 数据中心部分
                | (workerId << WORKER_ID_SHIFT)               // 机器 ID 部分
                | sequence;                                   // 序列号部分
    }

    // 等待下一毫秒
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    // 获取当前时间戳
    protected long timeGen() {
        return System.currentTimeMillis();
    }
}
