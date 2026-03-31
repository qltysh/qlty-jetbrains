package com.qlty.intellij

import com.intellij.lang.annotation.HighlightSeverity
import com.qlty.intellij.model.Issue
import com.qlty.intellij.util.SeverityMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityMapperTest {
    private fun issueWith(
        level: String,
        category: String = "CATEGORY_LINT",
    ): Issue = Issue(level = level, category = category)

    @Test
    fun highLevelMapsToError() {
        assertEquals(HighlightSeverity.ERROR, SeverityMapper.mapSeverity(issueWith("LEVEL_HIGH")))
    }

    @Test
    fun mediumLevelMapsToWarning() {
        assertEquals(HighlightSeverity.WARNING, SeverityMapper.mapSeverity(issueWith("LEVEL_MEDIUM")))
    }

    @Test
    fun lowLevelMapsToWarning() {
        assertEquals(HighlightSeverity.WARNING, SeverityMapper.mapSeverity(issueWith("LEVEL_LOW")))
    }

    @Test
    fun noteLevelMapsToWeakWarning() {
        assertEquals(HighlightSeverity.WEAK_WARNING, SeverityMapper.mapSeverity(issueWith("LEVEL_NOTE")))
    }

    @Test
    fun fmtLevelMapsToWeakWarning() {
        assertEquals(HighlightSeverity.WEAK_WARNING, SeverityMapper.mapSeverity(issueWith("LEVEL_FMT")))
    }

    @Test
    fun unspecifiedLevelMapsToWeakWarning() {
        assertEquals(HighlightSeverity.WEAK_WARNING, SeverityMapper.mapSeverity(issueWith("LEVEL_UNSPECIFIED")))
    }

    @Test
    fun vulnerabilityCategoryMapsToError() {
        assertEquals(HighlightSeverity.ERROR, SeverityMapper.mapSeverity(issueWith("LEVEL_LOW", "CATEGORY_VULNERABILITY")))
    }

    @Test
    fun securityHotspotCategoryMapsToError() {
        assertEquals(HighlightSeverity.ERROR, SeverityMapper.mapSeverity(issueWith("LEVEL_NOTE", "CATEGORY_SECURITY_HOTSPOT")))
    }

    @Test
    fun secretCategoryMapsToError() {
        assertEquals(HighlightSeverity.ERROR, SeverityMapper.mapSeverity(issueWith("LEVEL_LOW", "CATEGORY_SECRET")))
    }

    @Test
    fun dependencyAlertCategoryMapsToError() {
        assertEquals(HighlightSeverity.ERROR, SeverityMapper.mapSeverity(issueWith("LEVEL_LOW", "CATEGORY_DEPENDENCY_ALERT")))
    }

    @Test
    fun typeCheckCategoryMapsToError() {
        assertEquals(HighlightSeverity.ERROR, SeverityMapper.mapSeverity(issueWith("LEVEL_LOW", "CATEGORY_TYPE_CHECK")))
    }

    @Test
    fun categoryPrefixForVulnerability() {
        assertEquals("Vulnerability: ", SeverityMapper.categoryPrefix(issueWith("LEVEL_LOW", "CATEGORY_VULNERABILITY")))
    }

    @Test
    fun categoryPrefixForSecret() {
        assertEquals("Secret: ", SeverityMapper.categoryPrefix(issueWith("LEVEL_LOW", "CATEGORY_SECRET")))
    }

    @Test
    fun categoryPrefixForLint() {
        assertEquals("", SeverityMapper.categoryPrefix(issueWith("LEVEL_LOW", "CATEGORY_LINT")))
    }

    @Test
    fun categoryPrefixForSecurity() {
        assertEquals("Security: ", SeverityMapper.categoryPrefix(issueWith("LEVEL_LOW", "CATEGORY_SECURITY_HOTSPOT")))
    }

    @Test
    fun categoryPrefixForDependency() {
        assertEquals("Dependency: ", SeverityMapper.categoryPrefix(issueWith("LEVEL_LOW", "CATEGORY_DEPENDENCY_ALERT")))
    }
}
