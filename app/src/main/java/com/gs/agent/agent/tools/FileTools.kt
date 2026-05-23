package com.gs.agent.agent.tools

import android.content.Context
import android.os.Environment
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private const val MAX_READ_BYTES = 200_000
private const val MAX_LIST_ITEMS = 500

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun resolvePath(raw: String?): File? {
    if (raw.isNullOrBlank()) return null
    val path = raw.trim()
    val base = when {
        path.startsWith("~") -> File(Environment.getExternalStorageDirectory(), path.removePrefix("~").trimStart('/'))
        path.startsWith("/") -> File(path)
        else -> File(Environment.getExternalStorageDirectory(), path)
    }
    return base
}

object ReadFileTool : Tool {
    override val name = "read_file"
    override val description = "Read text contents of a file from device storage. Returns up to 200KB."
    override val argumentsSchema = """{"path": "absolute or relative path"}"""

    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val file = resolvePath(args.str("path"))
            ?: return ToolResult(false, "Missing required 'path' argument")
        if (!file.exists()) return ToolResult(false, "File does not exist: ${file.absolutePath}")
        if (file.isDirectory) return ToolResult(false, "Path is a directory, use list_directory instead")
        return try {
            val bytes = file.readBytes()
            val truncated = bytes.size > MAX_READ_BYTES
            val text = String(bytes.take(MAX_READ_BYTES).toByteArray(), Charsets.UTF_8)
            ToolResult(true, text, truncated)
        } catch (e: Exception) {
            ToolResult(false, "Read failed: ${e.message}")
        }
    }
}

object WriteFileTool : Tool {
    override val name = "write_file"
    override val description = "Write text content to a file. Creates parent directories. Overwrites if exists."
    override val argumentsSchema = """{"path": "string", "content": "string", "append": false}"""

    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val file = resolvePath(args.str("path"))
            ?: return ToolResult(false, "Missing required 'path' argument")
        val content = args.str("content") ?: ""
        val append = args["append"]?.jsonPrimitive?.contentOrNull == "true"
        return try {
            file.parentFile?.mkdirs()
            if (append) file.appendText(content) else file.writeText(content)
            ToolResult(true, "Wrote ${content.length} chars to ${file.absolutePath}")
        } catch (e: Exception) {
            ToolResult(false, "Write failed: ${e.message}")
        }
    }
}

object ListDirectoryTool : Tool {
    override val name = "list_directory"
    override val description = "List files and folders in a directory."
    override val argumentsSchema = """{"path": "string"}"""

    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val dir = resolvePath(args.str("path"))
            ?: return ToolResult(false, "Missing 'path' argument")
        if (!dir.exists()) return ToolResult(false, "Directory does not exist: ${dir.absolutePath}")
        if (!dir.isDirectory) return ToolResult(false, "Not a directory: ${dir.absolutePath}")
        val list = dir.listFiles()?.toList().orEmpty().sortedBy { it.name }
        val truncated = list.size > MAX_LIST_ITEMS
        val visible = list.take(MAX_LIST_ITEMS)
        val text = buildString {
            append(dir.absolutePath).append(" (").append(list.size).append(" items)\n")
            for (f in visible) {
                if (f.isDirectory) append("[DIR]  ").append(f.name).append("/\n")
                else append("[FILE] ").append(f.name).append("  (").append(f.length()).append(" bytes)\n")
            }
        }
        return ToolResult(true, text, truncated)
    }
}

object DeleteFileTool : Tool {
    override val name = "delete_file"
    override val description = "Delete a file or empty directory. Recursive deletion when recursive=true."
    override val argumentsSchema = """{"path": "string", "recursive": false}"""

    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val file = resolvePath(args.str("path"))
            ?: return ToolResult(false, "Missing 'path' argument")
        if (!file.exists()) return ToolResult(false, "Path does not exist")
        val recursive = args["recursive"]?.jsonPrimitive?.contentOrNull == "true"
        return try {
            val ok = if (recursive) file.deleteRecursively() else file.delete()
            if (ok) ToolResult(true, "Deleted ${file.absolutePath}")
            else ToolResult(false, "Delete failed (use recursive for non-empty dirs)")
        } catch (e: Exception) {
            ToolResult(false, "Delete failed: ${e.message}")
        }
    }
}

object MakeDirectoryTool : Tool {
    override val name = "make_directory"
    override val description = "Create a directory (and parents). Returns success info."
    override val argumentsSchema = """{"path": "string"}"""
    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val dir = resolvePath(args.str("path"))
            ?: return ToolResult(false, "Missing 'path'")
        return try {
            if (dir.mkdirs() || dir.isDirectory) ToolResult(true, "Created ${dir.absolutePath}")
            else ToolResult(false, "Could not create directory")
        } catch (e: Exception) { ToolResult(false, "Error: ${e.message}") }
    }
}

object MoveFileTool : Tool {
    override val name = "move_file"
    override val description = "Move or rename a file/directory."
    override val argumentsSchema = """{"from": "string", "to": "string"}"""
    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val from = resolvePath(args.str("from")) ?: return ToolResult(false, "Missing 'from'")
        val to = resolvePath(args.str("to")) ?: return ToolResult(false, "Missing 'to'")
        if (!from.exists()) return ToolResult(false, "Source does not exist")
        return try {
            to.parentFile?.mkdirs()
            if (from.renameTo(to)) ToolResult(true, "Moved to ${to.absolutePath}")
            else {
                from.copyRecursively(to, overwrite = true)
                from.deleteRecursively()
                ToolResult(true, "Moved (copy+delete) to ${to.absolutePath}")
            }
        } catch (e: Exception) { ToolResult(false, "Move failed: ${e.message}") }
    }
}

object SearchFilesTool : Tool {
    override val name = "search_files"
    override val description = "Recursively search for files whose name contains a query. Returns matching paths."
    override val argumentsSchema = """{"root": "string", "query": "string", "max": 200}"""
    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val root = resolvePath(args.str("root")) ?: return ToolResult(false, "Missing 'root'")
        val query = args.str("query") ?: return ToolResult(false, "Missing 'query'")
        val max = args.str("max")?.toIntOrNull() ?: 200
        if (!root.exists() || !root.isDirectory) return ToolResult(false, "Root must be an existing directory")
        val matches = mutableListOf<String>()
        try {
            root.walkTopDown().forEach { f ->
                if (matches.size >= max) return@forEach
                if (f.name.contains(query, ignoreCase = true)) matches += f.absolutePath
            }
        } catch (e: Exception) {
            return ToolResult(false, "Search failed: ${e.message}")
        }
        return ToolResult(true, matches.joinToString("\n").ifEmpty { "No matches" })
    }
}

object FileInfoTool : Tool {
    override val name = "file_info"
    override val description = "Get metadata about a file or directory (size, modified time, type)."
    override val argumentsSchema = """{"path": "string"}"""
    override suspend fun execute(context: Context, args: JsonObject): ToolResult {
        val f = resolvePath(args.str("path")) ?: return ToolResult(false, "Missing 'path'")
        if (!f.exists()) return ToolResult(false, "Not found")
        val info = buildString {
            appendLine("Path: ${f.absolutePath}")
            appendLine("Type: ${if (f.isDirectory) "directory" else "file"}")
            appendLine("Size: ${f.length()} bytes")
            appendLine("Modified: ${java.util.Date(f.lastModified())}")
            appendLine("Readable: ${f.canRead()}  Writable: ${f.canWrite()}")
        }
        return ToolResult(true, info)
    }
}
