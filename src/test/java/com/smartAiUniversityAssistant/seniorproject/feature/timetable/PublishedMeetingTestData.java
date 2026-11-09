package com.smartAiUniversityAssistant.seniorproject.feature.timetable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test data for the published timetable of a course section (spec 40). Course sections no longer carry free-text
 * room and schedule columns, so fixtures that used to write "11-402" / "Tuesday 09:00-11:45" on the section now
 * publish one weekly LECTURE meeting per named weekday in a campus room of that number (site, building and room are
 * created on demand). A published meeting needs a campus room, so a section without a room gets no meeting.
 */
public final class PublishedMeetingTestData {
    public static final String SITE_CODE = "TST-SITE";
    public static final String BUILDING_CODE = "TST-B1";
    public static final String BUILDING_NAME = "Test Building";
    private static final Pattern TIMES = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})");
    private static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");

    private PublishedMeetingTestData() {
    }

    /** Publishes the section's meetings: weekdays and times from schedule (Monday 09:00-12:00 when absent). */
    public static List<Long> publish(JdbcTemplate db, long sectionId, String roomNumber, String schedule) {
        if (roomNumber == null) return List.of();
        long room = room(db, roomNumber);
        String start = "09:00", end = "12:00";
        Matcher m = TIMES.matcher(schedule == null ? "" : schedule);
        if (m.find()) {
            start = pad(m.group(1)) + ":" + m.group(2);
            end = pad(m.group(3)) + ":" + m.group(4);
        }
        var ids = new ArrayList<Long>();
        for (int day : days(schedule)) {
            ids.add(db.queryForObject("""
                    INSERT INTO course_section_meetings(course_section_id,meeting_type,day_of_week,start_time,end_time,
                        effective_from,effective_to,room_id,room_label,status,is_active)
                    VALUES (?,'LECTURE',?,?::time,?::time,DATE '2026-01-05',DATE '2026-12-25',?,?,'PUBLISHED',TRUE) RETURNING id
                    """, Long.class, sectionId, day, start, end, room, roomNumber));
        }
        return ids;
    }

    /** Moves every meeting of the section to campus room roomNumber; null unpublishes them (no visible timetable). */
    public static void setRoom(JdbcTemplate db, long sectionId, String roomNumber) {
        if (roomNumber == null) {
            db.update("UPDATE course_section_meetings SET room_id=NULL,room_label=NULL,status='DRAFT',is_active=FALSE WHERE course_section_id=?", sectionId);
        } else {
            db.update("UPDATE course_section_meetings SET room_id=?,room_label=?,status='PUBLISHED',is_active=TRUE WHERE course_section_id=?",
                    room(db, roomNumber), roomNumber, sectionId);
        }
    }

    /** Deletes all meetings and their exceptions plus the campus records this class created; call before sections. */
    public static void clean(JdbcTemplate db) {
        db.update("DELETE FROM timetable_meeting_exceptions");
        db.update("DELETE FROM course_section_meetings");
        db.update("DELETE FROM campus_rooms WHERE building_id IN (SELECT id FROM campus_buildings WHERE building_code=?)", BUILDING_CODE);
        db.update("DELETE FROM campus_buildings WHERE building_code=?", BUILDING_CODE);
        db.update("DELETE FROM campus_sites WHERE site_code=?", SITE_CODE);
    }

    /** The room number of the first meeting in an assistant tool result ("meetings" list), or null when none. */
    public static Object room(Map<?, ?> toolData) {
        if (!(toolData.get("meetings") instanceof List<?> meetings) || meetings.isEmpty()) return null;
        return ((Map<?, ?>) meetings.getFirst()).get("roomNumber");
    }

    private static long room(JdbcTemplate db, String number) {
        long building = building(db);
        Long existing = db.query("SELECT id FROM campus_rooms WHERE building_id=? AND room_number=?",
                rs -> rs.next() ? rs.getLong(1) : null, building, number);
        return existing != null ? existing : db.queryForObject(
                "INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type) VALUES (?,?,1,'CLASSROOM') RETURNING id",
                Long.class, building, number);
    }

    private static long building(JdbcTemplate db) {
        Long existing = db.query("SELECT id FROM campus_buildings WHERE building_code=?", rs -> rs.next() ? rs.getLong(1) : null, BUILDING_CODE);
        if (existing != null) return existing;
        Long site = db.query("SELECT id FROM campus_sites WHERE site_code=?", rs -> rs.next() ? rs.getLong(1) : null, SITE_CODE);
        if (site == null) site = db.queryForObject("INSERT INTO campus_sites(site_code,site_name) VALUES (?,'Test Campus') RETURNING id", Long.class, SITE_CODE);
        return db.queryForObject("INSERT INTO campus_buildings(site_id,building_code,building_name,total_floors) VALUES (?,?,?,3) RETURNING id",
                Long.class, site, BUILDING_CODE, BUILDING_NAME);
    }

    private static List<Integer> days(String schedule) {
        var out = new ArrayList<Integer>();
        if (schedule != null) for (String word : schedule.split("[^A-Za-z]+")) {
            if (word.length() < 3) continue;
            int i = DAYS.indexOf(word.substring(0, 3).toLowerCase(Locale.ROOT));
            if (i >= 0 && !out.contains(i + 1)) out.add(i + 1);
        }
        return out.isEmpty() ? List.of(1) : out;
    }

    private static String pad(String hour) { return hour.length() == 1 ? "0" + hour : hour; }
}
