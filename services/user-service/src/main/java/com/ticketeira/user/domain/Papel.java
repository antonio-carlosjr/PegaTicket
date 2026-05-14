package com.ticketeira.user.domain;

public enum Papel {
    /** Usuario comum que se inscreve em eventos. */
    PARTICIPANTE,

    /** Organizador de eventos. Comeca com verificado=false ate aprovacao do admin. */
    PROMOTOR,

    /** Administrador da plataforma (aprova promotores, audita). */
    ADMIN
}
