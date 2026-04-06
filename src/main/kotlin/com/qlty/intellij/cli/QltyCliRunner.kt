package com.qlty.intellij.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.qlty.intellij.model.Issue
import com.qlty.intellij.settings.QltySettings
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class QltyCliRunner(private val project: Project) {
    private val logger = Logger.getInstance(QltyCliRunner::class.java)

    fun analyzeFile(
        filePath: String,
        workDir: String,
    ): List<Issue> {
        val settings = QltySettings.getInstance(project)
        if (!settings.enabled) {
            logger.info("Qlty is disabled in settings, skipping analysis")
            return emptyList()
        }

        val binary = resolveBinary(settings.qltyBinaryPath)
        if (binary == null) {
            logger.warn("Could not find qlty binary (configured: '${settings.qltyBinaryPath}')")
            return emptyList()
        }
        logger.info("Using qlty binary: $binary")

        val relativePath = File(filePath).relativeTo(File(workDir)).path

        val checkFuture = CompletableFuture.supplyAsync {
            runCommand(
                binary,
                listOf("check", "--no-progress", "--json", "--trigger", "ide", "--", filePath),
                workDir,
            )
        }
        val smellsFuture = CompletableFuture.supplyAsync {
            runCommand(
                binary,
                listOf("smells", "--json", "--", filePath),
                workDir,
            )
        }

        val checkOutput = checkFuture.get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        val smellsOutput = smellsFuture.get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        val checkIssues = if (checkOutput != null) {
            QltyJsonParser.parseIssues(checkOutput).filter { it.location.path == relativePath }
        } else {
            emptyList()
        }
        val smellsIssues = if (smellsOutput != null) QltyJsonParser.parseIssues(smellsOutput) else emptyList()

        val allIssues = (checkIssues + smellsIssues).take(MAX_ISSUES)
        logger.info("Qlty analysis of $relativePath: ${checkIssues.size} check issues, ${smellsIssues.size} smell issues")

        return allIssues
    }

    fun checkProject(workDir: String): String? {
        val settings = QltySettings.getInstance(project)
        val binary = resolveBinary(settings.qltyBinaryPath)
        if (binary == null) {
            logger.warn("Could not find qlty binary for project check, skipping")
            return null
        }
        logger.info("Running project-wide qlty check in $workDir")
        return runCommand(binary, listOf("check", "--all", "--no-progress", "--json", "--trigger", "ide"), workDir)
    }

    fun fixFile(
        filePath: String,
        workDir: String,
    ) {
        val settings = QltySettings.getInstance(project)
        val binary = resolveBinary(settings.qltyBinaryPath)
        if (binary == null) {
            logger.warn("Could not find qlty binary for fix, skipping")
            return
        }
        logger.info("Running qlty fix on $filePath in $workDir")
        runCommand(binary, listOf("check", "--no-progress", "--fix", "--trigger", "ide", "--", filePath), workDir)
    }

    fun checkFileWithFilter(
        filePath: String,
        workDir: String,
        tool: String,
    ): String? {
        val settings = QltySettings.getInstance(project)
        val binary = resolveBinary(settings.qltyBinaryPath)
        if (binary == null) {
            logger.warn("Could not find qlty binary for filtered check, skipping")
            return null
        }
        logger.info("Running qlty check --filter $tool on $filePath")
        return runCommand(
            binary,
            listOf("check", "--all", "--no-progress", "--json", "--filter", tool, "--", filePath),
            workDir,
        )
    }

    fun fixProjectWithFilter(
        workDir: String,
        tool: String,
        ruleKey: String,
    ) {
        val settings = QltySettings.getInstance(project)
        val binary = resolveBinary(settings.qltyBinaryPath)
        if (binary == null) {
            logger.warn("Could not find qlty binary for project fix, skipping")
            return
        }
        val filter = "$tool:$ruleKey"
        logger.info("Running qlty check --fix --filter $filter in $workDir")
        runCommand(binary, listOf("check", "--all", "--no-progress", "--fix", "--filter", filter), workDir)
    }

    fun formatFile(
        filePath: String,
        workDir: String,
    ) {
        val settings = QltySettings.getInstance(project)
        val binary = resolveBinary(settings.qltyBinaryPath)
        if (binary == null) {
            logger.warn("Could not find qlty binary for fmt, skipping")
            return
        }
        logger.info("Running qlty fmt on $filePath")
        runCommand(binary, listOf("fmt", "--no-progress", "--", filePath), workDir)
    }

    private fun resolveBinary(configured: String): String? {
        // If an absolute path is configured, use it directly
        if (File(configured).isAbsolute) {
            if (File(configured).canExecute()) {
                logger.debug("Resolved absolute binary path: $configured")
                return configured
            }
            logger.debug("Absolute path '$configured' is not executable")
            return null
        }

        // Check well-known locations first
        val home = System.getProperty("user.home") ?: ""
        val commonPaths = listOf(
            "$home/.qlty/bin/qlty",
            "/usr/local/bin/qlty",
            "/opt/homebrew/bin/qlty",
        )

        for (path in commonPaths) {
            if (File(path).canExecute()) {
                logger.debug("Found qlty binary at: $path")
                return path
            }
        }

        // Fall back to searching PATH
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
        for (dir in pathDirs) {
            val candidate = File(dir, configured)
            if (candidate.canExecute()) {
                logger.debug("Found qlty binary on PATH: ${candidate.absolutePath}")
                return candidate.absolutePath
            }
        }

        logger.debug("Could not find qlty binary '$configured' in common paths or PATH")
        return null
    }

    private fun runCommand(
        binary: String,
        args: List<String>,
        workDir: String,
    ): String? {
        val fullCommand = "$binary ${args.joinToString(" ")}"
        logger.debug("Executing: $fullCommand (cwd: $workDir)")

        return try {
            val commandLine =
                GeneralCommandLine(binary)
                    .withParameters(args)
                    .withWorkDirectory(workDir)
                    .withCharset(Charsets.UTF_8)
                    .withEnvironment("NO_COLOR", "1")

            val processOutput = ExecUtil.execAndGetOutput(commandLine, TIMEOUT_MS)

            if (processOutput.exitCode != 0) {
                logger.info("qlty exited with code ${processOutput.exitCode} for: $fullCommand")
                if (processOutput.stderr.isNotEmpty()) {
                    logger.info("qlty stderr: ${processOutput.stderr.take(2000)}")
                }
            }

            val stdout = processOutput.stdout
            if (stdout.length > MAX_OUTPUT_BYTES) {
                logger.warn("Qlty output exceeded ${MAX_OUTPUT_BYTES} bytes (${stdout.length}), discarding")
                null
            } else {
                stdout
            }
        } catch (e: Exception) {
            logger.warn("Failed to execute: $fullCommand", e)
            null
        }
    }

    companion object {
        private const val TIMEOUT_MS = 60_000
        private const val FUTURE_TIMEOUT_MS = 90_000L
        private const val MAX_OUTPUT_BYTES = 10 * 1024 * 1024 // 10MB
        private const val MAX_ISSUES = 1_000
    }
}
