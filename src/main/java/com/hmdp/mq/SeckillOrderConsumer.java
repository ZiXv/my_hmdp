package com.hmdp.mq;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SeckillOrderConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 监听 RabbitMQ 中 SECKILL_QUEUE, 收到消息后执行处理。
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void onMessage(VoucherOrderDTO voucherOrder) {
        if (voucherOrder == null) {
            return;
        }
        handleVoucherOrder(voucherOrder);
    }

    /**
     * 具体处理下单逻辑
     */
    private void handleVoucherOrder(VoucherOrderDTO voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 1. 创建锁对象(避免同一个用户重复下单)
        RLock redisLock = redissonClient.getLock("order:" + userId);
        boolean isLock = false;
        try {
            // 2. 尝试获取锁
            isLock = redisLock.tryLock(1, 10, TimeUnit.SECONDS);
            if(!isLock){
                log.error("不允许重复下单!");
                return;
            }
            // 3. 这里调用下单逻辑（使用服务方法）
            //    如果 createVoucherOrder 需要事务，则要确保它是从代理对象里调用的
            //    可以在 IVoucherOrderService 实现类里做 AopContext.currentProxy()，或者在这里注入代理 Bean
            voucherOrderService.createVoucherOrder(voucherOrder);

        } catch (Exception e) {
            log.error("订单处理异常：", e);
        } finally {
            if(isLock){
                redisLock.unlock();
            }
        }
    }
}
