# oficina-agua-replicacion

Servicio de replicación de AquaTech: el `sync-service` (Spring Boot, puerto 8085) lee la réplica MariaDB (usuario `lectura`) y copia clientes y pagos a DynamoDB Local (puerto 8011).

## Arranque rápido

1. Copiar `.env.example` a `.env` y poner la clave de lectura en `DB_REPLICA_PASSWORD`. El `.env` no se sube a Git.
2. Levantar DynamoDB Local y crear las tablas: `docker compose up -d`
3. Arrancar el servicio (la réplica en `:3307` debe estar prendida desde `oficina-agua-infra`):
    - Windows: `cd sync-service` y `.\mvnw.cmd spring-boot:run`
    - Linux/Mac: `cd sync-service` y `./mvnw spring-boot:run`

El servicio lee el `.env` al arrancar (`spring.config.import`), así que no hace falta definir variables en la consola. Si se definen, las de la consola tienen prioridad.

Detalle del servicio, endpoints y pruebas: [`sync-service/README.md`](sync-service/README.md).