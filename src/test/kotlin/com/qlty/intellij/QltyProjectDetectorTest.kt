package com.qlty.intellij

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.qlty.intellij.util.QltyProjectDetector
import java.io.File

class QltyProjectDetectorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        QltyProjectDetector.clearCache()
    }

    fun testFindsNearestQltyRootInProjectTree() {
        val projectRoot = requireNotNull(project.basePath)
        File(projectRoot, ".qlty").mkdirs()
        File(projectRoot, ".qlty/qlty.toml").writeText("version = 1\n")

        val nestedDir = File(projectRoot, "src/deep")
        nestedDir.mkdirs()
        val sourceFile = File(nestedDir, "example.kt").apply {
            writeText("fun main() = Unit\n")
        }

        val virtualFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceFile))

        val root = QltyProjectDetector.findQltyRoot(virtualFile, project)

        assertEquals(projectRoot, root)
    }

    fun testReturnsNullWhenConfigExistsOnlyOutsideProjectBoundary() {
        val projectRoot = requireNotNull(project.basePath)
        File(projectRoot, ".qlty/qlty.toml").delete()
        File(projectRoot, ".qlty").delete()
        val parentDir = File(projectRoot).parentFile
        File(parentDir, ".qlty").mkdirs()
        File(parentDir, ".qlty/qlty.toml").writeText("version = 1\n")

        val sourceFile = File(projectRoot, "src/example.kt").apply {
            parentFile.mkdirs()
            writeText("fun main() = Unit\n")
        }

        val virtualFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceFile))

        val root = QltyProjectDetector.findQltyRoot(virtualFile, project)

        assertNull(root)
    }

    fun testFindsQltyRootInAttachedContentRootOutsideProjectBasePath() {
        val projectRoot = requireNotNull(project.basePath)
        val attachedRoot = File(projectRoot).parentFile.resolve("attached-root")
        File(attachedRoot, ".qlty").mkdirs()
        File(attachedRoot, ".qlty/qlty.toml").writeText("version = 1\n")

        val sourceFile = File(attachedRoot, "README.md").apply {
            writeText("# demo\n")
        }

        val attachedVirtualRoot = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(attachedRoot))
        PsiTestUtil.addContentRoot(module, attachedVirtualRoot)

        val virtualFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceFile))

        val root = QltyProjectDetector.findQltyRoot(virtualFile, project)

        assertEquals(attachedRoot.absolutePath, root)
    }
}
