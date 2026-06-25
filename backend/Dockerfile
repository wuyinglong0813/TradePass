FROM eclipse-temurin:17-jre
WORKDIR /app

COPY target/tradepass-server-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 9999
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
