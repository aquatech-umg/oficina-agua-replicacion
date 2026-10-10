package com.aquatech.sync;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SyncJobService {

    private final ConcurrentHashMap<UUID, JobProgreso> jobs =
        new ConcurrentHashMap<>();

    public JobAceptado crearJob() {
        UUID jobId = UUID.randomUUID();

        jobs.put(jobId, new JobProgreso(
            jobId,
            EstadoJob.PENDIENTE,
            0,
            "Trabajo recibido. Simulacion pendiente."
        ));

        return new JobAceptado(jobId, EstadoJob.PENDIENTE);
    }

    public Optional<JobProgreso> consultarJob(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Scheduled(fixedDelay = 2000)
    public void avanzarJobs() {
        jobs.replaceAll((jobId, actual) -> {
            if (actual.estado() == EstadoJob.COMPLETADO
                    || actual.estado() == EstadoJob.FALLIDO) {
                return actual;
            }

            int progreso = Math.min(actual.progreso() + 20, 100);
            boolean terminado = progreso == 100;

            return new JobProgreso(
                jobId,
                terminado
                    ? EstadoJob.COMPLETADO
                    : EstadoJob.EN_PROGRESO,
                progreso,
                terminado
                    ? "Simulacion completada. No se copiaron datos."
                    : "Simulacion en progreso."
            );
        });
    }
}