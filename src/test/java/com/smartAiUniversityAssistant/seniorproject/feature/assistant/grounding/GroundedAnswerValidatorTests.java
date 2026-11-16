package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingFixtures.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingValidationResult.Outcome.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingValidationResult.Outcome;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.GroundedAnswerValidatorImpl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec §25/§26: deterministic checks of the values that can be verified, never a model's own verdict. */
class GroundedAnswerValidatorTests {
    private final GroundedAnswerValidator validator = new GroundedAnswerValidatorImpl();

    private static AcademicEvidence verified(String tool, Object data, String identifier) {
        return new AcademicEvidence(tool, data instanceof List<?> l && l.isEmpty() ? VERIFIED_EMPTY : VERIFIED, data,
                Instant.EPOCH, null, identifier);
    }

    private Outcome outcome(String answer, AcademicEvidence... evidence) {
        return validator.validate(answer, List.of(evidence)).outcome();
    }

    private static final AcademicEvidence SMITH = verified("get_my_course_lecturer", lecturer("CS301", "SEC-01", "Dr. John Smith"), "CS301");
    private static final AcademicEvidence CS301 = verified("get_course_information", course("CS301", "Data Structures", 3), "CS301");

    @Test
    void verifiedLecturerAnswersPassIncludingEquivalentTitles() {
        assertThat(outcome("Your CS301 course is taught by Dr. John Smith.", SMITH)).isEqualTo(GROUNDED);
        assertThat(outcome("Professor John Smith teaches your class.", SMITH)).isEqualTo(GROUNDED);
        assertThat(outcome("Dr. Smith's section is SEC-01.", SMITH)).isEqualTo(GROUNDED);
        assertThat(outcome("Your lecturer is John Smith.", SMITH)).isEqualTo(GROUNDED);
    }

