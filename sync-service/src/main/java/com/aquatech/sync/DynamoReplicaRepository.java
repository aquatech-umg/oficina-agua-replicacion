package com.aquatech.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
public class DynamoReplicaRepository {

    private final DynamoDbClient dynamo;

    public DynamoReplicaRepository(DynamoDbClient dynamo) {
        this.dynamo = dynamo;
    }

    public boolean sincronizarCliente(ClienteReplica cliente) {
        Map<String, AttributeValue> datos = Map.of(
                "nombre", texto(cliente.datos().nombre()),
                "activo", AttributeValue.builder()
                        .bool(cliente.datos().activo()).build()
        );

        return sincronizar(
                "clientes", "clienteId", cliente.clienteId(),
                datos, null);
    }

    public boolean sincronizarPago(PagoReplica pago) {
        Map<String, AttributeValue> datos = Map.of(
                "reciboId", texto(pago.datos().reciboId()),
                "metodoPagoId", texto(pago.datos().metodoPagoId()),
                "monto", texto(pago.datos().monto()),
                "fechaPago", texto(pago.datos().fechaPago())
        );

        return sincronizar(
                "pagos", "pagoId", pago.pagoId(),
                datos, pago.clienteId());
    }

    private boolean sincronizar(
            String tabla,
            String nombreClave,
            String id,
            Map<String, AttributeValue> datosOrigen,
            String clienteId) {

        Map<String, AttributeValue> clave =
                Map.of(nombreClave, texto(id));

        // Un error de lectura se propaga; no se trata como ausencia.
        Map<String, AttributeValue> actual = dynamo.getItem(
                GetItemRequest.builder()
                        .tableName(tabla)
                        .key(clave)
                        .consistentRead(true)
                        .build()
        ).item();

        AttributeValue atributoDatos = actual.get("datos");

        if (atributoDatos != null && !atributoDatos.hasM()) {
            throw new IllegalStateException(
                    "Formato invalido de datos en " + tabla + "/" + id);
        }

        Map<String, AttributeValue> datosActuales =
                atributoDatos == null ? Map.of() : atributoDatos.m();

        boolean iguales = !actual.isEmpty()
                && atributoDatos != null;

        for (var campo : datosOrigen.entrySet()) {
            if (!Objects.equals(
                    datosActuales.get(campo.getKey()), campo.getValue())) {
                iguales = false;
            }
        }

        if (clienteId != null
                && !Objects.equals(actual.get("clienteId"), texto(clienteId))) {
            iguales = false;
        }

        if (iguales) {
            return false;
        }

        // Conserva campos exclusivos dentro del mapa datos.
        Map<String, AttributeValue> datosCombinados =
                new HashMap<>(datosActuales);
        datosCombinados.putAll(datosOrigen);

        Map<String, String> nombres = new HashMap<>();
        nombres.put("#datos", "datos");

        Map<String, AttributeValue> valores = new HashMap<>();
        valores.put(":datos", AttributeValue.builder()
                .m(datosCombinados).build());

        String expresion = "SET #datos = :datos";

        if (clienteId != null) {
            nombres.put("#cliente", "clienteId");
            valores.put(":cliente", texto(clienteId));
            expresion += ", #cliente = :cliente";
        }

        // UpdateItem inserta si falta y conserva otros atributos raíz.
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(tabla)
                .key(clave)
                .updateExpression(expresion)
                .expressionAttributeNames(nombres)
                .expressionAttributeValues(valores)
                .build());

        return true;
    }

    private static AttributeValue texto(String valor) {
        return AttributeValue.builder().s(valor).build();
    }
}