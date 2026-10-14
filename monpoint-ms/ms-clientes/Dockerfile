# =============================================================================
# ms-clientes — imagen de despliegue (misma que ms-auth)
# =============================================================================
# Construir (desde monpoint-ms/ms-clientes):   docker build -t monpoint/ms-clientes .
# En el sistema completo la construye docker compose (build: ../ms-clientes).
# =============================================================================

# --- Etapa 1: compilacion ----------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Las pruebas se omiten aqui porque necesitan MongoDB: se corren antes con ./mvnw test.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package -DskipTests

# --- Etapa 2: ejecucion ------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Usuario sin privilegios: si alguien compromete la aplicacion, no es root.
RUN useradd --system --uid 10001 monpoint
COPY --from=build /app/target/ms-clientes-*.jar app.jar
USER monpoint
EXPOSE 8084
# healthy cuando /actuator/health responde UP (la app arranco y MongoDB contesta).
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=10 \
  CMD curl -fs http://localhost:${SERVER_PORT:-8084}/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
