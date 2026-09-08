package com.drakkar.erp.dao;

import com.drakkar.erp.dto.ApiModels.*;
import com.drakkar.erp.domain.DomainException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class PreparationDao {
    private final NamedParameterJdbcTemplate jdbc;

    public PreparationDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Shared first lock for short business transactions: tenant -> expedition/ship -> stock.
    public void lockSettlement(Long settlementId) {
        jdbc.queryForObject("select id from settlement where id = :id for update",
                Map.of("id", settlementId), Long.class);
    }

    public Instant databaseTime() {
        return jdbc.queryForObject("select clock_timestamp()", Map.of(), Timestamp.class).toInstant();
    }

    public List<RoutePoint> route(Long expeditionId) {
        return jdbc.query("select name, distance_km from expedition_route_point where expedition_id = :id order by position",
                Map.of("id", expeditionId), (rs, n) -> new RoutePoint(rs.getString(1), rs.getInt(2)));
    }

    public List<RecipeResourceView> requirements(Long expeditionId) {
        return jdbc.query("select resource, quantity from expedition_resource_requirement where expedition_id = :id order by resource",
                Map.of("id", expeditionId), (rs, n) -> new RecipeResourceView(rs.getString(1), rs.getInt(2)));
    }

    public int reserved(Long settlementId, String resource, Instant at) {
        return jdbc.queryForObject("""
                select coalesce(sum(i.quantity), 0)::integer from resource_reservation r
                join resource_reservation_item i on i.reservation_id = r.id
                where r.settlement_id = :settlement and r.status = 'ACTIVE'
                  and r.expires_at > :at and i.resource = :resource
                """, Map.of("settlement", settlementId, "resource", resource, "at", Timestamp.from(at)), Integer.class);
    }

    public int available(Long settlementId, String resource, Instant at) {
        Integer stock = jdbc.query("select quantity from warehouse_stock where settlement_id = :settlement and resource = :resource",
                Map.of("settlement", settlementId, "resource", resource), rs -> rs.next() ? rs.getInt(1) : null);
        return stock == null ? 0 : stock - reserved(settlementId, resource, at);
    }

    public List<ReservationView> reservations(Long expeditionId, Instant at) {
        return jdbc.query("""
                select id, case when status = 'ACTIVE' and expires_at <= :at then 'EXPIRED' else status end,
                       expires_at from resource_reservation where expedition_id = :id order by id desc limit 10
                """, Map.of("id", expeditionId, "at", Timestamp.from(at)), (rs, n) -> new ReservationView(
                rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant(), items(rs.getLong(1))));
    }

    public List<RecipeResourceView> items(Long reservationId) {
        return jdbc.query("select resource, quantity from resource_reservation_item where reservation_id = :id order by resource",
                Map.of("id", reservationId), (rs, n) -> new RecipeResourceView(rs.getString(1), rs.getInt(2)));
    }

    public int expeditionReserved(Long expeditionId, String resource, Instant at) {
        return jdbc.queryForObject("""
                select coalesce(sum(i.quantity), 0)::integer from resource_reservation r
                join resource_reservation_item i on i.reservation_id = r.id
                where r.expedition_id = :id and r.status = 'ACTIVE' and r.expires_at > :at and i.resource = :resource
                """, Map.of("id", expeditionId, "resource", resource, "at", Timestamp.from(at)), Integer.class);
    }

    public void lockStock(Long settlementId) {
        jdbc.queryForList("select resource from warehouse_stock where settlement_id = :id order by resource for update",
                Map.of("id", settlementId), String.class);
    }

    public Long reserve(Long settlementId, Long expeditionId, Long userId, Instant at, Instant expiry) {
        return jdbc.queryForObject("""
                insert into resource_reservation(settlement_id, expedition_id, created_by, created_at, expires_at)
                values (:settlement, :expedition, :user, :at, :expiry) returning id
                """, Map.of("settlement", settlementId, "expedition", expeditionId, "user", userId,
                "at", Timestamp.from(at), "expiry", Timestamp.from(expiry)), Long.class);
    }

    public void addItem(Long id, RecipeResourceView item) {
        jdbc.update("insert into resource_reservation_item values (:id, :resource, :quantity)",
                Map.of("id", id, "resource", item.resource(), "quantity", item.quantity()));
    }

    public void releaseExpedition(Long expeditionId, Instant at) {
        jdbc.update("""
                update resource_reservation set status = 'RELEASED', closed_at = :at
                where expedition_id = :id and status = 'ACTIVE'
                """, Map.of("id", expeditionId, "at", Timestamp.from(at)));
    }

    public void consumeExpedition(Long settlementId, Long expeditionId, Instant at) {
        for (RecipeResourceView item : requirements(expeditionId)) deduct(settlementId, item);
        jdbc.update("""
                update resource_reservation set status = 'CONSUMED', closed_at = :at
                where expedition_id = :id and status = 'ACTIVE' and expires_at > :at
                """, Map.of("id", expeditionId, "at", Timestamp.from(at)));
    }

    public void deduct(Long settlementId, RecipeResourceView item) {
        int changed = jdbc.update("""
                update warehouse_stock set quantity = quantity - :quantity, version = version + 1
                where settlement_id = :settlement and resource = :resource and quantity >= :quantity
                """, Map.of("settlement", settlementId, "resource", item.resource(), "quantity", item.quantity()));
        if (changed != 1) throw DomainException.conflict("INSUFFICIENT_STOCK", "Недостаточно ресурса " + item.resource());
    }

    public void snapshot(Long expeditionId, Instant at) {
        jdbc.update("""
                update expedition set started_at = :at, departure_snapshot = expedition_departure_data(id) where id = :id
                """, Map.of("id", expeditionId, "at", Timestamp.from(at)));
    }

    public Map<String, Object> departure(Long expeditionId) {
        return jdbc.queryForMap("select started_at, departure_snapshot::text as snapshot from expedition where id = :id", Map.of("id", expeditionId));
    }

    public void bumpVersion(Long expeditionId) {
        jdbc.update("update expedition set version = version + 1 where id = :id and status = 'PREPARATION'", Map.of("id", expeditionId));
    }

    public void savePlan(Long expeditionId, PreparationRequest request) {
        Map<String, Object> params = Map.of("id", expeditionId);
        jdbc.update("delete from expedition_route_point where expedition_id = :id", params);
        for (int i = 0; i < request.route().size(); i++) {
            RoutePoint point = request.route().get(i);
            jdbc.update("insert into expedition_route_point values (:id, :position, :name, :distance)",
                    Map.of("id", expeditionId, "position", i, "name", point.name().trim(), "distance", point.distanceKm()));
        }
        jdbc.update("delete from expedition_resource_requirement where expedition_id = :id", params);
        for (SupplyRequest item : request.resources()) {
            jdbc.update("insert into expedition_resource_requirement values (:id, :resource, :quantity)",
                    Map.of("id", expeditionId, "resource", item.resource(), "quantity", item.quantity()));
        }
    }

    public record ExpiredReservation(Long id, Long settlementId, Long expeditionId, Long shipId) {}

    public List<ExpiredReservation> expired() {
        return jdbc.query("""
                select id, settlement_id, expedition_id, ship_id from resource_reservation
                where status = 'ACTIVE' and expires_at <= clock_timestamp() order by expires_at, id limit 100
                """, Map.of(), (rs, n) -> new ExpiredReservation(rs.getLong(1), rs.getLong(2),
                rs.getObject(3, Long.class), rs.getObject(4, Long.class)));
    }

    public boolean expire(Long id, Instant at) {
        return jdbc.update("""
                update resource_reservation set status = 'RELEASED', closed_at = :at
                where id = :id and status = 'ACTIVE' and expires_at <= :at
                """, Map.of("id", id, "at", Timestamp.from(at))) == 1;
    }

    public int ownStageReserved(Long shipId, int stage, String resource, Instant at) {
        return jdbc.queryForObject("""
                select coalesce(sum(i.quantity), 0)::integer from resource_reservation r
                join resource_reservation_item i on i.reservation_id = r.id
                where r.ship_id = :ship and r.ship_stage = :stage and r.status = 'ACTIVE'
                  and r.expires_at > :at and i.resource = :resource
                """, Map.of("ship", shipId, "stage", stage, "resource", resource, "at", Timestamp.from(at)), Integer.class);
    }

    public void consumeStage(Long shipId, int stage, Instant at) {
        jdbc.update("""
                update resource_reservation set status = 'CONSUMED', closed_at = :at
                where ship_id = :ship and ship_stage = :stage and status = 'ACTIVE' and expires_at > :at
                """, Map.of("ship", shipId, "stage", stage, "at", Timestamp.from(at)));
    }

    public void seedDemo(Long settlementId) {
        jdbc.update("""
                insert into expedition_route_point
                select e.id, 0, s.name, 0 from expedition e join settlement s on s.id = e.settlement_id
                where e.settlement_id = :id on conflict do nothing
                """, Map.of("id", settlementId));
        jdbc.update("""
                insert into expedition_route_point select id, 1, target,
                case id when 207 then 650 when 208 then 950 else 800 end
                from expedition where settlement_id = :id on conflict do nothing
                """, Map.of("id", settlementId));
        jdbc.update("""
                insert into expedition_resource_requirement select id, 'PROVISIONS', 20
                from expedition where settlement_id = :id and status = 'PREPARATION' on conflict do nothing
                """, Map.of("id", settlementId));
        jdbc.update("""
                update expedition set departure_snapshot = expedition_departure_data(id)
                where settlement_id = :id and status in ('SAILING', 'COMPLETED') and departure_snapshot is null
                """, Map.of("id", settlementId));
    }
}
