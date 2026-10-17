package com.aquatech.sync;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncJobServiceTest {

    private final ReplicaRepository origen =
            mock(ReplicaRepository.class);

    private final ReplicaLecturaService lectura =
            mock(ReplicaLecturaService.class);

    private final DynamoReplicaRepository destino =
            mock(DynamoReplicaRepository.class);

    private final SyncJobService servicio =
            new SyncJobService(origen, lectura, destino);

    @Test
    void errorDeEscrituraDejaJobFallidoYPermiteOtroJob() {
        when(origen.contarClientes()).thenReturn(1L);
        when(origen.contarPagos()).thenReturn(0L);

        ClienteReplica cliente = new ClienteReplica(
                "1", new ClienteReplica.Datos("Prueba", true));

        doAnswer(invocacion -> {
            Consumer<List<ClienteReplica>> procesar =
                    invocacion.getArgument(0);
            procesar.accept(List.of(cliente));
            return 1L;
        }).when(lectura).recorrerClientes(any());

        when(destino.sincronizarCliente(any()))
                .thenThrow(new IllegalStateException(
                        "Error de escritura simulado"));

        JobAceptado aceptado = servicio.crearJob();
        servicio.avanzarJobs();

        JobProgreso resultado = servicio
                .consultarJob(aceptado.jobId()).orElseThrow();

        assertEquals(EstadoJob.FALLIDO, resultado.estado());
        assertTrue(resultado.progreso() < 100);
        assertTrue(resultado.mensaje()
                .contains("escrituras confirmadas: 0"));

        verify(lectura, never()).recorrerPagos(any());
        assertDoesNotThrow(() -> servicio.crearJob());
    }

    @Test
    void rechazaSegundoJobMientrasExisteUnoPendiente() {
        servicio.crearJob();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> servicio.crearJob());

        assertEquals(409, error.getStatusCode().value());
    }
}