package com.example

import com.example.data.McqImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McqImportPipelineTest {



    @Test
    fun testLegacyFlatJsonArrayParsing() {
        val json = """[
            {
                "subject": "Physiology",
                "topic": "Renal",
                "question": "What is the primary site of glucose reabsorption in nephrons?",
                "options": ["Proximal Convoluted Tubule", "Loop of Henle", "Distal Tubule", "Collecting Duct"],
                "correct_answer": "Proximal Convoluted Tubule",
                "explanation": "Over 99% of filtered glucose is reabsorbed in PCT via SGLT2 and SGLT1."
            }
        ]"""

        val errors = mutableListOf<String>()
        val collection = McqImporter.parseToCanonicalCollection(json, "custom://flat_test.json", errors)

        assertTrue("Should have no fatal parsing errors", errors.isEmpty())
        assertNotNull(collection)
        assertEquals("flat test", collection.name)

        val allQs = collection.unsectionedSets.flatMap { it.questions } + collection.sections.flatMap { sec -> sec.sets.flatMap { it.questions } }
        assertEquals(1, allQs.size)
        val q = allQs.first()
        assertEquals("Physiology", q.subject)
        assertEquals("Renal", q.topic)
        assertEquals("Proximal Convoluted Tubule", q.correct_answer)
    }

    @Test
    fun testCoreBtrFormatParsingWithQbankAndTests() {
        val json = """{
            "collection_name": "Core BTR Question Bank",
            "source_provider": "CoreBTR Medical",
            "qbank": {
                "Anatomy": {
                    "Abdomen & Pelvis": [
                        {
                            "question": "Which artery supplies the foregut?",
                            "options": ["Celiac Trunk", "Superior Mesenteric Artery", "Inferior Mesenteric Artery"],
                            "correct_answer": "Celiac Trunk"
                        }
                    ]
                }
            },
            "tests": {
                "Grand Tests": [
                    {
                        "set_name": "Grand Test 1",
                        "duration_minutes": 180,
                        "questions": [
                            {
                                "question": "What is the most common cause of acute appendicitis in adults?",
                                "options": ["Fecalith", "Lymphoid Hyperplasia", "Foreign Body"],
                                "correct_answer": "Fecalith"
                            }
                        ]
                    }
                ]
            }
        }"""

        val errors = mutableListOf<String>()
        val collection = McqImporter.parseToCanonicalCollection(json, "custom://CoreBTR.json", errors)

        assertTrue("Should parse Core BTR format without errors", errors.isEmpty())
        assertEquals("Core BTR Question Bank", collection.name)
        assertEquals(2, collection.sections.size)

        val qbankSec = collection.sections.find { it.name.contains("Anatomy") }
        assertNotNull("Qbank section for Anatomy should exist", qbankSec)
        assertEquals(1, qbankSec!!.sets.size)
        val abdSet = qbankSec.sets.first()
        assertEquals("Abdomen & Pelvis", abdSet.title)
        assertEquals("PRACTICE", abdSet.setType)
        assertEquals(1, abdSet.questions.size)

        val testSec = collection.sections.find { it.name == "Grand Tests" }
        assertNotNull("Tests section for Grand Tests should exist", testSec)
        assertEquals(1, testSec!!.sets.size)
        val gtSet = testSec.sets.first()
        assertEquals("Grand Test 1", gtSet.title)
        assertEquals("TEST", gtSet.setType)
        assertEquals(180, gtSet.durationMinutes)
    }

    @Test
    fun testOptionObjectsWithIsCorrectFlags() {
        val json = """{
            "title": "Pathology Quiz",
            "questions": [
                {
                    "question_text": "Which condition is characterized by Reed-Sternberg cells?",
                    "choices": [
                        {"text": "Hodgkin Lymphoma", "is_correct": true},
                        {"text": "Non-Hodgkin Lymphoma", "is_correct": false},
                        {"text": "Multiple Myeloma", "is_correct": false}
                    ],
                    "solution": "Reed-Sternberg cells are diagnostic pathognomonic giant cells found in Hodgkin lymphoma."
                }
            ]
        }"""

        val errors = mutableListOf<String>()
        val collection = McqImporter.parseToCanonicalCollection(json, "custom://pathology.json", errors)

        val set = collection.unsectionedSets.firstOrNull() ?: collection.sections.flatMap { it.sets }.first()
        assertEquals(1, set.questions.size)
        val q = set.questions.first()
        assertEquals("Which condition is characterized by Reed-Sternberg cells?", q.question)
        assertEquals(3, q.options.size)
        assertEquals("Hodgkin Lymphoma", q.correct_answer)
        assertEquals("Reed-Sternberg cells are diagnostic pathognomonic giant cells found in Hodgkin lymphoma.", q.explanation)
    }

    @Test
    fun testPreviewReportGeneration() {
        val json = """[
            {
                "subject": "Microbiology",
                "topic": "Bacteriology",
                "question": "Which bacteria is gram positive coagulase positive?",
                "options": ["Staphylococcus aureus", "Staphylococcus epidermidis", "Streptococcus pyogenes"],
                "correct_answer": "Staphylococcus aureus"
            }
        ]"""

        val errors = mutableListOf<String>()
        val collection = McqImporter.parseToCanonicalCollection(json, "custom://micro.json", errors)
        val preview = McqImporter.buildPreviewReport(collection)

        assertEquals("micro", preview.collectionName)

        assertEquals(1, preview.totalQuestions)
        assertEquals(1, preview.setsCount)
    }

    @Test
    fun testZipStreamParsingHierarchy() {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("CORE-BTR-JSON/manifest.json"))
            zos.write("""{
                "schema_version": "1.0.0",
                "dataset_name": "Core BTR Test Bank",
                "source_file": "Test Source"
            }""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("CORE-BTR-JSON/qbank/anatomy/01-neuroanatomy.json"))
            zos.write("""{
                "id": "01_neuroanatomy",
                "title": "Neuroanatomy",
                "type": "qbank_topic",
                "path": ["qBank", "Anatomy"],
                "duration_minutes": 71,
                "questions": [
                    {
                        "id": "Q-1",
                        "text": "Charcot artery supplies which limb of internal capsule?",
                        "options": [
                            {"label": "A", "text": "Posterior limb", "correct": true},
                            {"label": "B", "text": "Anterior limb", "correct": false}
                        ],
                        "correct_answer": "Posterior limb",
                        "explanation": "Supplies posterior limb."
                    }
                ]
            }""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("CORE-BTR-JSON/tests/grand-test/gt-1.json"))
            zos.write("""{
                "id": "gt_1",
                "title": "Grand Test 1",
                "type": "test",
                "path": ["tests", "downloaded_tests", "grand_test"],
                "duration_minutes": 180,
                "questions": [
                    {
                        "id": "Q-2",
                        "text": "Most common site of ectopic pregnancy?",
                        "options": ["Ampulla", "Isthmus", "Fimbria"],
                        "correct_answer": "Ampulla",
                        "explanation": "Ampulla is the most common site."
                    }
                ]
            }""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val errors = mutableListOf<String>()
        val zipInputStream = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(baos.toByteArray()))
        val canonicalCol = McqImporter.parseZipStream(zipInputStream, "CoreBTR.zip", errors)

        assertTrue("Should have no errors", errors.isEmpty())
        assertEquals("Core BTR Test Bank", canonicalCol.name)
        assertEquals(2, canonicalCol.sections.size)

        val qbankSec = canonicalCol.sections.find { it.name.contains("Anatomy") }
        assertNotNull(qbankSec)
        assertEquals("subject", qbankSec!!.sectionType)
        assertEquals(1, qbankSec.sets.size)
        assertEquals("Neuroanatomy", qbankSec.sets[0].title)
        assertEquals("PRACTICE", qbankSec.sets[0].setType)
        assertEquals(1, qbankSec.sets[0].questions.size)
        assertEquals("Posterior limb", qbankSec.sets[0].questions[0].correct_answer)

        val testSec = canonicalCol.sections.find { it.name.contains("Grand Test") }
        assertNotNull(testSec)
        assertEquals("test_category", testSec!!.sectionType)
        assertEquals("TEST", testSec.sets[0].setType)
        assertEquals(1, testSec.sets[0].questions.size)
    }
}
