package com.gs.agent.agent.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ShellTool : Tool {
    override val name = "run_shell"
    override val description = "Execute a shell command using /system/bin/sh -c. Returns stdout+stderr. Note: non-rooted devices have limited capability."
    override val argumentsSchema = """{"command": "string", "timeout_seconds": 30}"""

    override suspend fun execute(context: Context, args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val cmd = args["command"]?.jsonPrimitive?.contentOrNull
            ?: return@withContext ToolResult(false, "Missing 'command' argument")
        val timeoutSeconds = args["timeout_seconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 30

        try {
            val proc = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext ToolResult(false, "Timed out after $timeoutSeconds s")
            }
            val output = proc.inputStream.bufferedReader().readText().take(50_000)
            val code = proc.exitValue()
            ToolResult(
                success = code == 0,
                output = "exit=$code\n$output"
            )
        } catch (e: Exception) {
            ToolResult(false, "Shell error: ${e.message}")
        }
    }
}

object EditFileTool : Tool {
    override val name = "edit_file"
    override val description = "Replace a literal text fragment in a file. Useful for surgical code edits."
    override val argumentsSchema = """{"path": "string", "find": "string", "replace": "string", "replace_all": false}"""

    override suspend fun execute(context: Context, args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return@withContext ToolResult(false, "Missing 'path'")
        val find = args["find"]?.jsonPrimitive?.contentOrNull ?: return@withContext ToolResult(false, "Missing 'find'")
        val replace = args["replace"]?.jsonPrimitive?.contentOrNull ?: ""
        val replaceAll = args["replace_all"]?.jsonPrimitive?.contentOrNull == "true"

        val file = java.io.File(if (path.startsWith("/")) path else android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + path)
        if (!file.exists()) return@withContext ToolResult(false, "File does not exist")
        try {
            val text = file.readText()
            if (!text.contains(find)) return@withContext ToolResult(false, "Find text not found in file")
            val updated = if (replaceAll) text.replace(find, replace) else text.replaceFirst(find, replace)
            file.writeText(updated)
            ToolResult(true, "Edit applied to ${file.absolutePath}")
        } catch (e: Exception) {
            ToolResult(false, "Edit failed: ${e.message}")
        }
    }
}
