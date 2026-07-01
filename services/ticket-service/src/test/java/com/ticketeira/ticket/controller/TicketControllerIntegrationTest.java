package com.ticketeira.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.messaging.PedidoCriadoPublisher;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @MockBean
    EventClient eventClient;

    @MockBean
    PedidoCriadoPublisher pedidoCriadoPublisher;

    private static final Long USUARIO_ID = 10L;
    private static final Long EVENTO_ID = 42L;

    @BeforeEach
    void setUp() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();

        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Show", "GRATUITO", "PUBLICADO", 10, 100,
                        null, 1L, java.time.OffsetDateTime.now().plusDays(30), null));
        doNothing().when(eventClient).reservarVaga(anyLong());
        doNothing().when(eventClient).liberarVaga(anyLong());
    }

    // ---- B9: Auth/borda ----

    @Test
    void inscrever_semUserId_retorna401() throws Exception {
        mvc.perform(post("/tickets/inscricoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("eventoId", EVENTO_ID))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inscrever_semEventoId_retorna400() throws Exception {
        mvc.perform(post("/tickets/inscricoes")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inscrever_eventoIdInvalido_retorna400() throws Exception {
        mvc.perform(post("/tickets/inscricoes")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\": \"nao-numerico\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- B1: Caminho-feliz ----

    @Test
    void inscrever_caminhoFeliz_retorna201ComIngresso() throws Exception {
        mvc.perform(post("/tickets/inscricoes")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("eventoId", EVENTO_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventoId").value(EVENTO_ID))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.ingresso.codigoUnico").isNotEmpty())
                .andExpect(jsonPath("$.ingresso.status").value("ATIVO"));

        assertThat(inscricaoRepository.count()).isEqualTo(1);
        assertThat(ingressoRepository.count()).isEqualTo(1);
    }

    @Test
    void inscrever_duplicado_retorna409() throws Exception {
        // Primeira inscricao
        mvc.perform(post("/tickets/inscricoes")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("eventoId", EVENTO_ID))))
                .andExpect(status().isCreated());

        // Segunda tentativa: ja inscrito (pre-check)
        mvc.perform(post("/tickets/inscricoes")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("eventoId", EVENTO_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("JA_INSCRITO"));
    }

    // ---- B7: GET /tickets/me ----

    @Test
    void meusIngressos_semUserId_retorna401() throws Exception {
        mvc.perform(get("/tickets/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meusIngressos_usuarioSemIngressos_retornaListaVazia() throws Exception {
        mvc.perform(get("/tickets/me")
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void meusIngressos_retorna2Ingressos() throws Exception {
        // Inscreve em 2 eventos diferentes
        Long evento2 = 43L;
        when(eventClient.getEvento(evento2))
                .thenReturn(new EventResumo(evento2, "Show 2", "GRATUITO", "PUBLICADO", 5, 50,
                        null, 1L, java.time.OffsetDateTime.now().plusDays(30), null));

        mvc.perform(post("/tickets/inscricoes")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("eventoId", EVENTO_ID))))
                .andExpect(status().isCreated());

        mvc.perform(post("/tickets/inscricoes")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("eventoId", evento2))))
                .andExpect(status().isCreated());

        mvc.perform(get("/tickets/me")
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /**
     * Regressao CR-S4-04 (P1): inscricao PENDENTE_PAGAMENTO (sem ingresso) DEVE aparecer em
     * GET /tickets/me para a tela "Meus ingressos" exibir "aguardando confirmacao" (US-041 crit.5).
     * Antes do LEFT JOIN, o INNER JOIN excluia inscricoes sem ingresso e o card nunca renderizava.
     */
    @Test
    void meusIngressos_incluiPendentePagamentoSemIngresso() throws Exception {
        // ATIVA com ingresso
        Inscricao ativa = inscricaoRepository.save(Inscricao.criar(USUARIO_ID, EVENTO_ID));
        ingressoRepository.save(Ingresso.emitir(ativa.getId()));
        // PENDENTE_PAGAMENTO sem ingresso (ramo PAGO antes de pagamento.aprovado)
        inscricaoRepository.save(Inscricao.pendentePagamento(USUARIO_ID, 43L));

        mvc.perform(get("/tickets/me")
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // ha exatamente um item PENDENTE_PAGAMENTO com codigoUnico vazio (sem QR)
                .andExpect(jsonPath("$[?(@.statusInscricao == 'PENDENTE_PAGAMENTO')].codigoUnico")
                        .value(org.hamcrest.Matchers.hasItem("")))
                .andExpect(jsonPath("$[?(@.statusInscricao == 'ATIVA')].codigoUnico")
                        .exists());
    }

    @Test
    void meusIngressos_naoVazaDeOutroUsuario() throws Exception {
        Long outroUsuario = 99L;
        mvc.perform(post("/tickets/inscricoes")
                        .header("X-User-Id", outroUsuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("eventoId", EVENTO_ID))))
                .andExpect(status().isCreated());

        // usuario 10 nao tem ingressos
        mvc.perform(get("/tickets/me")
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- B8: GET /tickets/inscricoes/me ----

    @Test
    void historicoInscricoes_semUserId_retorna401() throws Exception {
        mvc.perform(get("/tickets/inscricoes/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historicoInscricoes_paginado() throws Exception {
        mvc.perform(post("/tickets/inscricoes")
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("eventoId", EVENTO_ID))))
                .andExpect(status().isCreated());

        mvc.perform(get("/tickets/inscricoes/me")
                        .header("X-User-Id", USUARIO_ID)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventoId").value(EVENTO_ID))
                .andExpect(jsonPath("$.content[0].status").value("ATIVA"));
    }

    @Test
    void historicoInscricoes_sizeCapadoEm100() throws Exception {
        mvc.perform(get("/tickets/inscricoes/me")
                        .header("X-User-Id", USUARIO_ID)
                        .param("size", "9999"))
                .andExpect(status().isOk()); // Nao deve dar 500
    }
}
