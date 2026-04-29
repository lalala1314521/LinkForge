package com.example.project.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置开启Mapper包扫描
 */

@Configuration
@MapperScan("com.example.project.mapper")
public class MyBatisConfig {

}