package com.simplemobiletools.gallery.pro.activities

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video
import android.util.Log
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.CreateNewFolderDialog
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.Release
import com.simplemobiletools.commons.utils.KietLog
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.gallery.pro.BuildConfig
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.adapters.DirectoryAdapter
import com.simplemobiletools.gallery.pro.databases.GalleryDatabase
import com.simplemobiletools.gallery.pro.dialogs.ChangeSortingDialog
import com.simplemobiletools.gallery.pro.dialogs.ChangeViewTypeDialog
import com.simplemobiletools.gallery.pro.dialogs.FilterMediaDialog
import com.simplemobiletools.gallery.pro.dialogs.GrantAllFilesDialog
import com.simplemobiletools.gallery.pro.extensions.*
import com.simplemobiletools.gallery.pro.helpers.*
import com.simplemobiletools.gallery.pro.interfaces.DirectoryOperationsListener
import com.simplemobiletools.gallery.pro.jobs.NewPhotoFetcher
import com.simplemobiletools.gallery.pro.models.Directory
import com.simplemobiletools.gallery.pro.models.Medium
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class MainActivity : SimpleActivity(), DirectoryOperationsListener {

    private var mIsPickImageIntent = false
    private var mIsPickVideoIntent = false
    private var mIsGetImageContentIntent = false
    private var mIsGetVideoContentIntent = false
    private var mIsGetAnyContentIntent = false
    private var mIsSetWallpaperIntent = false
    private var mAllowPickingMultiple = false
    private var mIsThirdPartyIntent = false
    private var mIsGettingDirs = false
    private var mLoadedInitialPhotos = false
    private var mIsPasswordProtectionPending = false
    private var mWasProtectionHandled = false
    private var mShouldStopFetching = false
    private var mWasDefaultFolderChecked = false
    private var mWasMediaManagementPromptShown = false
    private var mWasUpgradedFromFreeShown = false
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mCurrentPathPrefix = ""                 // used at "Group direct subfolders" for navigation
    private var mOpenedSubfolders = arrayListOf("")     // used at "Group direct subfolders" for navigating Up with the back button
    private var mDateFormat = ""
    private var mTimeFormat = ""
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null
    private var mLastMediaFetcher: MediaFetcher? = null
    private var mDirs = listOf<Directory>()
    private var mDirsIgnoringSearch = listOf<Directory>()

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredStyleString = ""

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (savedInstanceState == null) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            removeTempFolder()
            checkRecycleBinItems()
            startNewPhotoFetcher()
        }

        mIsPickImageIntent = isPickImageIntent(intent)
        mIsPickVideoIntent = isPickVideoIntent(intent)
        mIsGetImageContentIntent = isGetImageContentIntent(intent)
        mIsGetVideoContentIntent = isGetVideoContentIntent(intent)
        mIsGetAnyContentIntent = isGetAnyContentIntent(intent)
        mIsSetWallpaperIntent = isSetWallpaperIntent(intent)
        mAllowPickingMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        mIsThirdPartyIntent = mIsPickImageIntent || mIsPickVideoIntent || mIsGetImageContentIntent || mIsGetVideoContentIntent ||
            mIsGetAnyContentIntent || mIsSetWallpaperIntent

        setupOptionsMenu()
        refreshMenuItems()

        updateMaterialActivityViews(directories_coordinator, directories_grid, useTransparentNavigation = !config.scrollHorizontally, useTopSearchMenu = true)

        directories_refresh_layout.setOnRefreshListener { getDirectories() }
        storeStateVariables()
        checkWhatsNewDialog()

        mIsPasswordProtectionPending = config.isAppPasswordProtectionOn
        setupLatestMediaId()

        if (!config.wereFavoritesPinned) {
            config.addPinnedFolders(hashSetOf(FAVORITES))
            config.wereFavoritesPinned = true
        }

        if (!config.wasRecycleBinPinned) {
            config.addPinnedFolders(hashSetOf(RECYCLE_BIN))
            config.wasRecycleBinPinned = true
            config.saveFolderGrouping(SHOW_ALL, GROUP_BY_DATE_TAKEN_DAILY or GROUP_DESCENDING)
        }

        if (!config.wasSVGShowingHandled) {
            config.wasSVGShowingHandled = true
            if (config.filterMedia.notHas(TYPE_SVGS)) {
                config.filterMedia += TYPE_SVGS
            }
        }

        if (!config.wasSortingByNumericValueAdded) {
            config.wasSortingByNumericValueAdded = true
            config.sorting = config.sorting + SORT_USE_NUMERIC_VALUE
        }

        updateWidgets()
        registerFileUpdateListener()

        directories_switch_searching.setOnClickListener {
            launchSearchActivity()
        }

        // just request the permission, tryLoadGallery will then trigger in onResume
        handleMediaPermissions { success ->
            if (!success) {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    private fun handleMediaPermissions(callback: (granted: Boolean) -> Unit) {
        handlePermission(getPermissionToRequest()) { granted ->
            callback(granted)
            if (granted && isRPlus()) {
                handlePermission(PERMISSION_MEDIA_LOCATION) {}
                if (isTiramisuPlus()) {
                    handlePermission(PERMISSION_READ_MEDIA_VIDEO) {}
                }

                if (!mWasMediaManagementPromptShown) {
                    mWasMediaManagementPromptShown = true
                    handleMediaManagementPrompt { }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onResume() {
        super.onResume()
        updateMenuColors()
        config.isThirdPartyIntent = false
        mDateFormat = config.dateFormat
        mTimeFormat = getTimeFormat()

        refreshMenuItems()

        if (mStoredAnimateGifs != config.animateGifs) {
            getRecyclerAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getRecyclerAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            directories_grid.adapter = null
            getDirectories()
        }

        if (mStoredTextColor != getProperTextColor()) {
            getRecyclerAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getRecyclerAdapter()?.updatePrimaryColor()
        }

        val styleString = "${config.folderStyle}${config.showFolderMediaCount}${config.limitFolderTitle}"
        if (mStoredStyleString != styleString) {
            setupAdapter("onResume", mDirs, forceRecreate = true)
        }

        directories_fastscroller.updateColors(primaryColor)
        directories_refresh_layout.isEnabled = config.enablePullToRefresh
        getRecyclerAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        directories_empty_placeholder.setTextColor(getProperTextColor())
        directories_empty_placeholder_2.setTextColor(primaryColor)
        directories_switch_searching.setTextColor(primaryColor)
        directories_switch_searching.underlineText()
        directories_empty_placeholder_2.bringToFront()

        if (!main_menu.isSearchOpen) {
            refreshMenuItems()
            if (mIsPasswordProtectionPending && !mWasProtectionHandled) {
                handleAppPasswordProtection {
                    mWasProtectionHandled = it
                    if (it) {
                        mIsPasswordProtectionPending = false
                        tryLoadGallery()
                    } else {
                        finish()
                    }
                }
            } else {
                tryLoadGallery()
            }
        }

        if (config.searchAllFilesByDefault) {
            main_menu.updateHintText(getString(R.string.search_files))
        } else {
            main_menu.updateHintText(getString(R.string.search_folders))
        }
    }

    override fun onPause() {
        super.onPause()
        directories_refresh_layout.isRefreshing = false
        mIsGettingDirs = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation || config.temporarilyShowExcluded) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.temporarilyShowExcluded = false
                config.tempSkipDeleteConfirmation = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
            removeTempFolder()
            unregisterFileUpdateListener()

            if (!config.showAll) {
                mLastMediaFetcher?.shouldStop = true
                GalleryDatabase.destroyInstance()
            }
        }
    }

    override fun onBackPressed() {
        if (main_menu.isSearchOpen) {
            main_menu.closeSearch()
        } else if (config.groupDirectSubfolders) {
            if (mCurrentPathPrefix.isEmpty()) {
                super.onBackPressed()
            } else {
                mOpenedSubfolders.removeLast()
                mCurrentPathPrefix = mOpenedSubfolders.last()
                setupAdapter("onBackPressed", mDirs)
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == Companion.PICK_MEDIA && resultData != null) {
                val resultIntent = Intent()
                var resultUri: Uri? = null
                if (mIsThirdPartyIntent) {
                    when {
                        intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true && intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 -> {
                            resultUri = fillExtraOutput(resultData)
                        }

                        resultData.extras?.containsKey(PICKED_PATHS) == true -> fillPickedPaths(resultData, resultIntent)
                        else -> fillIntentPath(resultData, resultIntent)
                    }
                }

                if (resultUri != null) {
                    resultIntent.data = resultUri
                    resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else if (requestCode == Companion.PICK_WALLPAPER) {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun refreshMenuItems() {
        if (!mIsThirdPartyIntent) {
            main_menu.getToolbar().menu.apply {
                findItem(R.id.column_count).isVisible = config.viewTypeFolders == VIEW_TYPE_GRID
                findItem(R.id.set_as_default_folder).isVisible = config.defaultFolder.isNotEmpty()
                findItem(R.id.open_recycle_bin).isVisible = config.useRecycleBin && !config.showRecycleBinAtFolders
                findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(R.bool.hide_google_relations)
            }
        }

        main_menu.getToolbar().menu.apply {
            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible = (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            findItem(R.id.temporarily_show_excluded).isVisible = !config.temporarilyShowExcluded
            findItem(R.id.stop_showing_excluded).isVisible = config.temporarilyShowExcluded
        }
    }

    private fun setupOptionsMenu() {
        val menuId = if (mIsThirdPartyIntent) {
            R.menu.menu_main_intent
        } else {
            R.menu.menu_main
        }

        main_menu.getToolbar().inflateMenu(menuId)
        main_menu.toggleHideOnScroll(!config.scrollHorizontally)
        main_menu.setupMenu()

        main_menu.onSearchOpenListener = {
            if (config.searchAllFilesByDefault) {
                launchSearchActivity()
            }
        }

        main_menu.onSearchTextChangedListener = { text ->
            setupAdapter("main_menu.onSearchTextChangedListener", mDirsIgnoringSearch, text)
            directories_refresh_layout.isEnabled = text.isEmpty() && config.enablePullToRefresh
            directories_switch_searching.beVisibleIf(text.isNotEmpty())
        }

        main_menu.getToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterMediaDialog()
                R.id.open_camera -> launchCamera()
                R.id.show_all -> showAllMedia()
                R.id.change_view_type -> changeViewType()
                R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                R.id.temporarily_show_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.stop_showing_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.create_new_folder -> createNewFolder()
                R.id.open_recycle_bin -> openRecycleBin()
                R.id.column_count -> changeColumnCount()
                R.id.set_as_default_folder -> setAsDefaultFolder()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(WAS_PROTECTION_HANDLED, mWasProtectionHandled)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mWasProtectionHandled = savedInstanceState.getBoolean(WAS_PROTECTION_HANDLED, false)
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
        main_menu.updateColors()
    }

    private fun getRecyclerAdapter() = directories_grid.adapter as? DirectoryAdapter

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredStyleString = "$folderStyle$showFolderMediaCount$limitFolderTitle"
        }
    }

    private fun startNewPhotoFetcher() {
        if (isNougatPlus()) {
            val photoFetcher = NewPhotoFetcher()
            if (!photoFetcher.isScheduled(applicationContext)) {
                photoFetcher.scheduleJob(applicationContext)
            }
        }
    }

    private fun removeTempFolder() {
        if (config.tempFolderPath.isNotEmpty()) {
            val newFolder = File(config.tempFolderPath)
            if (getDoesFilePathExist(newFolder.absolutePath) && newFolder.isDirectory) {
                if (newFolder.getProperSize(true) == 0L && newFolder.getFileCount(true) == 0 && newFolder.list()?.isEmpty() == true) {
                    toast(String.format(getString(R.string.deleting_folder), config.tempFolderPath), Toast.LENGTH_LONG)
                    tryDeleteFileDirItem(newFolder.toFileDirItem(applicationContext), true, true)
                }
            }
            config.tempFolderPath = ""
        }
    }

    private fun checkOTGPath() {
        ensureBackgroundThread {
            if (!config.wasOTGHandled && hasPermission(getPermissionToRequest()) && hasOTGConnected() && config.OTGPath.isEmpty()) {
                getStorageDirectories().firstOrNull { it.trimEnd('/') != internalStoragePath && it.trimEnd('/') != sdCardPath }?.apply {
                    config.wasOTGHandled = true
                    val otgPath = trimEnd('/')
                    config.OTGPath = otgPath
                    config.addIncludedFolder(otgPath)
                }
            }
        }
    }

    private fun checkDefaultSpamFolders() {
        if (!config.spamFoldersChecked) {
            val spamFolders = arrayListOf(
                "/storage/emulated/0/Android/data/com.facebook.orca/files/stickers"
            )

            val OTGPath = config.OTGPath
            spamFolders.forEach {
                if (getDoesFilePathExist(it, OTGPath)) {
                    config.addExcludedFolder(it)
                }
            }
            config.spamFoldersChecked = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun tryLoadGallery() {
        // avoid calling anything right after granting the permission, it will be called from onResume()
        val wasMissingPermission = config.appRunCount == 1 && !hasPermission(getPermissionToRequest())
        handleMediaPermissions { success ->
            if (success) {
                if (wasMissingPermission) {
                    return@handleMediaPermissions
                }

                if (!mWasDefaultFolderChecked) {
                    openDefaultFolder()
                    mWasDefaultFolderChecked = true
                }

                if (!mWasUpgradedFromFreeShown && isPackageInstalled("com.simplemobiletools.gallery")) {
                    ConfirmationDialog(this, "", R.string.upgraded_from_free, R.string.ok, 0, false) {}
                    mWasUpgradedFromFreeShown = true
                }

                checkOTGPath()
                checkDefaultSpamFolders()

                if (config.showAll) {
                    showAllMedia()
                } else {
                    getDirectories()
                }

                setupLayoutManager()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @RequiresApi(Build.VERSION_CODES.N)
    private fun getDirectories() {
        if (mIsGettingDirs) {
            return
        }
        val throwable = Throwable()
        Log.i("kiet", "getDirectories called\n${throwable.stackTraceToString()}")

        mShouldStopFetching = true
        mIsGettingDirs = true
        val getImagesOnly = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideosOnly = mIsPickVideoIntent || mIsGetVideoContentIntent

        getCachedDirectories(getVideosOnly, getImagesOnly) {
            val time = measureTime {
                gotDirectories(addTempFolderIfNeeded(it))
            }
            KietLog.i("gotDirectories(addTempFolderIfNeeded(it)) time: $time")
        }
    }

    private fun launchSearchActivity() {
        hideKeyboard()
        Intent(this, SearchActivity::class.java).apply {
            startActivity(this)
        }

        main_menu.postDelayed({
            main_menu.closeSearch()
        }, 500)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun showSortingDialog() {
        ChangeSortingDialog(this, true, false) {
            directories_grid.adapter = null
            if (config.directorySorting has SORT_BY_DATE_MODIFIED || config.directorySorting has SORT_BY_DATE_TAKEN) {
                getDirectories()
            } else {
                ensureBackgroundThread {
                    gotDirectories(getCurrentlyDisplayedDirs())
                }
            }

            getRecyclerAdapter()?.directorySorting = config.directorySorting
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mShouldStopFetching = true
            directories_refresh_layout.isRefreshing = true
            directories_grid.adapter = null
            getDirectories()
        }
    }

    private fun showAllMedia() {
        config.showAll = true
        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, "")

            if (mIsThirdPartyIntent) {
                handleMediaIntent(this)
            } else {
                hideKeyboard()
                startActivity(this)
                finish()
            }
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, true) {
            refreshMenuItems()
            setupLayoutManager()
            directories_grid.adapter = null
            setupAdapter(
                "changeViewType",
                getRecyclerAdapter()?.dirs
                    ?: mDirs
            )
        }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                handleHiddenFolderPasswordProtection {
                    toggleTemporarilyShowHidden(true)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun toggleTemporarilyShowHidden(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowHidden = show
        directories_grid.adapter = null
        getDirectories()
        refreshMenuItems()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun tryToggleTemporarilyShowExcluded() {
        if (config.temporarilyShowExcluded) {
            toggleTemporarilyShowExcluded(false)
        } else {
            handleExcludedFolderPasswordProtection {
                toggleTemporarilyShowExcluded(true)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun toggleTemporarilyShowExcluded(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowExcluded = show
        directories_grid.adapter = null
        getDirectories()
        refreshMenuItems()
    }

    override fun deleteFolders(folders: ArrayList<File>) {
        val fileDirItems =
            folders.asSequence().filter { it.isDirectory }.map { FileDirItem(it.absolutePath, it.name, true) }.toMutableList() as ArrayList<FileDirItem>
        when {
            fileDirItems.isEmpty() -> return
            fileDirItems.size == 1 -> {
                try {
                    toast(String.format(getString(R.string.deleting_folder), fileDirItems.first().name))
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> {
                val baseString = if (config.useRecycleBin) R.plurals.moving_items_into_bin else R.plurals.delete_items
                val deletingItems = resources.getQuantityString(baseString, fileDirItems.size, fileDirItems.size)
                toast(deletingItems)
            }
        }

        val itemsToDelete = ArrayList<FileDirItem>()
        val filter = config.filterMedia
        val showHidden = config.shouldShowHidden
        fileDirItems.filter { it.isDirectory }.forEach { it ->
            val files = File(it.path).listFiles()
            files?.filter {
                it.absolutePath.isMediaFile() && (showHidden || !it.name.startsWith('.')) &&
                    ((it.isImageFast() && filter.has(TYPE_IMAGES)) ||
                        (it.isVideoFast() && filter.has(TYPE_VIDEOS)) ||
                        (it.isGif() && filter.has(TYPE_GIFS)) ||
                        (it.isRawFast() && filter.has(TYPE_RAWS)) ||
                        (it.isSvg() && filter.has(TYPE_SVGS)))
            }?.mapTo(itemsToDelete) { it.toFileDirItem(applicationContext) }
        }

        if (config.useRecycleBin) {
            val pathsToDelete = ArrayList<String>()
            itemsToDelete.mapTo(pathsToDelete) { it.path }

            movePathsInRecycleBin(pathsToDelete) {
                if (it) {
                    deleteFilteredFileDirItems(itemsToDelete, folders)
                } else {
                    toast(R.string.unknown_error_occurred)
                }
            }
        } else {
            deleteFilteredFileDirItems(itemsToDelete, folders)
        }
    }

    private fun deleteFilteredFileDirItems(fileDirItems: ArrayList<FileDirItem>, folders: ArrayList<File>) {
        val OTGPath = config.OTGPath
        deleteFiles(fileDirItems) {
            runOnUiThread {
                refreshItems()
            }

            ensureBackgroundThread {
                folders.asSequence().filter { !getDoesFilePathExist(it.absolutePath, OTGPath) }.forEach {
                    directoryDB.deleteDirPath(it.absolutePath)
                }

                if (config.deleteEmptyFolders) {
                    folders.asSequence()
                        .filter { !it.absolutePath.isDownloadsFolder() && it.isDirectory && it.toFileDirItem(this).getProperFileCount(this, true) == 0 }
                        .forEach {
                            tryDeleteFileDirItem(it.toFileDirItem(this), true, true)
                        }
                }
            }
        }
    }

    private fun setupLayoutManager() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }

        (directories_refresh_layout.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.BELOW, R.id.directories_switch_searching)
    }

    private fun setupGridLayoutManager() {
        val layoutManager = directories_grid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            directories_refresh_layout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            directories_refresh_layout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        layoutManager.spanCount = config.dirColumnCnt
    }

    private fun setupListLayoutManager() {
        val layoutManager = directories_grid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        directories_refresh_layout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mZoomListener = null
    }

    private fun initZoomListener() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            val layoutManager = directories_grid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun createNewFolder() {
        FilePickerDialog(this, internalStoragePath, false, config.shouldShowHidden, false, true) { it ->
            CreateNewFolderDialog(this, it) {
                config.tempFolderPath = it
                ensureBackgroundThread {
                    gotDirectories(addTempFolderIfNeeded(getCurrentlyDisplayedDirs()))
                }
            }
        }
    }

    private fun changeColumnCount() {
        val items = (1..MAX_COLUMN_COUNT).map { i -> RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)) }

        val currentColumnCount = (directories_grid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.dirColumnCnt = newColumnCount
                columnCountChanged()
            }
        }
    }

    private fun increaseColumnCount() {
        config.dirColumnCnt += 1
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.dirColumnCnt -= 1
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (directories_grid.layoutManager as MyGridLayoutManager).spanCount = config.dirColumnCnt
        refreshMenuItems()
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, dirs.size)
        }
    }

    private fun isPickImageIntent(intent: Intent) = isPickIntent(intent) && (hasImageContentData(intent) || isImageType(intent))

    private fun isPickVideoIntent(intent: Intent) = isPickIntent(intent) && (hasVideoContentData(intent) || isVideoType(intent))

    private fun isPickIntent(intent: Intent) = intent.action == Intent.ACTION_PICK

    private fun isGetContentIntent(intent: Intent) = intent.action == Intent.ACTION_GET_CONTENT && intent.type != null

    private fun isGetImageContentIntent(intent: Intent) = isGetContentIntent(intent) &&
        (intent.type!!.startsWith("image/") || intent.type == Images.Media.CONTENT_TYPE)

    private fun isGetVideoContentIntent(intent: Intent) = isGetContentIntent(intent) &&
        (intent.type!!.startsWith("video/") || intent.type == Video.Media.CONTENT_TYPE)

    private fun isGetAnyContentIntent(intent: Intent) = isGetContentIntent(intent) && intent.type == "*/*"

    private fun isSetWallpaperIntent(intent: Intent?) = intent?.action == Intent.ACTION_SET_WALLPAPER

    private fun hasImageContentData(intent: Intent) = (intent.data == Images.Media.EXTERNAL_CONTENT_URI ||
        intent.data == Images.Media.INTERNAL_CONTENT_URI)

    private fun hasVideoContentData(intent: Intent) = (intent.data == Video.Media.EXTERNAL_CONTENT_URI ||
        intent.data == Video.Media.INTERNAL_CONTENT_URI)

    private fun isImageType(intent: Intent) = (intent.type?.startsWith("image/") == true || intent.type == Images.Media.CONTENT_TYPE)

    private fun isVideoType(intent: Intent) = (intent.type?.startsWith("video/") == true || intent.type == Video.Media.CONTENT_TYPE)

    private fun fillExtraOutput(resultData: Intent): Uri? {
        val file = File(resultData.data!!.path!!)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val output = intent.extras!!.get(MediaStore.EXTRA_OUTPUT) as Uri
            inputStream = FileInputStream(file)
            outputStream = contentResolver.openOutputStream(output)
            inputStream.copyTo(outputStream!!)
        } catch (e: SecurityException) {
            showErrorToast(e)
        } catch (ignored: FileNotFoundException) {
            return getFilePublicUri(file, BuildConfig.APPLICATION_ID)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }

        return null
    }

    private fun fillPickedPaths(resultData: Intent, resultIntent: Intent) {
        val paths = resultData.extras!!.getStringArrayList(PICKED_PATHS)
        val uris = paths!!.map { getFilePublicUri(File(it), BuildConfig.APPLICATION_ID) } as ArrayList
        val clipData = ClipData("Attachment", arrayOf("image/*", "video/*"), ClipData.Item(uris.removeAt(0)))

        uris.forEach {
            clipData.addItem(ClipData.Item(it))
        }

        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resultIntent.clipData = clipData
    }

    private fun fillIntentPath(resultData: Intent, resultIntent: Intent) {
        val data = resultData.data
        val path = if (data.toString().startsWith("/")) data.toString() else data!!.path
        val uri = getFilePublicUri(File(path!!), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        resultIntent.setDataAndTypeAndNormalize(uri, type)
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun itemClicked(path: String) {
        handleLockedFolderOpening(path) { success ->
            if (success) {
                Intent(this, MediaActivity::class.java).apply {
                    putExtra(SKIP_AUTHENTICATION, true)
                    putExtra(DIRECTORY, path)
                    handleMediaIntent(this)
                }
            }
        }
    }

    private fun handleMediaIntent(intent: Intent) {
        hideKeyboard()
        intent.apply {
            if (mIsSetWallpaperIntent) {
                putExtra(SET_WALLPAPER_INTENT, true)
                startActivityForResult(this, Companion.PICK_WALLPAPER)
            } else {
                putExtra(GET_IMAGE_INTENT, mIsPickImageIntent || mIsGetImageContentIntent)
                putExtra(GET_VIDEO_INTENT, mIsPickVideoIntent || mIsGetVideoContentIntent)
                putExtra(GET_ANY_INTENT, mIsGetAnyContentIntent)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mAllowPickingMultiple)
                startActivityForResult(this, Companion.PICK_MEDIA)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalTime::class)
    @RequiresApi(Build.VERSION_CODES.N)
    private fun gotDirectories(newDirs: List<Directory>) {
        //        val throwable = Throwable()
        //        Log.i("kiet", "gotDirectories called\n${throwable.stackTraceToString()}")
        mIsGettingDirs = false
        mShouldStopFetching = false

        var dirs: MutableList<Directory> = getSortedDirectories(newDirs).toMutableList().apply {
            // if hidden item showing is disabled but all Favorite items are hidden, hide the Favorites folder
            if (!config.shouldShowHidden) {
                val favoritesFolder = newDirs.firstOrNull { it.areFavorites() }
                if (favoritesFolder != null && favoritesFolder.tmb.getFilenameFromPath().startsWith('.')) {
                    removeIf { it == favoritesFolder }
                }
            }

            val hasRecycleBin = any { it.path == RECYCLE_BIN }
            if (config.showRecycleBinAtFolders && !config.showRecycleBinLast && !hasRecycleBin) {
                if (mediaDB.getDeletedMediaCount() > 0) {
                    val recycleBin = Directory().apply {
                        path = RECYCLE_BIN
                        name = getString(R.string.recycle_bin)
                        location = LOCATION_INTERNAL
                    }
                    add(0, recycleBin)
                }
            }
            val hasFavorites = any { it.path == FAVORITES }
            if (hasFavorites) {
                if (mediaDB.getFavoritesCount() > 0) {
                    val favorites = Directory().apply {
                        path = FAVORITES
                        name = getString(R.string.favorites)
                        location = LOCATION_INTERNAL
                    }
                    add(0, favorites)
                }
            }
        }

        if (config.groupDirectSubfolders) {
            mDirs = dirs
        }

        var isPlaceholderVisible = dirs.isEmpty()

        val initialSetupTime = measureTime {
            checkPlaceholderVisibility(dirs)
            setupAdapter("gotDirectories 1", dirs)
        }
        KietLog.i("initialSetupTime: $initialSetupTime")

        // cached folders have been loaded, recheck folders one by one starting with the first displayed
        mLastMediaFetcher?.shouldStop = true
        mLastMediaFetcher = MediaFetcher(applicationContext)
        val getImagesOnly = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideosOnly = mIsPickVideoIntent || mIsGetVideoContentIntent
        val favoritePaths = getFavoritePaths()
        val hiddenString = getString(R.string.hidden)
        val albumCovers = config.parseAlbumCovers()
        val includedFolders = config.includedFolders
        val noMediaFolders = getNoMediaFoldersSync()
        val tempFolderPath = config.tempFolderPath
        val getProperFileSize = config.directorySorting has SORT_BY_SIZE
        val lastModifieds = mLastMediaFetcher!!.getLastModifieds()
        val dateTakens = mLastMediaFetcher!!.getDateTakens()

        // fetch files from MediaStore only, unless the app has the MANAGE_EXTERNAL_STORAGE permission on Android 11+
        val android11Files = mLastMediaFetcher?.getAndroid11FolderMedia(
            getImagesOnly, getVideosOnly, favoritePaths,
            getFavoritePathsOnly = false,
            getProperDateTaken = true,
            dateTakenss = dateTakens
        )

        val dirPathsToRemove = mutableSetOf<String>()
        val block1 = measureTime {
            var aggregateCurMediaTime = Duration.ZERO
            for (directory in dirs) {
                if (mShouldStopFetching || isDestroyed || isFinishing) {
                    return
                }

                val sorting = config.getFolderSorting(directory.path)
                val grouping = config.getFolderGrouping(directory.path)
                val getProperDateTaken = config.directorySorting has SORT_BY_DATE_TAKEN ||
                    sorting.has(SORT_BY_DATE_TAKEN) ||
                    grouping.has(GROUP_BY_DATE_TAKEN_DAILY) ||
                    grouping.has(GROUP_BY_DATE_TAKEN_MONTHLY)
                val getProperLastModified = config.directorySorting has SORT_BY_DATE_MODIFIED ||
                    sorting.has(SORT_BY_DATE_MODIFIED) ||
                    grouping.has(GROUP_BY_LAST_MODIFIED_DAILY) ||
                    grouping.has(GROUP_BY_LAST_MODIFIED_MONTHLY)

                val curMediaTimedValue = measureTimedValue {  mLastMediaFetcher!!.getFilesFrom(
                    directory.path, getImagesOnly, getVideosOnly, getProperDateTaken, getProperLastModified,
                    getProperFileSize, favoritePaths, false, lastModifieds, dateTakens, android11Files
                ) }
                aggregateCurMediaTime += curMediaTimedValue.duration

                val curMedia = curMediaTimedValue.value

                val newDir = if (curMedia.isEmpty()) {
                    if (directory.path != tempFolderPath) {
                        dirPathsToRemove.add(directory.path)
                    }
                    directory
                } else {
                    createDirectoryFromMedia(directory.path, curMedia, albumCovers, hiddenString, includedFolders, getProperFileSize, noMediaFolders)
                }

                // we are looping through the already displayed folders looking for changes, do not do anything if nothing changed
                if (directory.copy(subfoldersCount = 0, subfoldersMediaCount = 0) == newDir) {
                    continue
                }

                directory.apply {
                    tmb = newDir.tmb
                    name = newDir.name
                    mediaCnt = newDir.mediaCnt
                    modified = newDir.modified
                    taken = newDir.taken
                    this@apply.size = newDir.size
                    _types = newDir._types
                    sortValue = getDirectorySortingValue(curMedia, path, name, size)
                }

                // update directories and media files in the local db, delete invalid items. Intentionally creating a new thread
                updateDBDirectory(directory)
                if (!directory.isRecycleBin() && !directory.areFavorites()) {
                    GlobalScope.launch(Dispatchers.IO) {
                        mediaDB.insertAll(curMedia)
                    }
                }

                if (!directory.isRecycleBin()) {
                    // Already optimized
                    val getCachedMediaTime = measureTime {
                        getCachedMedia(directory.path, getVideosOnly, getImagesOnly) { it ->
                            val mediaToDelete = mutableListOf<Medium>()
                            it.forEach {
                                if (!curMedia.contains(it)) {
                                    val medium = it as? Medium
                                    val path = medium?.path
                                    if (path != null) {
                                        mediaToDelete.add(medium)
                                    }
                                }
                            }
                            mediaDB.deleteMedia(*mediaToDelete.toTypedArray())
                        }
                    }
                    KietLog.i("getCachedMediaTime: $getCachedMediaTime")
                }
            }

            KietLog.i("aggregateCurMediaTime: $aggregateCurMediaTime")

            if (dirPathsToRemove.isNotEmpty()) {
                val dirsToRemove = dirs.asSequence().filter { dirPathsToRemove.contains(it.path) }.toSet()
                dirsToRemove.forEach {
                    directoryDB.deleteDirPath(it.path)
                }
                dirs.removeAll(dirsToRemove)
            }
        }
        KietLog.i("block1 time: $block1")

        val scanBlock = measureTime {
            val foldersToScan: List<String> = mLastMediaFetcher!!.getFoldersToScan().apply {
                remove(FAVORITES)
                add(0, FAVORITES)
                remove(RECYCLE_BIN)

                if (config.showRecycleBinAtFolders) {
                    add(0, RECYCLE_BIN)
                }

                // Remove the folders that we've already scanned?
                dirs.asSequence().filterNot { it.path == RECYCLE_BIN || it.path == FAVORITES }.forEach {
                    remove(it.path)
                }
            }

            // check the remaining folders which were not cached at all yet
            for (folder in foldersToScan) {
                if (mShouldStopFetching || isDestroyed || isFinishing) {
                    return
                }

                val sorting = config.getFolderSorting(folder)
                val grouping = config.getFolderGrouping(folder)
                val getProperDateTaken = config.directorySorting has SORT_BY_DATE_TAKEN ||
                    sorting.has(SORT_BY_DATE_TAKEN) ||
                    grouping.has(GROUP_BY_DATE_TAKEN_DAILY) ||
                    grouping.has(GROUP_BY_DATE_TAKEN_MONTHLY)

                val getProperLastModified = config.directorySorting has SORT_BY_DATE_MODIFIED ||
                    sorting.has(SORT_BY_DATE_MODIFIED) ||
                    grouping.has(GROUP_BY_LAST_MODIFIED_DAILY) ||
                    grouping.has(GROUP_BY_LAST_MODIFIED_MONTHLY)

                val newMedia = mLastMediaFetcher!!.getFilesFrom(
                    folder, getImagesOnly, getVideosOnly, getProperDateTaken, getProperLastModified,
                    getProperFileSize, favoritePaths, false, lastModifieds, dateTakens, android11Files
                )

                if (newMedia.isEmpty()) {
                    continue
                }

                if (isPlaceholderVisible) {
                    isPlaceholderVisible = false
                    runOnUiThread {
                        directories_empty_placeholder.beGone()
                        directories_empty_placeholder_2.beGone()
                        directories_fastscroller.beVisible()
                    }
                }

                val newDir = createDirectoryFromMedia(folder, newMedia, albumCovers, hiddenString, includedFolders, getProperFileSize, noMediaFolders)
                dirs += newDir

                // make sure to create a new thread for these operations, dont just use the common bg thread
                GlobalScope.launch(Dispatchers.IO) {
                    directoryDB.insert(newDir)
                    if (folder != RECYCLE_BIN && folder != FAVORITES) {
                        mediaDB.insertAll(newMedia)
                    }
                }
            }
        }
        KietLog.i("scanBlock time: $block1")


        mLoadedInitialPhotos = true
        if (config.appRunCount > 1) {
            checkLastMediaChanged()
        }

        runOnUiThread {
            directories_refresh_layout.isRefreshing = false
            checkPlaceholderVisibility(dirs)
        }

        val invalidDirs = calculateInvalidDirs(dirs)
        if (invalidDirs.isNotEmpty()) {
            dirs -= invalidDirs
            invalidDirs.forEach {
                try {
                    directoryDB.deleteDirPath(it.path)
                } catch (ignored: Exception) {
                    Log.e("kiet", "Exception: $ignored")
                }
            }
        }

        if (mDirs.size > 50) {
            val time = measureTime {
                excludeSpamFolders()
            }
            KietLog.i("excludeSpamFolders time: $time")
        }

        setupAdapter("gotDirectories 2", dirs)

        val excludedFolders = config.excludedFolders
        val everShownFolders = config.everShownFolders.toMutableSet()

        // do not add excluded folders and their subfolders at everShownFolders
        dirs.asSequence().filter { dir ->
            return@filter !excludedFolders.any { dir.path.startsWith(it) }
        }.mapTo(everShownFolders) { it.path }

        try {
            // scan the internal storage from time to time for new folders
            if (config.appRunCount == 1 || config.appRunCount % 30 == 0) {
                everShownFolders.addAll(getFoldersWithMedia(config.internalStoragePath))
            }

            // catch some extreme exceptions like too many everShownFolders for storing, shouldnt really happen
            config.everShownFolders = everShownFolders
        } catch (e: Exception) {
            config.everShownFolders = HashSet()
        }

        mDirs = dirs
    }

    private fun setAsDefaultFolder() {
        config.defaultFolder = ""
        refreshMenuItems()
    }

    private fun openDefaultFolder() {
        if (config.defaultFolder.isEmpty()) {
            return
        }

        val defaultDir = File(config.defaultFolder)

        if ((!defaultDir.exists() || !defaultDir.isDirectory) && (config.defaultFolder != RECYCLE_BIN && config.defaultFolder != FAVORITES)) {
            config.defaultFolder = ""
            return
        }

        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, config.defaultFolder)
            handleMediaIntent(this)
        }
    }

    private fun checkPlaceholderVisibility(dirs: List<Directory>) {
        directories_empty_placeholder.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)
        directories_empty_placeholder_2.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)

        if (main_menu.isSearchOpen) {
            directories_empty_placeholder.text = getString(R.string.no_items_found)
            directories_empty_placeholder_2.beGone()
        } else if (dirs.isEmpty() && config.filterMedia == getDefaultFileFilter()) {
            if (isRPlus() && !isExternalStorageManager()) {
                directories_empty_placeholder.text = getString(R.string.no_items_found)
                directories_empty_placeholder_2.beGone()
            } else {
                directories_empty_placeholder.text = getString(R.string.no_media_add_included)
                directories_empty_placeholder_2.text = getString(R.string.add_folder)
            }

            directories_empty_placeholder_2.setOnClickListener {
                showAddIncludedFolderDialog {
                    refreshItems()
                }
            }
        } else {
            directories_empty_placeholder.text = getString(R.string.no_media_with_filters)
            directories_empty_placeholder_2.text = getString(R.string.change_filters_underlined)

            directories_empty_placeholder_2.setOnClickListener {
                showFilterMediaDialog()
            }
        }

        directories_empty_placeholder_2.underlineText()
        directories_fastscroller.beVisibleIf(directories_empty_placeholder.isGone())
    }

    @OptIn(ExperimentalTime::class)
    private fun setupAdapter(caller: String, dirs: List<Directory>, textToSearch: String = main_menu.getCurrentQuery(), forceRecreate: Boolean = false) {
        val throwable = Throwable()
        Log.i("kiet", "setupAdapter called from $caller\n${throwable.stackTraceToString()}")
        val currAdapter = directories_grid.adapter
        val dirsToShowTimedValue = measureTimedValue {
            val distinctDirs = dirs.distinctBy { it.path.getDistinctPath() }
            val sortedDirs = getSortedDirectories(distinctDirs)
            getDirsToShow(sortedDirs, mDirs, mCurrentPathPrefix)
        }
        KietLog.i("Time for dirsToShowTimedValue: ${dirsToShowTimedValue.duration}")
        var dirsToShow = dirsToShowTimedValue.value

        if (currAdapter == null || forceRecreate) {
            mDirsIgnoringSearch = dirs
            initZoomListener()
            DirectoryAdapter(
                this,
                dirsToShow.toMutableList(),
                this,
                directories_grid,
                isPickIntent(intent) || isGetAnyContentIntent(intent),
                directories_refresh_layout
            ) {
                val clickedDir = it as Directory
                val path = clickedDir.path
                if (clickedDir.subfoldersCount == 1 || !config.groupDirectSubfolders) {
                    if (path != config.tempFolderPath) {
                        itemClicked(path)
                    }
                } else {
                    mCurrentPathPrefix = path
                    mOpenedSubfolders.add(path)
                    setupAdapter("setupAdapter", mDirs, "")
                }
            }.apply {
                setupZoomListener(mZoomListener)
                runOnUiThread {
                    directories_grid.adapter = this
                    setupScrollDirection()

                    if (config.viewTypeFolders == VIEW_TYPE_LIST && areSystemAnimationsEnabled) {
                        directories_grid.scheduleLayoutAnimation()
                    }
                }
            }
        } else {
            runOnUiThread {
                if (textToSearch.isNotEmpty()) {
                    dirsToShow = dirsToShow.asSequence().filter { it.name.contains(textToSearch, true) }.sortedBy { !it.name.startsWith(textToSearch, true) }
                        .toList()
                }
                checkPlaceholderVisibility(dirsToShow)

                (directories_grid.adapter as? DirectoryAdapter)?.updateDirs(dirsToShow)
            }
        }

        // recyclerview sometimes becomes empty at init/update, triggering an invisible refresh like this seems to work fine
        //        directories_grid.postDelayed({
        //            directories_grid.scrollBy(0, 0)
        //        }, 500)
    }

    private fun setupScrollDirection() {
        val scrollHorizontally = config.scrollHorizontally && config.viewTypeFolders == VIEW_TYPE_GRID
        directories_fastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun calculateInvalidDirs(dirs: List<Directory>): Set<Directory> {
        val invalidDirs = mutableSetOf<Directory>()
        val OTGPath = config.OTGPath
        dirs.filter { !it.areFavorites() && !it.isRecycleBin() }.forEach { it ->
            if (!getDoesFilePathExist(it.path, OTGPath)) {
                invalidDirs.add(it)
            } else if (it.path != config.tempFolderPath && (!isRPlus() || isExternalStorageManager())) {
                // avoid calling file.list() or listfiles() on Android 11+, it became way too slow
                val children = if (isPathOnOTG(it.path)) {
                    getOTGFolderChildrenNames(it.path)
                } else {
                    File(it.path).list()?.asList()
                }

                val hasMediaFile = children?.any {
                    it != null && (it.isMediaFile() || (it.startsWith("img_", true) && File(it).isDirectory))
                }
                    ?: false

                if (!hasMediaFile) {
                    invalidDirs.add(it)
                }
            }
        }

        if (getFavoritePaths().isEmpty()) {
            val favoritesFolder = dirs.firstOrNull { it.areFavorites() }
            if (favoritesFolder != null) {
                invalidDirs.add(favoritesFolder)
            }
        }

        if (config.useRecycleBin) {
            val binFolder = dirs.firstOrNull { it.path == RECYCLE_BIN }
            if (binFolder != null && mediaDB.getDeletedMedia().isEmpty()) {
                invalidDirs.add(binFolder)
            }
        }

        return invalidDirs
    }

    private fun getCurrentlyDisplayedDirs() = getRecyclerAdapter()?.dirs
        ?: emptyList()

    private fun setupLatestMediaId() {
        if (hasPermission(PERMISSION_READ_STORAGE)) {
            mLatestMediaId = getLatestMediaId()
            mLatestMediaDateId = getLatestMediaByDateId()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun checkLastMediaChanged() {
        if (isDestroyed) {
            return
        }

        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getDirectories()
                    }
                } else {
                    mLastMediaHandler.removeCallbacksAndMessages(null)
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun checkRecycleBinItems() {
        if (config.useRecycleBin && config.lastBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000) {
            config.lastBinCheck = System.currentTimeMillis()
            Handler().postDelayed({
                ensureBackgroundThread {
                    try {
                        val filesToDelete = mediaDB.getOldRecycleBinItems(System.currentTimeMillis() - MONTH_MILLISECONDS)
                        filesToDelete.forEach {
                            if (File(it.path.replaceFirst(RECYCLE_BIN, recycleBinPath)).delete()) {
                                mediaDB.deleteMediumPath(it.path)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }, 3000L)
        }
    }

    // exclude probably unwanted folders, for example facebook stickers are split between hundreds of separate folders like
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/175139712676531/209575122566323
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/497837993632037/499671223448714
    private fun excludeSpamFolders() {
        ensureBackgroundThread {
            try {
                val internalPath = internalStoragePath
                val checkedPaths = mutableSetOf<String>()
                val oftenRepeatedPaths = mutableSetOf<String>()
                val paths = mDirs.map { it.path.removePrefix(internalPath) }
                paths.forEach { it ->
                    val parts = it.split("/")
                    var currentString = ""
                    for (element in parts) {
                        currentString += "${element}/"

                        if (!checkedPaths.contains(currentString)) {
                            val cnt = paths.count { it.startsWith(currentString) }
                            if (cnt > 50 && currentString.startsWith("/Android/data", true)) {
                                oftenRepeatedPaths.add(currentString)
                            }
                        }

                        checkedPaths.add(currentString)
                    }
                }

                val substringToRemove = oftenRepeatedPaths.asSequence().filter { it ->
                    it == "/" || oftenRepeatedPaths.any { it != it && it.startsWith(it) }
                }.toSet()

                val OTGPath = config.OTGPath
                oftenRepeatedPaths.removeAll(substringToRemove)
                oftenRepeatedPaths.forEach {
                    val file = File("$internalPath/$it")
                    if (getDoesFilePathExist(file.absolutePath, OTGPath)) {
                        config.addExcludedFolder(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
                KietLog.e("Ignore exception: $e")
            }
        }
    }

    private fun getFoldersWithMedia(path: String): HashSet<String> {
        val folders = HashSet<String>()
        try {
            val files = File(path).listFiles()
            if (files != null) {
                files.sortBy { !it.isDirectory }
                for (file in files) {
                    if (file.isDirectory && !file.startsWith("${config.internalStoragePath}/Android")) {
                        folders.addAll(getFoldersWithMedia(file.absolutePath))
                    } else if (file.isFile && file.isMediaFile()) {
                        folders.add(
                            file.parent
                                ?: ""
                        )
                        break
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        return folders
    }

    override fun refreshItems() {
        getDirectories()
    }

    override fun recheckPinnedFolders() {
        ensureBackgroundThread {
            gotDirectories(movePinnedDirectoriesToFront(getCurrentlyDisplayedDirs()))
        }
    }

    override fun updateDirectories(directories: List<Directory>) {
        ensureBackgroundThread {
            storeDirectoryItems(directories)
            removeInvalidDBDirectories()
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(213, R.string.release_213))
            add(Release(217, R.string.release_217))
            add(Release(220, R.string.release_220))
            add(Release(221, R.string.release_221))
            add(Release(225, R.string.release_225))
            add(Release(258, R.string.release_258))
            add(Release(277, R.string.release_277))
            add(Release(295, R.string.release_295))
            add(Release(327, R.string.release_327))
            add(Release(369, R.string.release_369))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    companion object {
        private val PICK_MEDIA = 2
        private val PICK_WALLPAPER = 3
        private val LAST_MEDIA_CHECK_PERIOD = 3000L
    }
}
