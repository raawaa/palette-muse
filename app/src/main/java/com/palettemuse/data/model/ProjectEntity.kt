package com.palettemuse.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class ProjectType { CAPTURE, OUTFIT }

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val imagePath: String,
    val type: ProjectType
)
