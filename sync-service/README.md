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
