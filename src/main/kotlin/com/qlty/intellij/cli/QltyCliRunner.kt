package com.qlty.intellij.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.qlty.intellij.model.Issue
import com.qlty.intellij.settings.QltySettings
import java.io.File
import java.util.concurrent.CompletableFuture

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
                listOf("smells", "--json", filePath),
                workDir
            )
        }

        val checkOutput = checkFuture.get()
        val smellsOutput = smellsFuture.get()

        val checkIssues = if (checkOutput != null) {
            QltyJsonParser.parseIssues(checkOutput).filter { it.location.path == relativePath }
        } else {
            emptyList()
        }
        val smellsIssues = if (smellsOutput != null) QltyJsonParser.parseIssues(smellsOutput) else emptyList()

        return checkIssues + smellsIssues
    }

    fun fixFile(filePath: String, workDir: String) {
        val settings = QltySettings.getInstance(project)
        val binary = resolveBinary(settings.qltyBinaryPath) ?: return
        runCommand(binary, listOf("check", "--no-progress", "--fix", "--trigger", "ide"), workDir)
    }

    private fun resolveBinary(configured: String): String? {
        if (File(configured).isAbsolute && File(configured).canExecute()) {
            return configured
        }

        val home = System.getProperty("user.home") ?: return configured
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

        return configured
    }

    private fun runCommand(binary: String, args: List<String>, workDir: String): String? {
        return try {
            val commandLine = GeneralCommandLine(binary)
                .withParameters(args)
                .withWorkDirectory(workDir)
                .withCharset(Charsets.UTF_8)
                .withEnvironment("NO_COLOR", "1")

            ScriptRunnerUtil.getProcessOutput(
                commandLine,
                ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER,
                TIMEOUT_MS.toLong()
            )
        } catch (e: Exception) {
            logger.warn("Failed to run qlty ${args.firstOrNull()}: ${e.message}")
            null
        }
    }

    companion object {
        private const val TIMEOUT_MS = 60_000
    }
}
