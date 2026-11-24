FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Maven Wrapper: a mesma versão do Maven (.mvn/wrapper) no Docker, no CI e na máquina de cada dev
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
ENV TZ=America/Recife LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS="-XX:MaxRAMPercentage=75"
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --no-create-home api   # processo sem root (Guia §6.3)
WORKDIR /app
COPY --from=build /src/target/relatorios-api.jar app.jar
USER api
EXPOSE 8080 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8081/actuator/health/readiness >/dev/null 2>&1 || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
