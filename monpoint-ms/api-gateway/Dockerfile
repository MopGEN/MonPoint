# =============================================================================
# api-gateway — imagen de despliegue
# =============================================================================
# Dos etapas: la primera compila con Maven y la segunda solo lleva el JRE y el
# jar. La imagen final no carga Maven, el codigo fuente ni las dependencias de
# compilacion. Mismo esquema que ms-auth.
#
# Construir (desde monpoint-ms/api-gateway):   docker build -t monpoint/api-gateway .
# En el sistema completo la construye docker compose (build: ../api-gateway).
# =============================================================================

# --- Etapa 1: compilacion ----------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Las pruebas se corren antes con ./mvnw test; aqui se omiten para que la
# imagen se construya rapido. El cache de ~/.m2 sobrevive entre construcciones
# y evita volver a descargar todas las dependencias cada vez.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package -DskipTests

# --- Etapa 2: ejecucion ------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Usuario sin privilegios: si alguien compromete la aplicacion, no es root.
RUN useradd --system --uid 10001 monpoint
COPY --from=build /app/target/api-gateway-*.jar app.jar
USER monpoint
EXPOSE 8080
# Docker (y compose) marcan el contenedor como healthy cuando /actuator/health
# responde UP.
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=10 \
  CMD curl -fs http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
