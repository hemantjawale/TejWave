package com.example.tejastra.utils

fun String.toTitleCase(): String {
    return this.split(Regex("[\\s_]+")).filter { it.isNotEmpty() }.joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}
