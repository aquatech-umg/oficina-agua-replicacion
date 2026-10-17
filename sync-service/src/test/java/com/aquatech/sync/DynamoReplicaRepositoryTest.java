package com.aquatech.sync;

import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DynamoReplicaRepositoryTest {

    private final DynamoDbClient dynamo = mock(DynamoDbClient.class);

    private final DynamoReplicaRepository repository =
            new DynamoReplicaRepository(dynamo);

    private ClienteReplica cliente() {
        return new ClienteReplica(
                "1", new ClienteReplica.Datos("Cliente prueba", true));
    }

    @Test
    void errorDeLecturaNoProvocaEscritura() {
        when(dynamo.getItem(any(GetItemRequest.class)))
                .thenThrow(new IllegalStateException("Lectura fallida"));

        assertThrows(IllegalStateException.class,
                () -> repository.sincronizarCliente(cliente()));

        verify(dynamo, never())
                .updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void errorDeEscrituraSePropaga() {
        when(dynamo.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder()
                        .item(Map.of())
                        .build());

        when(dynamo.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(new IllegalStateException("Escritura fallida"));

        assertThrows(IllegalStateException.class,
                () -> repository.sincronizarCliente(cliente()));

        verify(dynamo).updateItem(any(UpdateItemRequest.class));
    }
}