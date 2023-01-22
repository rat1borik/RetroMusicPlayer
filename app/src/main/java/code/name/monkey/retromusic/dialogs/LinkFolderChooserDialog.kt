package code.name.monkey.retromusic.dialogs

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.EXTRA_PLAYLIST_ID
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.db.PlaylistEntity
import code.name.monkey.retromusic.extensions.extraNotNull
import code.name.monkey.retromusic.extensions.materialDialog
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.fragments.LibraryViewModel
import code.name.monkey.retromusic.util.getExternalStorageDirectory
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.updateListItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.io.File

class LinkFolderChooserDialog : DialogFragment() {
    private val libraryViewModel by sharedViewModel<LibraryViewModel>()

    private var initialPath: String = getExternalStorageDirectory().absolutePath
    private var parentFolder: File? = null
    private var parentContents: Array<File>? = null
    private var canGoUp = false
    private var callback: FolderCallback? = null
    private val contentsArray: Array<String?>
        get() {
            if (parentContents == null) {
                return if (canGoUp) {
                    arrayOf("..")
                } else arrayOf()
            }
            val results = arrayOfNulls<String>(parentContents!!.size + if (canGoUp) 1 else 0)
            if (canGoUp) {
                results[0] = ".."
            }
            for (i in parentContents!!.indices) {
                results[if (canGoUp) i + 1 else i] = parentContents?.getOrNull(i)?.name
            }
            return results
        }

    private fun listFiles(): Array<File>? {
        val results = mutableListOf<File>()
        parentFolder?.listFiles()?.let { files ->
            files.forEach { file -> if (file.isDirectory) results.add(file) }
            return results.sortedBy { it.name }.toTypedArray()
        }
        return null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val playlistEntity = extraNotNull<PlaylistEntity>(EXTRA_PLAYLIST_ID).value
        var mSavedInstanceState = savedInstanceState
        if (VersionUtils.hasMarshmallow()
            && ActivityCompat.checkSelfPermission(
                requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            return materialDialog().show {
                title(res = R.string.md_error_label)
                message(res = R.string.md_storage_perm_error)
                positiveButton(res = android.R.string.ok)
            }
        }
        if (mSavedInstanceState == null) {
            mSavedInstanceState = Bundle()
        }
        if (!mSavedInstanceState.containsKey("current_path")) {
            mSavedInstanceState.putString("current_path", initialPath)
        }
        parentFolder = File(mSavedInstanceState.getString("current_path", File.pathSeparator))
        checkIfCanGoUp()
        parentContents = listFiles()
        return materialDialog()
            .title(text = parentFolder?.absolutePath)
            .listItems(
                items = contentsArray.toCharSequence(),
                waitForPositiveButton = false
            ) { _: MaterialDialog, i: Int, _: CharSequence ->
                onSelection(i)
            }
            .noAutoDismiss()
            .positiveButton(res = R.string.add_action) {
                libraryViewModel.linkWithFolder(requireContext(), playlistEntity, parentFolder!!)

                dismiss()
            }
            .negativeButton(res = android.R.string.cancel) { dismiss() }
    }

    private fun onSelection(i: Int) {
        if (canGoUp && i == 0) {
            parentFolder = parentFolder?.parentFile
            if (parentFolder?.absolutePath == "/storage/emulated") {
                parentFolder = parentFolder?.parentFile
            }
            checkIfCanGoUp()
        } else {
            parentFolder = parentContents?.getOrNull(if (canGoUp) i - 1 else i)
            canGoUp = true
            if (parentFolder?.absolutePath == "/storage/emulated") {
                parentFolder = getExternalStorageDirectory()
            }
        }
        reload()
    }

    private fun checkIfCanGoUp() {
        canGoUp = parentFolder?.parent != null
    }

    private fun reload() {
        parentContents = listFiles()
        val dialog = dialog as MaterialDialog?
        dialog?.setTitle(parentFolder?.absolutePath)
        dialog?.updateListItems(items = contentsArray.toCharSequence())
    }

    private fun Array<String?>.toCharSequence(): List<CharSequence> {
        return map { it as CharSequence }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("current_path", parentFolder?.absolutePath)
    }

    fun setCallback(callback: FolderCallback?) {
        this.callback = callback
    }

    interface FolderCallback {
        fun onFolderSelection(context: Context, folder: File)
    }

    companion object {
        fun create(playlistEntity: PlaylistEntity): LinkFolderChooserDialog {
            Log.d("op", playlistEntity.toString())
            return LinkFolderChooserDialog().apply {
                arguments = bundleOf(
                    EXTRA_PLAYLIST_ID to playlistEntity
                )
            }
        }
    }
}