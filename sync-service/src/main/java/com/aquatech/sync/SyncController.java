package com.aquatech.sync;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncJobService servicio;

    public SyncController(SyncJobService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/resumen")
    public ResponseEntity<JobAceptado> iniciar() {
        return ResponseEntity.accepted()
            .body(servicio.crearJob());
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> consultar(
            @PathVariable("jobId") UUID jobId) {

        return servicio.consultarJob(jobId)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(404)
                .body(Map.of(
                    "mensaje", "No existe un trabajo con ese jobId."
                )));
    }
}