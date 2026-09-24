# lifeline-docking 后端镜像（东京 VPS）
#
# 两段式构建：
#   build   —— maven:3.9-eclipse-temurin-17 编译打包（需要联网拉依赖）
#   runtime —— eclipse-temurin:17-jre-jammy 只跑 jar，镜像小
#
# 构建上下文 = 项目根目录（compose.yaml 里 context: ..）

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
# 先只拷 pom 拉依赖，利用 docker layer cache
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseContainerSupport"
COPY --from=build /build/target/lifeline-docking.jar /app/lifeline-docking.jar
EXPOSE 3110
# 端口与数据源由环境变量覆盖，见 compose.yaml
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/lifeline-docking.jar"]
