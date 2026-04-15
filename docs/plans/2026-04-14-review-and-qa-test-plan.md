# Review and QA Test Plan

## Harness Requirements

### Harness 1: IntelliJ BasePlatformTestCase (existing)

- **What it does:** Provides a lightweight IntelliJ IDE environment with a temporary project directory, fixture-based file creation, and editor/document access. Used by all 10 existing test classes.
- **What it exposes:** `myFixture` (editor, file creation, highlighting), `project` (Project instance with basePath), PsiFile/VirtualFile creation, Document manipulation via WriteCommandAction.
- **Estimated complexity to build:** Zero -- already exists and is battle-tested in this codebase.
- **Tests that depend on it:** Tests 1, 2, 3, 4, 5, 6, 8, 9.

### Harness 2: JUnit 4 plain (existing)

- **What it does:** Standard JUnit 4 test runner. Used by QltyJsonParserTest and SeverityMapperTest.
- **What it exposes:** @Test annotations, standard assertions.
- **Estimated complexity to build:** Zero -- already in use.
- **Tests that depend on it:** Test 7.

### Harness 3: Gradle verifyPlugin task (existing)

- **What it does:** Runs JetBrains plugin verification rules against the built plugin artifact, checking for Marketplace submission compliance.
- **What it exposes:** Exit code 0/non-zero, stderr messages identifying specific violations.
- **Estimated complexity to build:** Zero -- already configured in build.gradle.kts.
- **Tests that depend on it:** Test 1.

No new harnesses need to be built.

---

## Test Plan

### Test 1: Plugin passes Marketplace verification after ID rename

- **Name:** Plugin verification accepts the plugin (no "intellij" substring rejection)
- **Type:** regression
- **Disposition:** existing (fix failing check)
- **Harness:** Gradle verifyPlugin task
- **Preconditions:** `src/main/resources/META-INF/plugin.xml` contains `<id>com.qlty.intellij</id>` (current state, known to fail).
- **Actions:**
  1. Run `./gradlew verifyPlugin` to confirm the failure (expect exit code non-zero, stderr contains "should not include the word 'intellij'").
  2. Change `plugin.xml` line 2 from `<id>com.qlty.intellij</id>` to `<id>com.qlty.jetbrains</id>`.
  3. Run `./gradlew verifyPlugin` again.
  4. Run `./gradlew test` to confirm no regressions.
- **Expected outcome:** `./gradlew verifyPlugin` exits 0 after the change. All existing tests continue to pass. Source of truth: JetBrains Marketplace plugin verification rules (IDs must not contain the substring "intellij").
- **Interactions:** None. The plugin ID is independent of all Kotlin package names and test class references.

### Test 2: External annotator pipeline produces correct QltyResult for apply()

