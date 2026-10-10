package com.aquatech.sync;

import java.util.UUID;

public record JobAceptado(
    UUID jobId,
    EstadoJob estado
) {
}