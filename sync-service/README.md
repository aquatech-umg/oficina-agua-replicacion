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

## AQ-124: sincronización real hacia DynamoDB

POST /api/sync/resumen crea un job y responde con su ID.
La ejecución ocurre en segundo plano.

GET /api/sync/jobs/{jobId} devuelve el estado y progreso real.

### Funcionamiento

- Lee clientes y pagos por páginas desde la réplica.
- Consulta cada registro en DynamoDB mediante su clave.
- Compara únicamente los campos administrados por la réplica.
- Inserta registros nuevos y actualiza los modificados.
- No escribe cuando los datos son iguales.
- Conserva atributos exclusivos del destino.
- No elimina registros ausentes en MySQL.
- Cuenta los registros procesados y las escrituras confirmadas.
- Solo informa COMPLETADO y 100 % al terminar correctamente.
- Los errores se registran y dejan el job en FALLIDO.
- Rechaza otro job pendiente o activo con HTTP 409.

### Configuración adicional

| Variable | Valor local predeterminado |
|----------|----------------------------|
| DYNAMODB_ENDPOINT | http://127.0.0.1:8011 |
| AWS_REGION | us-east-1 |
| DYNAMODB_ACCESS_KEY | local |
| DYNAMODB_SECRET_KEY | local |

Las credenciales local/local son ficticias para DynamoDB Local.

### Validaciones realizadas

1. Primera copia: 37579 registros y 37579 escrituras confirmadas.
2. Cambio de un cliente: exactamente una escritura.
3. Sin cambios: cero escrituras.
4. Registro exclusivo del destino: conservado.
5. Segunda ejecución: sin duplicación de claves ni escrituras.
6. Error de lectura simulado: no se intenta escribir.
7. Error de escritura simulado: el job termina en FALLIDO,
   con progreso inferior a 100.
8. Atributo notaInterna: conservado al actualizar el cliente.

También se verificó el rechazo de un segundo job pendiente
y la posibilidad de crear otro después de un fallo.

El nombre del cliente de prueba se restauró a su valor original.

### Ejecución

Con MySQL, su réplica y DynamoDB Local activos, y la contraseña
DB_REPLICA_PASSWORD definida en la terminal:

    .\mvnw.cmd spring-boot:run

En otra terminal PowerShell:

    $job = Invoke-RestMethod -Method Post -Uri "http://localhost:8085/api/sync/resumen"
    Invoke-RestMethod -Uri "http://localhost:8085/api/sync/jobs/$($job.jobId)"

### Límites de esta implementación

Los jobs y el control de concurrencia viven en memoria y
corresponden a una sola instancia del servicio.

La lectura del destino se realiza registro por registro,
sin BatchGetItem. La primera carga puede tardar varios minutos.

El recorrido no utiliza un snapshot del origen. Si el número
de registros procesados difiere del total inicial, el job falla
y requiere una nueva ejecución. Los cambios de valores durante
el recorrido se vuelven a comparar en una ejecución posterior.
