package com.smartAiUniversityAssistant.seniorproject.feature.timetable.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.TimetableOption;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;

/**
 * Dropdown data for the timetable filters, read from the authoritative academic and campus entities. Each list is
 * optionally narrowed by its parent so the UI can cascade faculty -> department -> course -> section and
 * campus -> building -> room. Filters are added to the JPQL only when given, so no untyped null is ever bound.
 */
@Repository
public class TimetableLookupRepository {
    private final EntityManager entityManager;
    public TimetableLookupRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public List<TimetableOption> faculties() {
        return query("select f.id, f.facultyCode, f.facultyNameEn, f.id from Faculty f where f.deleted = false and f.active = true",
                " order by f.facultyNameEn, f.id", false, Map.of());
    }
    public List<TimetableOption> departments(Long facultyId) {
        return query("select d.id, d.departmentCode, d.departmentName, d.faculty.id from Department d where d.deleted = false and d.active = true",
                " order by d.departmentName, d.id", true, filters("d.faculty.id", facultyId));
    }
    public List<TimetableOption> courses(Long facultyId, Long departmentId) {
        return query("select c.id, c.courseCode, c.courseName, c.department.id from Course c where c.deleted = false and c.active = true",
                " order by c.courseCode, c.id", true, filters("c.faculty.id", facultyId, "c.department.id", departmentId));
    }
    public List<TimetableOption> courseSections(Long courseId, Long academicTermId) {
        return query("select s.id, s.sectionNumber, concat(s.course.courseCode, ' ', s.sectionNumber), s.course.id from CourseSection s"
                + " where s.deleted = false and s.status = 'ACTIVE'", " order by s.course.courseCode, s.sectionNumber, s.id", true,
                filters("s.course.id", courseId, "s.academicTerm.id", academicTermId));
    }
    public List<TimetableOption> lecturers(Long facultyId, Long departmentId) {
        return query("select l.id, l.lectureNameTh, l.lectureNameEn, l.department.id from Lecture l where l.deleted = false and l.status = 'ACTIVE'",
                " order by l.lectureNameEn, l.id", true, filters("l.faculty.id", facultyId, "l.department.id", departmentId));
    }
    public List<TimetableOption> campuses() {
        return query("select s.id, s.siteCode, s.siteName, s.id from CampusSite s where s.deleted = false and s.active = true",
                " order by s.siteName, s.id", false, Map.of());
    }
    public List<TimetableOption> buildings(Long campusId) {
        return query("select b.id, b.buildingCode, b.buildingName, b.site.id from CampusBuilding b where b.deleted = false and b.site.deleted = false",
                " order by b.buildingName, b.id", true, filters("b.site.id", campusId));
    }
    public List<TimetableOption> rooms(Long campusId, Long buildingId) {
        return query("select r.id, r.roomNumber, concat(r.building.buildingCode, ' ', r.roomNumber), r.building.id from CampusRoom r"
                + " where r.deleted = false and r.building.deleted = false", " order by r.building.buildingCode, r.roomNumber, r.id", true,
                filters("r.building.site.id", campusId, "r.building.id", buildingId));
    }

    private List<TimetableOption> query(String select, String order, boolean hasParent, Map<String, Long> filters) {
        var jpql = new StringBuilder(select);
        var parameters = new ArrayList<Long>();
        for (var filter : filters.entrySet()) {
            parameters.add(filter.getValue());
            jpql.append(" and ").append(filter.getKey()).append(" = ?").append(parameters.size());
        }
        var query = entityManager.createQuery(jpql.append(order).toString(), Object[].class);
        for (int i = 0; i < parameters.size(); i++) query.setParameter(i + 1, parameters.get(i));
        var result = new ArrayList<TimetableOption>();
        for (Object[] row : query.getResultList())
            result.add(new TimetableOption((Long) row[0], (String) row[1], (String) row[2], hasParent ? (Long) row[3] : null));
        return result;
    }
    /** Only the non-null values become filters; insertion order is kept for stable parameter numbering. */
    private static Map<String, Long> filters(Object... pathsAndValues) {
        var map = new LinkedHashMap<String, Long>();
        for (int i = 0; i < pathsAndValues.length; i += 2)
            if (pathsAndValues[i + 1] != null) map.put((String) pathsAndValues[i], (Long) pathsAndValues[i + 1]);
        return map;
    }
}
