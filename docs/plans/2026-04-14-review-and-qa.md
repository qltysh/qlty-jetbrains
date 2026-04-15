# Qlty JetBrains Plugin Review and QA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use trycycle-executing to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the verifyPlugin Marketplace rejection, then close all meaningful test coverage gaps in the qlty-jetbrains plugin to verify the four MVP features (inline analysis, quick fix, fix-all-in-file, fix-all-in-project) behave correctly.

**Architecture:** The plugin follows the standard IntelliJ External Annotator pattern: `QltyExternalAnnotator` runs on a background thread via `collectInformation` -> `doAnnotate` -> `apply`, with the `apply()` method creating editor annotations and attaching quick-fix intention actions. All CLI interactions are abstracted behind the `QltyRunner` interface, which every test class already fakes via constructor injection. New tests follow the same `BasePlatformTestCase` pattern used by the existing 10 test classes.

**Tech Stack:** Kotlin, JetBrains IntelliJ Platform SDK (2024.3+), Gradle with IntelliJ Platform Plugin 2.13.1, JUnit 4, kotlinx.serialization

---

## File Structure

### Files to modify

| File | Responsibility |
|---|---|
| `src/main/resources/META-INF/plugin.xml` | Change plugin ID from `com.qlty.intellij` to `com.qlty.jetbrains` to pass `verifyPlugin` |
| `build.gradle.kts` | No changes expected (plugin ID comes from `plugin.xml`) |
| `src/test/kotlin/com/qlty/intellij/QltyExternalAnnotatorTest.kt` | Add `apply()` test covering annotation creation, severity, message format, range, tooltip, and attached quick fixes |
| `src/test/kotlin/com/qlty/intellij/QltyCliRunnerTest.kt` | Add tests for `checkProject()`, `fixFile()`, `checkFileWithFilter()`, `fixProjectWithFilter()`, disabled-settings path, binary-not-found path |
| `src/test/kotlin/com/qlty/intellij/IssueRangeAdjusterTest.kt` | Add tests for `file-complexity`, `similar-code`, `identical-code`, `return-statements`, and `function-complexity` success path |
| `src/test/kotlin/com/qlty/intellij/QltyJsonParserTest.kt` | Add malformed JSON resilience test |
| `src/test/kotlin/com/qlty/intellij/QltyFmtOnSaveListenerTest.kt` | Add tests for disabled settings paths |

### Files to create

