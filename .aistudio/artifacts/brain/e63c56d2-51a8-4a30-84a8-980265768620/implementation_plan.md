# Implementation Plan - UI Polish & Inconsistency Fixes

Address the UI defects and inconsistencies identified across the three screenshots while adhering to lazy, efficient engineering practices.

## Proposed Changes

### 1. Explanation Formatting & Bot Tag Cleanup (`HtmlText.kt`)
- **Problem (Screenshot 1)**: Explanation displays unrendered markdown asterisks (`**Stem of the lateral sulcus**`, `**Pterion**`) and raw channel handles (`@neet_pg_bot`).
- **Fix**:
  - Add lightweight preprocessing in `formatExplanationHtml` to convert Markdown bold (`**...**` and `__...__`) to `<b>...</b>` and italics to `<i>...</i>` prior to HTML styling.
  - Smartly clean trailing bot handles/watermarks (e.g., regex pattern `(?m)^\s*@[a-zA-Z0-9_]+\s*$`) so explanations display clean clinical prose.

### 2. Source Documents & Topic Browser (`McqUiComponents.kt`)
- **Problem (Screenshot 2)**:
  - Redundant back arrow icon `<` next to `Browse Topics in File` directly below the main TopAppBar's back icon.
  - Awkward wording: `"38 MCQs / 0 over"`.
- **Fix**:
  - Remove the duplicate inner back `IconButton` in `BrowseTopicsInFileContent`, keeping the breadcrumb (`Files / <FileName>`) and clear title hierarchy.
  - Update the item counter label to `"${item.totalCount} MCQs • ${item.finishedCount} completed"`.

### 3. Custom Sets Screen Header & List Spacing (`CustomSourcesScreen.kt`)
- **Problem (Screenshot 3)**:
  - Duplicate TopAppBars: Root `MainAppLayout` shows `< Custom Sets`, and nested `CustomSourcesScreen` creates a second `< Custom Sets Library` bar with a second back arrow.
  - Stray empty card outline at the top of the collection list.
  - Floating Action Button (`+ Import Bank`) covers the bottom card and its delete button.
- **Fix**:
  - Remove redundant nested `TopAppBar` when embedded in `MainAppLayout` (respect `showTopBar` or omit the redundant nested top bar in library view).
  - Remove the empty clipped container at the top of the list.
  - Add `contentPadding = PaddingValues(bottom = 96.dp)` to the `LazyColumn` so all cards and action buttons can be fully scrolled clear of the FAB.

---

## Verification Plan

### Automated Tests & Builds
- Run `compile_applet` to ensure syntax, imports, and Compose UI layouts compile cleanly.
- Run `gradle :app:testDebugUnitTest` to guarantee all existing parser and repository tests pass.

### Manual Verification via Emulator
- Verify Question Session: open a question and check explanation rendering (true bold text without raw asterisks, no `@bot` watermarks).
- Verify Topic Browser: navigate to `Files / <File>` and confirm single back arrow and `X MCQs • Y completed` label.
- Verify Custom Sets: open Custom Sets and confirm a single top app bar, no empty card artifacts, and bottom cards scroll freely above the FAB.
