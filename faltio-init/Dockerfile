FROM maven:3.9.9-eclipse-temurin-21 AS builder
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml package -DskipTests
WORKDIR /home/app/target

FROM eclipse-temurin:21-jre-alpine AS final

RUN apk add --no-cache python3 py3-pip && \
    ln -sf python3 /usr/bin/python && \
    pip install --break-system-packages --no-cache-dir --no-deps mlflow==2.21.3 && \
    pip install --break-system-packages --no-cache-dir \
        protobuf \
        packaging \
        pydantic \
        requests \
        opentelemetry.sdk \
        databricks.sdk \
        click \
        pyyaml \
        sqlparse && \
    rm -rf /root/.cache

COPY --from=builder /home/app/target/faltio-init-0.0.1-SNAPSHOT.jar /app/app.jar

RUN adduser -D -u 1001 appuser
USER appuser
WORKDIR /app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
