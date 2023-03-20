package com.simplemobiletools.gallery.pro.enums

// file loading priority
enum class FileLoadingPriorityEnum(val id: Int) {
    SPEED(0),
    COMPROMISE(1),
    VALIDITY(2);

    companion object {
        val ID_TO_ENUM_MAP = values().map { it.id to it }.toMap()
        fun getEnumFromId(id: Int) = ID_TO_ENUM_MAP[id]!!
    }
}
