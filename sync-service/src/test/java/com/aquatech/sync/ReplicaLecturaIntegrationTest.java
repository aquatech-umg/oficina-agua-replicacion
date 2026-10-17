package com.aquatech.sync;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "sync.page-size=137"
)
class ReplicaLecturaIntegrationTest {

    @Autowired
    ReplicaRepository repository;

    @Autowired
    ReplicaLecturaService lectura;

    @Test
    void recorreClientesYPagosDeLaReplica() {
        long clientesEsperados = repository.contarClientes();
        long pagosEsperados = repository.contarPagos();

        assertTrue(clientesEsperados > 0);
        assertTrue(pagosEsperados > 0);

        AtomicLong ultimoCliente = new AtomicLong();
        AtomicLong ultimoPago = new AtomicLong();

        long clientes = lectura.recorrerClientes(pagina -> {
            assertTrue(pagina.size() <= 137);

            for (ClienteReplica cliente : pagina) {
                long id = Long.parseLong(cliente.clienteId());
                assertTrue(id > ultimoCliente.get());
                ultimoCliente.set(id);
                assertNotNull(cliente.datos());
                assertFalse(cliente.datos().nombre().isBlank());
            }
        });

        long pagos = lectura.recorrerPagos(pagina -> {
            assertTrue(pagina.size() <= 137);

            for (PagoReplica pago : pagina) {
                long id = Long.parseLong(pago.pagoId());
                assertTrue(id > ultimoPago.get());
                ultimoPago.set(id);
                assertNotNull(pago.clienteId());
                assertTrue(pago.datos().monto()
                        .matches("[0-9]+\\.[0-9]{2}"));
                assertTrue(pago.datos().fechaPago()
                        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"));
            }
        });

        assertEquals(clientesEsperados, clientes);
        assertEquals(pagosEsperados, pagos);

        System.out.printf(
                "Lectura verificada: %d clientes y %d pagos.%n",
                clientes, pagos);
    }
}