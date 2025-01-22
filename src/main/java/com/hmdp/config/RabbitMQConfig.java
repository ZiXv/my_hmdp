package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 队列名称
    public static final String SECKILL_QUEUE = "queue.seckill.order";
    // 交换机名称
    public static final String SECKILL_EXCHANGE = "exchange.seckill.order";
    // RoutingKey
    public static final String SECKILL_ROUTING_KEY = "routing.seckill.order";

    @Bean
    public Queue seckillQueue() {
        // 持久化保证服务重启后数据也不丢失
        return new Queue(SECKILL_QUEUE, true, false, false);
    }

    @Bean
    public Exchange seckillExchange() {
        return ExchangeBuilder
                .directExchange(SECKILL_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder
                .bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY)
                .noargs();
    }
}
