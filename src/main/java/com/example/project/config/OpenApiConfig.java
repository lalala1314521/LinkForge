package com.example.project.config;

/**
 * Swagger/ OpenAPI 3.0配置
 * 访问地址 ： https://localhost:8080/swagger-ui/html
 */
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        //定义 JWT Bearer Token认证方式
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("请先调用 /api/auth/login 获取 Token , 然后在此输入Bearer <token>");

        //全局安全要求
        SecurityRequirement securityRequirement = new SecurityRequirement().addList("Bearer");

        return new OpenAPI()
                .info(new Info()
                        .title("reference API")
                        .version("1.0")
                        .description("业务管理平台接口文档\n\n" +
                                "## 认证方式\n" +
                                "1.调用‘POST /api/auth/login' 获取 JWT Token\n" +
                                "2.点击右上角 **Authorize** 按钮\n" +
                                "3.输入 ’Bearer <your_token>‘ 即可认证后续清求")
                        .contact(new Contact()
                                .name("reference 开发团队")
                                .email("dev@example.com")))
                .components(new Components()
                        .addSecuritySchemes("Bearer", bearerScheme))
                .addSecurityItem(securityRequirement);
    }
}