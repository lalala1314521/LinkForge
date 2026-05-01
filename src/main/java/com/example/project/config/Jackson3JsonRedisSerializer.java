package com.example.project.config;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.DefaultTyping;

/**
 * 基于 Jackson 3 (tools.jackson) 的 Redis JSON 序列化器
 *
 * 功能等价于 GenericJackson2JsonRedisSerializer：
 * - 序列化时自动写入 @class 类型信息（支持多态反序列化）
 * - 自动注册 JavaTimeModule（支持 LocalDateTime 等类型）
 */
public class Jackson3JsonRedisSerializer implements RedisSerializer<Object> {

    private final ObjectMapper mapper;

    public Jackson3JsonRedisSerializer() {
        this.mapper = JsonMapper.builder()
                .findAndAddModules()
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .build(),
                        DefaultTyping.NON_FINAL
                )
                .build();
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new SerializationException("序列化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(bytes, Object.class);
        } catch (Exception e) {
            throw new SerializationException("反序列化失败: " + e.getMessage(), e);
        }
    }
}


