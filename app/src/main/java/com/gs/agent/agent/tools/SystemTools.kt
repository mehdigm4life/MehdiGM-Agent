package com.gs.agent.agent.tools

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.serialization.json.JsonObject

object DeviceInfoTool : Tool {
    override val name = "device_info"
    override val description = "Get information about the device: model, Android version, storage, build."
    override val argumentsSchema = """{}"""
    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val storage = Environment.getExternalStorageDirectory()
        val stat = StatFs(storage.absolutePath)
        val freeGb = stat.availableBytes / 1024.0 / 1024.0 / 1024.0
        val totalGb = stat.totalBytes / 1024.0 / 1024.0 / 1024.0
        val info = buildString {
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("Storage: ${"%.2f".format(freeGb)} GB free of ${"%.2f".format(totalGb)} GB")
            appendLine("External storage path: ${storage.absolutePath}")
        }
        return ToolResult(true, info)
    }
}

object CurrentTimeTool : Tool {
    override val name = "current_time"
    override val description = "Get the current local date and time."
    override val argumentsSchema = """{}"""
    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", java.util.Locale.US).format(java.util.Date())
        return ToolResult(true, now)
    }
}

fun registerDefaultTools() {
    ToolRegistry.register(ReadFileTool)
    ToolRegistry.register(WriteFileTool)
    ToolRegistry.register(ListDirectoryTool)
    ToolRegistry.register(DeleteFileTool)
    ToolRegistry.register(MakeDirectoryTool)
    ToolRegistry.register(MoveFileTool)
    ToolRegistry.register(SearchFilesTool)
    ToolRegistry.register(FileInfoTool)
    ToolRegistry.register(EditFileTool)
    ToolRegistry.register(ShellTool)
    ToolRegistry.register(DeviceInfoTool)
    ToolRegistry.register(CurrentTimeTool)
}
