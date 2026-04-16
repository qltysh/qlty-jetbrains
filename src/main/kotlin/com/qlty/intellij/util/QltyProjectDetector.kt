package com.qlty.intellij.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object QltyProjectDetector {
    private val cache = ConcurrentHashMap<String, String?>()

    fun findQltyRoot(
        file: VirtualFile,
        project: Project,
    ): String? {
        val filePath = file.path
        val cached = cache[filePath]
        if (cached != null) return cached

        val searchBoundaryPath = findSearchBoundary(file, project)
        val root = walkUpForQltyToml(filePath, searchBoundaryPath)
        if (root != null) {
            cache[filePath] = root
        }
        return root
    }

    fun clearCache() {
        cache.clear()
    }

    private fun findSearchBoundary(
        file: VirtualFile,
        project: Project,
    ): String? {
        val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
        return contentRoot?.path ?: project.basePath
    }

    private fun walkUpForQltyToml(
        filePath: String,
        searchBoundaryPath: String?,
    ): String? {
        var dir = File(filePath).parentFile
        val searchBoundary = searchBoundaryPath?.let { File(it).absoluteFile.toPath().normalize() }

        while (dir != null) {
            val dirPath = dir.absoluteFile.toPath().normalize()
            if (searchBoundary != null && !dirPath.startsWith(searchBoundary)) {
                break
            }
            if (File(dir, ".qlty/qlty.toml").exists()) {
                return dir.absolutePath
            }
            if (searchBoundary != null && dirPath == searchBoundary) {
                break
            }
            dir = dir.parentFile
        }
        return null
    }
}