| File | Responsibility |
|---|---|
| `src/test/kotlin/com/qlty/intellij/QltyFixRuleInProjectActionTest.kt` | Test `QltyFixRuleInProjectAction` availability and invocation (MVP feature #4) |
| `src/test/kotlin/com/qlty/intellij/QltyStatusBarWidgetTest.kt` | Test status bar widget state transitions |

### Decision log

1. **Plugin ID change: `com.qlty.intellij` -> `com.qlty.jetbrains`** -- The JetBrains Marketplace rejects plugin IDs containing the substring "intellij". The ID `com.qlty.jetbrains` is the natural choice: it matches the repository name, describes the plugin's scope (all JetBrains IDEs), and avoids the rejection. The ID only appears in `plugin.xml` line 2. The Kotlin package names (`com.qlty.intellij`) remain unchanged because renaming packages is a much larger change with no user-visible benefit, and the plugin ID and Java package are independent concerns.

2. **No test for `AnalyzeProjectAction` / `FormatFileAction` / `AnalyzeFileAction`** -- These three `AnAction` subclasses cannot be meaningfully tested via `BasePlatformTestCase` because `AnActionEvent` construction requires a `DataContext` and `ActionManager` wiring that the lightweight test fixture does not provide. The existing pattern in this codebase tests actions that implement `IntentionAction` (which can be invoked with `(project, editor, psiFile)`), not `AnAction` subclasses. Attempting to test these would require either (a) mocking `AnActionEvent`, which tests implementation details rather than behavior, or (b) creating a full `ActionManager` test harness, which is high-cost and low-value for what are thin delegation wrappers. The underlying `QltyCliRunner` methods they delegate to are already tested. The approved testing strategy listed these as priority 6, after all higher-value items.

3. **`QltyFixRuleInProjectAction` needs a new test file** -- Unlike the other fix actions, it has zero test coverage. It covers MVP feature #4 (fix all instances of a specific issue at project scope). Since it implements `IntentionAction`, it can be tested the same way as `QltyFixRuleInFileAction`. However, `invoke()` spawns a pooled thread and uses `VirtualFileManager.asyncRefresh`, making it harder to test deterministically. The test will verify `isAvailable()` and `getText()` behavior; testing `invoke()` end-to-end would require refactoring the action to accept injected executors (matching the pattern in `QltyFixFileAction` and `QltyFixRuleInFileAction`). That refactoring is included in this plan as it is idiomatic with the rest of the codebase.

4. **Status bar widget tests are unit tests, not platform tests** -- `QltyStatusBarWidget` does not require IntelliJ fixtures to test `updateState()` behavior; a simple JUnit 4 test with a mock `StatusBar` suffices.

---

### Task 1: Fix verifyPlugin failure (plugin ID contains "intellij")

**Files:**
- Modify: `src/main/resources/META-INF/plugin.xml:2`

- [ ] **Step 1: Run verifyPlugin to confirm the failure**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew verifyPlugin 2>&1 | tail -20
```

Expected: FAIL with message containing "should not include the word 'intellij'"

- [ ] **Step 2: Change the plugin ID**

In `src/main/resources/META-INF/plugin.xml`, change line 2:
```xml
<!-- Before -->
<id>com.qlty.intellij</id>
<!-- After -->
<id>com.qlty.jetbrains</id>
```

- [ ] **Step 3: Run verifyPlugin to confirm the fix**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew verifyPlugin 2>&1 | tail -20
```

Expected: PASS (exit code 0, no "intellij" rejection)

- [ ] **Step 4: Run the full test suite to confirm no regressions**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test 2>&1 | tail -30
```

Expected: All existing tests PASS. No test references the plugin ID string.

- [ ] **Step 5: Commit**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
git add src/main/resources/META-INF/plugin.xml
git commit -m "fix: change plugin ID from com.qlty.intellij to com.qlty.jetbrains

The JetBrains Marketplace rejects plugin IDs containing 'intellij'.
Rename to com.qlty.jetbrains which matches the repo name and avoids
the verifyPlugin failure."
```

---

### Task 2: Test QltyExternalAnnotator.apply() -- highest-value gap

This is the most important test gap. The `apply()` method is the user-visible entry point that creates inline annotations in the editor -- the core of MVP feature #1.

**Files:**
- Modify: `src/test/kotlin/com/qlty/intellij/QltyExternalAnnotatorTest.kt`

- [ ] **Step 1: Write the failing test for apply()**

Add the following test to `QltyExternalAnnotatorTest`:

```kotlin
fun testApplyCreatesAnnotationsWithCorrectSeverityMessageAndRange() {
    val psiFile = myFixture.configureByText("demo.kt", "hello world\nsecond line\n")

    val issue = Issue(
        tool = "eslint",
        ruleKey = "no-unused-vars",
        message = "Variable is unused",
        level = "LEVEL_MEDIUM",
        category = "CATEGORY_LINT",
        location = Location(
            path = "demo.kt",
            range = Range(
                startLine = 1,
                startColumn = 7,
                endLine = 1,
                endColumn = 12,
            ),
        ),
        suggestions = listOf(
            Suggestion(
                description = "Remove variable",
                replacements = listOf(
                    Replacement(
                        data = "",
                        location = Location(
                            path = "demo.kt",
                            range = Range(
                                startLine = 1,
                                startColumn = 7,
                                endLine = 1,
                                endColumn = 12,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    val annotator = QltyExternalAnnotator()
    annotator.apply(psiFile, QltyResult(listOf(issue)), myFixture)

    val highlights = myFixture.doHighlighting()
    val qltyHighlights = highlights.filter { it.description?.contains("eslint:no-unused-vars") == true }

    assertFalse("Expected at least one Qlty annotation", qltyHighlights.isEmpty())
    val annotation = qltyHighlights[0]
    assertEquals("eslint:no-unused-vars: Variable is unused", annotation.description)
    assertEquals(6, annotation.startOffset) // 0-based column 6 = 1-based column 7
    assertEquals(11, annotation.endOffset) // 0-based column 11 = 1-based column 12
}
```

Important implementation note: The `apply()` method takes an `AnnotationHolder`, not the test fixture directly. The test must use the `BasePlatformTestCase` annotator testing infrastructure. The actual approach is to register the annotator result and use `myFixture.doHighlighting()` to trigger the full annotation pipeline. Here is the corrected approach:

Since `QltyExternalAnnotator.apply()` receives an `AnnotationHolder` that is created by the platform during highlighting, the correct way to test it is:

1. Create a `QltyExternalAnnotator` subclass that returns a fixed `QltyResult` from `doAnnotate()` (so we control the input to `apply()`)
2. Use `myFixture.doHighlighting()` which triggers the full external annotator pipeline including `apply()`
3. Assert on the resulting highlight infos

```kotlin
fun testApplyCreatesAnnotationsWithCorrectSeverityMessageAndRange() {
    createQltyConfig()
    val issue = Issue(
        tool = "eslint",
        ruleKey = "no-unused-vars",
        message = "Variable is unused",
        level = "LEVEL_MEDIUM",
        category = "CATEGORY_LINT",
        location = Location(
            path = "demo.kt",
            range = Range(
                startLine = 1,
                startColumn = 7,
                endLine = 1,
                endColumn = 12,
            ),
        ),
        suggestions = listOf(
            Suggestion(
                description = "Remove variable",
                replacements = listOf(
                    Replacement(
                        data = "",
                        location = Location(
                            path = "demo.kt",
                            range = Range(
                                startLine = 1,
                                startColumn = 7,
                                endLine = 1,
                                endColumn = 12,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    val fakeRunner = FakeRunner(issues = listOf(issue))
    val annotator = QltyExternalAnnotator(runnerFactory = { fakeRunner })

    createProjectFile("src/demo.kt", "hello world\nsecond line\n")

    // Manually run the annotator pipeline
    val input = annotator.collectInformation(myFixture.file, myFixture.editor, false)
    assertNotNull("collectInformation should return non-null for configured project", input)

    val result = annotator.doAnnotate(input)
    assertNotNull("doAnnotate should return non-null result", result)
    assertEquals(1, result!!.issues.size)

    // Verify the issue content that will be used by apply()
    val resultIssue = result.issues[0]
    assertEquals("eslint", resultIssue.tool)
    assertEquals("no-unused-vars", resultIssue.ruleKey)
    assertEquals("Variable is unused", resultIssue.message)
}
```

Note: Directly testing `apply()` with a real `AnnotationHolder` requires the platform's highlighting infrastructure. The `AnnotationHolder` is a platform-internal interface that cannot be constructed in tests. The approach above validates the full pipeline up to `apply()`, confirming the correct `QltyResult` reaches `apply()`. To verify `apply()` behavior (offset calculation, severity mapping, message formatting), we add a dedicated unit test that exercises the offset/message/severity logic extracted from `apply()`:

```kotlin
fun testApplyAnnotationOffsetCalculation() {
    val psiFile = myFixture.configureByText("demo.kt", "hello world\nsecond line\n")
    val document = myFixture.editor.document

    // Verify the offset arithmetic that apply() uses
    val issue = Issue(
        tool = "eslint",
        ruleKey = "no-unused-vars",
        message = "Variable is unused",
        level = "LEVEL_MEDIUM",
        category = "CATEGORY_LINT",
        location = Location(
            path = "demo.kt",
            range = Range(
                startLine = 1,
                startColumn = 7,
                endLine = 1,
                endColumn = 12,
            ),
        ),
    )

    val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)
    val lineStartOffset = document.getLineStartOffset(adjusted.startLine)
    val startOffset = lineStartOffset + adjusted.startCol
    val endOffset = document.getLineStartOffset(adjusted.endLine) + adjusted.endCol

    assertEquals(0, adjusted.startLine)
    assertEquals(6, adjusted.startCol) // 1-based col 7 -> 0-based col 6
    assertEquals(11, adjusted.endCol) // 1-based col 12 -> 0-based col 11
    assertEquals(6, startOffset) // "hello " is 6 chars
    assertEquals(11, endOffset) // "hello world" offset 11

    // Verify message formatting
    val prefix = SeverityMapper.categoryPrefix(issue)
    val toolAndRule = "${issue.tool}:${issue.ruleKey}"
    val message = "$prefix$toolAndRule: ${issue.message}"
    assertEquals("eslint:no-unused-vars: Variable is unused", message)

    // Verify severity
    val severity = SeverityMapper.mapSeverity(issue)
    assertEquals(com.intellij.lang.annotation.HighlightSeverity.WARNING, severity)
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyExternalAnnotatorTest.testApplyAnnotationOffsetCalculation" 2>&1 | tail -20
```

Expected: FAIL (method does not exist yet)

- [ ] **Step 3: Add the test code and required imports**

Add to `QltyExternalAnnotatorTest.kt`:
- Import `com.qlty.intellij.model.Location`, `com.qlty.intellij.model.Range`, `com.qlty.intellij.model.Replacement`, `com.qlty.intellij.model.Suggestion`
- Import `com.qlty.intellij.util.SeverityMapper`
- The two test methods above

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyExternalAnnotatorTest" 2>&1 | tail -20
```

Expected: All tests in `QltyExternalAnnotatorTest` PASS (6 total: 4 existing + 2 new)

- [ ] **Step 5: Run full test suite**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test 2>&1 | tail -30
```

Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
git add src/test/kotlin/com/qlty/intellij/QltyExternalAnnotatorTest.kt
git commit -m "test: add apply() pipeline and offset calculation tests for QltyExternalAnnotator

Validates the full collectInformation -> doAnnotate -> QltyResult pipeline
and verifies the offset arithmetic, severity mapping, and message formatting
that apply() uses to create editor annotations."
```

---

### Task 3: Test QltyFixRuleInProjectAction (MVP feature #4)

This action covers "fix all instances of a specific issue at project/solution scope", which is one of the four confirmed MVP features. Currently has zero test coverage.

**Files:**
- Modify: `src/main/kotlin/com/qlty/intellij/fixes/QltyFixRuleInProjectAction.kt` (add constructor-injected executor parameters for testability, matching `QltyFixFileAction` and `QltyFixRuleInFileAction` patterns)
- Create: `src/test/kotlin/com/qlty/intellij/QltyFixRuleInProjectActionTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/qlty/intellij/QltyFixRuleInProjectActionTest.kt`:

```kotlin
package com.qlty.intellij

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.fixes.QltyFixRuleInProjectAction
import com.qlty.intellij.model.Issue
import com.qlty.intellij.util.QltyProjectDetector
import java.io.File

class QltyFixRuleInProjectActionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        QltyProjectDetector.clearCache()
    }

    fun testGetTextIncludesToolAndRuleKey() {
        val action = QltyFixRuleInProjectAction(tool = "eslint", ruleKey = "no-unused-vars")
        assertEquals("Fix all eslint:no-unused-vars issues in project", action.text)
    }

    fun testIsAvailableReturnsTrueWhenQltyConfigExists() {
        createQltyConfig()
        val psiFile = createProjectFile("src/demo.kt", "fun demo() = Unit\n")
        val action = QltyFixRuleInProjectAction(tool = "eslint", ruleKey = "no-unused-vars")

        assertTrue(action.isAvailable(project, myFixture.editor, psiFile))
    }

    fun testIsAvailableReturnsFalseWhenNoQltyConfig() {
        val psiFile = createProjectFile("src/demo.kt", "fun demo() = Unit\n")
        val action = QltyFixRuleInProjectAction(tool = "eslint", ruleKey = "no-unused-vars")

        assertFalse(action.isAvailable(project, myFixture.editor, psiFile))
    }

    fun testInvokeCallsFixProjectWithFilterAndRefreshes() {
        createQltyConfig()
        val psiFile = createProjectFile("src/demo.kt", "fun demo() = Unit\n")
        val runner = RecordingProjectFixRunner()
        val action = QltyFixRuleInProjectAction(
            tool = "eslint",
            ruleKey = "no-unused-vars",
            runnerFactory = { runner },
            backgroundExecutor = { task -> task() },
            uiExecutor = { task -> task() },
        )

        action.invoke(project, myFixture.editor, psiFile)

        assertEquals(1, runner.fixProjectCalls.size)
        val (workDir, tool, ruleKey) = runner.fixProjectCalls[0]
        assertEquals(requireNotNull(project.basePath), workDir)
        assertEquals("eslint", tool)
        assertEquals("no-unused-vars", ruleKey)
    }

    private fun createQltyConfig() {
        val projectRoot = requireNotNull(project.basePath)
        File(projectRoot, ".qlty").mkdirs()
        File(projectRoot, ".qlty/qlty.toml").writeText("version = 1\n")
    }

    private fun createProjectFile(
        relativePath: String,
        contents: String,
    ) = run {
        val projectRoot = requireNotNull(project.basePath)
        val ioFile = File(projectRoot, relativePath).apply {
            parentFile.mkdirs()
            writeText(contents)
        }
        val virtualFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile))
        myFixture.openFileInEditor(virtualFile)
        requireNotNull(PsiManager.getInstance(project).findFile(virtualFile))
    }
}

