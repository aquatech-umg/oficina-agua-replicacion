# AQ-99: Elección de base NoSQL y modelo de clientes y pagos

Responsable: Nayeli Melissa Urrutia Orellana.
Servicio: sync-service.

## 1. Elección de la base de datos

Se utilizará DynamoDB Local como destino de la réplica de clientes
y pagos.

Esta elección sigue la tecnología indicada para el módulo de
replicación en la arquitectura de la Fase 2. MongoDB se considera
una alternativa permitida, pero no se utilizará en este módulo.

DynamoDB Local se ejecutará mediante Docker Compose y estará
disponible desde la computadora en el puerto 8011.

El servicio Spring Boot de sincronización utilizará el puerto 8085.

## 2. Alcance

El servicio copiará clientes y pagos desde la fuente MySQL
de solo lectura indicada por la arquitectura hacia DynamoDB.

La sincronización insertará registros nuevos y actualizará los
registros modificados. No eliminará registros del destino ni
modificará el monolito.

## 3. Organización de los datos

Se proponen dos tablas independientes:

| Tabla | Clave de partición | Tipo de clave | Contenido |
|-------|--------------------|---------------|-----------|
| clientes | clienteId | String | Información de cada cliente |
| pagos | pagoId | String | Información de cada pago |

Cada elemento conservará el identificador del registro de origen,
convertido a texto.

No se utilizará clave de ordenación en este modelo inicial.

## 4. Documento de cliente

Ejemplo ilustrativo del modelo de destino:

```json
{
  "clienteId": "123",
  "datos": {
    "nombre": "Cliente de ejemplo"
  }
}
```

- clienteId: identificador único y estable del cliente en el origen.
- datos: mapa con los atributos del cliente que se replicarán.

Los atributos concretos de datos se definirán al revisar el esquema
real de MySQL. El ejemplo no establece los nombres de las columnas
del sistema original.

## 5. Documento de pago

Ejemplo ilustrativo del modelo de destino:

```json
{
  "pagoId": "456",
  "clienteId": "123",
  "datos": {
    "monto": "150.00",
    "fechaPago": "2026-10-09"
  }
}
```

- pagoId: identificador único y estable del pago en el origen.
- clienteId: identificador del cliente al que corresponde el pago.
- datos: mapa con los atributos del pago que se replicarán.

Los atributos concretos y la forma de obtener clienteId se
confirmarán al revisar las tablas y relaciones reales de MySQL.

En este modelo los importes se conservarán como texto decimal,
sin convertirlos a números de punto flotante.

## 6. Relación entre clientes y pagos

Un cliente puede tener varios pagos.

Cada pago conservará el clienteId del cliente correspondiente.
Los pagos se guardarán como elementos independientes para poder
actualizarlos sin reemplazar todos los pagos de un cliente.

DynamoDB no impondrá una clave foránea. El servicio será responsable
de conservar correctamente esta asociación.

## 7. Reglas de actualización

- Si el identificador no existe en el destino, insertar el elemento.
- Si existe y sus datos cambiaron, actualizar el elemento.
- Si existe y sus datos son iguales, conservarlo sin cambios.
- Si deja de aparecer en el origen, conservarlo en el destino.
- Repetir una sincronización no debe crear registros duplicados.

Los identificadores estables permiten insertar o actualizar el mismo
elemento durante diferentes ejecuciones.

La estrategia concreta para detectar diferencias se documentará
en AQ-101.

## 8. Validación pendiente para la implementación

Antes de programar la copia real se verificará:

- Las claves primarias de clientes y pagos en MySQL.
- Los campos que se incluirán en cada mapa datos.
- La relación real que permite asociar un pago con su cliente.
- La representación de valores nulos, fechas e importes.

Este documento define la elección de base NoSQL y el modelo
propuesto. No implementa todavía la sincronización.