# Stage 1: Maven 构建
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Stage 2: 运行时
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/tradepass-server-*.jar app.jar

EXPOSE 9999
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