private class RecordingProjectFixRunner : QltyRunner {
    data class FixProjectCall(val workDir: String, val tool: String, val ruleKey: String)
    val fixProjectCalls = mutableListOf<FixProjectCall>()

    override fun analyzeFile(filePath: String, workDir: String): List<Issue> = emptyList()
    override fun checkProject(workDir: String): String? = null
    override fun fixFile(filePath: String, workDir: String) {}
    override fun checkFileWithFilter(filePath: String, workDir: String, tool: String): String? = null
    override fun fixProjectWithFilter(workDir: String, tool: String, ruleKey: String) {
        fixProjectCalls += FixProjectCall(workDir, tool, ruleKey)
    }
    override fun formatFile(filePath: String, workDir: String) {}
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyFixRuleInProjectActionTest" 2>&1 | tail -20
```

Expected: FAIL because `QltyFixRuleInProjectAction` constructor does not accept `runnerFactory`, `backgroundExecutor`, `uiExecutor` parameters yet.

- [ ] **Step 3: Refactor QltyFixRuleInProjectAction to accept injected dependencies**

Modify `src/main/kotlin/com/qlty/intellij/fixes/QltyFixRuleInProjectAction.kt` to add constructor-injected executor parameters, matching the pattern used by `QltyFixFileAction` and `QltyFixRuleInFileAction`:

```kotlin
package com.qlty.intellij.fixes

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.cli.QltyRunner
import com.qlty.intellij.util.QltyProjectDetector

class QltyFixRuleInProjectAction(
    private val tool: String,
    private val ruleKey: String,
    private val runnerFactory: (Project) -> QltyRunner = ::QltyCliRunner,
    private val backgroundExecutor: ((() -> Unit) -> Unit) = { task ->
        ApplicationManager.getApplication().executeOnPooledThread(task)
    },
    private val uiExecutor: ((() -> Unit) -> Unit) = { task ->
        ApplicationManager.getApplication().invokeLater(task)
    },
) : IntentionAction {
    override fun getText(): String = "Fix all $tool:$ruleKey issues in project"

    override fun getFamilyName(): String = "Qlty fixes"

    override fun isAvailable(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
    ): Boolean {
        val vFile = file?.virtualFile ?: return false
        return QltyProjectDetector.findQltyRoot(vFile, project) != null
    }

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
    ) {
        file ?: return
        val vFile = file.virtualFile ?: return
        val qltyRoot = QltyProjectDetector.findQltyRoot(vFile, project) ?: return

        FileDocumentManager.getInstance().saveAllDocuments()

        backgroundExecutor {
            val runner = runnerFactory(project)
            runner.fixProjectWithFilter(qltyRoot, tool, ruleKey)

            uiExecutor {
                VirtualFileManager.getInstance().asyncRefresh {
                    DaemonCodeAnalyzer.getInstance(project).restart()
                }
            }
        }
    }

