package com.aquatech.sync;

import java.util.UUID;

public record JobProgreso(
    UUID jobId,
    EstadoJob estado,
    int progreso,
    String mensaje
) {
}