package com.simplemobiletools.gallery.pro.extensions

import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.commons.models.PackedInt

fun Int.isSortingAscending() = this and SORT_DESCENDING == 0
fun PackedInt.isSortingAscending() = this notHas SORT_DESCENDING

