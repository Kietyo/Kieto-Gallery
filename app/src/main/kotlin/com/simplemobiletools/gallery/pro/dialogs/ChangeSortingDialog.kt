package com.simplemobiletools.gallery.pro.dialogs

import android.content.DialogInterface
import android.view.View
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.PackedInt
import com.simplemobiletools.commons.models.toPackedInt
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.extensions.config
import com.simplemobiletools.gallery.pro.helpers.SHOW_ALL
import kotlinx.android.synthetic.main.dialog_change_sorting.view.*

class ChangeSortingDialog(
    val activity: BaseSimpleActivity, private val isDirectorySorting: Boolean, private val showFolderCheckbox: Boolean,
    val path: String = "", val callback: () -> Unit
) :
    DialogInterface.OnClickListener {
    private var currSorting = PackedInt(0)
    private var config = activity.config
    private var pathToUse = if (!isDirectorySorting && path.isEmpty()) SHOW_ALL else path
    private var view: View

    init {
        currSorting = if (isDirectorySorting) config.directorySorting else config.getFolderSorting(pathToUse)
        view = activity.layoutInflater.inflate(R.layout.dialog_change_sorting, null).apply {
            use_for_this_folder_divider.beVisibleIf(showFolderCheckbox || (currSorting has SORT_BY_NAME || currSorting has SORT_BY_PATH))

            sorting_dialog_numeric_sorting.beVisibleIf(showFolderCheckbox && (currSorting has SORT_BY_NAME || currSorting has SORT_BY_PATH))
            sorting_dialog_numeric_sorting.isChecked = currSorting has SORT_USE_NUMERIC_VALUE

            sorting_dialog_use_for_this_folder.beVisibleIf(showFolderCheckbox)
            sorting_dialog_use_for_this_folder.isChecked = config.hasCustomSorting(pathToUse)
            sorting_dialog_bottom_note.beVisibleIf(!isDirectorySorting)
            sorting_dialog_radio_custom.beVisibleIf(isDirectorySorting)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, this)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.sort_by)
            }

        setupSortRadio()
        setupOrderRadio()
    }

    private fun setupSortRadio() {
        val sortingRadio = view.sorting_dialog_radio_sorting
        sortingRadio.setOnCheckedChangeListener { group, checkedId ->
            val isSortingByNameOrPath = checkedId == sortingRadio.sorting_dialog_radio_name.id || checkedId == sortingRadio.sorting_dialog_radio_path.id
            view.sorting_dialog_numeric_sorting.beVisibleIf(isSortingByNameOrPath)
            view.use_for_this_folder_divider.beVisibleIf(view.sorting_dialog_numeric_sorting.isVisible() || view.sorting_dialog_use_for_this_folder.isVisible())

            val hideSortOrder = checkedId == sortingRadio.sorting_dialog_radio_custom.id || checkedId == sortingRadio.sorting_dialog_radio_random.id
            view.sorting_dialog_radio_order.beGoneIf(hideSortOrder)
            view.sorting_dialog_order_divider.beGoneIf(hideSortOrder)
        }

        val sortBtn = when {
            currSorting has SORT_BY_PATH -> sortingRadio.sorting_dialog_radio_path
            currSorting has SORT_BY_SIZE -> sortingRadio.sorting_dialog_radio_size
            currSorting has SORT_BY_DATE_MODIFIED -> sortingRadio.sorting_dialog_radio_last_modified
            currSorting has SORT_BY_DATE_TAKEN -> sortingRadio.sorting_dialog_radio_date_taken
            currSorting has SORT_BY_RANDOM -> sortingRadio.sorting_dialog_radio_random
            currSorting has SORT_BY_CUSTOM -> sortingRadio.sorting_dialog_radio_custom
            else -> sortingRadio.sorting_dialog_radio_name
        }
        sortBtn.isChecked = true
    }

    private fun setupOrderRadio() {
        val orderRadio = view.sorting_dialog_radio_order
        var orderBtn = orderRadio.sorting_dialog_radio_ascending

        if (currSorting has SORT_DESCENDING) {
            orderBtn = orderRadio.sorting_dialog_radio_descending
        }
        orderBtn.isChecked = true
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        val sortingRadio = view.sorting_dialog_radio_sorting
        var sorting = when (sortingRadio.checkedRadioButtonId) {
            R.id.sorting_dialog_radio_name -> SORT_BY_NAME
            R.id.sorting_dialog_radio_path -> SORT_BY_PATH
            R.id.sorting_dialog_radio_size -> SORT_BY_SIZE
            R.id.sorting_dialog_radio_last_modified -> SORT_BY_DATE_MODIFIED
            R.id.sorting_dialog_radio_random -> SORT_BY_RANDOM
            R.id.sorting_dialog_radio_custom -> SORT_BY_CUSTOM
            else -> SORT_BY_DATE_TAKEN
        }.toPackedInt()

        if (view.sorting_dialog_radio_order.checkedRadioButtonId == R.id.sorting_dialog_radio_descending) {
            sorting += SORT_DESCENDING
        }

        if (view.sorting_dialog_numeric_sorting.isChecked) {
            sorting += SORT_USE_NUMERIC_VALUE
        }

        if (isDirectorySorting) {
            config.directorySorting = sorting
        } else {
            if (view.sorting_dialog_use_for_this_folder.isChecked) {
                config.saveCustomSorting(pathToUse, sorting)
            } else {
                config.removeCustomSorting(pathToUse)
                config.sorting = sorting
            }
        }

        if (currSorting != sorting) {
            callback()
        }
    }
}
