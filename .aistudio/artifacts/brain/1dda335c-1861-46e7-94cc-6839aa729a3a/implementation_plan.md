# Adaptive Collection-Aware MCQ Import & Custom Sets Architecture

A comprehensive overhaul of the MCQ import pipeline, Room database schema, domain models, and Custom Sets screen in Medika. This design enables the app to intelligently parse, group, store, and navigate multi-tier question banks (including Core BTR style Qbank subjects & categorized tests) while preserving legacy flat JSON support and existing user data.

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your responses, we have established the following baseline rules for import behavior and browsing UX:

- **Confirmed Decision 1 (Re-importing)**: Automatically update existing sets and collections when re-importing matching source IDs/fingerprints, preserving existing user progress and bookmarks.
- **Confirmed Decision 2 (Custom Sets UI)**: Implement a hierarchical drill-down library interface (Collections → Subjects / Sections → Practice Sets / Timed Tests → Questions).

---

## 1. Overview & Core Concept

### What It Does
1. **Adaptive Import Engine**: Automatically detects incoming JSON structures—ranging from flat question arrays to nested collections with subjects, topic sets, and categorized timed tests (such as Core BTR datasets).
2. **Normalized Collection-Set Hierarchy**: Replaces flat single-question rows with an explicit domain hierarchy: `Collection` → `Section / Subject` → `Set` → `Set-Question Membership`.
3. **Smart Upgrades & Deduplication**: Prevents thousand-item list clutter by creating distinct sets with proper metadata (question counts, set types, durations, subjects). Automatically updates matching sets on re-import.
4. **Hierarchical Custom Sets Library**: Provides a clean Material 3 drill-down experience to browse imported question banks, inspect set details, delete sets safely, and jump straight into practice or timed test sessions.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Importing a File**:
   - User taps **Add** on Custom Sets screen or selects **Import JSON**.
   - System auto-detects schema format (Legacy Flat Array, Single Set, Multi-Subject Collection, Core BTR Dataset).
   - Shows **Import Preview & Validation Report** summarizing detected collections, subjects, practice sets, timed tests, and question counts before saving.
   - Saves atomically using Room transactions and updates matching existing sets seamlessly.

2. **Browsing Custom Sets**:
   - Level 1: **Collections List** (e.g., "Core BTR Qbank", "Cardiology Review 2026") showing source badge and set counts.
   - Level 2: **Subject / Section Groupings** (e.g., "Anatomy", "Surgery", "Grand Tests").
   - Level 3: **Set Cards** with badges for set type (`Practice Qbank` vs `Timed Test`), total questions, duration, and completion progress.
   - Level 4: Tap any set to open in `StandardMcqSessionScreen` or `McqList`.

3. **Set Management**:
   - Safe deletion of specific sets or entire collections without deleting shared questions or affecting other sets.

---

## 3. Key Product Decisions & Trade-Offs

- **Normalized Question Storage with Explicit Set Membership**:
  - *Chosen Approach*: Questions reside in normalized entities, while `SetQuestionCrossRef` maintains explicit ordering and set membership.
  - *Why*: Allows identical questions to appear across multiple practice sets or mock exams without duplicating heavy strings or causing key collisions.
- **Room Migration 7 → 8 (Non-Destructive)**:
  - *Chosen Approach*: Add `mcq_collections`, `mcq_sections`, `mcq_sets`, and `set_question_cross_ref` tables. Auto-migrate existing untagged/custom questions into a "Legacy Custom Sets" collection.
  - *Why*: Guarantees zero data loss for existing users' bookmarks, progress history, and custom questions.
- **Flexible Schema Adapters**:
  - *Chosen Approach*: Register format adapters for (A) Legacy Flat Arrays, (B) Single Set Objects, (C) Nested Collections, (D) Core BTR Subject & Test Collections, and (E) Alias variants (`question_text`, `choices`, `is_correct`, `exp_html`).
  - *Why*: Eliminates fragile checks while cleanly handling diverse question bank sources.

