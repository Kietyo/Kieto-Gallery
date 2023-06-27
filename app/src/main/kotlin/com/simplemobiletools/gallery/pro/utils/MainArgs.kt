package com.simplemobiletools.gallery.pro.utils

data class MainArgs(
    val mIsPickImageIntent: Boolean = false,
    val mIsPickVideoIntent: Boolean = false,
    val mIsGetImageContentIntent: Boolean = false,
    val mIsGetVideoContentIntent: Boolean = false,
    val mIsGetAnyContentIntent: Boolean = false,
    val mIsSetWallpaperIntent: Boolean = false,
    val mAllowPickingMultiple: Boolean = false,
) {
    val mIsThirdPartyIntent: Boolean = mIsPickImageIntent || mIsPickVideoIntent || mIsGetImageContentIntent || mIsGetVideoContentIntent ||
        mIsGetAnyContentIntent || mIsSetWallpaperIntent
}
