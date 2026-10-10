# AQ-101: Estrategia para detectar diferencias sin borrar

Responsable: Nayeli Urrutia.

## Objetivo

Diseñar la sincronización de clientes y pagos desde MySQL hacia
DynamoDB mediante comparación de datos (diff) e inserción o
actualización (upsert), sin eliminar registros del destino.

Este documento describe la estrategia. La sincronización real
todavía no está implementada en el esqueleto de AQ-100.

## Origen y destino

- Origen: réplica MySQL del monolito, con acceso de solo lectura.
- Destino: DynamoDB, según el modelo definido en modelo-nosql.md.
- Servicio responsable: sync-service.
- El monolito y sus datos no se modifican.

Antes de implementar se deben confirmar los nombres de las tablas,
las claves primarias y los campos disponibles en la réplica MySQL.

## Identificación de los registros

Cada registro conserva el identificador de origen como una clave
estable en DynamoDB:

- Clientes: clienteId, de tipo String.
- Pagos: pagoId, de tipo String.

La correspondencia entre estas claves y las columnas de MySQL se
confirmará al revisar el esquema real.

No se genera un identificador nuevo en cada sincronización.
Así, un mismo cliente o pago se reconoce en todas las ejecuciones.

## Reglas de comparación

| Situación | Acción |
| --- | --- |
| Existe en MySQL y falta en DynamoDB | Insertar |
| Existe en ambos y cambió algún dato replicado | Actualizar |
| Existe en ambos y los datos replicados son iguales | No escribir |
| Existe en DynamoDB y no aparece en MySQL | Conservar |

La ausencia de un registro en el origen nunca provoca su eliminación
en DynamoDB.

## Cómo detectar cambios

Para cada registro del origen:

1. Validar que tenga un identificador.
2. Convertirlo al formato del modelo NoSQL.
3. Consultar el registro del destino usando la misma clave.
4. Comparar los campos replicados dentro de datos.
5. Insertar si falta o actualizar si hay diferencias.

La comparación se realiza por campos y valores, no por el orden
de las propiedades de un JSON.

Ambos registros deben usar las mismas reglas de conversión:

- Identificadores representados como String.
- Importes representados como cadenas decimales con una escala
  acordada, sin convertirlos a números de punto flotante.
- Fechas representadas con el mismo formato y, cuando corresponda,
  la misma zona horaria.
- Valores nulos tratados de manera consistente.
- Textos conservados sin cambios arbitrarios de mayúsculas o espacios.

No se comparan metadatos del destino, como la fecha de sincronización.
Un cambio en esos metadatos no representa un cambio en MySQL.

Un registro incompleto o inválido se considera un error; no se usa
para sobrescribir datos válidos del destino.

## Aplicación del upsert

Si falta el registro, se crea usando la clave de origen y sus datos.

Si existe y cambió, se actualizan los campos administrados por la
replicación. Los atributos exclusivos del destino se conservan.

No se ejecutan operaciones de borrado de registros ni se vacían
las tablas para volver a cargarlas.

La política inicial evita borrados físicos. Un posible indicador
de baja lógica solo se replicará si forma parte del esquema y
del alcance acordado; no se deduce de la ausencia del registro.

## Recorrido de los datos

La primera implementación comparará todos los registros del origen,
recorriéndolos por páginas para evitar cargarlos todos en memoria.

Para cada página se consultarán las claves correspondientes
en DynamoDB y se aplicarán únicamente los upserts necesarios.

No se dependerá exclusivamente de updated_at, porque todavía
no se ha confirmado que todos los cambios actualicen ese campo.

## Repetición y concurrencia

La estrategia es idempotente: repetir la sincronización con los
mismos datos no crea duplicados ni genera actualizaciones innecesarias.

La implementación inicial procesará una sincronización a la vez.
Si existen varias instancias del servicio, se necesitará coordinación
compartida para evitar que una ejecución antigua sobrescriba otra nueva.

Si el origen cambia mientras se recorre, una ejecución posterior
volverá a comparar los registros. Una lectura consistente requerirá
una estrategia de snapshot acordada para la réplica MySQL.

## Errores y progreso

Los errores de lectura o escritura deben registrarse y reflejarse
en el estado del trabajo.

Una lectura fallida del destino no se interpreta como registro ausente.

Los fallos transitorios podrán reintentarse de forma limitada.
Si una escritura no se confirma, el registro no se cuenta como exitoso.

Si quedan errores sin resolver, el trabajo termina en FALLIDO.
Las escrituras ya confirmadas se conservan y una nueva ejecución
puede volver a comparar los datos.

El progreso real se calculará según los registros procesados.
COMPLETADO y 100 % solo se informarán cuando todo el recorrido
y las escrituras necesarias hayan terminado correctamente.

## Ejemplo ilustrativo

Estos identificadores y valores son ejemplos, no datos reales:

- Cliente 10: aparece en MySQL y falta en DynamoDB.
  Resultado: insertar con clienteId "10".
- Cliente 20: cambió su teléfono en MySQL.
  Resultado: actualizar sus datos replicados.
- Pago 30: tiene los mismos datos en ambos sistemas.
  Resultado: no escribir.
- Pago 40: existe solo en DynamoDB.
  Resultado: conservarlo.

## Casos para validar la implementación futura

1. Un registro nuevo se inserta con su identificador de origen.
2. Un cambio en un campo replicado actualiza el registro.
3. Un registro sin cambios no genera escritura.
4. Un registro ausente en MySQL permanece en DynamoDB.
5. Repetir la ejecución no produce duplicados.
6. Un error de lectura no provoca una inserción incorrecta.
7. Un error de escritura impide informar COMPLETADO.
8. Los atributos exclusivos del destino se conservan.

## Documentación relacionada

- Modelo NoSQL: modelo-nosql.md.
- Contrato OpenAPI: ../openapi/openapi.yaml.