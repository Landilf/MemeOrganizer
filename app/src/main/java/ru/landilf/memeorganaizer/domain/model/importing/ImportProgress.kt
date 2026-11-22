package ru.landilf.memeorganaizer.domain.model.importing

data class ImportProgress(
    val total: Int,
    val completed: Int,
    val latestSource: ImportSource? = null,
    val latestResult: ImportResult? = null
)
