package com.qlty.intellij.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.qlty.intellij.model.Issue
import com.qlty.intellij.settings.QltySettings
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class QltyCliRunner(private val project: Project) {

    private val logger = Logger.getInstance(QltyCliRunner::class.java)

    fun analyzeFile(filePath: String, workDir: String): List<Issue> {
        val settings = QltySettings.getInstance(project)
        if (!settings.enabled) return emptyList()

        val binary = resolveBinary(settings.qltyBinaryPath)
        if (binary == null) {
            logger.warn("Could not find qlty binary")
            return emptyList()
        }

        val relativePath = File(filePath).relativeTo(File(workDir)).path

        val checkFuture = CompletableFuture.supplyAsync {
            runCommand(
                binary,
                listOf("check", "--no-progress", "--json", "--trigger", "ide"),
                workDir
            )
        }
        val smellsFuture = CompletableFuture.supplyAsync {
            runCommand(
                binary,
                listOf("smells", "--json", "--", filePath),
                workDir
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

        return (checkIssues + smellsIssues).take(MAX_ISSUES)
    }

    fun fixFile(filePath: String, workDir: String) {
        val settings = QltySettings.getInstance(project)
        val binary = resolveBinary(settings.qltyBinaryPath) ?: return
        runCommand(binary, listOf("check", "--no-progress", "--fix", "--trigger", "ide"), workDir)
    }

    private fun resolveBinary(configured: String): String? {
        if (File(configured).isAbsolute) {
            if (File(configured).canExecute() && File(configured).name == "qlty") {
                return configured
            }
            return null
        }

        val home = System.getProperty("user.home") ?: return null
        val commonPaths = listOf(
            "$home/.qlty/bin/qlty",
            "/usr/local/bin/qlty",
            "/opt/homebrew/bin/qlty",
        )

        for (path in commonPaths) {
            if (File(path).canExecute()) {
                return path
            }
        }

        return null
    }

    private fun runCommand(binary: String, args: List<String>, workDir: String): String? {
        return try {
            val commandLine = GeneralCommandLine(binary)
                .withParameters(args)
                .withWorkDirectory(workDir)
                .withCharset(Charsets.UTF_8)
                .withEnvironment("NO_COLOR", "1")

            val output = ScriptRunnerUtil.getProcessOutput(
                commandLine,
                ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER,
                TIMEOUT_MS.toLong()
            )

            if (output.length > MAX_OUTPUT_BYTES) {
                logger.warn("Qlty output exceeded ${MAX_OUTPUT_BYTES} bytes, truncating")
                null
            } else {
                output
            }
        } catch (e: Exception) {
            logger.warn("Failed to run qlty ${args.firstOrNull()}: ${e.message}")
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
