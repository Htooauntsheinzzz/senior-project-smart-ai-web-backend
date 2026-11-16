package com.smartAiUniversityAssistant.seniorproject.feature.academicquery;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.CourseNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.CourseSectionNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.PublishedMeetingTestData;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.EntityManagerFactory;
import java.time.*;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AcademicQueryIntegrationTests extends IntegrationSupport {
    private static final String ADMIN_PASSWORD = "query actor password";
    private static final String PASSWORD = "academic query password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired JwtAuthenticationConverter jwtConverter;
    @Autowired AcademicQueryService queries;
    @Autowired EntityManagerFactory entityManagerFactory;
    private long actorId, faculty, department, program, lectureA, lectureB, semester1, semester2;
    private long courseA, courseB, sectionA1, sectionB1, sectionA2Next;
    private long studentA, studentB;
    private String hash;

    @BeforeEach
    void fixture() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        cleanDatabase();
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = id("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('AQ-ACTOR','Query','Actor','query.actor@example.test','ACTIVE') RETURNING id
                """);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(ADMIN_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='SUPER_ADMIN'", actorId);
        hash = encoder.encode(PASSWORD);
        faculty = id("INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,created_by) VALUES ('ICT','College of ICT','วิทยาลัยไอซีที',?) RETURNING id", actorId);
        department = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('CS','Computer Science',?,?) RETURNING id", faculty, actorId);
        program = id("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('BSC-CS','BSc Computer Science','Bachelor',?,?) RETURNING id", department, actorId);
        lectureA = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,lecture_nickname,faculty_id,department_id,created_by) VALUES ('จอห์น','Dr. John Smith','John',?,?,?) RETURNING id", faculty, department, actorId);
        lectureB = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES ('นิตยา','Nittaya Chindakharn',?,?,?) RETURNING id", faculty, department, actorId);
        semester2 = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2027,'ภาคการศึกษาที่ 1','Semester 1',?) RETURNING id", actorId);
        semester1 = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'ภาคการศึกษาที่ 1','Semester 1',?) RETURNING id", actorId);
        courseB = course("CS302", "Operating Systems", 200, null);
        courseA = course("CS301", "Data Structures & Algorithms", 300, "CS201, CS202");
        sectionB1 = section(courseB, "SEC-01", lectureB, semester1, null, null, 40);
        sectionA1 = section(courseA, "SEC-01", lectureA, semester1, "11-402", "Tuesday 09:00-11:45", 40);
        sectionA2Next = section(courseA, "SEC-02", lectureA, semester2, "11-403", "Friday 13:00-15:45", 40);
        studentA = student("6600001", "Thanapon", "Srisuk");
        studentB = student("6600002", "Malee", "Chaiyo");
        db.update("UPDATE students SET faculty_id=?,department_id=?,program_id=?,semester_id=?,academic_year=2026,enrollment_year=2024,phone_number='0812345678',date_of_birth='2004-05-12' WHERE id=?",
                faculty, department, program, semester1, studentA);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM student_credentials");
        db.update("DELETE FROM students");
    }

    @Test
    void profileComesFromTheAuthenticatedStudentsJwt() throws Exception {
        authenticateWith(login("6600001"));
        var profile = queries.getMyAcademicProfile();
        assertThat(profile.studentId()).isEqualTo(studentA);
        assertThat(profile.studentCode()).isEqualTo("6600001");
        assertThat(profile.fullName()).isEqualTo("Thanapon Srisuk");
        assertThat(profile.universityEmail()).isEqualTo("6600001@rsu.ac.th");
        assertThat(profile.facultyId()).isEqualTo(faculty);
        assertThat(profile.facultyCode()).isEqualTo("ICT");
        assertThat(profile.facultyNameEn()).isEqualTo("College of ICT");
        assertThat(profile.facultyNameTh()).isEqualTo("วิทยาลัยไอซีที");
        assertThat(profile.departmentId()).isEqualTo(department);
        assertThat(profile.departmentCode()).isEqualTo("CS");
        assertThat(profile.departmentName()).isEqualTo("Computer Science");
        assertThat(profile.programId()).isEqualTo(program);
        assertThat(profile.programCode()).isEqualTo("BSC-CS");
        assertThat(profile.programName()).isEqualTo("BSc Computer Science");
        assertThat(profile.semesterId()).isEqualTo(semester1);
        assertThat(profile.semesterAcademicYear()).isEqualTo(2026);
        assertThat(profile.semesterNameEn()).isEqualTo("Semester 1");
        assertThat(profile.semesterNameTh()).isEqualTo("ภาคการศึกษาที่ 1");
        assertThat(profile.academicYear()).isEqualTo(2026);
        assertThat(profile.enrollmentYear()).isEqualTo(2024);
        // No security or unnecessary private fields on any AI-facing result.
        for (Class<?> type : List.of(AcademicProfileQueryResult.class, AcademicEnrollmentQueryResult.class,
                AcademicCourseQueryResult.class, AcademicScheduleQueryResult.class, AcademicCourseSectionQueryResult.class,
                AcademicLecturerQueryResult.class, CourseInformationQueryResult.class, SectionAvailabilityQueryResult.class))
            assertThat(Arrays.stream(type.getRecordComponents()).map(c -> c.getName().toLowerCase(Locale.ROOT)))
                    .noneMatch(n -> n.contains("password") || n.contains("token") || n.contains("phone")
                            || n.contains("birth") || n.contains("created") || n.contains("updated") || n.contains("deleted")
                            || n.contains("hash") || n.contains("failed") || n.contains("attempt") || n.contains("lock")
                            || n.contains("session"));

        // Another Student's token resolves to that Student only.
        authenticateWith(login("6600002"));
        assertThat(queries.getMyAcademicProfile().studentId()).isEqualTo(studentB);
    }

    @Test
    void selfRegisteredStudentGetsNullPlacementAndEmptyLists() throws Exception {
        authenticateWith(login("6600002"));
        var profile = queries.getMyAcademicProfile();
        assertThat(profile.facultyId()).isNull();
        assertThat(profile.departmentId()).isNull();
        assertThat(profile.programId()).isNull();
        assertThat(profile.semesterId()).isNull();
        assertThat(profile.academicYear()).isNull();
        assertThat(queries.getMyEnrollments()).isEmpty();
        assertThat(queries.getMyCourses()).isEmpty();
        assertThat(queries.getMySchedule()).isEmpty();
    }

    @Test
    void myListsReturnOnlyMyActiveNonDeletedEnrollments() throws Exception {
        long other = enroll(studentA, sectionB1, "ACTIVE", false);
        long active = enroll(studentA, sectionA1, "ACTIVE", false);
        enroll(studentA, sectionA2Next, "PENDING", false);
        enroll(studentA, section(courseB, "SEC-09", lectureB, semester2, null, null, 40), "ACTIVE", true);
        enroll(studentB, sectionA1, "ACTIVE", false);
        authenticateAs(studentA);

        var enrollments = queries.getMyEnrollments();
        assertThat(enrollments).extracting(AcademicEnrollmentQueryResult::enrollmentId).containsExactly(active, other);
        var first = enrollments.getFirst();
        assertThat(first.enrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(first.enrollmentDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(first.courseId()).isEqualTo(courseA);
        assertThat(first.courseCode()).isEqualTo("CS301");
        assertThat(first.courseName()).isEqualTo("Data Structures & Algorithms");
        assertThat(first.creditHours()).isEqualTo(3);
        assertThat(first.courseSectionId()).isEqualTo(sectionA1);
        assertThat(first.sectionNumber()).isEqualTo("SEC-01");
        assertThat(first.semesterId()).isEqualTo(semester1);
        assertThat(first.semesterAcademicYear()).isEqualTo(2026);
        assertThat(first.semesterNameEn()).isEqualTo("Semester 1");
        assertThat(first.lecturerId()).isEqualTo(lectureA);
        assertThat(first.lecturerNameEn()).isEqualTo("Dr. John Smith");
        assertThat(first.lecturerNameTh()).isEqualTo("จอห์น");
        assertThat(first.meetings()).extracting(AcademicMeetingQueryResult::roomNumber, AcademicMeetingQueryResult::dayOfWeek)
                .containsExactly(tuple("11-402", "TUESDAY"));

        var courses = queries.getMyCourses();
        assertThat(courses).extracting(AcademicCourseQueryResult::courseCode).containsExactly("CS301", "CS302");
        assertThat(courses.getFirst().courseId()).isEqualTo(courseA);
        assertThat(courses.getFirst().courseName()).isEqualTo("Data Structures & Algorithms");
        assertThat(courses.get(1).courseName()).isEqualTo("Operating Systems");
        assertThat(courses.getFirst().creditHours()).isEqualTo(3);
        assertThat(courses.getFirst().description()).isEqualTo("CS301 description");
        assertThat(courses.getFirst().courseType()).isEqualTo(300);
        assertThat(courses.getFirst().courseTypeName()).isEqualTo("Major Required");
        assertThat(courses.getFirst().courseSectionId()).isEqualTo(sectionA1);
        assertThat(courses.getFirst().semesterId()).isEqualTo(semester1);
        assertThat(courses.get(1).courseTypeName()).isEqualTo("Major Elective");

        var schedule = queries.getMySchedule();
        assertThat(schedule).extracting(AcademicScheduleQueryResult::courseSectionId).containsExactly(sectionA1, sectionB1);
        assertThat(schedule.getFirst().courseId()).isEqualTo(courseA);
        assertThat(schedule.getFirst().courseCode()).isEqualTo("CS301");
        assertThat(schedule.getFirst().courseName()).isEqualTo("Data Structures & Algorithms");
        assertThat(schedule.getFirst().sectionNumber()).isEqualTo("SEC-01");
        assertThat(schedule.getFirst().semesterId()).isEqualTo(semester1);
        assertThat(schedule.get(1).courseCode()).isEqualTo("CS302");
        assertThat(schedule.get(1).courseName()).isEqualTo("Operating Systems");
        assertThat(schedule.getFirst().meetings()).extracting(AcademicMeetingQueryResult::roomNumber, AcademicMeetingQueryResult::dayOfWeek,
                m -> m.startTime().toString(), m -> m.endTime().toString(), AcademicMeetingQueryResult::buildingName)
                .containsExactly(tuple("11-402", "TUESDAY", "09:00", "11:45", PublishedMeetingTestData.BUILDING_NAME));
        assertThat(schedule.getFirst().lecturerId()).isEqualTo(lectureA);
        assertThat(schedule.getFirst().lecturerNameEn()).isEqualTo("Dr. John Smith");
        assertThat(schedule.getFirst().semesterNameEn()).isEqualTo("Semester 1");
        assertThat(schedule.getFirst().semesterNameTh()).isEqualTo("ภาคการศึกษาที่ 1");
        assertThat(schedule.getFirst().semesterAcademicYear()).isEqualTo(2026);
        // A section without published meetings has an empty timetable rather than invented values.
        assertThat(schedule.get(1).meetings()).isEmpty();
        assertThat(schedule.get(1).lecturerNameEn()).isEqualTo("Nittaya Chindakharn");
    }

    @Test
    void myCourseSectionResolvesCodeAndNameWithinMyEnrollmentsOnly() {
        enroll(studentA, sectionA1, "ACTIVE", false);
        enroll(studentB, sectionB1, "ACTIVE", false);
        authenticateAs(studentA);

        for (String identifier : List.of("CS301", "Data Structures & Algorithms", "  cs301 ",
                "data structures & algorithms", "Data Structures")) {
            var section = queries.getMyCourseSection(identifier);
            assertThat(section.courseSectionId()).as(identifier).isEqualTo(sectionA1);
            assertThat(section.courseCode()).isEqualTo("CS301");
            assertThat(section.sectionNumber()).isEqualTo("SEC-01");
            assertThat(section.semesterId()).isEqualTo(semester1);
            assertThat(section.semesterAcademicYear()).isEqualTo(2026);
            assertThat(section.meetings()).extracting(AcademicMeetingQueryResult::roomNumber).containsExactly("11-402");
            assertThat(section.lecturerId()).isEqualTo(lectureA);
            assertThat(section.enrollmentStatus()).isEqualTo("ACTIVE");
        }
        // CS302 exists, and Student B is enrolled in it, but Student A is not: B's section is never returned.
        assertThatThrownBy(() -> queries.getMyCourseSection("CS302"))
                .isInstanceOf(StudentCourseEnrollmentNotFoundException.class)
                .extracting("code", "status").containsExactly("STUDENT_COURSE_ENROLLMENT_NOT_FOUND", 404);
        assertThatThrownBy(() -> queries.getMyCourseSection("XYZ999")).isInstanceOf(CourseNotFoundException.class);
        // The same course ACTIVE in two semesters: the newest semester's section is returned.
        enroll(studentA, sectionA2Next, "ACTIVE", false);
        var newest = queries.getMyCourseSection("CS301");
        assertThat(newest.courseSectionId()).isEqualTo(sectionA2Next);
        assertThat(newest.semesterId()).isEqualTo(semester2);
        assertThat(newest.semesterAcademicYear()).isEqualTo(2027);
        assertThat(queries.getMyCourseLecturer("CS301").courseSectionId()).isEqualTo(sectionA2Next);
        // Only ACTIVE enrollments count as "mine".
        db.update("UPDATE enrollments SET status='WITHDRAWN' WHERE student_id=?", studentA);
        assertThatThrownBy(() -> queries.getMyCourseSection("CS301"))
                .isInstanceOf(StudentCourseEnrollmentNotFoundException.class);
        for (String invalid : Arrays.asList(null, "", "   ", "x".repeat(256), "CS\u0000301", "CS301\nCS302", "CS\u2028301"))
            assertThatThrownBy(() -> queries.getMyCourseSection(invalid))
                    .isInstanceOf(AcademicQueryValidationException.class)
                    .extracting("code", "status").containsExactly("VALIDATION_ERROR", 400);
    }

    @Test
    void ambiguousCoursesAreNeverGuessed() {
        long programming1 = course("CS101", "Programming I", 100, null);
        long programming2 = course("CS102", "Programming II", 100, null);
        long mobile = course("CS340", "Mobile Programming", 200, null);
        long p1 = section(programming1, "SEC-01", lectureA, semester1, null, null, 40);
        long p2 = section(programming2, "SEC-01", lectureB, semester1, null, null, 40);
        enroll(studentA, p1, "ACTIVE", false);
        authenticateAs(studentA);

        // Globally "Programming" is ambiguous; the candidates let the assistant ask which course is meant.
        assertThatThrownBy(() -> queries.getCourseInformation("Programming"))
                .isInstanceOfSatisfying(AcademicCourseAmbiguousException.class, e -> {
                    assertThat(e.code()).isEqualTo("ACADEMIC_COURSE_AMBIGUOUS");
                    assertThat(e.status()).isEqualTo(409);
                    assertThat(e.candidates()).extracting(AcademicCourseCandidate::courseId)
                            .containsExactly(programming1, programming2, mobile);
                });
        // An exact name wins over looser matches: "Programming I" is also a substring of "Programming II".
        assertThat(queries.getCourseInformation("Programming I").courseId()).isEqualTo(programming1);
        // Among several catalogue matches, my single enrollment decides.
        assertThat(queries.getMyCourseSection("Programming").courseSectionId()).isEqualTo(p1);
        // An exact match on a course I am not taking is not redirected to a different course I am taking.
        assertThatThrownBy(() -> queries.getMyCourseSection("Programming II"))
                .isInstanceOf(StudentCourseEnrollmentNotFoundException.class);
        // The reverse matters more: "Programming I" partially matches "Programming II", which Student B takes,
        // but the exact catalogue match decides first, so B is told they are not enrolled instead of getting II.
        enroll(studentB, p2, "ACTIVE", false);
        authenticateAs(studentB);
        assertThatThrownBy(() -> queries.getMyCourseSection("Programming I"))
                .isInstanceOf(StudentCourseEnrollmentNotFoundException.class);
        assertThatThrownBy(() -> queries.getMyCourseLecturer("Programming I"))
                .isInstanceOf(StudentCourseEnrollmentNotFoundException.class);
        assertThat(queries.getMyCourseSection("Programming II").courseSectionId()).isEqualTo(p2);
        authenticateAs(studentA);
        enroll(studentA, p2, "ACTIVE", false);
        assertThatThrownBy(() -> queries.getMyCourseLecturer("programming"))
                .isInstanceOfSatisfying(AcademicCourseAmbiguousException.class, e -> assertThat(e.candidates())
                        .extracting(AcademicCourseCandidate::courseId).containsExactly(programming1, programming2));
        // LIKE wildcards in the identifier are literal characters.
        assertThatThrownBy(() -> queries.getCourseInformation("%")).isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> queries.getCourseInformation("CS_01")).isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void myCourseLecturerReturnsTheSectionsLecturer() {
        enroll(studentA, sectionA1, "ACTIVE", false);
        enroll(studentA, sectionB1, "ACTIVE", false);
        authenticateAs(studentA);

        for (String identifier : List.of("CS301", "Data Structures & Algorithms")) {
            var lecturer = queries.getMyCourseLecturer(identifier);
            assertThat(lecturer.lecturerId()).isEqualTo(lectureA);
            assertThat(lecturer.lecturerNameEn()).isEqualTo("Dr. John Smith");
            assertThat(lecturer.lecturerNameTh()).isEqualTo("จอห์น");
            assertThat(lecturer.lecturerNickname()).isEqualTo("John");
            assertThat(lecturer.courseId()).isEqualTo(courseA);
            assertThat(lecturer.courseSectionId()).isEqualTo(sectionA1);
            assertThat(lecturer.courseCode()).isEqualTo("CS301");
            assertThat(lecturer.courseName()).isEqualTo("Data Structures & Algorithms");
            assertThat(lecturer.sectionNumber()).isEqualTo("SEC-01");
            assertThat(lecturer.semesterId()).isEqualTo(semester1);
        }
        assertThat(queries.getMyCourseLecturer("operating systems").lecturerNickname()).isNull();
        long courseC = course("CS401", "Compilers", 300, "CS301");
        section(courseC, "SEC-01", lectureA, semester1, null, null, 40);
        assertThatThrownBy(() -> queries.getMyCourseLecturer("CS401"))
                .isInstanceOf(StudentCourseEnrollmentNotFoundException.class);
    }

    @Test
    void courseInformationIsCatalogueMetadata() {
        authenticateAs(studentB);
        db.update("UPDATE courses SET program_id=?,maximum_students_per_section=45,recommended_academic_year=2 WHERE id=?",
                program, courseA);
        for (String identifier : List.of("CS301", "cs301", "Data Structures & Algorithms", "data structures")) {
            var course = queries.getCourseInformation(identifier);
            assertThat(course.courseId()).as(identifier).isEqualTo(courseA);
            assertThat(course.courseCode()).isEqualTo("CS301");
            assertThat(course.courseName()).isEqualTo("Data Structures & Algorithms");
            assertThat(course.creditHours()).isEqualTo(3);
            assertThat(course.description()).isEqualTo("CS301 description");
            assertThat(course.courseType()).isEqualTo(300);
            assertThat(course.courseTypeName()).isEqualTo("Major Required");
            assertThat(course.prerequisiteCourses()).isEqualTo("CS201, CS202");
            assertThat(course.maximumStudentsPerSection()).isEqualTo(45);
            assertThat(course.recommendedAcademicYear()).isEqualTo(2);
            assertThat(course.active()).isTrue();
            assertThat(course.facultyId()).isEqualTo(faculty);
            assertThat(course.facultyNameEn()).isEqualTo("College of ICT");
            assertThat(course.departmentId()).isEqualTo(department);
            assertThat(course.departmentName()).isEqualTo("Computer Science");
            assertThat(course.programId()).isEqualTo(program);
            assertThat(course.programName()).isEqualTo("BSc Computer Science");
        }
        var withoutProgram = queries.getCourseInformation("CS302");
        assertThat(withoutProgram.programId()).isNull();
        assertThat(withoutProgram.prerequisiteCourses()).isNull();
        // Inactive courses remain visible with their flag; deleted ones do not exist for the assistant.
        db.update("UPDATE courses SET is_active=FALSE WHERE id=?", courseB);
        assertThat(queries.getCourseInformation("CS302").active()).isFalse();
        db.update("UPDATE courses SET is_deleted=TRUE WHERE id=?", courseB);
        assertThatThrownBy(() -> queries.getCourseInformation("CS302")).isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> queries.getCourseInformation("Operating")).isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> queries.getCourseInformation("XYZ999")).isInstanceOf(CourseNotFoundException.class)
                .extracting("code").isEqualTo("COURSE_NOT_FOUND");
    }

    @Test
    void sectionAvailabilityCountsPendingAndActiveSeatsOnly() {
        long section = section(courseB, "SEC-02", lectureB, semester1, "IT-201", null, 40);
        int n = 0;
        for (var entry : Map.of("PENDING", 3, "ACTIVE", 35, "WITHDRAWN", 5, "DROPPED", 2).entrySet())
            for (int i = 0; i < entry.getValue(); i++) enroll(student("77" + String.format("%05d", n++), "S", "T"), section, entry.getKey(), false);
        enroll(student("7799999", "Deleted", "Seat"), section, "ACTIVE", true);
        authenticateAs(studentA);

        var availability = queries.getSectionAvailability(section);
        assertThat(availability.courseSectionId()).isEqualTo(section);
        assertThat(availability.courseId()).isEqualTo(courseB);
        assertThat(availability.courseCode()).isEqualTo("CS302");
        assertThat(availability.sectionNumber()).isEqualTo("SEC-02");
        assertThat(availability.capacity()).isEqualTo(40);
        assertThat(availability.occupiedSeats()).isEqualTo(38);
        assertThat(availability.availableSeats()).isEqualTo(2);
        assertThat(availability.isFull()).isFalse();
        assertThat(availability.sectionStatus()).isEqualTo("ACTIVE");
        assertThat(availability.semesterId()).isEqualTo(semester1);
        assertThat(availability.semesterAcademicYear()).isEqualTo(2026);

        db.update("UPDATE course_sections SET capacity=38 WHERE id=?", section);
        var full = queries.getSectionAvailability(section);
        assertThat(full.capacity()).isEqualTo(38);
        assertThat(full.occupiedSeats()).isEqualTo(38);
        assertThat(full.availableSeats()).isZero();
        assertThat(full.isFull()).isTrue();
        // Data written outside the capacity guard never yields negative seats.
        db.update("UPDATE course_sections SET capacity=30 WHERE id=?", section);
        var over = queries.getSectionAvailability(section);
        assertThat(over.availableSeats()).isZero();
        assertThat(over.isFull()).isTrue();

        db.update("UPDATE course_sections SET is_deleted=TRUE WHERE id=?", section);
        assertThatThrownBy(() -> queries.getSectionAvailability(section)).isInstanceOf(CourseSectionNotFoundException.class);
        assertThatThrownBy(() -> queries.getSectionAvailability(Long.MAX_VALUE)).isInstanceOf(CourseSectionNotFoundException.class);
        assertThatThrownBy(() -> queries.getSectionAvailability(null)).isInstanceOf(AcademicQueryValidationException.class);
        assertThatThrownBy(() -> queries.getSectionAvailability(0L)).isInstanceOf(AcademicQueryValidationException.class);
    }

    @Test
    void myCourseSectionFeedsSectionAvailability() throws Exception {
        enroll(studentA, sectionA1, "ACTIVE", false);
        enroll(studentB, sectionA1, "PENDING", false);
        authenticateWith(login("6600001"));
        long sectionId = queries.getMyCourseSection("CS301").courseSectionId();
        var availability = queries.getSectionAvailability(sectionId);
        assertThat(availability.occupiedSeats()).isEqualTo(2);
        assertThat(availability.availableSeats()).isEqualTo(38);
    }

    @Test
    void everyQueryRequiresAnActiveStudentContext() throws Exception {
        List<Runnable> calls = List.of(queries::getMyAcademicProfile, queries::getMyEnrollments, queries::getMyCourses,
                queries::getMySchedule, () -> queries.getMyCourseSection("CS301"), () -> queries.getMyCourseLecturer("CS301"),
                () -> queries.getCourseInformation("CS301"), () -> queries.getSectionAvailability(sectionA1));
        for (var call : calls) {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(call::run).isInstanceOfSatisfying(AuthenticationFailure.class,
                    e -> assertThat(e.code()).isEqualTo("UNAUTHORIZED"));
            SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
            assertThatThrownBy(call::run).isInstanceOf(AuthenticationFailure.class);
        }
        // An admin JWT never silently becomes a Student context.
        authenticateWith(adminLogin());
        for (var call : calls) assertThatThrownBy(call::run).isInstanceOf(AccessDeniedException.class);
        // A Student principal without ROLE_STUDENT is rejected too.
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedStudent(studentA, "6600001", "sid", false), null, List.of()));
        for (var call : calls) assertThatThrownBy(call::run).isInstanceOf(AccessDeniedException.class);
        // Forced password change keeps the same restriction as the student routes.
        authenticateAs(studentA, true);
        for (var call : calls) assertThatThrownBy(call::run).isInstanceOf(PasswordChangeRequiredException.class);
        // Non-ACTIVE and deleted Students are refused even with a still-present context.
        authenticateAs(studentA);
        for (String state : List.of("SUSPENDED", "INACTIVE", "PENDING")) {
            db.update("UPDATE students SET account_status=? WHERE id=?", state, studentA);
            for (var call : calls) assertThatThrownBy(call::run).as(state)
                    .isInstanceOf(StudentAccountNotActiveException.class)
                    .extracting("code").isEqualTo("STUDENT_ACCOUNT_NOT_ACTIVE");
        }
        db.update("UPDATE students SET account_status='ACTIVE',is_deleted=TRUE WHERE id=?", studentA);
        for (var call : calls) assertThatThrownBy(call::run).isInstanceOf(StudentNotFoundException.class);
    }

    /** Spec 29 verification matrix: each future question mapped by hand to the method that answers it. */
    @Test
    void verificationMatrixAnswersEachQuestionWithoutAi() {
        // On top of the shared fixture: CS301 SEC-02 in the same semester is taught by Dr. Jane Doe, CSC452 is the
        // Student's second course, and terminal, soft-deleted and other-Student rows must never show up.
        long janeDoe = lecturer("เจน", "Dr. Jane Doe");
        long janeSection = section(courseA, "SEC-02", janeDoe, semester1, "11-410", "Thursday 13:00-15:45", 40);
        long mobile = course("CSC452", "Mobile Programming", 300, null);
        long mobileSection = section(mobile, "SEC-02", lectureB, semester1, "5-201", "Friday 09:00-11:45", 40);
        long cs301 = enroll(studentA, sectionA1, "ACTIVE", false);
        long csc452 = enroll(studentA, mobileSection, "ACTIVE", false);
        enroll(studentA, section(course("CS350", "Computer Networks", 300, null), "SEC-01", lectureA, semester1, null, null, 40), "WITHDRAWN", false);
        enroll(studentA, section(course("CS360", "Database Systems", 300, null), "SEC-01", lectureA, semester1, null, null, 40), "DROPPED", false);
        enroll(studentA, section(course("CS370", "Software Engineering", 300, null), "SEC-01", lectureA, semester1, null, null, 40), "ACTIVE", true);
        enroll(studentB, janeSection, "ACTIVE", false);
        // CS301 SEC-01 seats: 30 ACTIVE (Student A included), 5 PENDING, 3 WITHDRAWN, 2 DROPPED.
        int n = 0;
        for (var seats : Map.of("ACTIVE", 29, "PENDING", 5, "WITHDRAWN", 3, "DROPPED", 2).entrySet())
            for (int i = 0; i < seats.getValue(); i++)
                enroll(student("78" + String.format("%05d", n++), "S", "T"), sectionA1, seats.getKey(), false);
        authenticateAs(studentA);

        // AQ-001 "What courses am I enrolled in?" -> getMyEnrollments()
        var enrollments = queries.getMyEnrollments();
        assertThat(enrollments).as("AQ-001").extracting(AcademicEnrollmentQueryResult::enrollmentId).containsExactly(cs301, csc452);
        assertThat(enrollments).extracting(AcademicEnrollmentQueryResult::courseCode).containsExactly("CS301", "CSC452");
        assertThat(enrollments).extracting(AcademicEnrollmentQueryResult::enrollmentStatus).containsOnly("ACTIVE");
        // "What courses do I have?" -> getMyCourses()
        assertThat(queries.getMyCourses()).extracting(AcademicCourseQueryResult::courseCode, AcademicCourseQueryResult::creditHours)
                .containsExactly(tuple("CS301", 3), tuple("CSC452", 3));

        // AQ-002 "Which section am I in for CS301?" and AQ-004 "Where is my CS301 class?" -> getMyCourseSection("CS301")
        var section = queries.getMyCourseSection("CS301");
        assertThat(section.courseCode()).as("AQ-002").isEqualTo("CS301");
        assertThat(section.courseSectionId()).isEqualTo(sectionA1);
        assertThat(section.sectionNumber()).isEqualTo("SEC-01");
        assertThat(section.meetings()).as("AQ-004").extracting(AcademicMeetingQueryResult::roomNumber, AcademicMeetingQueryResult::dayOfWeek)
                .containsExactly(tuple("11-402", "TUESDAY"));
        assertThat(section.lecturerNameEn()).isEqualTo("Dr. John Smith");

        // AQ-003 "Who teaches CS301?" -> the lecturer of my own section, not Dr. Jane Doe's SEC-02.
        var lecturer = queries.getMyCourseLecturer("CS301");
        assertThat(lecturer.lecturerId()).as("AQ-003").isEqualTo(lectureA).isNotEqualTo(janeDoe);
        assertThat(lecturer.lecturerNameEn()).isEqualTo("Dr. John Smith");
        assertThat(lecturer.courseCode()).isEqualTo("CS301");
        assertThat(lecturer.courseSectionId()).isEqualTo(sectionA1);
        assertThat(lecturer.sectionNumber()).isEqualTo("SEC-01");

        // AQ-005 "What is my schedule?" -> getMySchedule()
        assertThat(queries.getMySchedule()).as("AQ-005")
                .extracting(AcademicScheduleQueryResult::courseCode, s -> s.meetings().getFirst().dayOfWeek(),
                        s -> s.meetings().getFirst().roomNumber())
                .containsExactly(tuple("CS301", "TUESDAY", "11-402"), tuple("CSC452", "FRIDAY", "5-201"));

        // AQ-006 "How many credits is CS301?" -> getCourseInformation("CS301")
        var course = queries.getCourseInformation("CS301");
        assertThat(course.creditHours()).as("AQ-006").isEqualTo(3);
        assertThat(course.prerequisiteCourses()).isEqualTo("CS201, CS202");

        // AQ-007 program, AQ-008 department, and faculty -> getMyAcademicProfile()
        var profile = queries.getMyAcademicProfile();
        assertThat(profile.programCode()).as("AQ-007").isEqualTo("BSC-CS");
        assertThat(profile.departmentCode()).as("AQ-008").isEqualTo("CS");
        assertThat(profile.facultyCode()).isEqualTo("ICT");

        // AQ-009 "Is section 801 full?" and AQ-010 "How many seats are left?" -> getSectionAvailability(id)
        var availability = queries.getSectionAvailability(sectionA1);
        assertThat(availability.occupiedSeats()).as("AQ-009").isEqualTo(35);
        assertThat(availability.availableSeats()).as("AQ-010").isEqualTo(5);
        assertThat(availability.isFull()).isFalse();
    }

    @Test
    void deletedSectionsAndCoursesLeaveMyResultsButClosedSectionsStay() throws Exception {
        enroll(studentA, sectionA1, "ACTIVE", false);
        enroll(studentA, sectionB1, "ACTIVE", false);
        authenticateAs(studentA);
        // A section without published meetings has an empty timetable rather than invented values.
        assertThat(queries.getMyCourseSection("CS302").meetings()).isEmpty();

        // CLOSED only stops new enrollments; an existing ACTIVE enrollment is still the Student's class.
        db.update("UPDATE course_sections SET status='CLOSED' WHERE id=?", sectionB1);
        assertThat(queries.getMyCourseSection("CS302").courseSectionId()).isEqualTo(sectionB1);
        assertThat(queries.getSectionAvailability(sectionB1).sectionStatus()).isEqualTo("CLOSED");

        String admin = adminLogin();
        admin(delete("/api/v1/admin/course-sections/" + sectionB1), admin);
        assertThat(queries.getMyEnrollments()).extracting(AcademicEnrollmentQueryResult::courseSectionId).containsExactly(sectionA1);
        assertThat(queries.getMyCourses()).extracting(AcademicCourseQueryResult::courseSectionId).containsExactly(sectionA1);
        assertThat(queries.getMySchedule()).extracting(AcademicScheduleQueryResult::courseSectionId).containsExactly(sectionA1);
        assertThatThrownBy(() -> queries.getMyCourseSection("CS302")).isInstanceOf(StudentCourseEnrollmentNotFoundException.class);
        assertThatThrownBy(() -> queries.getMyCourseLecturer("CS302")).isInstanceOf(StudentCourseEnrollmentNotFoundException.class);

        assertThatThrownBy(() -> queries.getSectionAvailability(sectionB1)).isInstanceOf(CourseSectionNotFoundException.class);
        assertThat(queries.getSectionAvailability(sectionA1).courseCode()).isEqualTo("CS301");
        admin(delete("/api/v1/admin/courses/" + courseA), admin);
        assertThat(queries.getMyEnrollments()).isEmpty();
        // The Course delete leaves its Sections undeleted, but they are not live for seat questions either.
        assertThatThrownBy(() -> queries.getSectionAvailability(sectionA1)).isInstanceOf(CourseSectionNotFoundException.class);
        assertThatThrownBy(() -> queries.getMyCourseSection("CS301")).isInstanceOf(CourseNotFoundException.class);
        // The student routes share the query, including the history view.
        assertThat(studentGet(login("6600001"), "/enrollments?status=ALL")).isEmpty();
    }

    @Test
    void adminChangesShowUpOnTheNextQuery() throws Exception {
        long janeDoe = lecturer("เจน", "Dr. Jane Doe");
        long mobileSection = section(course("CSC452", "Mobile Programming", 300, null), "SEC-02", lectureB, semester1,
                "5-201", "Friday 09:00-11:45", 40);
        long cs301 = enroll(studentA, sectionA1, "ACTIVE", false);
        long cs302 = enroll(studentA, sectionB1, "ACTIVE", false);
        authenticateAs(studentA);
        assertThat(queries.getMyCourseSection("CS301").meetings()).extracting(AcademicMeetingQueryResult::roomNumber).containsExactly("11-402");
        assertThat(queries.getMyCourseLecturer("CS301").lecturerId()).isEqualTo(lectureA);
        assertThat(queries.getCourseInformation("CS301").creditHours()).isEqualTo(3);
        // The admin Course update validates prerequisites, which the SQL fixture never created.
        course("CS201", "Discrete Mathematics", 100, null);
        course("CS202", "Computer Organization", 100, null);
        String admin = adminLogin();

        admin(put("/api/v1/admin/course-sections/" + sectionA1).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("courseId", courseA, "sectionNumber", "SEC-01", "capacity", 40,
                        "lectureId", janeDoe,
                        "semesterId", semester1, "status", "ACTIVE"))), admin);
        PublishedMeetingTestData.setRoom(db, sectionA1, "11-405");
        assertThat(queries.getMyCourseSection("CS301").meetings()).extracting(AcademicMeetingQueryResult::roomNumber).containsExactly("11-405");
        assertThat(queries.getMyCourseLecturer("CS301").lecturerNameEn()).isEqualTo("Dr. Jane Doe");

        admin(put("/api/v1/admin/courses/" + courseA).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("courseCode", "CS301", "courseName", "Data Structures & Algorithms",
                        "creditHours", 4, "description", "CS301 description", "facultyId", faculty, "departmentId", department,
                        "prerequisiteCourses", "CS201, CS202", "courseType", 300, "isActive", true))), admin);
        assertThat(queries.getCourseInformation("CS301").creditHours()).isEqualTo(4);
        assertThat(queries.getMyCourses().getFirst().creditHours()).isEqualTo(4);

        admin(post("/api/v1/admin/enrollments").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("studentId", studentA, "courseSectionId", mobileSection, "status", "ACTIVE"))), admin);
        assertThat(codes()).containsExactly("CS301", "CS302", "CSC452");
        admin(patch("/api/v1/admin/enrollments/" + cs302 + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("status", "WITHDRAWN"))), admin);
        assertThat(codes()).containsExactly("CS301", "CSC452");
        admin(delete("/api/v1/admin/enrollments/" + cs301), admin);
        assertThat(codes()).containsExactly("CSC452");
    }

    @Test
    void studentRoutesAgreeWithTheQueryService() throws Exception {
        enroll(studentA, sectionA1, "ACTIVE", false);
        enroll(studentA, sectionB1, "ACTIVE", false);
        enroll(studentA, sectionA2Next, "PENDING", false);
        String token = login("6600001");
        authenticateWith(token);

        var profile = studentGet(token, "/profile");
        var queried = queries.getMyAcademicProfile();
        assertThat(profile.get("studentId").asLong()).isEqualTo(queried.studentId());
        assertThat(profile.get("faculty").get("code").asText()).isEqualTo(queried.facultyCode());
        assertThat(profile.get("department").get("code").asText()).isEqualTo(queried.departmentCode());
        assertThat(profile.get("program").get("code").asText()).isEqualTo(queried.programCode());
        assertThat(longs(studentGet(token, "/enrollments"), "enrollmentId"))
                .isEqualTo(queries.getMyEnrollments().stream().map(AcademicEnrollmentQueryResult::enrollmentId).toList());
        assertThat(longs(studentGet(token, "/courses"), "courseSectionId"))
                .isEqualTo(queries.getMyCourses().stream().map(AcademicCourseQueryResult::courseSectionId).toList());
        assertThat(longs(studentGet(token, "/schedule"), "courseSectionId"))
                .isEqualTo(queries.getMySchedule().stream().map(AcademicScheduleQueryResult::courseSectionId).toList());
        assertThat(longs(studentGet(token, "/lecturers"), "lecturerId"))
                .containsExactly(queries.getMyCourseLecturer("CS301").lecturerId(), queries.getMyCourseLecturer("CS302").lecturerId());
    }

    @Test
    void listQueriesUseConstantStatementsAndNeverWrite() {
        for (int i = 0; i < 10; i++) {
            long course = course("PERF" + i, "Performance " + i, 100, null);
            long lecture = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES (?,?,?,?,?) RETURNING id",
                    "อาจารย์" + i, "Lecturer " + i, faculty, department, actorId);
            long semester = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2030,?,?,?) RETURNING id",
                    "ภาค " + i, "Term " + i, actorId);
            enroll(studentA, section(course, "SEC-01", lecture, semester, "R" + i, "Wed", 40), "ACTIVE", false);
        }
        authenticateAs(studentA);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            for (Runnable call : List.<Runnable>of(queries::getMyEnrollments, queries::getMyCourses, queries::getMySchedule)) {
                statistics.clear();
                call.run();
                // Student with placement, credential check, enrollments with every relationship, one meetings query and, since
                // the timetable (D58), one query for the exceptions of those meetings: five, however many sections there are.
                assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
            }
            statistics.clear();
            queries.getMyAcademicProfile();
            queries.getMyCourseSection("PERF3");
            queries.getMyCourseLecturer("Performance 4");
            queries.getCourseInformation("perf5");
            queries.getSectionAvailability(sectionA1);
            assertThat(statistics.getEntityInsertCount() + statistics.getEntityUpdateCount()
                    + statistics.getEntityDeleteCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private List<String> codes() {
        return queries.getMyEnrollments().stream().map(AcademicEnrollmentQueryResult::courseCode).toList();
    }
    private void admin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token)
            throws Exception {
        var response = perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isBetween(200, 299);
    }
    private JsonNode studentGet(String token, String path) throws Exception {
        var result = perform(get("/api/v1/student/academic" + path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
    // MockMvc clears the SecurityContext after each request; keep the Student context the service calls use.
    private org.springframework.test.web.servlet.ResultActions perform(
            org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            return mvc.perform(request);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
    private static List<Long> longs(JsonNode array, String field) {
        var values = new ArrayList<Long>();
        array.forEach(node -> values.add(node.get(field).asLong()));
        return values;
    }
    private void authenticateWith(String token) {
        SecurityContextHolder.getContext().setAuthentication(jwtConverter.convert(jwtDecoder.decode(token)));
    }
    private void authenticateAs(long studentId) { authenticateAs(studentId, false); }
    private void authenticateAs(long studentId, boolean forcePasswordChange) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedStudent(studentId, "code", "sid", forcePasswordChange), null,
                AuthorityUtils.createAuthorityList("ROLE_STUDENT")));
    }

    private void cleanDatabase() {
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM student_credentials");
        db.update("DELETE FROM students");
        db.update("UPDATE app_users SET department_id=NULL,created_by=NULL,updated_by=NULL");
        PublishedMeetingTestData.clean(db);
        db.update("DELETE FROM course_sections");
        db.update("DELETE FROM courses");
        db.update("DELETE FROM lectures");
        db.update("DELETE FROM semesters");
        db.update("DELETE FROM programs");
        db.update("DELETE FROM departments");
        db.update("DELETE FROM faculties");
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("DELETE FROM app_users");
    }

    private long course(String code, String name, int type, String prerequisites) {
        return id("""
                INSERT INTO courses(course_code,course_name,credit_hours,description,faculty_id,department_id,course_type,prerequisite_courses,created_by)
                VALUES (?,?,3,?,?,?,?,?,?) RETURNING id
                """, code, name, code + " description", faculty, department, type, prerequisites, actorId);
    }
    private long lecturer(String nameTh, String nameEn) {
        return id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES (?,?,?,?,?) RETURNING id",
                nameTh, nameEn, faculty, department, actorId);
    }
    private long section(long course, String number, long lecture, long semester, String room, String schedule, int capacity) {
        long id = id("""
                INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,status,created_by)
                VALUES (?,?,?,?,?,'ACTIVE',?) RETURNING id
                """, course, number, capacity, lecture, semester, actorId);
        PublishedMeetingTestData.publish(db, id, room, schedule);
        return id;
    }
    private long student(String code, String first, String last) {
        long id = id("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status)
                VALUES (?,?,?,?,'ACTIVE') RETURNING id
                """, code, code + "@rsu.ac.th", first, last);
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, hash);
        return id;
    }
    private long enroll(long student, long section, String status, boolean deleted) {
        return id("""
                INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,is_deleted,created_by)
                VALUES (?,?,DATE '2026-08-15',?,?,?) RETURNING id
                """, student, section, status, deleted, actorId);
    }
    private String login(String code) throws Exception {
        var result = perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
    private String adminLogin() throws Exception {
        var result = perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", "query.actor@example.test", "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
}
