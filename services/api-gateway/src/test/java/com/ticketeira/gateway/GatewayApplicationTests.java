package com.ticketeira.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=test-secret-com-mais-de-32-bytes-para-passar-na-validacao",
        "jwt.expiration-ms=60000"
})
class GatewayApplicationTests {

    @Test
    void contextLoads() {
        // O proprio @SpringBootTest verifica que o contexto sobe com routes + filtro JWT.
    }
}
