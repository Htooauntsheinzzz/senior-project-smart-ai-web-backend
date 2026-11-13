package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import java.util.List;

/**
 * Retrieval evaluation cases for the sample document of spec file 47, section 19 (spec file 52, section 18.1). FOUND cases
 * name text that appears in exactly one chunk; NOT_FOUND cases have no answer in the document. X cases ask in another
 * language than the passage. A native speaker should check the Thai and Myanmar questions before they are relied on.
 * Spec file 55, section 12: each case names its document; the CUR cases ask about the sample curriculum
 * (docker/dev-fixtures/rag-sample-curriculum-cs.txt).
 */
public final class KnowledgeRetrievalEvaluationDataset {
    private KnowledgeRetrievalEvaluationDataset() {}

    public enum Expectation { FOUND, NOT_FOUND }

    /** The sample document a case is searched in. */
    public enum Document {
        REGULATIONS("docker/dev-fixtures/rag-sample-regulations.txt"), CURRICULUM("docker/dev-fixtures/rag-sample-curriculum-cs.txt");
        public final String path;
        Document(String path) { this.path = path; }
    }

    public record Case(String caseId, String language, String question, Expectation expectation, String mustContain, Document document) {
        public String group() { return caseId.substring(0, caseId.indexOf('-')); }
    }

    private static Case found(String id, String language, String question, String mustContain) {
        return new Case(id, language, question, Expectation.FOUND, mustContain, Document.REGULATIONS);
    }

    private static Case curriculum(String id, String language, String question, String mustContain) {
        return new Case(id, language, question, Expectation.FOUND, mustContain, Document.CURRICULUM);
    }

    private static Case none(String id, String language, String question) {
        return new Case(id, language, question, Expectation.NOT_FOUND, null, Document.REGULATIONS);
    }

    public static final List<Case> CASES = List.of(
            found("EN-01", "en", "How many credits do I need to graduate?", "at least 120 credits"),
            found("EN-02", "en", "What is the minimum GPA required for graduation?", "at least 2.00"),
            found("EN-03", "en", "What is the maximum number of credits I can register for in a regular semester?", "at most 22 credits"),
            found("EN-04", "en", "Until when can I drop a course without it showing on my transcript?", "first 14 days of a regular semester"),
            // "500 baht" alone also occurs in "1,500 baht" (chunk 2); the longer text names exactly one chunk.
            found("EN-05", "en", "How much is the late registration fee?", "fee of 500 baht"),
            found("EN-06", "en", "What happens if I pay my tuition late?", "50 baht per day"),
            found("EN-07", "en", "What attendance do I need to take the final exam?", "80 percent"),
            found("EN-08", "en", "How long do I have to ask for a grade review?", "within 7 days"),
            found("EN-09", "en", "When must I apply for graduation?", "first 30 days of the final semester"),
            found("EN-10", "en", "How many weeks is the summer session?", "lasts 8 weeks"),
            found("EN-11", "en", "What grade is recorded if I withdraw from a course?", "grade W"),
            found("EN-12", "en", "Can I take the midterm if I have not paid tuition?", "end of week 4"),
            found("TH-01", "th", "ลาพักการศึกษาได้นานที่สุดกี่ภาคการศึกษา", "ไม่เกิน 2 ภาคการศึกษา"),
            found("TH-02", "th", "ค่าธรรมเนียมรักษาสถานภาพนักศึกษาเท่าไร", "1,000 บาท"),
            found("X-01", "th", "ต้องเรียนกี่หน่วยกิตจึงจะสำเร็จการศึกษา", "at least 120 credits"),
            found("X-02", "th", "ค่าปรับลงทะเบียนล่าช้าเท่าไร", "fee of 500 baht"),
            found("X-03", "en", "How long can a student take a leave of absence?", "ไม่เกิน 2 ภาคการศึกษา"),
            found("X-04", "my", "ဘွဲ့ရရန် ခရက်ဒစ် မည်မျှ လိုအပ်သနည်း", "at least 120 credits"),
            none("NA-01", "en", "What are the opening hours of the swimming pool?"),
            none("NA-02", "en", "How do I apply for a dormitory room?"),
            none("NA-03", "en", "What scholarships are available for international students?"),
            none("NA-04", "th", "ห้องสมุดเปิดกี่โมง"),
            none("NA-05", "en", "How do I reset my university email password?"),
            none("NA-06", "en", "What is the capital of France?"),
            curriculum("CUR-01", "en", "What are the graduation requirements of the computer science program?", "all of these conditions are met"),
            curriculum("CUR-02", "en", "What electives can I choose?", "chosen from the list below"),
            curriculum("CUR-03", "en", "Is there an internship option instead of cooperative education?", "CSC491 Internship"),
            curriculum("CUR-04", "en", "How many credits does the computer science program require?", "at least 132 credits to graduate"),
            curriculum("CUR-05", "en", "How is the curriculum structured?", "divided into three groups"),
            curriculum("CUR-06", "en", "Do I have to pass every major required course?", "must pass all major required courses"),
            curriculum("CUR-07", "en", "What do I need before I can register for cooperative education?", "earned at least 90 credits"),
            curriculum("CUR-08", "en", "Is Compiler Construction an elective?", "CSC486 | Compiler Construction"),
            curriculum("CUR-TH-01", "th", "ต้องมีหน่วยกิตสะสมเท่าไรจึงจะลงทะเบียนสหกิจศึกษาได้", "ไม่น้อยกว่า 90 หน่วยกิต"),
            curriculum("CUR-X-01", "th", "วิชาเลือกเอกต้องเรียนอย่างน้อยกี่หน่วยกิต", "at least 15 credits of major elective courses"));
}