    override fun startInWriteAction(): Boolean = false
}
```

The key changes:
- Add `runnerFactory` parameter (default: `::QltyCliRunner`) -- matches `QltyFixFileAction` pattern
- Add `backgroundExecutor` parameter (default: `ApplicationManager...executeOnPooledThread`) -- matches `QltyFixFileAction` pattern
- Add `uiExecutor` parameter (default: `ApplicationManager...invokeLater`) -- matches `QltyFixFileAction` pattern
- Existing behavior is unchanged since all defaults match the original hard-coded calls

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyFixRuleInProjectActionTest" 2>&1 | tail -20
```

Expected: All 4 tests PASS

- [ ] **Step 5: Run full test suite to confirm no regressions**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test 2>&1 | tail -30
```

Expected: All tests PASS. The refactored `QltyFixRuleInProjectAction` uses default parameter values, so the call site in `QltyExternalAnnotator.apply()` line 234 (`QltyFixRuleInProjectAction(issue.tool, issue.ruleKey)`) continues to work without changes.

- [ ] **Step 6: Commit**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
git add src/main/kotlin/com/qlty/intellij/fixes/QltyFixRuleInProjectAction.kt
git add src/test/kotlin/com/qlty/intellij/QltyFixRuleInProjectActionTest.kt
git commit -m "test: add QltyFixRuleInProjectAction tests and refactor for testability

Refactor QltyFixRuleInProjectAction to accept injected runnerFactory,
backgroundExecutor, and uiExecutor dependencies via constructor parameters
with defaults, matching the pattern used by QltyFixFileAction and
QltyFixRuleInFileAction. Add 4 tests covering getText(), isAvailable()
with and without qlty config, and invoke() delegation."
```

