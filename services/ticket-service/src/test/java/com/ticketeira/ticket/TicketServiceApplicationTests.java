package com.ticketeira.ticket;

import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.messaging.PedidoCriadoPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TicketServiceApplicationTests {

    // RabbitMQ excluido no perfil test — beans dependentes precisam ser mockados
    @MockBean
    PedidoCriadoPublisher pedidoCriadoPublisher;

    @MockBean
    EventClient eventClient;

    @Test
    void contextLoads() {
    }
}
