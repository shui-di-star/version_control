# ---- 后端运行镜像（直接用编译好的 JAR）----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
