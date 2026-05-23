package com.gs.agent.ui.explorer

import java.io.File

/**
 * Simple representation of a file system node used by the explorer UI.
 * Only the direct children are loaded to keep memory usage low – the UI can
 * request deeper levels on demand.
 */
data class FileNode(
    val file: File,
    val children: List<FileNode> = emptyList(),
    val isDirectory: Boolean = file.isDirectory
)
