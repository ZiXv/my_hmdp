package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisSnowflake;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Autowired
    private ISeckillVoucherService seckillVoucherService;

//  @Autowired
//  private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisSnowflake redisSnowflake;
    private IVoucherOrderService proxy;




//@Override
//public Result seckillVoucher(Long voucherId) {
//    LambdaQueryWrapper<SeckillVoucher> queryWrapper = new LambdaQueryWrapper<>();
//    //1. 查询优惠券
//    queryWrapper.eq(SeckillVoucher::getVoucherId, voucherId);
//    SeckillVoucher seckillVoucher = seckillVoucherService.getOne(queryWrapper);
//    //2. 判断秒杀时间是否开始
//    if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
//        return Result.fail("秒杀还未开始，请耐心等待");
//    }
//    //3. 判断秒杀时间是否结束
//    if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
//        return Result.fail("秒杀已经结束！");
//    }
//    //4. 判断库存是否充足
//    if (seckillVoucher.getStock() < 1) {
//        return Result.fail("优惠券已被抢光了哦，下次记得手速快点");
//    }
//           // 一人一单逻辑
//           Long userId = UserHolder.getUser().getId();
//          int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
//          if (count > 0){
//                  return Result.fail("你已经抢过优惠券了哦");
//               }
//    //5. 扣减库存
//    boolean success = seckillVoucherService.update()
//            .setSql("stock = stock - 1")
//            .eq("voucher_id", voucherId)
//            .gt("stock", 0)
//            .update();
//    if (!success) {
//        return Result.fail("库存不足");
//    }
//    //6. 创建订单
//    VoucherOrder voucherOrder = new VoucherOrder();
//    //6.1 设置订单id
//    long orderId = redisIdWorker.nextId("order");
//    //6.2 设置用户id
//    Long id = UserHolder.getUser().getId();
//    //6.3 设置代金券id
//    voucherOrder.setVoucherId(voucherId);
//    voucherOrder.setId(orderId);
//    voucherOrder.setUserId(id);
//    //7. 将订单数据保存到表中
//    save(voucherOrder);
//    //8. 返回订单id
//    return Result.ok(orderId);
//}
    String queueName = "stream.orders";


//    private void handlePendingList() {
//        while (true) {
//            try {
//                //1. 获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders 0
//                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
//                        Consumer.from("g1", "c1"),
//                        StreamReadOptions.empty().count(1),
//                        StreamOffset.create(queueName, ReadOffset.from("0")));
//                //2. 判断pending-list中是否有未处理消息
//                if (records == null || records.isEmpty()) {
//                    //如果没有就说明没有异常消息，直接结束循环
//                    break;
//                }
//                //3. 消息获取成功之后，我们需要将其转为对象
//                MapRecord<String, Object, Object> record = records.get(0);
//                Map<Object, Object> values = record.getValue();
//                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                //4. 获取成功，执行下单逻辑，将数据保存到数据库中
//                handleVoucherOrder(voucherOrder);
//                //5. 手动ACK，SACK stream.orders g1 id
//                stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
//            } catch (Exception e) {
//                log.info("处理pending-list异常");
//                //如果怕异常多次出现，可以在这里休眠一会儿
//                try {
//                    Thread.sleep(50);
//                } catch (InterruptedException ex) {
//                    throw new RuntimeException(ex);
//                }
//            }
//        }
//    }
//    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        Long userId = voucherOrder.getUserId();
//        //2. 创建锁对象，作为兜底方案
//        RLock redisLock = redissonClient.getLock("order:" + userId);
//        //3. 获取锁
//        boolean isLock = redisLock.tryLock();
//        //4. 判断是否获取锁成功
//        if (!isLock) {
//            log.error("不允许重复下单!");
//            return;
//        }
//        try {
//            //5. 使用代理对象，由于这里是另外一个线程，
//            proxy.createVoucherOrder(voucherOrder);
//        } finally {
//            redisLock.unlock();
//        }
//    }


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT ;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1. 获取用户
        Long userId = voucherOrder.getUserId();
        //2. 创建锁对象，作为兜底方案
        RLock redisLock = redissonClient.getLock("order:" + userId);
        //3. 获取锁
        boolean isLock = redisLock.tryLock();
        //4. 判断是否获取锁成功
        if (!isLock) {
            log.error("不允许重复下单!");
            return;
        }
        try {
            //5. 使用代理对象，由于这里是另外一个线程，
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            redisLock.unlock();
        }
    }
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //1. 获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2. 创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("订单处理异常", e);
                }
            }
        }
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(), voucherId.toString(),
                UserHolder.getUser().getId().toString());
        if (result.intValue() != 0) {
            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
        }
        long orderId = redisSnowflake.nextId();
        //封装到voucherOrder中
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setId(orderId);
        //加入到阻塞队列
        orderTasks.add(voucherOrder);
        //主线程获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单逻辑
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        RLock redisLock = redissonClient.getLock("order:" + userId);
        boolean isLock = redisLock.tryLock();
        try {
            if (!isLock) {
                // 获取锁失败，说明该用户已有线程在下单
                log.error("不允许重复下单！userId={}", userId);
                return;
            }
            int count = Math.toIntExact(query()
                    .eq("voucher_id", voucherId)
                    .eq("user_id", userId)
                    .count());
            if (count > 0) {
                log.error("你已经抢过优惠券了哦！");
                return;
            }
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // stock=stock-1
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                // 库存不足
                log.error("库存不足！");
                return;
            }

            // 5. 将订单数据保存到表中
            save(voucherOrder);
        } finally {
            if (isLock) {
                redisLock.unlock();
            }
        }

    }


