package com.aquatech.sync;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReplicaLecturaService {

    private final ReplicaRepository repository;
    private final int tamanoPagina;

    public ReplicaLecturaService(
            ReplicaRepository repository,
            @Value("${sync.page-size:500}") int tamanoPagina) {

        if (tamanoPagina < 1 || tamanoPagina > 1000) {
            throw new IllegalArgumentException(
                    "sync.page-size debe estar entre 1 y 1000");
        }

        this.repository = repository;
        this.tamanoPagina = tamanoPagina;
    }

    public long recorrerClientes(
            Consumer<List<ClienteReplica>> procesarPagina) {

        long ultimoId = 0;
        long procesados = 0;

        while (true) {
            List<ClienteReplica> pagina =
                    repository.leerClientes(ultimoId, tamanoPagina);

            if (pagina.isEmpty()) {
                return procesados;
            }

            procesarPagina.accept(pagina);
            procesados += pagina.size();

            ultimoId = Long.parseLong(
                    pagina.get(pagina.size() - 1).clienteId());
        }
    }

    public long recorrerPagos(
            Consumer<List<PagoReplica>> procesarPagina) {

        long ultimoId = 0;
        long procesados = 0;

        while (true) {
            List<PagoReplica> pagina =
                    repository.leerPagos(ultimoId, tamanoPagina);

            if (pagina.isEmpty()) {
                return procesados;
            }

            procesarPagina.accept(pagina);
            procesados += pagina.size();

            ultimoId = Long.parseLong(
                    pagina.get(pagina.size() - 1).pagoId());
        }
    }
}