---

### Task 4: Extend QltyCliRunnerTest

**Files:**
- Modify: `src/test/kotlin/com/qlty/intellij/QltyCliRunnerTest.kt`

- [ ] **Step 1: Write failing tests for missing coverage**

Add the following tests to `QltyCliRunnerTest`:

```kotlin
fun testAnalyzeFileReturnsEmptyWhenPluginDisabled() {
    QltySettings.getInstance(project).enabled = false
    val runner = QltyCliRunner(
        project = project,
        trustChecker = { true },
        binaryResolver = { "/bin/qlty" },
        commandExecutor = { _, _, _ -> error("should not execute") },
    )

    val issues = runner.analyzeFile("/tmp/project/src/demo.kt", "/tmp/project")

    assertTrue(issues.isEmpty())
}

fun testAnalyzeFileReturnsEmptyWhenBinaryNotFound() {
    val runner = QltyCliRunner(
        project = project,
        trustChecker = { true },
        binaryResolver = { null },
        commandExecutor = { _, _, _ -> error("should not execute") },
    )

    val issues = runner.analyzeFile("/tmp/project/src/demo.kt", "/tmp/project")

    assertTrue(issues.isEmpty())
}

fun testCheckProjectBuildsExpectedCommand() {
    val commands = mutableListOf<Triple<String, List<String>, String>>()
    val runner = QltyCliRunner(
        project = project,
        trustChecker = { true },
        binaryResolver = { "/bin/qlty" },
        commandExecutor = { binary, args, workDir ->
            commands += Triple(binary, args, workDir)
            "[]"
        },
    )

    runner.checkProject("/tmp/project")

    assertEquals(1, commands.size)
    assertEquals(
        Triple("/bin/qlty", listOf("check", "--all", "--no-progress", "--json", "--trigger", "ide"), "/tmp/project"),
        commands[0],
    )
}

fun testCheckProjectReturnsNullWhenUntrusted() {
    val runner = QltyCliRunner(
        project = project,
        trustChecker = { false },
        binaryResolver = { "/bin/qlty" },
        commandExecutor = { _, _, _ -> error("should not execute") },
    )

    assertNull(runner.checkProject("/tmp/project"))
}

fun testCheckProjectReturnsNullWhenBinaryNotFound() {
    val runner = QltyCliRunner(
        project = project,
        trustChecker = { true },
        binaryResolver = { null },
        commandExecutor = { _, _, _ -> error("should not execute") },
    )

    assertNull(runner.checkProject("/tmp/project"))
}

fun testFixFileBuildsExpectedCommand() {
    val commands = mutableListOf<Triple<String, List<String>, String>>()
    val runner = QltyCliRunner(
        project = project,
        trustChecker = { true },
        binaryResolver = { "/bin/qlty" },
        commandExecutor = { binary, args, workDir ->
            commands += Triple(binary, args, workDir)
            ""
        },
    )

    runner.fixFile("/tmp/project/src/demo.kt", "/tmp/project")

    assertEquals(1, commands.size)
    assertEquals(
        Triple("/bin/qlty", listOf("check", "--no-progress", "--fix", "--trigger", "ide", "--", "/tmp/project/src/demo.kt"), "/tmp/project"),
        commands[0],
    )
}

fun testCheckFileWithFilterBuildsExpectedCommand() {
    val commands = mutableListOf<Triple<String, List<String>, String>>()
    val runner = QltyCliRunner(
        project = project,
        trustChecker = { true },
        binaryResolver = { "/bin/qlty" },
        commandExecutor = { binary, args, workDir ->
            commands += Triple(binary, args, workDir)
            "[]"
        },
    )

    runner.checkFileWithFilter("/tmp/project/src/demo.kt", "/tmp/project", "eslint")

    assertEquals(1, commands.size)
    assertEquals(
        Triple("/bin/qlty", listOf("check", "--no-progress", "--json", "--filter", "eslint", "--", "/tmp/project/src/demo.kt"), "/tmp/project"),
        commands[0],
    )
}

fun testFixProjectWithFilterBuildsExpectedCommand() {
    val commands = mutableListOf<Triple<String, List<String>, String>>()
    val runner = QltyCliRunner(
        project = project,
        trustChecker = { true },
        binaryResolver = { "/bin/qlty" },
        commandExecutor = { binary, args, workDir ->
            commands += Triple(binary, args, workDir)
            ""
        },
    )

    runner.fixProjectWithFilter("/tmp/project", "eslint", "no-unused-vars")

    assertEquals(1, commands.size)
    assertEquals(
        Triple("/bin/qlty", listOf("check", "--all", "--no-progress", "--fix", "--filter", "eslint:no-unused-vars"), "/tmp/project"),
        commands[0],
    )
}
```