//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1. 执行lua脚本
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                Collections.emptyList(), voucherId.toString(),
//                UserHolder.getUser().getId().toString());
//        //2. 判断返回值，并返回错误信息
//        if (result.intValue() != 0) {
//            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
//        }
//        long orderId = redisIdWorker.nextId("order");
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        voucherOrder.setId(orderId);
//        //加入到阻塞队列
//        orderTasks.add(voucherOrder);
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //3. 返回订单id
//        return Result.ok(orderId);
//    }
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 一人一单逻辑
//        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
//            int count = Math.toIntExact(query().eq("voucher_id", voucherId).eq("user_id", userId).count());
//            if (count > 0) {
//                return Result.fail("你已经抢过优惠券了哦");
//            }
//            //5. 扣减库存
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock - 1")
//                    .eq("voucher_id", voucherId)
//                    .gt("stock", 0)
//                    .update();
//            if (!success) {
//                return Result.fail("库存不足");
//            }
//            //6. 创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            //6.1 设置订单id
//            long orderId = redisSnowflake.nextId();
//            //6.2 设置用户id
//            Long id = UserHolder.getUser().getId();
//            //6.3 设置代金券id
//            voucherOrder.setVoucherId(voucherId);
//            voucherOrder.setId(orderId);
//            voucherOrder.setUserId(id);
//            //7. 将订单数据保存到表中
//            save(voucherOrder);
//            //8. 返回订单id
//            return Result.ok(orderId);
//        }
//        //执行到这里，锁已经被释放了，但是可能当前事务还未提交，如果此时有线程进来，不能确保事务不出问题
//    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        long orderId = redisSnowflake.nextId();
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                Collections.emptyList(), voucherId.toString(),
//                UserHolder.getUser().getId().toString(), String.valueOf(orderId));
//        if (result.intValue() != 0) {
//            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
//        }
//        //主线程获取代理对象
//        Long userId = UserHolder.getUser().getId();
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        synchronized (userId.toString().intern()) {
//            return createVoucherOrder(voucherId);
//        }
//    }
    /*
    下面函数是redission实现的分布式锁
    */

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        LambdaQueryWrapper<SeckillVoucher> queryWrapper = new LambdaQueryWrapper<>();
//        //1. 查询优惠券
//        queryWrapper.eq(SeckillVoucher::getVoucherId, voucherId);
//        SeckillVoucher seckillVoucher = seckillVoucherService.getOne(queryWrapper);
//        //2. 判断秒杀时间是否开始
//        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
//            return Result.fail("秒杀还未开始，请耐心等待");
//        }
//        //3. 判断秒杀时间是否结束
//        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
//            return Result.fail("秒杀已经结束！");
//        }
//        //4. 判断库存是否充足
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("优惠券已被抢光了哦，下次记得手速快点");
//        }
//        Long userId = UserHolder.getUser().getId();
//        RLock redisLock = redissonClient.getLock("order:" + userId);
//        boolean isLock = redisLock.tryLock();
//        if (!isLock) {
//            return Result.fail("不允许抢多张优惠券");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            redisLock.unlock();
//        }
//    }



}
