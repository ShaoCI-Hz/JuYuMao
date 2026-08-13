package com.hezi.juyumao.ui.theme

import java.util.Locale

object FormatUtils {
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeGroup = digitGroups.coerceIn(0, units.size - 1)
        return String.format(
            Locale.US,
            "%.1f %s",
            bytes / Math.pow(1024.0, safeGroup.toDouble()),
            units[safeGroup]
        )
    }

    /** 毫秒 → m:ss（零值/负值回退 [zeroText]） */
    fun formatDuration(ms: Long, zeroText: String = "--:--"): String {
        if (ms <= 0) return zeroText
        val totalSeconds = ms / 1000
        // 指定 Locale，避免阿拉伯语等系统 locale 下输出非拉丁数字
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}