    @Test
    void aDifferentLecturerIsInconsistentAndAnyLecturerWithoutEvidenceIsUnsupported() {
        var result = validator.validate("CS301 is taught by Dr. Jane Lee.", List.of(SMITH));
        assertThat(result.outcome()).isEqualTo(INCONSISTENT_ACADEMIC_VALUE);
        assertThat(result.issues()).containsExactly("LECTURER");
        var missing = verified("get_my_course_lecturer", lecturer("CSC452", "SEC-02", null), "CSC452");
        assertThat(outcome("The lecturer for your CSC452 section is not currently available.", missing)).isEqualTo(GROUNDED);
        assertThat(outcome("CSC452 is taught by Dr. Jane Lee.", missing)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
    }

    @Test
    void creditHoursMustEqualTheCatalogueValueInDigitsOrWords() {
        assertThat(outcome("CS301 is worth 3 credit hours.", CS301)).isEqualTo(GROUNDED);
        assertThat(outcome("The course carries three credit hours.", CS301)).isEqualTo(GROUNDED);
        assertThat(outcome("CS301 มี 3 หน่วยกิต", CS301)).isEqualTo(GROUNDED);
        assertThat(outcome("Credit hours: 3.", CS301)).isEqualTo(GROUNDED);
        assertThat(outcome("Credit hours: 4.", CS301)).isEqualTo(INCONSISTENT_ACADEMIC_VALUE);
        var wrong = validator.validate("CS301 is worth 4 credits.", List.of(CS301));
        assertThat(wrong.outcome()).isEqualTo(INCONSISTENT_ACADEMIC_VALUE);
        assertThat(wrong.issues()).containsExactly("CREDIT_HOURS").noneMatch(i -> i.contains("4"));
        assertThat(outcome("CS301 is worth five credits.", CS301)).isEqualTo(INCONSISTENT_ACADEMIC_VALUE);
        // A credit value with no credit evidence at all is unsupported.
        assertThat(outcome("CS301 is worth 3 credits.", SMITH)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
    }

    @Test
    void courseListsMayNotGainCoursesTheQueryDidNotReturn() {
        var enrollments = verified("get_my_enrollments", List.of(enrollment("CS301", "Data Structures", "SEC-01"),
                enrollment("CSC452", "Mobile Programming", "SEC-02")), null);
        assertThat(outcome("You are enrolled in:\n1. CS301 — Data Structures\n2. CSC452 — Mobile Programming", enrollments)).isEqualTo(GROUNDED);
        var invented = validator.validate("1. CS301\n2. CSC452\n3. CS305\n4. CSC486", List.of(enrollments));
        assertThat(invented.outcome()).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
        assertThat(invented.issues()).containsExactly("COURSE_CODE");
    }

    @Test
    void roomsSectionsTimesAndDaysMustComeFromTheEvidence() {
        var noRoom = verified("get_my_course_section", section("CS301", "SEC-01", null, null, "Dr. John Smith"), "CS301");
        assertThat(outcome("Your CS301 classroom information is not currently available.", noRoom)).isEqualTo(GROUNDED);
        assertThat(outcome("Your class is in Room 11-402.", noRoom)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
        assertThat(outcome("Your CS301 class meets on Tuesday at 09:00.", noRoom)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
        var moved = verified("get_my_course_section", section("CS301", "SEC-01", "11-405", "Tuesday 09:00-11:45", "Dr. John Smith"), "CS301");
        assertThat(outcome("CS301 is in room 11-405, Tuesday 09:00-11:45.", moved)).isEqualTo(GROUNDED);
        assertThat(outcome("CS301 is in 9:00 to 11:45 on Tuesdays in room 11-405.", moved)).isEqualTo(GROUNDED);
        // An old room remembered from the conversation contradicts the fresh result.
        assertThat(outcome("CS301 is still in room 11-402.", moved)).isEqualTo(INCONSISTENT_ACADEMIC_VALUE);
        assertThat(outcome("You are in SEC-02 of CS301.", moved)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
        assertThat(outcome("CS301 also meets Friday 13:00-15:45.", moved)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
    }

    @Test
    void seatCountsMustMatchTheAvailabilityResult() {
        var seats = verified("get_section_availability", availability(40, 3), "1501");
        assertThat(outcome("Section 1501 has 37 of 40 seats available; 3 seats are taken.", seats)).isEqualTo(GROUNDED);
        assertThat(outcome("CS301 SEC-01 is not full: 37 seats left.", seats)).isEqualTo(GROUNDED);
        assertThat(outcome("There are 12 seats available.", seats)).isEqualTo(INCONSISTENT_ACADEMIC_VALUE);
        assertThat(outcome("Capacity is 50.", seats)).isEqualTo(INCONSISTENT_ACADEMIC_VALUE);
        assertThat(outcome("It has 20 seats.", SMITH)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
    }

    @Test
    void semestersAndYearsCannotBeInferred() {
        var none = verified("get_my_academic_profile", profile("Computer Science", null), null);
        assertThat(outcome("Your current semester information is not available.", none)).isEqualTo(GROUNDED);
        assertThat(outcome("You are in Semester 2 of 2026.", none)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
        var first = verified("get_my_academic_profile", profile("Computer Science", "Semester 1"), null);
        assertThat(outcome("You are in Semester 1 (2026).", first)).isEqualTo(GROUNDED);
        assertThat(outcome("You are in Semester 2.", first)).isEqualTo(INCONSISTENT_ACADEMIC_VALUE);
    }

    @Test
    void programNamesMustMatchTheProfile() {
        var profile = verified("get_my_academic_profile", profile("Computer Science", "Semester 1"), null);
        assertThat(outcome("Your program is Computer Science.", profile)).isEqualTo(GROUNDED);
        assertThat(outcome("Your program is Medicine.", profile)).isEqualTo(INCONSISTENT_ACADEMIC_VALUE);
        assertThat(outcome("Your major is Medicine.", SMITH)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
    }

    @Test
    void universityFactsInAnAnswerAreRecognisedButGeneralExplanationsAreNot() {
        for (String facts : List.of("Your class is in room 11-402.", "CS301 is taught by Dr. John Smith.", "You are in SEC-01.",
                "Your program is Medicine.", "Rooms in use: 11-500 and 9-101."))
            assertThat(validator.containsUniversityFacts(facts)).as(facts).isTrue();
        for (String general : Arrays.asList("A credit hour is a unit of study time.", "Most courses are worth 3 credits.",
                "GPA means grade point average.", "", null))
            assertThat(validator.containsUniversityFacts(general)).as(String.valueOf(general)).isFalse();
    }

    @Test
    void answersWithoutCheckableClaimsAndGeneralExplanationsPass() {
        assertThat(outcome("I couldn't find that information. Please check the course code.", SMITH)).isEqualTo(GROUNDED);
        assertThat(outcome("A credit hour is a unit that measures study time.", CS301)).isEqualTo(GROUNDED);
        assertThat(outcome("", SMITH)).isEqualTo(GROUNDED);
        // A course the backend queried may be named even when it was not found.
        var notFound = new AcademicEvidence("get_course_information", NOT_FOUND, null, Instant.EPOCH, "COURSE_NOT_FOUND", "XYZ999");
        assertThat(outcome("I could not find XYZ999.", notFound)).isEqualTo(GROUNDED);
    }

    @Test
    void noEvidenceIsMissingAndClaimsWithoutAnySuccessfulQueryAreUnverifiable() {
        assertThat(validator.validate("You are in Computer Science.", List.of()).outcome()).isEqualTo(MISSING_REQUIRED_EVIDENCE);
        assertThat(validator.validate("x", null).valid()).isFalse();
        var invalid = new AcademicEvidence("get_my_course_lecturer", ERROR, null, Instant.EPOCH, "INVALID_AI_TOOL_ARGUMENTS", null);
        assertThat(outcome("CS301 is taught by Dr. John Smith in 11-402.", invalid)).isEqualTo(UNVERIFIABLE_ANSWER);
    }

    @Test
    void ambiguityCandidatesAreTrustedBackendData() {
        var ambiguous = new AcademicEvidence("get_my_course_lecturer", AMBIGUOUS,
                List.of(map("courseCode", "CSC452", "courseName", "Mobile Programming"), map("courseCode", "CSC453", "courseName", "Web Programming")),
                Instant.EPOCH, "ACADEMIC_COURSE_AMBIGUOUS", "Programming");
        assertThat(outcome("Do you mean CSC452 Mobile Programming or CSC453 Web Programming?", ambiguous)).isEqualTo(GROUNDED);
        // Only candidates, no successful query: a course outside them cannot be verified.
        assertThat(outcome("Do you mean CSC452 or CSC499?", ambiguous)).isEqualTo(UNVERIFIABLE_ANSWER);
    }

    @Test
    void anotherStudentsCodeOrRoomIsNotSupportedByThisStudentsEvidence() {
        var mine = verified("get_my_academic_profile", profile("Computer Science", "Semester 1"), null);
        assertThat(outcome("Your student code is STD-TEST-001.", mine)).isEqualTo(GROUNDED);
        assertThat(outcome("Student STD-TEST-002 studies Information Systems.", mine)).isEqualTo(UNSUPPORTED_ACADEMIC_CLAIM);
    }
}
