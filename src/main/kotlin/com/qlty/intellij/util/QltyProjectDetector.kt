package com.qlty.intellij.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object QltyProjectDetector {

    private val cache = ConcurrentHashMap<String, String?>()

    fun findQltyRoot(file: VirtualFile, project: Project): String? {
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

    private fun walkUpForQltyToml(filePath: String, projectBasePath: String?): String? {
        var dir = File(filePath).parentFile
        val stopAt = projectBasePath?.let { File(it).parentFile }

        while (dir != null) {
            if (File(dir, ".qlty/qlty.toml").exists()) {
                return dir.absolutePath
            }
            if (stopAt != null && dir.absolutePath == stopAt.absolutePath) {
                break
            }
            dir = dir.parentFile
        }
        return null
    }
}
