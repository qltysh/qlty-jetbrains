package com.qlty.intellij.util

import com.intellij.openapi.project.Project
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

        val root = walkUpForQltyToml(filePath, project.basePath)
        if (root != null) {
            cache[filePath] = root
        }
        return root
    }

    fun clearCache() {
        cache.clear()
    }

    private fun walkUpForQltyToml(
        filePath: String,
        projectBasePath: String?,
    ): String? {
        var dir = File(filePath).parentFile
        val projectRoot = projectBasePath?.let { File(it).absoluteFile.toPath().normalize() }

        while (dir != null) {
            val dirPath = dir.absoluteFile.toPath().normalize()
            if (projectRoot != null && !dirPath.startsWith(projectRoot)) {
                break
            }
            if (File(dir, ".qlty/qlty.toml").exists()) {
                return dir.absolutePath
            }
            if (projectRoot != null && dirPath == projectRoot) {
                break
            }
            dir = dir.parentFile
        }
        return null
    }
}
