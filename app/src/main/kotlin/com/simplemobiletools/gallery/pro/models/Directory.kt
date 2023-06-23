package com.simplemobiletools.gallery.pro.models

import android.content.Context
import androidx.room.*
import com.bumptech.glide.signature.ObjectKey
import com.simplemobiletools.commons.extensions.formatDate
import com.simplemobiletools.commons.extensions.formatSize
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.PackedInt
import com.simplemobiletools.commons.models.toPackedInt
import com.simplemobiletools.gallery.pro.helpers.RECYCLE_BIN

@Entity(tableName = "directories", indices = [Index(value = ["path"], unique = true)])
data class Directory(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "path") var path: String,
    @ColumnInfo(name = "thumbnail") var tmb: String,
    @ColumnInfo(name = "filename") var name: String,
    @ColumnInfo(name = "media_count") var mediaCnt: Int,
    @ColumnInfo(name = "last_modified") var modified: Long,
    @ColumnInfo(name = "date_taken") var taken: Long,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "location") var location: Int,
    @ColumnInfo(name = "media_types") var types_: Int,
    @ColumnInfo(name = "sort_value") var sortValue: String,

    // used with "Group direct subfolders" enabled
    @Ignore var subfoldersCount: Int = 0,
    @Ignore var subfoldersMediaCount: Int = 0,
    @Ignore var containsMediaFilesDirectly: Boolean = true
) {
    val types: PackedInt get() = types_.toPackedInt()

    constructor() : this(null, "", "", "", 0, 0L, 0L, 0L, 0, 0, "", 0, 0)

    fun getBubbleText(sorting: PackedInt, context: Context, dateFormat: String? = null, timeFormat: String? = null) = when {
        sorting has SORT_BY_NAME -> name
        sorting has SORT_BY_PATH -> path
        sorting has SORT_BY_SIZE -> size.formatSize()
        sorting has SORT_BY_DATE_MODIFIED -> modified.formatDate(context, dateFormat, timeFormat)
        sorting has SORT_BY_RANDOM -> name
        else -> taken.formatDate(context)
    }

    fun areFavorites() = path == FAVORITES

    fun isRecycleBin() = path == RECYCLE_BIN

    fun getKey() = ObjectKey("$path-$modified")
}
