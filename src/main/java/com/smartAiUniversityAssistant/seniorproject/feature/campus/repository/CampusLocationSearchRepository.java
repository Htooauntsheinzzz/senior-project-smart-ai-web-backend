package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;

/**
 * The unified student location list: eligible buildings and places in one keyset-paged query. Filters are bound parameters
 * and the sort is fixed, so no client value reaches the SQL text. A building is dropped when a listed place in it has the
 * same name, because the two rows would then be one logical destination.
 */
@Repository
public class CampusLocationSearchRepository {
    public record Row(CampusLocationType type, long id, long siteId, String code, String name, String category, CampusMobileGroup group,
            String description, String status, boolean popular, Long buildingId, String buildingStatus) {}
    public record Cursor(String name, String type, long id) {}
    public record Filter(Long siteId, String search, CampusMobileGroup group) {}

    @PersistenceContext private EntityManager entityManager;

    public long count(Filter filter) {
        Query query = entityManager.createNativeQuery(common(filter) + " SELECT count(*) FROM locations");
        bind(query, filter);
        return ((Number) query.getSingleResult()).longValue();
    }

    public List<Row> search(Filter filter, Cursor cursor, Integer limit) {
        String sql = common(filter) + " SELECT location_type, id, site_id, code, name, category, mobile_group, description, status, is_popular, building_id, building_status"
                + " FROM locations"
                + (cursor == null ? "" : " WHERE (lower(name), location_type, id) > (:cursorName, :cursorType, :cursorId)")
                + " ORDER BY lower(name), location_type, id"
                + (limit == null ? "" : " LIMIT :limit");
        Query query = entityManager.createNativeQuery(sql);
        bind(query, filter);
        if (cursor != null) {
            query.setParameter("cursorName", cursor.name());
            query.setParameter("cursorType", cursor.type());
            query.setParameter("cursorId", cursor.id());
        }
        if (limit != null) query.setParameter("limit", limit);
        var rows = new ArrayList<Row>();
        for (Object result : query.getResultList()) {
            Object[] r = (Object[]) result;
            rows.add(new Row(CampusLocationType.valueOf((String) r[0]), ((Number) r[1]).longValue(), ((Number) r[2]).longValue(), (String) r[3], (String) r[4],
                    (String) r[5], CampusMobileGroup.valueOf((String) r[6]), (String) r[7], (String) r[8], (Boolean) r[9],
                    r[10] == null ? null : ((Number) r[10]).longValue(), (String) r[11]));
        }
        return rows;
    }

    private String common(Filter filter) {
        String site = filter.siteId() == null ? "" : " AND s.id = :siteId";
        String match = " LIKE :search ESCAPE '\\'";
        String placeSearch = filter.search() == null ? "" : " AND (lower(p.place_name)" + match + " OR lower(p.place_code)" + match
                + " OR lower(coalesce(p.place_name_th, ''))" + match + " OR lower(c.category_name)" + match + ")";
        String buildingSearch = filter.search() == null ? "" : " AND (lower(b.building_name)" + match + " OR lower(b.building_code)" + match
                + " OR lower(coalesce(b.building_name_th, ''))" + match + " OR 'building'" + match + ")";
        String placeGroup = filter.group() == null ? "" : " AND c.mobile_group = :group";
        String buildingGroup = filter.group() == null || filter.group() == CampusMobileGroup.BUILDINGS ? "" : " AND FALSE";
        return """
                WITH listed_places AS (
                    SELECT 'PLACE' AS location_type, p.id, s.id AS site_id, p.place_code AS code, p.place_name AS name, c.category_name AS category,
                           c.mobile_group, p.description, p.status, p.is_popular, p.building_id, pb.status AS building_status
                    FROM campus_places p
                    JOIN campus_categories c ON c.id = p.category_id AND c.is_deleted = FALSE
                    JOIN campus_sites s ON s.id = p.site_id AND s.is_deleted = FALSE AND s.is_active = TRUE
                    LEFT JOIN campus_buildings pb ON pb.id = p.building_id
                    WHERE p.is_deleted = FALSE AND p.is_visible_on_mobile = TRUE AND p.status <> 'INACTIVE'
                """ + site + placeSearch + placeGroup + """
                ),
                listed_buildings AS (
                    SELECT 'BUILDING' AS location_type, b.id, s.id AS site_id, b.building_code AS code, b.building_name AS name, 'Building' AS category,
                           'BUILDINGS' AS mobile_group, b.description, b.status, FALSE AS is_popular, b.id AS building_id, b.status AS building_status
                    FROM campus_buildings b
                    JOIN campus_sites s ON s.id = b.site_id AND s.is_deleted = FALSE AND s.is_active = TRUE
                    WHERE b.is_deleted = FALSE AND b.status <> 'INACTIVE'
                """ + site + buildingSearch + buildingGroup + """
                      AND NOT EXISTS (SELECT 1 FROM listed_places x WHERE x.building_id = b.id AND lower(x.name) = lower(b.building_name))
                ),
                locations AS (SELECT * FROM listed_places UNION ALL SELECT * FROM listed_buildings)
                """;
    }

    private void bind(Query query, Filter filter) {
        if (filter.siteId() != null) query.setParameter("siteId", filter.siteId());
        if (filter.search() != null) {
            String literal = filter.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.setParameter("search", "%" + literal + "%");
        }
        if (filter.group() != null) query.setParameter("group", filter.group().name());
    }
}
