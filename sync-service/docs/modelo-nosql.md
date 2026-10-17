# Modelo NoSQL de clientes y pagos — AQ-99 / AQ-122

Responsable: Nayeli Melissa Urrutia Orellana.
Repositorio: oficina-agua-replicacion.
Servicio: sync-service.

## 1. Objetivo

Replicar clientes y pagos desde la réplica de MySQL hacia
DynamoDB Local. Se insertan registros nuevos y se actualizan
los modificados, sin eliminar registros del destino.

El esquema de origen se verificó en:
oficina-agua-infra/db/01-esquema.sql.

## 2. Tablas de destino

| Tabla | Clave de partición | Tipo |
|-------|--------------------|------|
| clientes | clienteId | String |
| pagos | pagoId | String |

No se utiliza clave de ordenación ni índices secundarios.

DynamoDB Local está disponible en http://localhost:8011.
Desde otros contenedores del mismo Compose se utiliza
http://dynamodb-local:8000.

## 3. Campos de clientes

| Origen MySQL | Destino DynamoDB | Tipo de destino |
|--------------|-----------------|-----------------|
| clientes.id | clienteId | String |
| clientes.nombre | datos.nombre | String |
| clientes.activo | datos.activo | Boolean |

Se replican todos los clientes, incluidos los inactivos.
El valor activo se convierte de 0/1 a false/true.

Ejemplo:

```json
{
  "clienteId": "123",
  "datos": {
    "nombre": "Cliente de ejemplo",
    "activo": true
  }
}
```

### Campos excluidos

No se replican dpi, nit, telefono ni direccion_principal,
porque no son necesarios para identificar al cliente y
relacionarlo con sus pagos en este alcance.

Tampoco se copian created_at ni updated_at. La detección de
cambios compara los campos incluidos en el modelo.

## 4. Campos de pagos

| Origen MySQL | Destino DynamoDB | Tipo de destino |
|--------------|-----------------|-----------------|
| pagos.id | pagoId | String |
| contadores.cliente_id, mediante las relaciones | clienteId | String |
| pagos.recibo_id | datos.reciboId | String |
| pagos.metodo_pago_id | datos.metodoPagoId | String |
| pagos.monto | datos.monto | String decimal |
| pagos.fecha_pago | datos.fechaPago | String |

Ejemplo:

```json
{
  "pagoId": "456",
  "clienteId": "123",
  "datos": {
    "reciboId": "789",
    "metodoPagoId": "1",
    "monto": "150.00",
    "fechaPago": "2026-10-09T14:30:00"
  }
}
```

No se replican usuario_registro_id, referencia, observacion,
created_at ni updated_at. Se conserva la información necesaria
para identificar el pago, su cliente, recibo, método, monto
y fecha, evitando copiar textos libres y datos del operador.

## 5. Relación entre pagos y clientes

La tabla pagos no tiene una columna cliente_id.

La relación real es:

pagos → recibos → lecturas → contadores → clientes.

Consulta para obtener la asociación:

```sql
SELECT
    p.id AS pago_id,
    c.id AS cliente_id,
    p.recibo_id,
    p.metodo_pago_id,
    p.monto,
    p.fecha_pago
FROM pagos p
JOIN recibos r ON r.id = p.recibo_id
JOIN lecturas l ON l.id = r.lectura_id
JOIN contadores co ON co.id = l.contador_id
JOIN clientes c ON c.id = co.cliente_id;
```

Esta consulta documenta la relación. La lectura paginada y
la detección de registros inválidos se implementarán en AQ-123.
Un pago sin una relación válida con un cliente debe reportarse
como error y no omitirse silenciosamente.

DynamoDB no impone claves foráneas. El servicio de
sincronización debe validar la asociación.

## 6. Reglas de conversión

- Los identificadores se convierten a texto decimal.
- monto se obtiene como decimal exacto y se representa con
  dos decimales, por ejemplo "150.00"; no se utiliza double.
- fecha_pago se representa como yyyy-MM-dd'T'HH:mm:ss.
- fecha_pago es DATETIME en el origen y no contiene zona
  horaria; no se añade Z ni se supone una conversión a UTC.
- Todos los campos seleccionados son obligatorios según el
  esquema; un valor nulo inesperado se reporta como error.
- No se convierten valores nulos en la cadena "null".
- Los registros inválidos no se escriben en DynamoDB.

## 7. Reglas de sincronización

- Insertar cuando la clave no exista en el destino.
- Actualizar únicamente cuando cambien los campos replicados.
- No escribir si los datos son iguales.
- Conservar los atributos exclusivos del destino.
- No borrar registros aunque desaparezcan del origen.
- Repetir una ejecución no debe duplicar registros.

La estrategia se detalla en estrategia-diff-upsert.md.
La sincronización real se implementará en AQ-124.

## 8. Creación automática de tablas

El servicio dynamodb-replicacion-init del docker-compose.yml:

1. Espera a que DynamoDB Local responda.
2. Crea clientes con clave clienteId de tipo String si no existe.
3. Crea pagos con clave pagoId de tipo String si no existe.
4. Espera a que ambas tablas estén disponibles.
5. Finaliza con código 0 cuando termina correctamente.

Las credenciales local/local son valores ficticios para
DynamoDB Local, no credenciales de una cuenta AWS.

Para levantar los servicios:

```powershell
docker compose up -d
```

Para revisar el resultado:

```powershell
docker compose logs dynamodb-replicacion-init
docker compose ps -a
```

DynamoDB guarda los datos en el volumen dynamodb-data.

## 9. Validación realizada

- Inicializador terminado con Exited (0).
- Tabla clientes en ACTIVE, clave clienteId de tipo S.
- Tabla pagos en ACTIVE, clave pagoId de tipo S.

AQ-122 prepara las tablas y define el modelo real.
La lectura de MySQL y la copia de registros corresponden
a AQ-123 y AQ-124.