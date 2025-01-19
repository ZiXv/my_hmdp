package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedisSnowflake;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;


@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {
    @Resource
    private RedisIdWorker redisIdWorker;
    private ExecutorService es= Executors.newFixedThreadPool(500);

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopServiceImpl shopService;
    @Test
    void testidworker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time="+(end-start));
    }
    @Test
    public void test() throws InterruptedException {
        shopService.saveShop2Redis(1L,2L);
    }
    @Test
    public void loadShopData() {
        List<Shop> shopList = shopService.list();
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shops = entry.getValue();
            String key = SHOP_GEO_KEY + typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
                //将当前type的商铺都添加到locations集合中
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            //批量写入
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
    @Resource
    public RedisSnowflake redisSnowflake;

    @Test
    public void testGenerateId() throws InterruptedException {
        int threadCount = 300;
        // 每个线程生成的 ID 数量
        int idsPerThread = 100;

        // CountDownLatch 确保所有线程完成
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 用于存储生成的 ID
        StringBuilder resultLog = new StringBuilder();

        Runnable task = () -> {
            for (int i = 0; i < idsPerThread; i++) {
                long id = redisSnowflake.nextId();
                synchronized (resultLog) {
                    resultLog.append("ID: ").append(id).append("\n");
                }
            }
            latch.countDown();
        };

        long start = System.currentTimeMillis();

        // 提交并发任务
        for (int i = 0; i < threadCount; i++) {
            es.submit(task);
        }

        // 等待所有线程完成
        latch.await();

        long end = System.currentTimeMillis();
        System.out.println("Time taken: " + (end - start) + " ms");

        // 打印所有生成的 ID
        System.out.println(resultLog.toString());

    }
    @Resource
    private RedissonClient redissonClient;

    @Test

     void testRedisson() throws InterruptedException {
        //获取可重入锁
        RLock lock = redissonClient.getLock("anyLock");
        //尝试获取锁，三个参数分别是：获取锁的最大等待时间(期间会重试)，锁的自动释放时间，时间单位
        boolean success = lock.tryLock(1,10, TimeUnit.SECONDS);
        //判断获取锁成功
        if (success) {
            try {
                log.info("执行业务");
            } finally {
                //释放锁
                lock.unlock();
            }
        }
    }


}
