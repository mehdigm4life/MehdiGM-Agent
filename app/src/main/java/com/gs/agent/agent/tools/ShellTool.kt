package com.gs.agent.agent.tools

import android.content.Context
import android.os.Build
import com.gs.agent.BuildConfig               // استيراد BuildConfig الصحيح
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

object ShellTool : Tool {
    override val name = "run_shell"
    override val description = "Execute a shell command using /system/bin/sh -c."
    override val argumentsSchema = """{"command":"string","timeout_seconds":30}"""

    // في وضع Debug لا نغلق التطبيق، فقط نعيد الخطأ.
    private val isDebug = BuildConfig.DEBUG

    override suspend fun execute(context: Context, args: JsonObject): ToolResult =
        withContext(Dispatchers.IO) {
            val cmd = args["command"]?.jsonPrimitive?.contentOrNull
                ?: return@withContext ToolResult(false, "Missing 'command' argument")
            val timeout = args["timeout_seconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 30

            try {
                val proc = ProcessBuilder("/system/bin/sh", "-c", cmd)
                    .redirectErrorStream(true)
                    .start()
                val finished = proc.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    return@withContext ToolResult(false, "Timed out after $timeout s")
                }
                val out = proc.inputStream.bufferedReader().readText().take(50_000)
                val code = proc.exitValue()
                ToolResult(success = code == 0, output = "exit=$code\n$out")
            } catch (e: Exception) {
                // إظهار الخطأ فقط في وضع Debug، وإلا لا نغلق التطبيق.
                if (isDebug) {
                    ToolResult(false, "Shell error: ${e.message}")
                } else {
                    // وضع الإنتاج – يمكنك إضافة سلوك إغلاق إن رغبت، لكن الآن لا نستخدم SystemTools.
                    ToolResult(false, "Shell error: ${e.message}")
                }
            }
        }
}
