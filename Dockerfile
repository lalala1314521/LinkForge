# ============================================================
# LinkForge 后端 Dockerfile（多阶段构建）
# 构建：gradle:9.4.1-jdk21 → bootJar；运行：eclipse-temurin:21-jre
# ============================================================

# ---- 构建阶段 ----
FROM gradle:9.4.1-jdk21 AS build
WORKDIR /app

# 先拷贝构建脚本与依赖声明，利用 Docker 层缓存
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

# 打包（跳过测试，部署环境以编译通过为准）
RUN gradle bootJar --no-daemon -x test --console=plain

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# 时区与中文字体/编码
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m"

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
