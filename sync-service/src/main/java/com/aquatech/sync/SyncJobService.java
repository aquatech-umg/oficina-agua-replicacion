package com.aquatech.sync;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SyncJobService {

    private static final Logger log =
            LoggerFactory.getLogger(SyncJobService.class);

    private final ConcurrentHashMap<UUID, JobProgreso> jobs =
            new ConcurrentHashMap<>();

    private final AtomicReference<UUID> activo = new AtomicReference<>();

    private final ReplicaRepository origen;
    private final ReplicaLecturaService lectura;
    private final DynamoReplicaRepository destino;

    public SyncJobService(
            ReplicaRepository origen,
            ReplicaLecturaService lectura,
            DynamoReplicaRepository destino) {
        this.origen = origen;
        this.lectura = lectura;
        this.destino = destino;
    }

    public JobAceptado crearJob() {
        UUID id = UUID.randomUUID();

        if (!activo.compareAndSet(null, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una sincronizacion pendiente o en progreso.");
        }

        jobs.put(id, new JobProgreso(
                id, EstadoJob.PENDIENTE, 0,
                "Trabajo recibido. Pendiente de ejecucion."));

        return new JobAceptado(id, EstadoJob.PENDIENTE);
    }

    public Optional<JobProgreso> consultarJob(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Scheduled(fixedDelay = 500)
    public void avanzarJobs() {
        UUID id = activo.get();

        if (id == null) {
            return;
        }

        JobProgreso job = jobs.get(id);

        if (job == null || job.estado() != EstadoJob.PENDIENTE) {
            return;
        }

        jobs.put(id, new JobProgreso(
                id, EstadoJob.EN_PROGRESO, 0,
                "Contando registros del origen."));

        // Posicion 0: procesados; posicion 1: escrituras confirmadas.
        long[] contadores = {0, 0};

        try {
            long total = Math.addExact(
                    origen.contarClientes(), origen.contarPagos());

            lectura.recorrerClientes(pagina -> {
                for (ClienteReplica cliente : pagina) {
                    if (destino.sincronizarCliente(cliente)) {
                        contadores[1]++;
                    }
                    contadores[0]++;
                    publicarProgreso(id, contadores, total);
                }
            });

            lectura.recorrerPagos(pagina -> {
                for (PagoReplica pago : pagina) {
                    if (destino.sincronizarPago(pago)) {
                        contadores[1]++;
                    }
                    contadores[0]++;
                    publicarProgreso(id, contadores, total);
                }
            });

            if (contadores[0] != total) {
                throw new IllegalStateException(
                        "El total del origen cambio durante el recorrido; "
                                + "se requiere una nueva ejecucion.");
            }

            jobs.put(id, new JobProgreso(
                    id, EstadoJob.COMPLETADO, 100,
                    mensaje(contadores, total)));

        } catch (Exception error) {
            log.error("Fallo la sincronizacion del job {}", id, error);

            int progreso = jobs.get(id).progreso();

            jobs.put(id, new JobProgreso(
                    id, EstadoJob.FALLIDO, progreso,
                    "Sincronizacion fallida. Procesados: "
                            + contadores[0]
                            + "; escrituras confirmadas: "
                            + contadores[1]
                            + ". Consulte el registro del servicio."));
        } finally {
            activo.compareAndSet(id, null);
        }
    }

    private void publicarProgreso(
            UUID id, long[] contadores, long total) {

        int progreso = total == 0 ? 0
                : (int) Math.min(99,
                        contadores[0] * 100.0 / total);

        jobs.put(id, new JobProgreso(
                id, EstadoJob.EN_PROGRESO, progreso,
                mensaje(contadores, total)));
    }

    private static String mensaje(long[] contadores, long total) {
        return "Procesados: " + contadores[0] + "/" + total
                + "; escrituras confirmadas: " + contadores[1]
                + "; sin cambios: "
                + (contadores[0] - contadores[1]) + ".";
    }
}