- **Name:** Annotator pipeline delivers issues with correct tool, ruleKey, and message to apply()
- **Type:** integration
- **Disposition:** extend (add to existing QltyExternalAnnotatorTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Project has `.qlty/qlty.toml`. A FakeRunner returns one Issue with known tool/ruleKey/message fields.
- **Actions:**
  1. Create a QltyExternalAnnotator with a FakeRunner that returns a single issue (tool="eslint", ruleKey="no-unused-vars", message="Variable is unused", level="LEVEL_MEDIUM", location with startLine=1, startColumn=7, endLine=1, endColumn=12).
  2. Create a project file "src/demo.kt" with content "hello world\nsecond line\n".
  3. Call `collectInformation(psiFile, editor, false)` to get input.
  4. Call `doAnnotate(input)` to get result.
  5. Assert the result is non-null, contains exactly 1 issue, and the issue fields match the FakeRunner input.
- **Expected outcome:** `result.issues.size == 1`, `result.issues[0].tool == "eslint"`, `result.issues[0].ruleKey == "no-unused-vars"`, `result.issues[0].message == "Variable is unused"`. Source of truth: the QltyExternalAnnotator contract -- collectInformation gathers file info, doAnnotate invokes the runner, and the result carries through to apply().
- **Interactions:** Exercises QltyProjectDetector (config lookup), QltySettings (enabled check), and the QltyRunner interface boundary.

### Test 3: Annotation offset arithmetic, severity mapping, and message formatting match apply() logic

- **Name:** apply() would create annotations at correct offsets with correct severity and message text
- **Type:** unit
- **Disposition:** new (add to existing QltyExternalAnnotatorTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** A document with content "hello world\nsecond line\n" is loaded in the editor. An Issue with startLine=1, startColumn=7, endLine=1, endColumn=12, level="LEVEL_MEDIUM", category="CATEGORY_LINT" is constructed.
- **Actions:**
  1. Call `IssueRangeAdjuster.adjustRangeForSmells(issue, document)` to get the adjusted range.
  2. Compute startOffset and endOffset using the same arithmetic as `apply()`: `document.getLineStartOffset(adjusted.startLine) + adjusted.startCol`.
  3. Call `SeverityMapper.mapSeverity(issue)` and `SeverityMapper.categoryPrefix(issue)`.
  4. Construct the message string using `"$prefix$toolAndRule: $message"`.
- **Expected outcome:**
  - `adjusted.startLine == 0` (1-based to 0-based)
  - `adjusted.startCol == 6` (1-based column 7 to 0-based column 6)
  - `adjusted.endCol == 11` (1-based column 12 to 0-based column 11)
  - `startOffset == 6` ("hello " is 6 chars)
  - `endOffset == 11` ("hello world" ends at offset 11)
  - `severity == HighlightSeverity.WARNING` (LEVEL_MEDIUM maps to WARNING)
  - `message == "eslint:no-unused-vars: Variable is unused"` (CATEGORY_LINT has no prefix)
  - Source of truth: the IntelliJ Document API (getLineStartOffset) and the SeverityMapper/IssueRangeAdjuster source code contracts.
- **Interactions:** Exercises IssueRangeAdjuster and SeverityMapper together as collaborators used by apply().

### Test 4: QltyFixRuleInProjectAction getText includes tool and ruleKey

- **Name:** Fix-all-in-project action displays tool:ruleKey in its text
- **Type:** unit
- **Disposition:** new
- **Harness:** BasePlatformTestCase
- **Preconditions:** None.
- **Actions:**
  1. Construct `QltyFixRuleInProjectAction(tool="eslint", ruleKey="no-unused-vars")`.
  2. Read `action.text`.
- **Expected outcome:** `text == "Fix all eslint:no-unused-vars issues in project"`. Source of truth: the implementation plan specifies this is MVP feature #4, and the existing `QltyFixRuleInFileAction` follows the identical pattern with "in file".
- **Interactions:** None.

### Test 5: QltyFixRuleInProjectAction isAvailable returns true when qlty config exists

- **Name:** Fix-all-in-project action is available in a project with .qlty config
- **Type:** scenario
- **Disposition:** new
- **Harness:** BasePlatformTestCase
- **Preconditions:** Project has `.qlty/qlty.toml`. A file is open in the editor.
- **Actions:**
  1. Create qlty config and open a project file.
  2. Construct `QltyFixRuleInProjectAction(tool="eslint", ruleKey="no-unused-vars")`.
  3. Call `action.isAvailable(project, editor, psiFile)`.
- **Expected outcome:** Returns `true`. Source of truth: the action delegates to `QltyProjectDetector.findQltyRoot()`, which finds the config.
- **Interactions:** Exercises QltyProjectDetector (real implementation, not mocked).

### Test 6: QltyFixRuleInProjectAction isAvailable returns false without qlty config

- **Name:** Fix-all-in-project action is unavailable when no .qlty config exists
- **Type:** boundary
- **Disposition:** new
- **Harness:** BasePlatformTestCase
- **Preconditions:** Project has no `.qlty/qlty.toml`.
- **Actions:**
  1. Open a project file (no qlty config created).
  2. Call `action.isAvailable(project, editor, psiFile)`.
- **Expected outcome:** Returns `false`. Source of truth: QltyProjectDetector returns null when no config exists.
- **Interactions:** Exercises QltyProjectDetector.

### Test 7: QltyFixRuleInProjectAction invoke delegates to fixProjectWithFilter

- **Name:** Fix-all-in-project action calls runner.fixProjectWithFilter with correct tool and ruleKey
- **Type:** integration
- **Disposition:** new
- **Harness:** BasePlatformTestCase
- **Preconditions:** Project has `.qlty/qlty.toml`. A RecordingProjectFixRunner is injected via constructor. backgroundExecutor and uiExecutor are synchronous.
- **Actions:**
  1. Construct action with injected runner, synchronous executors.
  2. Call `action.invoke(project, editor, psiFile)`.
  3. Inspect the RecordingProjectFixRunner's captured calls.
- **Expected outcome:** `runner.fixProjectCalls.size == 1`, with `workDir == project.basePath`, `tool == "eslint"`, `ruleKey == "no-unused-vars"`. Source of truth: the action's contract is to delegate to `QltyRunner.fixProjectWithFilter`. This requires refactoring QltyFixRuleInProjectAction to accept `runnerFactory`, `backgroundExecutor`, and `uiExecutor` constructor parameters (matching the existing pattern in QltyFixFileAction and QltyFixRuleInFileAction).
- **Interactions:** Exercises QltyProjectDetector, FileDocumentManager.saveAllDocuments(), and the QltyRunner interface boundary.

### Test 8: QltyCliRunner.analyzeFile returns empty when plugin disabled

- **Name:** CLI runner skips analysis when Qlty is disabled in settings
- **Type:** boundary
- **Disposition:** extend (add to existing QltyCliRunnerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** `QltySettings.getInstance(project).enabled = false`. A commandExecutor that throws if called.
- **Actions:**
  1. Set settings.enabled = false.
  2. Construct QltyCliRunner with trust=true, binary="/bin/qlty", commandExecutor that errors.
  3. Call `runner.analyzeFile(filePath, workDir)`.
- **Expected outcome:** Returns empty list without calling commandExecutor. Source of truth: QltyCliRunner.analyzeFile checks `settings.enabled` first and returns early.
- **Interactions:** Exercises QltySettings.

### Test 9: QltyCliRunner.analyzeFile returns empty when binary not found

- **Name:** CLI runner skips analysis when qlty binary cannot be located
- **Type:** boundary
- **Disposition:** extend (add to existing QltyCliRunnerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** binaryResolver returns null. commandExecutor errors if called.
- **Actions:**
  1. Construct QltyCliRunner with trust=true, binaryResolver={ null }, commandExecutor that errors.
  2. Call `runner.analyzeFile(filePath, workDir)`.
- **Expected outcome:** Returns empty list. Source of truth: QltyCliRunner.analyzeFile checks binary != null and returns early if null.
- **Interactions:** None beyond QltySettings (enabled=true).

### Test 10: QltyCliRunner.checkProject builds expected command

- **Name:** checkProject invokes qlty with --all --no-progress --json --trigger ide flags
- **Type:** integration
- **Disposition:** extend (add to existing QltyCliRunnerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Trust=true, binary="/bin/qlty", commandExecutor captures calls.
- **Actions:**
  1. Call `runner.checkProject("/tmp/project")`.
  2. Inspect captured command.
- **Expected outcome:** Command is `("/bin/qlty", ["check", "--all", "--no-progress", "--json", "--trigger", "ide"], "/tmp/project")`. Source of truth: QltyCliRunner source, line 129.
- **Interactions:** None.

### Test 11: QltyCliRunner.checkProject returns null when untrusted

- **Name:** checkProject skips when project is not trusted
- **Type:** boundary
- **Disposition:** extend (add to existing QltyCliRunnerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** trustChecker returns false.
- **Actions:** Call `runner.checkProject("/tmp/project")`.
- **Expected outcome:** Returns null, commandExecutor never called. Source of truth: QltyCliRunner.checkProject checks trust first.
- **Interactions:** None.

### Test 12: QltyCliRunner.checkProject returns null when binary not found

- **Name:** checkProject skips when qlty binary is missing
- **Type:** boundary
- **Disposition:** extend (add to existing QltyCliRunnerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** binaryResolver returns null.
- **Actions:** Call `runner.checkProject("/tmp/project")`.
- **Expected outcome:** Returns null. Source of truth: QltyCliRunner.checkProject checks binary != null.
- **Interactions:** None.

### Test 13: QltyCliRunner.fixFile builds expected command

- **Name:** fixFile invokes qlty with --no-progress --fix --trigger ide flags and file path
- **Type:** integration
- **Disposition:** extend (add to existing QltyCliRunnerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Trust=true, binary="/bin/qlty", commandExecutor captures calls.
- **Actions:** Call `runner.fixFile("/tmp/project/src/demo.kt", "/tmp/project")`, inspect captured command.
- **Expected outcome:** Command is `("/bin/qlty", ["check", "--no-progress", "--fix", "--trigger", "ide", "--", "/tmp/project/src/demo.kt"], "/tmp/project")`. Source of truth: QltyCliRunner source, line 143.
- **Interactions:** None.

### Test 14: QltyCliRunner.checkFileWithFilter builds expected command

- **Name:** checkFileWithFilter invokes qlty with --filter flag for the specified tool
- **Type:** integration
- **Disposition:** extend (add to existing QltyCliRunnerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Trust=true, binary="/bin/qlty", commandExecutor captures calls.
- **Actions:** Call `runner.checkFileWithFilter("/tmp/project/src/demo.kt", "/tmp/project", "eslint")`, inspect captured command.
- **Expected outcome:** Command is `("/bin/qlty", ["check", "--no-progress", "--json", "--filter", "eslint", "--", "/tmp/project/src/demo.kt"], "/tmp/project")`. Source of truth: QltyCliRunner source, lines 158-162.
- **Interactions:** None.

### Test 15: QltyCliRunner.fixProjectWithFilter builds expected command

- **Name:** fixProjectWithFilter invokes qlty with --all --fix --filter tool:ruleKey flags
- **Type:** integration
- **Disposition:** extend (add to existing QltyCliRunnerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Trust=true, binary="/bin/qlty", commandExecutor captures calls.
- **Actions:** Call `runner.fixProjectWithFilter("/tmp/project", "eslint", "no-unused-vars")`, inspect captured command.
- **Expected outcome:** Command is `("/bin/qlty", ["check", "--all", "--no-progress", "--fix", "--filter", "eslint:no-unused-vars"], "/tmp/project")`. Source of truth: QltyCliRunner source, line 178.
- **Interactions:** None.

### Test 16: IssueRangeAdjuster collapses file-complexity to start line

- **Name:** file-complexity issues collapse range to the start line with zero columns
- **Type:** unit
- **Disposition:** extend (add to existing IssueRangeAdjusterTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Document with multi-line content. Issue with tool="qlty", ruleKey="file-complexity", spanning lines 1-3.
- **Actions:** Call `IssueRangeAdjuster.adjustRangeForSmells(issue, document)`.
- **Expected outcome:** `startLine==0, endLine==0, startCol==0, endCol==0`. Source of truth: IssueRangeAdjuster source -- file-complexity, similar-code, and identical-code all collapse to the start line.
- **Interactions:** None.

### Test 17: IssueRangeAdjuster collapses similar-code to start line

- **Name:** similar-code issues collapse range to the start line with zero columns
- **Type:** unit
- **Disposition:** extend (add to existing IssueRangeAdjusterTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Document, Issue with ruleKey="similar-code".
- **Actions:** Call `adjustRangeForSmells(issue, document)`.
- **Expected outcome:** `startLine==0, endLine==0, startCol==0, endCol==0`. Source of truth: same branch as file-complexity in IssueRangeAdjuster.
- **Interactions:** None.

### Test 18: IssueRangeAdjuster collapses identical-code to start line

- **Name:** identical-code issues collapse range to the start line with zero columns
- **Type:** unit
- **Disposition:** extend (add to existing IssueRangeAdjusterTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Document, Issue with ruleKey="identical-code".
- **Actions:** Call `adjustRangeForSmells(issue, document)`.
- **Expected outcome:** `startLine==0, endLine==0, startCol==0, endCol==0`. Source of truth: same branch.
- **Interactions:** None.

### Test 19: IssueRangeAdjuster highlights function name for return-statements

- **Name:** return-statements issues highlight the function name extracted from the message
- **Type:** unit
- **Disposition:** extend (add to existing IssueRangeAdjusterTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Document "fun calculate() = Unit\n". Issue with ruleKey="return-statements", message ending with "calculate", snippet="fun calculate() = Unit".
- **Actions:** Call `adjustRangeForSmells(issue, document)`.
- **Expected outcome:** `startCol==4` (index of "calculate" in snippet), `endCol==13` (4 + 9 = 13). Source of truth: IssueRangeAdjuster uses `snippet.indexOf(funcName)` for the "function-complexity" and "return-statements" branch.
- **Interactions:** None.

### Test 20: IssueRangeAdjuster highlights function name for function-complexity

- **Name:** function-complexity issues highlight the function name when found in snippet
- **Type:** unit
- **Disposition:** extend (add to existing IssueRangeAdjusterTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Document "fun doWork() = Unit\n". Issue with ruleKey="function-complexity", message ending with "doWork", snippet="fun doWork() = Unit".
- **Actions:** Call `adjustRangeForSmells(issue, document)`.
- **Expected outcome:** `startCol==4`, `endCol==10` (4 + 6 = 10). Source of truth: same branch as return-statements.
- **Interactions:** None.

### Test 21: QltyJsonParser returns empty list for malformed JSON

- **Name:** Malformed CLI output does not crash the annotator pipeline
- **Type:** boundary
- **Disposition:** new (add to existing QltyJsonParserTest)
- **Harness:** JUnit 4
- **Preconditions:** None.
- **Actions:** Call `QltyJsonParser.parseIssues("{not valid json[")`.
- **Expected outcome:** Returns empty list (no exception thrown). Source of truth: the implementation plan identifies this as a production code fix -- QltyJsonParser currently throws kotlinx.serialization.json.internal.JsonDecodingException on malformed input, which propagates up and crashes the annotator. After the fix (wrapping in try/catch), it returns emptyList(). This requires modifying QltyJsonParser to add try/catch resilience.
- **Interactions:** This test drives a production code change in QltyJsonParser.

### Test 22: QltyJsonParser uses defaults for missing required fields

- **Name:** JSON issues with missing fields deserialize with safe defaults
- **Type:** boundary
- **Disposition:** new (add to existing QltyJsonParserTest)
- **Harness:** JUnit 4
- **Preconditions:** None.
- **Actions:** Call `QltyJsonParser.parseIssues("""[{"location": {"path": "test.kt"}}]""")`.
- **Expected outcome:** Returns list of size 1 with `tool==""`, `ruleKey==""`, `message==""`. Source of truth: the Issue data class uses `= ""` defaults for all String fields; kotlinx.serialization with `coerceInputValues = true` handles missing fields.
- **Interactions:** None.

### Test 23: QltyFmtOnSaveListener does not format when plugin is disabled

- **Name:** Format-on-save skips formatting when the Qlty plugin is disabled in settings
- **Type:** scenario
- **Disposition:** extend (add to existing QltyFmtOnSaveListenerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Project has `.qlty/qlty.toml`. `QltySettings.enabled = false`. A RecordingRunner is injected.
- **Actions:**
  1. Create qlty config and open a file.
  2. Set `QltySettings.getInstance(project).enabled = false`.
  3. Modify the document, then call `listener.beforeAllDocumentsSaving()`.
- **Expected outcome:** `runner.formattedFiles` is empty. Document text is unchanged from the pre-save value. Source of truth: QltyFmtOnSaveListener.beforeAllDocumentsSaving checks `settings.enabled` before adding targets.
- **Interactions:** Exercises QltySettings and QltyProjectDetector.

### Test 24: QltyFmtOnSaveListener does not format when fmtOnSave is false

- **Name:** Format-on-save skips formatting when fmtOnSave setting is disabled
- **Type:** scenario
- **Disposition:** extend (add to existing QltyFmtOnSaveListenerTest)
- **Harness:** BasePlatformTestCase
- **Preconditions:** Project has `.qlty/qlty.toml`. `QltySettings.fmtOnSave = false`. A RecordingRunner is injected.
- **Actions:**
  1. Create qlty config and open a file.
  2. Set `QltySettings.getInstance(project).fmtOnSave = false`.
  3. Modify the document, then call `listener.beforeAllDocumentsSaving()`.
- **Expected outcome:** `runner.formattedFiles` is empty. Source of truth: same guard clause as Test 23.
- **Interactions:** Same as Test 23.

### Test 25: Status bar widget initial state is READY

- **Name:** Newly created status bar widget displays "Qlty" with "Qlty: Ready" tooltip
- **Type:** unit
- **Disposition:** new
- **Harness:** BasePlatformTestCase
- **Preconditions:** A project is available.
- **Actions:**
  1. Construct `QltyStatusBarWidget(project)`.
  2. Read `state`, `text`, `tooltipText`.
- **Expected outcome:** `state == State.READY`, `text == "Qlty"`, `tooltipText == "Qlty: Ready"`. Source of truth: QltyStatusBarWidget State enum definition.
- **Interactions:** None.

### Test 26: Status bar widget transitions through all states

- **Name:** updateState changes display text and tooltip for all five states
- **Type:** scenario
- **Disposition:** new
- **Harness:** BasePlatformTestCase
- **Preconditions:** A widget starts in READY state.
- **Actions:**
  1. Call `widget.updateState(State.ANALYZING)` -- assert text and tooltip.
  2. Call `widget.updateState(State.ERROR)` -- assert text and tooltip.
  3. Call `widget.updateState(State.DISABLED)` -- assert text and tooltip.
  4. Call `widget.updateState(State.NO_CONFIG)` -- assert text and tooltip.
  5. Call `widget.updateState(State.READY)` -- assert text and tooltip.
- **Expected outcome:**
  - ANALYZING: text="Qlty: Analyzing...", tooltip="Qlty: Running analysis on current file"
  - ERROR: text="Qlty: Error", tooltip="Qlty: Analysis failed -- check IDE log for details"
  - DISABLED: text="Qlty: Disabled", tooltip="Qlty: Plugin is disabled in settings"
  - NO_CONFIG: text="Qlty: No Config", tooltip="Qlty: No .qlty/qlty.toml found in project"
  - READY: text="Qlty", tooltip="Qlty: Ready"
  - Source of truth: QltyStatusBarWidget.State enum values.
- **Interactions:** None.

### Test 27: Status bar widget ID is "QltyStatusBar"

- **Name:** Widget ID matches the registration ID in plugin.xml
- **Type:** unit
- **Disposition:** new
- **Harness:** BasePlatformTestCase
- **Preconditions:** None.
- **Actions:** Construct widget, call `widget.ID()`.
- **Expected outcome:** `"QltyStatusBar"`. Source of truth: plugin.xml line 42 (`<statusBarWidgetFactory id="QltyStatusBar" ...>`).
- **Interactions:** None.

---

## Coverage Summary

### Covered by this plan

| Area | Tests | MVP Feature |
|---|---|---|
| Marketplace verification (plugin ID) | 1 | Submission gate |
| QltyExternalAnnotator (collectInformation + doAnnotate + apply offset/severity/message) | 2, 3 | #1: View inline analysis |
| QltyFixRuleInProjectAction (getText, isAvailable, invoke) | 4, 5, 6, 7 | #4: Fix all in project |
| QltyCliRunner (checkProject, fixFile, checkFileWithFilter, fixProjectWithFilter, disabled, binary-not-found) | 8-15 | All (CLI layer) |
| IssueRangeAdjuster (file-complexity, similar-code, identical-code, return-statements, function-complexity) | 16-20 | #1: View inline analysis |
| QltyJsonParser (malformed JSON, missing fields) | 21, 22 | #1: Resilience |
| QltyFmtOnSaveListener (disabled settings) | 23, 24 | Format-on-save |
| QltyStatusBarWidget (state transitions, ID) | 25, 26, 27 | Status bar UX |

### Already covered by existing tests (unchanged)

| Area | Existing Test Class | MVP Feature |
|---|---|---|
| QltyExternalAnnotator collectInformation/doAnnotate | QltyExternalAnnotatorTest (4 tests) | #1 |
| QltyQuickFix (inline fix) | QltyQuickFixTest | #2 |
| QltyFixFileAction (fix all in file) | QltyFixFileActionTest | #3 |
| QltyFixRuleInFileAction (fix rule in file) | QltyFixRuleInFileActionTest | #3 |
| QltyCliRunner (analyzeFile, formatFile, untrusted skip) | QltyCliRunnerTest (3 tests) | CLI |
| IssueRangeAdjuster (non-qlty, nested-control-flow, function-complexity fallback) | IssueRangeAdjusterTest (3 tests) | #1 |
| QltyJsonParser (empty, single, multiple, suggestions, unknown fields) | QltyJsonParserTest (6 tests) | #1 |
| QltyFmtOnSaveListener (format + reload, no-overwrite-on-concurrent-edit) | QltyFmtOnSaveListenerTest (2 tests) | Format-on-save |
| QltyProjectDetector | QltyProjectDetectorTest (2 tests) | All |
| SeverityMapper | SeverityMapperTest | #1 |

### Explicitly excluded (per agreed strategy)

| Area | Reason | Risk |
|---|---|---|
| AnalyzeProjectAction, FormatFileAction, AnalyzeFileAction | Thin AnAction wrappers delegating to tested QltyCliRunner methods. Cannot be meaningfully tested via BasePlatformTestCase without mocking AnActionEvent. Listed as priority 6 in strategy, lower than all included items. | Low: the underlying runner methods are tested. The wrappers have no branching logic. |
| QltyConfigurable settings panel | Trivial Swing wiring, no logic to test. | Negligible. |
| Real IDE visual rendering | Addressed separately by the computer-use test plan in project memory. Not automatable via unit/integration tests. | Medium: visual bugs could exist that these tests cannot catch, but the computer-use plan covers this. |
| QltyProjectDetector cache behavior | Existing 2 tests cover core find/boundary logic. Cache invalidation is an internal optimization. | Low. |

### Production code changes required

1. **QltyJsonParser** -- Add try/catch around `json.decodeFromString` to return `emptyList()` on malformed JSON (driven by Test 21).
2. **QltyFixRuleInProjectAction** -- Add `runnerFactory`, `backgroundExecutor`, `uiExecutor` constructor parameters with defaults matching current behavior (driven by Test 7). This matches the existing pattern in QltyFixFileAction and QltyFixRuleInFileAction.
3. **plugin.xml** -- Change plugin ID from `com.qlty.intellij` to `com.qlty.jetbrains` (driven by Test 1).
