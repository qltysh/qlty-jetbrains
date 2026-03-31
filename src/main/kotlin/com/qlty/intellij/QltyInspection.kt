package com.qlty.intellij

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection
import com.qlty.Values

class QltyInspection : LocalInspectionTool(), ExternalAnnotatorBatchInspection {
    override fun getShortName(): String = Values.INSPECTION_SHORT_NAME
}
