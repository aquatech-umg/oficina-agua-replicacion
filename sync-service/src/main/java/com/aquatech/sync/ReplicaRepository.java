package com.aquatech.sync;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReplicaRepository {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final JdbcTemplate jdbc;

    public ReplicaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ClienteReplica> leerClientes(long ultimoId, int tamano) {
        validarPagina(ultimoId, tamano);

        String sql = """
                SELECT id, nombre, activo
                FROM clientes
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """;

        return jdbc.query(sql, (rs, fila) -> {
            String id = leerId(rs, "id");
            String nombre = rs.getString("nombre");
            int activo = rs.getInt("activo");

            if (rs.wasNull() || (activo != 0 && activo != 1)) {
                throw new IllegalArgumentException(
                        "Cliente " + id + ": activo invalido");
            }

            if (nombre == null || nombre.isBlank()) {
                throw new IllegalArgumentException(
                        "Cliente " + id + ": nombre obligatorio");
            }

            return new ClienteReplica(
                    id,
                    new ClienteReplica.Datos(nombre, activo == 1)
            );
        }, ultimoId, tamano);
    }

    public List<PagoReplica> leerPagos(long ultimoId, int tamano) {
        validarPagina(ultimoId, tamano);

        String sql = """
                SELECT p.id, p.recibo_id, p.metodo_pago_id,
                       p.monto, p.fecha_pago, c.id AS cliente_id
                FROM pagos p
                LEFT JOIN recibos r ON r.id = p.recibo_id
                LEFT JOIN lecturas l ON l.id = r.lectura_id
                LEFT JOIN contadores co ON co.id = l.contador_id
                LEFT JOIN clientes c ON c.id = co.cliente_id
                WHERE p.id > ?
                ORDER BY p.id
                LIMIT ?
                """;

        return jdbc.query(sql, (rs, fila) -> {
            String id = leerId(rs, "id");
            String clienteId = leerId(rs, "cliente_id");
            String reciboId = leerId(rs, "recibo_id");
            String metodoPagoId = leerId(rs, "metodo_pago_id");

            BigDecimal monto = rs.getBigDecimal("monto");
            if (monto == null || monto.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Pago " + id + ": monto invalido");
            }

            LocalDateTime fecha =
                    rs.getObject("fecha_pago", LocalDateTime.class);

            if (fecha == null) {
                throw new IllegalArgumentException(
                        "Pago " + id + ": fecha obligatoria");
            }

            return new PagoReplica(
                    id,
                    clienteId,
                    new PagoReplica.Datos(
                            reciboId,
                            metodoPagoId,
                            monto.setScale(2, RoundingMode.UNNECESSARY)
                                    .toPlainString(),
                            fecha.format(FORMATO_FECHA)
                    )
            );
        }, ultimoId, tamano);
    }

    public long contarClientes() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM clientes", Long.class);
    }

    public long contarPagos() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pagos", Long.class);
    }

    private static String leerId(ResultSet rs, String campo)
            throws SQLException {
        long valor = rs.getLong(campo);

        if (rs.wasNull() || valor <= 0) {
            throw new IllegalArgumentException(
                    "Registro invalido: " + campo
                            + " ausente o fuera del rango admitido");
        }

        return Long.toString(valor);
    }

    private static void validarPagina(long ultimoId, int tamano) {
        if (ultimoId < 0 || tamano < 1 || tamano > 1000) {
            throw new IllegalArgumentException(
                    "Pagina invalida: ultimoId >= 0 y tamano entre 1 y 1000");
        }
    }
}