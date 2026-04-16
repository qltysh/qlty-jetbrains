package com.qlty.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.qlty.intellij.cli.QltyCliRunner
import com.qlty.intellij.settings.QltySettings
import java.util.concurrent.CompletableFuture

class QltyCliRunnerTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        QltySettings.getInstance(project).enabled = true
    }

    fun testAnalyzeFileBuildsExpectedCommandsAndFiltersIssuesByRelativePath() {
        val commands = mutableListOf<Triple<String, List<String>, String>>()
        val runner = QltyCliRunner(
            project = project,
            trustChecker = { true },
            binaryResolver = { "/bin/qlty" },
            commandExecutor = { binary, args, workDir ->
                commands += Triple(binary, args, workDir)
                when (args.first()) {
                    "check" ->
                        """
                        [
                          {"tool":"eslint","ruleKey":"a","message":"keep","location":{"path":"src/demo.kt","range":{"startLine":1,"startColumn":1,"endLine":1,"endColumn":2}}},
                          {"tool":"eslint","ruleKey":"b","message":"drop","location":{"path":"src/other.kt","range":{"startLine":1,"startColumn":1,"endLine":1,"endColumn":2}}}
                        ]
                        """.trimIndent()
                    "smells" ->
                        """
                        [
                          {"tool":"qlty","ruleKey":"nested-control-flow","message":"keep","snippet":"if (x)","location":{"path":"src/demo.kt","range":{"startLine":1,"startColumn":1,"endLine":1,"endColumn":2}}},
                          {"tool":"qlty","ruleKey":"nested-control-flow","message":"drop","snippet":"if (y)","location":{"path":"src/other.kt","range":{"startLine":1,"startColumn":1,"endLine":1,"endColumn":2}}}
                        ]
                        """.trimIndent()
                    else -> null
                }
            },
            asyncExecutor = { supplier -> CompletableFuture.completedFuture(supplier()) },
        )

        val issues = runner.analyzeFile("/tmp/project/src/demo.kt", "/tmp/project")

        assertEquals(2, issues.size)
        assertEquals(
            Triple("/bin/qlty", listOf("check", "--no-progress", "--json", "--trigger", "ide", "--", "/tmp/project/src/demo.kt"), "/tmp/project"),
            commands[0],
        )
        assertEquals(
            Triple("/bin/qlty", listOf("smells", "--json", "--", "/tmp/project/src/demo.kt"), "/tmp/project"),
            commands[1],
        )
    }

    fun testAnalyzeFileSkipsWhenProjectIsUntrusted() {
        var executed = false
        val runner = QltyCliRunner(
            project = project,
            trustChecker = { false },
            binaryResolver = {
                executed = true
                "/bin/qlty"
            },
            commandExecutor = { _, _, _ ->
                executed = true
                null
            },
        )

        val issues = runner.analyzeFile("/tmp/project/src/demo.kt", "/tmp/project")

        assertTrue(issues.isEmpty())
        assertFalse(executed)
    }

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

    fun testFormatFileBuildsExpectedCommand() {
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

        runner.formatFile("/tmp/project/src/demo.kt", "/tmp/project")

        assertEquals(
            listOf(
                Triple("/bin/qlty", listOf("fmt", "--no-progress", "--", "/tmp/project/src/demo.kt"), "/tmp/project"),
            ),
            commands,
        )
    }
}
