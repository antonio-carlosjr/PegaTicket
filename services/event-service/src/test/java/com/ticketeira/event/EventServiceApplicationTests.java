package com.ticketeira.event;

import com.ticketeira.event.messaging.EventoPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EventServiceApplicationTests {

    @MockBean
    EventoPublisher eventoPublisher;

    @Test
    void contextLoads() {
    }
}
