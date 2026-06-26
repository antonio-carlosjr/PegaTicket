package com.ticketeira.event.service;

import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.dto.EventoCreateRequest;
import com.ticketeira.event.dto.EventoUpdateRequest;
import com.ticketeira.event.repository.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Evento criar(Long promotorId, EventoCreateRequest req) {
        Evento evento = Evento.criar(
                promotorId,
                req.titulo(),
                req.descricao(),
                req.dataInicio(),
                req.dataFim(),
                req.local(),
                req.tipo(),
                req.capacidade(),
                req.preco(),
                req.prazoReembolsoDias(),
                req.imagemUrl()
        );
        return eventRepository.save(evento);
    }

    @Transactional
    public Evento editar(Long promotorId, Long eventoId, EventoUpdateRequest req) {
        Evento evento = carregarComOwnership(promotorId, eventoId);
        evento.atualizarDados(
                req.titulo(),
                req.descricao(),
                req.dataInicio(),
                req.dataFim(),
                req.local(),
                req.tipo(),
                req.capacidade(),
                req.preco(),
                req.prazoReembolsoDias(),
                req.imagemUrl()
        );
        return eventRepository.save(evento);
    }

    @Transactional
    public Evento publicar(Long promotorId, Long eventoId) {
        Evento evento = carregarComOwnership(promotorId, eventoId);
        evento.publicar();
        return eventRepository.save(evento);
    }

    @Transactional
    public Evento cancelar(Long promotorId, Long eventoId) {
        Evento evento = carregarComOwnership(promotorId, eventoId);
        evento.cancelar();
        return eventRepository.save(evento);
    }

    @Transactional(readOnly = true)
    public Page<Evento> listarMeus(Long promotorId, Pageable pageable) {
        return eventRepository.findByPromotorId(promotorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Evento> listarPublicados(String q, TipoEvento tipo, OffsetDateTime de,
                                         OffsetDateTime ate, Pageable pageable) {
        return eventRepository.buscarPublicados(q, tipo, de, ate, pageable);
    }

    @Transactional(readOnly = true)
    public Evento detalhe(Long userId, Long eventoId) {
        Evento evento = eventRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado."));
        // Evento publicado: qualquer autenticado pode ver
        if (evento.getStatus() == StatusEvento.PUBLICADO) {
            return evento;
        }
        // RASCUNHO/CANCELADO/REALIZADO: so o owner ve; outros recebem 404 (nao vaza existencia)
        if (!evento.getPromotorId().equals(userId)) {
            throw new NotFoundException("Evento nao encontrado.");
        }
        return evento;
    }

    /** Carrega evento e verifica ownership: se nao for do promotor, lanca 404 (nao vaza existencia). */
    private Evento carregarComOwnership(Long promotorId, Long eventoId) {
        Evento evento = eventRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado."));
        if (!evento.getPromotorId().equals(promotorId)) {
            throw new NotFoundException("Evento nao encontrado.");
        }
        return evento;
    }
}
