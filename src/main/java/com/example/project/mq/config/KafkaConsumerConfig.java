package com.example.project.mq.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        // recoverer 作为构造函数的第一个参数传入
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                // recoverer：重试全部失败后执行这里
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    log.error("Kafka 消息消费最终失败（已达最大重试次数），需人工处理！topic={}, partition={}, offset={}, key={}, value={}",
                            record.topic(), record.partition(), record.offset(),
                            record.key(), record.value(), ex);
                    // 扩展点：
                    // 1. 写入数据库死信表 → 后续人工补偿
                    // 2. 发送到 dead-letter topic
                    // 3. 触发告警通知
                },
                //第二个参数是 BackOff 策略
                backOff
        );

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}


