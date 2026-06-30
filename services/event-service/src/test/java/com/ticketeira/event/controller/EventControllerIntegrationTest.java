package com.ticketeira.event.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.dto.EventoCreateRequest;
import com.ticketeira.event.dto.EventoUpdateRequest;
import com.ticketeira.event.messaging.EventoPublisher;
import com.ticketeira.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    EventRepository eventRepository;

    @MockBean
    EventoPublisher eventoPublisher;

    private static final Long PROMOTOR_A = 1L;
    private static final Long PROMOTOR_B = 2L;
    private static final Long PARTICIPANTE_ID = 99L;

    @BeforeEach
    void limpar() {
        eventRepository.deleteAll();
    }

    private EventoCreateRequest reqGratuito(String titulo) {
        return new EventoCreateRequest(
                titulo, "Descricao do evento",
                OffsetDateTime.of(2026, 8, 10, 14, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 8, 10, 18, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Parque Central", TipoEvento.GRATUITO,
                100, null, null, null);
    }

    private EventoCreateRequest reqPago(String titulo) {
        return new EventoCreateRequest(
                titulo, null,
                OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 9, 1, 23, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Arena Norte", TipoEvento.PAGO,
                200, new BigDecimal("59.90"), 7, "http://img.example.com/img.jpg");
    }

    // ---- Authz cross-papel ----

    @Test
    void participante_naoPoderCriarEvento_retorna403() throws Exception {
        long countAntes = eventRepository.count();

        mvc.perform(post("/events")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reqGratuito("Teste"))))
                .andExpect(status().isForbidden());

        assertThat(eventRepository.count()).isEqualTo(countAntes);
    }

    @Test
    void semHeaderUserId_retorna401() throws Exception {
        mvc.perform(post("/events")
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reqGratuito("Teste"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void participante_naoPoderEditarEvento_retorna403() throws Exception {
        mvc.perform(put("/events/1")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new EventoUpdateRequest(
                                "T", null,
                                OffsetDateTime.now().plusDays(1),
                                OffsetDateTime.now().plusDays(1).plusHours(2),
                                "L", TipoEvento.GRATUITO, 10, null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void participante_naoPoderPublicar_retorna403() throws Exception {
        mvc.perform(post("/events/1/publicar")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void participante_naoPoderCancelar_retorna403() throws Exception {
        mvc.perform(post("/events/1/cancelar")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void participante_naoPoderAcessarMeus_retorna403() throws Exception {
        mvc.perform(get("/events/meus")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void promotor_poderCriarEvento_retorna201() throws Exception {
        mvc.perform(post("/events")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reqGratuito("Evento Válido"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.promotorId").value(PROMOTOR_A));
    }

    @Test
    void qualquerAutenticado_poderListarEventosPublicados() throws Exception {
        mvc.perform(get("/events")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isOk());

        mvc.perform(get("/events")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk());
    }

    // ---- Caminho-feliz do promotor (end-to-end) ----

    @Test
    void caminhoFeliz_criar_publicar_listar_detalhe_cancelar() throws Exception {
        // 1. Criar evento
        MvcResult criarResult = mvc.perform(post("/events")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reqGratuito("Show da Terra"))))
                .andExpect(status().isCreated())
                .andReturn();

        Long eventoId = mapper.readTree(criarResult.getResponse().getContentAsString())
                .get("id").asLong();

        // 2. GET /events — evento NAO aparece (ainda RASCUNHO)
        mvc.perform(get("/events")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 3. Publicar
        mvc.perform(post("/events/" + eventoId + "/publicar")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.vagasDisponiveis").value(100));

        // 4. GET /events — evento aparece
        mvc.perform(get("/events")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Show da Terra"));

        // 5. GET /events/{id} — detalhe correto
        mvc.perform(get("/events/" + eventoId)
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Show da Terra"))
                .andExpect(jsonPath("$.status").value("PUBLICADO"));

        // 6. Cancelar
        mvc.perform(post("/events/" + eventoId + "/cancelar")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        // 7. GET /events — evento sumiu da lista
        mvc.perform(get("/events")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---- Listagem: filtros e paginacao ----

    @Test
    void listar_soRetornaPublicados() throws Exception {
        // Cria 1 RASCUNHO, 1 PUBLICADO, 1 CANCELADO
        criarEvento("Rascunho A", PROMOTOR_A);
        Long idPub = criarEventoId("Publicado B", PROMOTOR_A);
        Long idCanc = criarEventoId("Cancelado C", PROMOTOR_A);
        publicarEvento(idPub, PROMOTOR_A);
        cancelarEvento(idCanc, PROMOTOR_A);

        mvc.perform(get("/events")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Publicado B"));
    }

    @Test
    void listar_filtroPorQ_titulo_eCaseInsensitive() throws Exception {
        Long id = criarEventoId("Festival da Luz", PROMOTOR_A);
        publicarEvento(id, PROMOTOR_A);

        mvc.perform(get("/events?q=festival")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/events?q=FESTIVAL")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listar_filtroPorQ_local() throws Exception {
        EventoCreateRequest req = new EventoCreateRequest(
                "Evento no Parque", null,
                OffsetDateTime.of(2026, 8, 10, 14, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 8, 10, 18, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Parque das Flores", TipoEvento.GRATUITO,
                50, null, null, null);
        Long id = criarEventoIdComReq(req, PROMOTOR_A);
        publicarEvento(id, PROMOTOR_A);

        mvc.perform(get("/events?q=flores")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listar_filtroPorTipo() throws Exception {
        Long idGrat = criarEventoId("Gratuito", PROMOTOR_A);
        Long idPago = criarEventoIdComReq(reqPago("Show Pago"), PROMOTOR_A);
        publicarEvento(idGrat, PROMOTOR_A);
        publicarEvento(idPago, PROMOTOR_A);

        mvc.perform(get("/events?tipo=GRATUITO")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("GRATUITO"));

        mvc.perform(get("/events?tipo=PAGO")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("PAGO"));
    }

    @Test
    void listar_semFiltro_retorna200() throws Exception {
        // Valida que a query com todos os filtros null nao lanca erro
        mvc.perform(get("/events")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listar_semMatch_retornaListaVazia200() throws Exception {
        Long id = criarEventoId("Evento X", PROMOTOR_A);
        publicarEvento(id, PROMOTOR_A);

        mvc.perform(get("/events?q=inexistentexyz")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listar_paginacao() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Long evtId = criarEventoId("Evento " + i, PROMOTOR_A);
            publicarEvento(evtId, PROMOTOR_A);
        }

        mvc.perform(get("/events?page=0&size=2")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));

        mvc.perform(get("/events?page=1&size=2")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    // ---- Detalhe ----

    @Test
    void detalhe_inexistente_retorna404() throws Exception {
        mvc.perform(get("/events/9999")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void detalhe_rascunhoDeOutroPromotor_retorna404() throws Exception {
        Long id = criarEventoId("Rascunho A", PROMOTOR_A);

        mvc.perform(get("/events/" + id)
                        .header("X-User-Id", PROMOTOR_B))
                .andExpect(status().isNotFound());
    }

    @Test
    void detalhe_rascunhoDoOwner_retorna200() throws Exception {
        Long id = criarEventoId("Meu Rascunho", PROMOTOR_A);

        mvc.perform(get("/events/" + id)
                        .header("X-User-Id", PROMOTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RASCUNHO"));
    }

    @Test
    void detalhe_canceladoAlheio_retorna404() throws Exception {
        Long id = criarEventoId("Cancelado A", PROMOTOR_A);
        cancelarEvento(id, PROMOTOR_A);

        mvc.perform(get("/events/" + id)
                        .header("X-User-Id", PROMOTOR_B))
                .andExpect(status().isNotFound());
    }

    @Test
    void detalhe_canceladoOwner_retorna200() throws Exception {
        Long id = criarEventoId("Cancelado A", PROMOTOR_A);
        cancelarEvento(id, PROMOTOR_A);

        mvc.perform(get("/events/" + id)
                        .header("X-User-Id", PROMOTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    // ---- Transicoes invalidas ----

    @Test
    void publicar_jaPublicado_retorna409() throws Exception {
        Long id = criarEventoId("Show X", PROMOTOR_A);
        publicarEvento(id, PROMOTOR_A);

        mvc.perform(post("/events/" + id + "/publicar")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("EVENTO_JA_PUBLICADO"));
    }

    @Test
    void cancelar_jaCancelado_retorna409() throws Exception {
        Long id = criarEventoId("Show Y", PROMOTOR_A);
        cancelarEvento(id, PROMOTOR_A);

        mvc.perform(post("/events/" + id + "/cancelar")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("EVENTO_JA_CANCELADO"));
    }

    @Test
    void editar_publicado_retorna409NaoEditavel() throws Exception {
        Long id = criarEventoId("Show Z", PROMOTOR_A);
        publicarEvento(id, PROMOTOR_A);

        EventoUpdateRequest req = new EventoUpdateRequest(
                "Tentativa", null,
                OffsetDateTime.of(2026, 8, 10, 14, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 8, 10, 18, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);

        mvc.perform(put("/events/" + id)
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("EVENTO_NAO_EDITAVEL"));
    }

    // ---- Ownership cross-promotor ----

    @Test
    void promtorB_naoPoderEditarEventoDeA_retorna404() throws Exception {
        Long id = criarEventoId("Evento de A", PROMOTOR_A);

        EventoUpdateRequest req = new EventoUpdateRequest(
                "Hackeado", null,
                OffsetDateTime.of(2026, 8, 10, 14, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 8, 10, 18, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);

        mvc.perform(put("/events/" + id)
                        .header("X-User-Id", PROMOTOR_B)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void promtorB_naoPoderPublicarEventoDeA_retorna404() throws Exception {
        Long id = criarEventoId("Evento de A", PROMOTOR_A);

        mvc.perform(post("/events/" + id + "/publicar")
                        .header("X-User-Id", PROMOTOR_B)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isNotFound());
    }

    // ---- Validacao de campo ----

    @Test
    void criar_semTitulo_retorna400() throws Exception {
        EventoCreateRequest req = new EventoCreateRequest(
                "", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 100, null, null, null);

        mvc.perform(post("/events")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_pagoSemPreco_retorna400() throws Exception {
        EventoCreateRequest req = new EventoCreateRequest(
                "Festival", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.PAGO, 100, null, null, null);

        mvc.perform(post("/events")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ---- Parametros de query/path malformados (contrato: 400, nunca 500) ----

    @Test
    void listar_tipoInvalido_retorna400() throws Exception {
        mvc.perform(get("/events?tipo=FOO")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_dataSemOffset_retorna400() throws Exception {
        // <input type="datetime-local"> envia "2026-06-20T14:00" (sem offset).
        // Deve ser 400 tipado, nunca 500 (api-contracts.md §6).
        mvc.perform(get("/events?de=2026-06-20T14:00")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detalhe_idNaoNumerico_retorna400() throws Exception {
        mvc.perform(get("/events/abc")
                        .header("X-User-Id", PARTICIPANTE_ID))
                .andExpect(status().isBadRequest());
    }

    // ---- Helpers ----

    private void criarEvento(String titulo, Long promotorId) throws Exception {
        mvc.perform(post("/events")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reqGratuito(titulo))))
                .andExpect(status().isCreated());
    }

    private Long criarEventoId(String titulo, Long promotorId) throws Exception {
        MvcResult result = mvc.perform(post("/events")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reqGratuito(titulo))))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long criarEventoIdComReq(EventoCreateRequest req, Long promotorId) throws Exception {
        MvcResult result = mvc.perform(post("/events")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void publicarEvento(Long id, Long promotorId) throws Exception {
        mvc.perform(post("/events/" + id + "/publicar")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk());
    }

    private void cancelarEvento(Long id, Long promotorId) throws Exception {
        mvc.perform(post("/events/" + id + "/cancelar")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk());
    }
}
