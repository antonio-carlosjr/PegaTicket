package com.ticketeira.user.service;

import com.ticketeira.user.domain.Papel;
import com.ticketeira.user.domain.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsula o envio de e-mails de boas-vindas pos-cadastro.
 * Falhas no envio (ex.: SMTP fora do ar) sao engolidas para nao impedir
 * o cadastro do usuario - ja foi commitado no banco.
 */
@Service
public class WelcomeEmailService {

    private static final Logger log = LoggerFactory.getLogger(WelcomeEmailService.class);

    private final EmailService emailService;
    private final String frontendBaseUrl;

    public WelcomeEmailService(EmailService emailService,
                               @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public void enviarBoasVindas(Usuario usuario) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("nome", usuario.getNome());
            vars.put("papel", usuario.getPapel().name());
            vars.put("linkApp", frontendBaseUrl);

            String assunto = usuario.getPapel() == Papel.PROMOTOR
                    ? "Recebemos seu cadastro de promotor - PegaTicket"
                    : "Bem-vindo(a) ao PegaTicket!";

            emailService.enviarHtml(usuario.getEmail(), assunto, "email/welcome", vars);
        } catch (Exception e) {
            // Cadastro nao deve falhar por causa de email. Loga e segue.
            log.warn("Falha ao enviar email de boas-vindas para usuario id={}: {}",
                    usuario.getId(), e.getMessage());
        }
    }
}
