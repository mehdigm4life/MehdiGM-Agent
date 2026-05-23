package com.gs.agent.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileExplorerViewModel : ViewModel() {
    private val _root = MutableStateFlow<FileNode?>(null)
    val root: StateFlow<FileNode?> = _root

    /** Load the given directory (one level deep). */
    fun loadDirectory(path: String) {
        viewModelScope.launch {
            val dir = File(path)
            if (!dir.isDirectory) return@launch
            val children = dir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { FileNode(it, isDirectory = it.isDirectory) }
                .orEmpty()
            _root.value = FileNode(dir, children, true)
        }
    }
}
