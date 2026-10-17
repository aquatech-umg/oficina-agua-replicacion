package com.aquatech.sync;

public record ClienteReplica(
        String clienteId,
        Datos datos
) {
    public record Datos(
            String nombre,
            boolean activo
    ) {
    }
}