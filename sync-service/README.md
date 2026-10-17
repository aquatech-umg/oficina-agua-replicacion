# Sync Service — AQ-100

Responsable: Nayeli Urrutia.
Servicio de sincronización de AquaTech.

## Alcance actual

API con trabajos y progreso simulado.
Todavía no copia datos desde MySQL hacia DynamoDB.

Los trabajos se guardan en memoria y se pierden al reiniciar.

## Cómo ejecutar

Desde la raíz del repositorio:

```powershell
cd .\sync-service
mvn spring-boot:run
```

Puerto del servicio: 8085.
Para detenerlo: Ctrl + C.

## Rutas disponibles

- POST /api/sync/resumen: crea un trabajo.
  Responde 202 con jobId y estado PENDIENTE.
  No requiere cuerpo.

- GET /api/sync/jobs/{jobId}: consulta su progreso.
  Responde 200 si existe y 404 si no existe.

## Simulación

El progreso aumenta 20 puntos cada dos segundos hasta llegar
a COMPLETADO con 100 %. Consultarlo no modifica su avance.

El estado FALLIDO está definido para futuras ejecuciones con errores.

## Documentación

- Contrato: openapi/openapi.yaml
- Modelo NoSQL: docs/modelo-nosql.md# Sync Service — AQ-100

## AQ-123: lectura paginada de la réplica

El servicio lee clientes y pagos desde la réplica MariaDB,
utilizando el usuario de solo lectura.

### Configuración por variables de entorno

| Variable | Valor predeterminado |
|----------|----------------------|
| DB_REPLICA_HOST | 127.0.0.1 |
| DB_REPLICA_PORT | 3307 |
| DB_REPLICA_NAME | oficina_agua |
| DB_REPLICA_USER | lectura |
| DB_REPLICA_PASSWORD | Obligatoria, sin valor predeterminado |
| SYNC_PAGE_SIZE | 500, admite valores entre 1 y 1000 |

Las contraseñas no se guardan en el repositorio.
Las variables deben estar definidas en la terminal que ejecuta Maven.

### Funcionamiento

- ReplicaRepository consulta con WHERE id > ultimoId,
  ORDER BY id y LIMIT, sin OFFSET.
- ReplicaLecturaService entrega una página a la vez,
  sin acumular todos los registros en memoria.
- ClienteReplica y PagoReplica representan el modelo NoSQL.
- Los identificadores se convierten a texto y deben ser
  positivos y representables como long de Java.
- Los montos se leen con BigDecimal y se convierten a texto
  con dos decimales, sin usar double.
- Las fechas se representan como yyyy-MM-dd'T'HH:mm:ss,
  sin añadir una zona horaria que el DATETIME no contiene.
- Se validan campos obligatorios y la relación pago-cliente.
- Un registro inválido interrumpe el recorrido y propaga
  el error; no se omite silenciosamente.

### Validación

Con la infraestructura activa, los datos de prueba cargados
y DB_REPLICA_PASSWORD definida, ejecutar desde sync-service:

    .\mvnw.cmd test

ReplicaLecturaIntegrationTest utiliza páginas de 137 registros.
Comprueba el orden creciente de los IDs, el tamaño de página,
el formato de montos y fechas y los totales contra la réplica.

Resultado obtenido: 500 clientes y 37079 pagos.
Dos pruebas ejecutadas, sin fallos ni errores.

Esta tarea implementa la lectura y conversión.
La escritura en DynamoDB y el progreso real corresponden a AQ-124.
