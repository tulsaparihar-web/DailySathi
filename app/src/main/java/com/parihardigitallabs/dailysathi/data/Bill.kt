package com.parihardigitallabs.dailysathi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountPaise: Long,
    val dueAtMillis: Long,
    val frequency: String = "One-time",
    val category: String = "Bill",
    val isPaid: Boolean = false,
    val note: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)
