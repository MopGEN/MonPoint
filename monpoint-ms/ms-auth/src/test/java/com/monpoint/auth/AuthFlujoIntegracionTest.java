package com.monpoint.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paso 7.4 — Verificación integral: contexto completo contra MongoDB real (requiere
 * {@code docker compose up -d mongodb}). Solo lee: usa el admin que siembra {@code DataInitializer}
 * con AUTOTACHI_VICTOR_PASSWORD del entorno. El arranque puede crear las cuatro cuentas;
 * ejecutar contra una base de pruebas aislada con SEMILLA_NOMINAL_HABILITADA=true.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class AuthFlujoIntegracionTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${AUTOTACHI_VICTOR_PASSWORD}")
    private String victorPassword;

    @Test
    void loginDelAdminSembrado_YLuegoMe_DevuelveSuPerfilDesdeMongo() throws Exception {
        String respuesta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonMapper.builder().build().writeValueAsString(Map.of(
                                "correo", "VICTOR.ORTIZ@autotachi.mx", "password", victorPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(respuesta, "$.token");

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("victor.ortiz@autotachi.mx"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginConPasswordIncorrecta_ContraLaBDReal_Retorna401Opaco() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo": "victor.ortiz@autotachi.mx", "password": "Incorrecta1!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales incorrectas"));
    }

    @Test
    void actuatorHealth_EsPublicoYReportaUpConMongoArriba() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
