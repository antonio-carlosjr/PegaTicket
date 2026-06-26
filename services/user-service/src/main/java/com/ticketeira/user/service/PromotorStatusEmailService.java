package com.ticketeira.user.service;

import com.ticketeira.user.domain.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class PromotorStatusEmailService {

    private static final Logger log = LoggerFactory.getLogger(PromotorStatusEmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String frontendUrl;

    public PromotorStatusEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.frontendUrl = System.getenv().getOrDefault("FRONTEND_URL", "http://localhost:5173");
    }

    @Async
    public void enviarAprovacao(Usuario usuario) {
        try {
            Context context = new Context();
            context.setVariable("nome", usuario.getNome());
            context.setVariable("linkApp", frontendUrl);

            String html = templateEngine.process("email/promotor-aprovado", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(usuario.getEmail());
            helper.setSubject("Sua conta de Promotor foi Aprovada! \uD83C\uDF89");
            helper.setText(html, true);

            mailSender.send(message);
            log.info("E-mail de aprovacao enviado para {}", usuario.getEmail());
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de aprovacao para {}", usuario.getEmail(), e);
        }
    }

    @Async
    public void enviarRejeicao(Usuario usuario, String motivo) {
        try {
            Context context = new Context();
            context.setVariable("nome", usuario.getNome());
            context.setVariable("motivo", motivo);
            context.setVariable("linkApp", frontendUrl);

            String html = templateEngine.process("email/promotor-rejeitado", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(usuario.getEmail());
            helper.setSubject("Atualização sobre sua conta de Promotor");
            helper.setText(html, true);

            mailSender.send(message);
            log.info("E-mail de rejeicao enviado para {}", usuario.getEmail());
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de rejeicao para {}", usuario.getEmail(), e);
        }
    }
}