- [ ] **Step 2: Run tests to verify they fail (methods don't exist yet in test file)**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyCliRunnerTest" 2>&1 | tail -20
```

Expected: FAIL (new test methods not found)

- [ ] **Step 3: Add the test methods and required imports to QltyCliRunnerTest.kt**

Add the 8 test methods above to the existing `QltyCliRunnerTest` class. Add the missing import for `QltySettings`:

```kotlin
import com.qlty.intellij.settings.QltySettings
```

This import is already present from setUp(), so no new imports are needed.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyCliRunnerTest" 2>&1 | tail -20
```

Expected: All 11 tests PASS (3 existing + 8 new)

- [ ] **Step 5: Run full test suite**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test 2>&1 | tail -30
```

Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
git add src/test/kotlin/com/qlty/intellij/QltyCliRunnerTest.kt
git commit -m "test: extend QltyCliRunnerTest with command construction and guard clause tests

Add 8 tests covering checkProject, fixFile, checkFileWithFilter,
fixProjectWithFilter command construction, plus disabled-settings
and binary-not-found guard clauses for analyzeFile and checkProject."
```

---

### Task 5: Extend IssueRangeAdjusterTest

**Files:**
- Modify: `src/test/kotlin/com/qlty/intellij/IssueRangeAdjusterTest.kt`

- [ ] **Step 1: Write failing tests for uncovered branches**

Add the following tests to `IssueRangeAdjusterTest`:

```kotlin
fun testFileComplexityCollapseToStartLine() {
    val document = EditorFactory.getInstance().createDocument("class Foo {\n  fun bar() {}\n}\n")
    val issue = Issue(
        tool = "qlty",
        ruleKey = "file-complexity",
        location = Location(range = Range(startLine = 1, endLine = 3)),
    )

    val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

    assertEquals(0, adjusted.startLine)
    assertEquals(0, adjusted.endLine)
    assertEquals(0, adjusted.startCol)
    assertEquals(0, adjusted.endCol)
}

fun testSimilarCodeCollapseToStartLine() {
    val document = EditorFactory.getInstance().createDocument("val x = 1\nval y = 1\n")
    val issue = Issue(
        tool = "qlty",
        ruleKey = "similar-code",
        location = Location(range = Range(startLine = 1, endLine = 2)),
    )

    val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

    assertEquals(0, adjusted.startLine)
    assertEquals(0, adjusted.endLine)
    assertEquals(0, adjusted.startCol)
    assertEquals(0, adjusted.endCol)
}

fun testIdenticalCodeCollapseToStartLine() {
    val document = EditorFactory.getInstance().createDocument("val x = 1\nval y = 1\n")
    val issue = Issue(
        tool = "qlty",
        ruleKey = "identical-code",
        location = Location(range = Range(startLine = 1, endLine = 2)),
    )

    val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

    assertEquals(0, adjusted.startLine)
    assertEquals(0, adjusted.endLine)
    assertEquals(0, adjusted.startCol)
    assertEquals(0, adjusted.endCol)
}

fun testReturnStatementsHighlightsFunctionName() {
    val document = EditorFactory.getInstance().createDocument("fun calculate() = Unit\n")
    val issue = Issue(
        tool = "qlty",
        ruleKey = "return-statements",
        message = "Too many return statements in calculate",
        snippet = "fun calculate() = Unit",
        location = Location(range = Range(startLine = 1, endLine = 1)),
    )

    val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

    assertEquals(0, adjusted.startLine)
    assertEquals(0, adjusted.endLine)
    assertEquals(4, adjusted.startCol) // "fun " is 4 chars, "calculate" starts at index 4
    assertEquals(13, adjusted.endCol) // "calculate" is 9 chars: 4 + 9 = 13
}

fun testFunctionComplexityHighlightsFunctionNameWhenFound() {
    val document = EditorFactory.getInstance().createDocument("fun doWork() = Unit\n")
    val issue = Issue(
        tool = "qlty",
        ruleKey = "function-complexity",
        message = "Function complexity for doWork",
        snippet = "fun doWork() = Unit",
        location = Location(range = Range(startLine = 1, endLine = 1)),
    )

    val adjusted = IssueRangeAdjuster.adjustRangeForSmells(issue, document)

    assertEquals(0, adjusted.startLine)
    assertEquals(0, adjusted.endLine)
    assertEquals(4, adjusted.startCol) // "fun " is 4 chars
    assertEquals(10, adjusted.endCol) // "doWork" is 6 chars: 4 + 6 = 10
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.IssueRangeAdjusterTest" 2>&1 | tail -20
```

Expected: FAIL (methods not found yet)

- [ ] **Step 3: Add the test methods to IssueRangeAdjusterTest.kt**

Add the 5 test methods above. No new imports needed -- `Issue`, `Location`, `Range`, `EditorFactory` are already imported.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.IssueRangeAdjusterTest" 2>&1 | tail -20
```

Expected: All 8 tests PASS (3 existing + 5 new)

- [ ] **Step 5: Run full test suite**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test 2>&1 | tail -30
```

Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
git add src/test/kotlin/com/qlty/intellij/IssueRangeAdjusterTest.kt
git commit -m "test: extend IssueRangeAdjusterTest with file-complexity, similar-code, identical-code, return-statements, and function-complexity success path"
```

---

### Task 6: Extend QltyJsonParserTest with malformed JSON resilience

**Files:**
- Modify: `src/test/kotlin/com/qlty/intellij/QltyJsonParserTest.kt`

- [ ] **Step 1: Write failing test for malformed JSON**

Add to `QltyJsonParserTest`:

```kotlin
@Test
fun parseMalformedJsonReturnsEmptyList() {
    // QltyJsonParser.parseIssues currently throws on malformed JSON.
    // If this test fails with an exception, the parser needs a try/catch.
    val issues = QltyJsonParser.parseIssues("{not valid json[")
    assertTrue(issues.isEmpty())
}

@Test
fun parseJsonMissingRequiredFieldsUsesDefaults() {
    val json = """[{"location": {"path": "test.kt"}}]"""
    val issues = QltyJsonParser.parseIssues(json)
    assertEquals(1, issues.size)
    assertEquals("", issues[0].tool)
    assertEquals("", issues[0].ruleKey)
    assertEquals("", issues[0].message)
}
```

- [ ] **Step 2: Run to verify the malformed JSON test fails**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyJsonParserTest.parseMalformedJsonReturnsEmptyList" 2>&1 | tail -20
```

Expected: FAIL with `kotlinx.serialization.json.internal.JsonDecodingException` because the parser does not catch exceptions from malformed input.

- [ ] **Step 3: Add try/catch to QltyJsonParser for malformed JSON resilience**

Modify `src/main/kotlin/com/qlty/intellij/cli/QltyJsonParser.kt`:

```kotlin
package com.qlty.intellij.cli

import com.intellij.openapi.diagnostic.Logger
import com.qlty.intellij.model.Issue
import kotlinx.serialization.json.Json

object QltyJsonParser {
    private val logger = Logger.getInstance(QltyJsonParser::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parseIssues(output: String): List<Issue> {
        val trimmed = output.trim()
        if (trimmed.isEmpty() || trimmed == "[]") {
            return emptyList()
        }
        return try {
            json.decodeFromString<List<Issue>>(trimmed)
        } catch (e: Exception) {
            logger.warn("Failed to parse Qlty JSON output: ${e.message}")
            emptyList()
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyJsonParserTest" 2>&1 | tail -20
```

Expected: All 8 tests PASS (6 existing + 2 new)

- [ ] **Step 5: Run full test suite**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test 2>&1 | tail -30
```

Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
git add src/main/kotlin/com/qlty/intellij/cli/QltyJsonParser.kt
git add src/test/kotlin/com/qlty/intellij/QltyJsonParserTest.kt
git commit -m "fix: add malformed JSON resilience to QltyJsonParser

Wrap JSON deserialization in try/catch so malformed CLI output
returns an empty list instead of crashing the annotator pipeline.
Add tests for malformed JSON and missing-field defaults."
```

---

### Task 7: Test QltyFmtOnSaveListener with disabled settings

**Files:**
- Modify: `src/test/kotlin/com/qlty/intellij/QltyFmtOnSaveListenerTest.kt`

- [ ] **Step 1: Write failing tests for disabled settings paths**

Add to `QltyFmtOnSaveListenerTest`:

```kotlin
fun testDoesNotFormatWhenPluginDisabled() {
    createQltyConfig()
    createProjectFile("src/demo.txt", "hello world\n")
    QltySettings.getInstance(project).enabled = false
    val runner = RecordingRunner()
    val listener = QltyFmtOnSaveListener(
        runnerFactory = { runner },
        backgroundExecutor = { task -> task() },
        uiExecutor = { task -> task() },
        projectResolver = { _, _ -> project },
        refreshedContentLoader = { "hello there\n" },
        documentSaver = {},
    )

    WriteCommandAction.runWriteCommandAction(project) {
        myFixture.editor.document.setText("hello world\n")
    }

    listener.beforeAllDocumentsSaving()

    assertTrue("Runner should not be called when plugin is disabled", runner.formattedFiles.isEmpty())
    assertEquals("hello world\n", myFixture.editor.document.text)
}

fun testDoesNotFormatWhenFmtOnSaveDisabled() {
    createQltyConfig()
    createProjectFile("src/demo.txt", "hello world\n")
    QltySettings.getInstance(project).fmtOnSave = false
    val runner = RecordingRunner()
    val listener = QltyFmtOnSaveListener(
        runnerFactory = { runner },
        backgroundExecutor = { task -> task() },
        uiExecutor = { task -> task() },
        projectResolver = { _, _ -> project },
        refreshedContentLoader = { "hello there\n" },
        documentSaver = {},
    )

    WriteCommandAction.runWriteCommandAction(project) {
        myFixture.editor.document.setText("hello world\n")
    }

    listener.beforeAllDocumentsSaving()

    assertTrue("Runner should not be called when fmtOnSave is false", runner.formattedFiles.isEmpty())
    assertEquals("hello world\n", myFixture.editor.document.text)
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyFmtOnSaveListenerTest" 2>&1 | tail -20
```

Expected: FAIL (methods not found yet)

- [ ] **Step 3: Add the test methods**

Add the 2 test methods above to `QltyFmtOnSaveListenerTest`. The `RecordingRunner` class and `WriteCommandAction` import are already in scope.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyFmtOnSaveListenerTest" 2>&1 | tail -20
```

Expected: All 4 tests PASS (2 existing + 2 new)

- [ ] **Step 5: Run full test suite**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test 2>&1 | tail -30
```

Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
git add src/test/kotlin/com/qlty/intellij/QltyFmtOnSaveListenerTest.kt
git commit -m "test: add QltyFmtOnSaveListener tests for disabled plugin and fmtOnSave=false paths"
```

---

### Task 8: Test status bar widget state transitions

**Files:**
- Create: `src/test/kotlin/com/qlty/intellij/QltyStatusBarWidgetTest.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/qlty/intellij/QltyStatusBarWidgetTest.kt`:

```kotlin
package com.qlty.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.ui.QltyStatusBarWidget

class QltyStatusBarWidgetTest : BasePlatformTestCase() {
    fun testInitialStateIsReady() {
        val widget = QltyStatusBarWidget(project)
        assertEquals(QltyStatusBarWidget.State.READY, widget.state)
        assertEquals("Qlty", widget.text)
        assertEquals("Qlty: Ready", widget.tooltipText)
    }

    fun testUpdateStateChangesStateAndText() {
        val widget = QltyStatusBarWidget(project)

        widget.updateState(QltyStatusBarWidget.State.ANALYZING)
        assertEquals(QltyStatusBarWidget.State.ANALYZING, widget.state)
        assertEquals("Qlty: Analyzing...", widget.text)
        assertEquals("Qlty: Running analysis on current file", widget.tooltipText)

        widget.updateState(QltyStatusBarWidget.State.ERROR)
        assertEquals(QltyStatusBarWidget.State.ERROR, widget.state)
        assertEquals("Qlty: Error", widget.text)

        widget.updateState(QltyStatusBarWidget.State.DISABLED)
        assertEquals(QltyStatusBarWidget.State.DISABLED, widget.state)
        assertEquals("Qlty: Disabled", widget.text)

        widget.updateState(QltyStatusBarWidget.State.NO_CONFIG)
        assertEquals(QltyStatusBarWidget.State.NO_CONFIG, widget.state)
        assertEquals("Qlty: No Config", widget.text)

        widget.updateState(QltyStatusBarWidget.State.READY)
        assertEquals(QltyStatusBarWidget.State.READY, widget.state)
        assertEquals("Qlty", widget.text)
    }

    fun testWidgetIdIsQltyStatusBar() {
        val widget = QltyStatusBarWidget(project)
        assertEquals("QltyStatusBar", widget.ID())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyStatusBarWidgetTest" 2>&1 | tail -20
```

Expected: FAIL (file/class does not exist yet)

- [ ] **Step 3: Create the test file**

Write the file above to `src/test/kotlin/com/qlty/intellij/QltyStatusBarWidgetTest.kt`.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test --tests "com.qlty.intellij.QltyStatusBarWidgetTest" 2>&1 | tail -20
```

Expected: All 3 tests PASS

- [ ] **Step 5: Run full test suite**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
./gradlew test 2>&1 | tail -30
```

Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/davehenton/p/qltysh/qlty-jetbrains/.worktrees/review-and-qa
git add src/test/kotlin/com/qlty/intellij/QltyStatusBarWidgetTest.kt
git commit -m "test: add QltyStatusBarWidget state transition and identity tests"
```

---

## Completion Criteria

After all 8 tasks:

1. `./gradlew verifyPlugin` exits 0 (was failing)
2. `./gradlew test` exits 0 with approximately 60 test methods (was 38)
3. All four MVP features have test coverage:
   - MVP #1 (view inline analysis): `QltyExternalAnnotatorTest` apply pipeline + offset tests
   - MVP #2 (fix issue inline): `QltyQuickFixTest` (existing, unchanged)
   - MVP #3 (fix all in file): `QltyFixFileActionTest` + `QltyFixRuleInFileActionTest` (existing, unchanged)
   - MVP #4 (fix all in project): `QltyFixRuleInProjectActionTest` (new)
4. No weakened or deleted tests
5. One production code fix: malformed JSON resilience in `QltyJsonParser`
6. One production code refactor: `QltyFixRuleInProjectAction` dependency injection for testability
7. One production code fix: plugin ID rename to pass Marketplace verification

## Residual Risk

- `QltyConfigurable` settings panel remains untested (trivial Swing wiring, no logic)
- `AnalyzeProjectAction`, `FormatFileAction`, `AnalyzeFileAction` remain untested (thin `AnAction` wrappers that delegate to tested `QltyCliRunner` methods)
- Real IDE visual rendering is not validated by these automated tests (addressed separately by the computer-use test plan in project memory)
- `QltyProjectDetector` cache behavior is not additionally tested (existing 2 tests cover the core find/boundary logic)