---

## 4. Technical Architecture & Data Strategy

### System Architecture & Data Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                          UI & Presentation                             │
│  CustomSourcesScreen (Library)  ◄──► McqViewModel ◄──► SessionScreen   │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼──────────────────────────────────────┐
│                        Data Layer & Importer                           │
│  McqRepository ◄──► McqImporter Pipeline (Adapters + Normalizer)      │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼──────────────────────────────────────┐
│                        Room Persistence (v8)                           │
│  ┌──────────────────────┐    ┌──────────────────────┐                  │
│  │  mcq_collections     │    │  mcq_sections        │                  │
│  └──────────┬───────────┘    └──────────┬───────────┘                  │
│             │                           │                              │
│             ▼                           ▼                              │
│  ┌──────────────────────────────────────────────────┐                  │
│  │                   mcq_sets                       │                  │
│  └──────────────────────┬───────────────────────────┘                  │
│                         │                                              │
│                         ▼                                              │
│            ┌──────────────────────────┐                                │
│            │ set_question_cross_ref   │                                │
│            └────────────┬─────────────┘                                │
│                         │                                              │
│                         ▼                                              │
│            ┌──────────────────────────┐                                │
│            │     mcq_questions        │                                │
│            └──────────────────────────┘                                │
└────────────────────────────────────────────────────────────────────────┘
```

### Data Entities & Schema Design

1. **`CollectionEntity`** (`mcq_collections`):
   - `id`: String (PK)
   - `name`: String
   - `sourceProvider`: String?
   - `importedAt`: Long

2. **`SectionEntity`** (`mcq_sections`):
   - `id`: String (PK)
   - `collectionId`: String (FK)
   - `name`: String
   - `sectionType`: String? (e.g. "subject", "category")
   - `displayOrder`: Int

3. **`McqSetEntity`** (`mcq_sets`):
   - `id`: String (PK)
   - `collectionId`: String (FK)
   - `sectionId`: String? (FK)
   - `sourceSetId`: String?
   - `title`: String
   - `setType`: String ("PRACTICE", "TEST")
   - `subject`: String?
   - `topic`: String?
   - `questionCount`: Int
   - `durationMinutes`: Int?
   - `totalMarks`: Int?
   - `displayOrder`: Int

4. **`SetQuestionCrossRef`** (`set_question_cross_ref`):
   - `setId`: String (FK)
   - `questionId`: String (FK)
   - `displayOrder`: Int
   - Primary Key: (`setId`, `questionId`, `displayOrder`)

5. **`McqEntity`** (`mcq_questions`):
   - Extended with `questionId`: String (PK/Unique Index) for stable cross-set referencing while preserving backwards compatibility.

---

## 5. Execution Plan & Validation Milestones

1. **Phase 1: Domain Models & Room Migration v7 → v8**:
   - Create new entities, DAOs, and write non-destructive Migration 7 to 8 in `RoomDb.kt`.
2. **Phase 2: Adaptive Import Architecture**:
   - Implement `McqImporter` with format adapters for Legacy, Nested, and Core BTR JSONs.
   - Add field alias normalization & validation diagnostics.
3. **Phase 3: Repository & ViewModel Updates**:
   - Update `McqRepository` and `McqViewModel` to support set-based lookups, collection counts, and automatic re-import updates.
4. **Phase 4: Custom Sets UI Redesign**:
   - Upgrade `CustomSourcesScreen.kt` with hierarchical collection/subject/set browsing, preview bottom sheet, and set deletion.
5. **Phase 5: Practice Engine Integration**:
   - Enable opening specific sets with correct question ordering in `StandardMcqSessionScreen`.
6. **Phase 6: Automated Testing & Verification**:
   - Add comprehensive unit tests in `CustomImportLogicTest.kt` for schema detection, normalization, deduplication, and migration.
