package com.aquatech.sync;

public record PagoReplica(
        String pagoId,
        String clienteId,
        Datos datos
) {
    public record Datos(
            String reciboId,
            String metodoPagoId,
            String monto,
            String fechaPago
    ) {
    }
}