package com.ticketeira.user.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.from = from;
    }

    /**
     * Renderiza e envia um email HTML. Locale pt-BR.
     */
    public void enviarHtml(String para, String assunto, String template, Map<String, Object> variaveis) {
        try {
            Context ctx = new Context(new Locale("pt", "BR"));
            variaveis.forEach(ctx::setVariable);
            String html = templateEngine.process(template, ctx);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(html, true);

            mailSender.send(mime);
            log.info("Email enviado para {} (template={})", para, template);
        } catch (MessagingException e) {
            log.error("Falha ao enviar email para {}: {}", para, e.getMessage());
            throw new IllegalStateException("Nao foi possivel enviar o email.", e);
        }
    }
}
