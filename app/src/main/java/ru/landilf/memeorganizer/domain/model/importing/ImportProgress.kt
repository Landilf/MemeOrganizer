package ru.landilf.memeorganizer.domain.model.importing

data class ImportProgress(
    val total: Int,
    val completed: Int,
    val latestSource: ImportSource? = null,
    val latestResult: ImportResult? = null
)
