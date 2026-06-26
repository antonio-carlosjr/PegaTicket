package com.ticketeira.user.service;

import com.ticketeira.user.domain.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PromotorStatusEmailService {

    private static final Logger log = LoggerFactory.getLogger(PromotorStatusEmailService.class);

    private final EmailService emailService;
    private final String frontendUrl;

    public PromotorStatusEmailService(EmailService emailService,
                                      @Value("${app.frontend.base-url:http://localhost:5173}") String frontendUrl) {
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    public void enviarAprovacao(Usuario usuario) {
        try {
            emailService.enviarHtml(
                    usuario.getEmail(),
                    "Sua conta de Promotor foi Aprovada! 🎉",
                    "email/promotor-aprovado",
                    Map.of(
                            "nome", usuario.getNome(),
                            "linkApp", frontendUrl
                    )
            );
            log.info("E-mail de aprovacao enviado para {}", usuario.getEmail());
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de aprovacao para {}", usuario.getEmail(), e);
        }
    }

    public void enviarRejeicao(Usuario usuario, String motivo) {
        try {
            emailService.enviarHtml(
                    usuario.getEmail(),
                    "Atualização sobre sua conta de Promotor",
                    "email/promotor-rejeitado",
                    Map.of(
                            "nome", usuario.getNome(),
                            "motivo", motivo != null ? motivo : "Não especificado.",
                            "linkApp", frontendUrl
                    )
            );
            log.info("E-mail de rejeicao enviado para {}", usuario.getEmail());
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de rejeicao para {}", usuario.getEmail(), e);
        }
    }
}
