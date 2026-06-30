package com.ticketeira.event.controller;

import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.messaging.EventoPublisher;
import com.ticketeira.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InternalEventControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EventRepository eventRepository;

    @MockBean
    EventoPublisher eventoPublisher;

    private static final String TOKEN_OK = "test-internal-secret";
    private static final String TOKEN_ERRADO = "token-errado";

    private Long criarEventoPublicado(int capacidade) {
        Evento e = Evento.criar(1L, "Evento Teste", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, capacidade, null, null, null);
        e.publicar();
        return eventRepository.save(e).getId();
    }

    @BeforeEach
    void limpar() {
        eventRepository.deleteAll();
    }

    // ---- Resumo interno (GET /internal/events/{id}) ----

    @Test
    void resumo_semToken_retorna403() throws Exception {
        Long id = criarEventoPublicado(10);
        mvc.perform(get("/internal/events/{id}", id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACESSO_INTERNO_NEGADO"));
    }

    @Test
    void resumo_tokenCorreto_retorna200ComCampos() throws Exception {
        Long id = criarEventoPublicado(10);
        mvc.perform(get("/internal/events/{id}", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.tipo").value("GRATUITO"))
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.vagasDisponiveis").value(10))
                .andExpect(jsonPath("$.capacidade").value(10));
    }

    @Test
    void resumo_inexistente_retorna404() throws Exception {
        mvc.perform(get("/internal/events/{id}", 99999L)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("EVENTO_NAO_ENCONTRADO"));
    }

    // ---- A6: Autorizacao interna ----

    @Test
    void reservarVaga_semToken_retorna403() throws Exception {
        Long id = criarEventoPublicado(10);
        mvc.perform(post("/internal/events/{id}/reservar-vaga", id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACESSO_INTERNO_NEGADO"));
    }

    @Test
    void reservarVaga_tokenErrado_retorna403() throws Exception {
        Long id = criarEventoPublicado(10);
        mvc.perform(post("/internal/events/{id}/reservar-vaga", id)
                        .header("X-Internal-Token", TOKEN_ERRADO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACESSO_INTERNO_NEGADO"));
    }

    @Test
    void reservarVaga_tokenCorreto_retorna200() throws Exception {
        Long id = criarEventoPublicado(10);
        mvc.perform(post("/internal/events/{id}/reservar-vaga", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(id))
                .andExpect(jsonPath("$.vagasDisponiveis").value(9));
    }

    @Test
    void reservarVaga_esgotado_retorna409() throws Exception {
        Long id = criarEventoPublicado(1);
        // Consome a unica vaga
        mvc.perform(post("/internal/events/{id}/reservar-vaga", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk());
        // Segunda tentativa: esgotado
        mvc.perform(post("/internal/events/{id}/reservar-vaga", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("EVENTO_ESGOTADO"));
    }

    @Test
    void reservarVaga_inexistente_retorna404() throws Exception {
        mvc.perform(post("/internal/events/{id}/reservar-vaga", 99999L)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("EVENTO_NAO_ENCONTRADO"));
    }

    @Test
    void reservarVaga_naoPublicado_retorna422() throws Exception {
        // Cria em RASCUNHO (nao publica)
        Evento e = Evento.criar(1L, "Rascunho", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 10, null, null, null);
        Long id = eventRepository.save(e).getId();

        mvc.perform(post("/internal/events/{id}/reservar-vaga", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("EVENTO_NAO_PUBLICADO"));
    }

    @Test
    void liberarVaga_semToken_retorna403() throws Exception {
        Long id = criarEventoPublicado(10);
        mvc.perform(post("/internal/events/{id}/liberar-vaga", id))
                .andExpect(status().isForbidden());
    }

    @Test
    void liberarVaga_tokenCorreto_retorna200() throws Exception {
        Long id = criarEventoPublicado(10);
        // Consome uma vaga primeiro
        mvc.perform(post("/internal/events/{id}/reservar-vaga", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk());
        // Libera (compensacao)
        mvc.perform(post("/internal/events/{id}/liberar-vaga", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasDisponiveis").value(10)); // voltou ao original
    }

    @Test
    void liberarVaga_noTeto_retorna200_naoExcede() throws Exception {
        Long id = criarEventoPublicado(10); // vagas=capacidade=10
        // liberar sem ter reservado = no-op (teto)
        mvc.perform(post("/internal/events/{id}/liberar-vaga", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasDisponiveis").value(10));
    }

    @Test
    void liberarVaga_inexistente_retorna404() throws Exception {
        mvc.perform(post("/internal/events/{id}/liberar-vaga", 99999L)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isNotFound());
    }
}
