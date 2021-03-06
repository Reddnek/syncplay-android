package com.reddnek.syncplay.room

import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.reddnek.syncplay.R
import com.reddnek.syncplay.room.ExoPlayerUtils.injectVideo
import com.reddnek.syncplay.room.UIUtils.broadcastMessage
import com.reddnek.syncplay.room.UIUtils.toasty
import com.reddnek.syncplayprotocol.JsonSender.sendPlaylistChange
import com.reddnek.syncplayprotocol.JsonSender.sendPlaylistIndex
import com.reddnek.syncplayutils.SyncplayUtils.getFileName
import com.reddnek.syncplayutils.utils.MediaFile
import com.reddnek.syncplay.main.DirectoriesActivity as DA

/** Shared Playlists also have their fair amount of methods related to it which I believe can be
 * wrapped into their own class. This is to cler any ambiguity or confusion as it makes it hard to
 * understand the code if we keep everything in one place.
 */
object SharedPlaylistUtils {

    /** Adding a file to the playlist: This basically adds one file name to the playlist, then,
     * adds the parent directory to the known media directories, after that, it informs the server
     * about it. The server will send back the new playlist which will invoke playlist updating */
    @JvmStatic
    fun RoomActivity.addFileToPlaylist(uri: Uri) {
        Thread {
            if (p.session.sharedPlaylist.isEmpty() && p.session.sharedPlaylistIndex == -1) {
                p.file = MediaFile()
                p.file!!.uri = uri
                p.file!!.collectInfo(this)
                injectVideo(uri)
                p.sendPacket(sendPlaylistIndex(0))
            }
            val newList = p.session.sharedPlaylist
            newList.add(getFileName(uri) ?: return@Thread)
            p.sendPacket(sendPlaylistChange(newList))
        }.start()
    }

    @JvmStatic
    fun RoomActivity.addFolderToPlaylist(uri: Uri) {
        Thread {
            /* First, we save it in our media directories as a common directory */
            saveFolderPathAsMediaDirectory(uri)

            /* Now we get children files */
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )

            /** Obtaining the children tree from the path **/
            val tree = DocumentFile.fromTreeUri(this, childrenUri) ?: return@Thread
            val files = tree.listFiles()

            /** We iterate through the children file tree and add them to playlist */
            val newList = mutableListOf<String>()
            for (file in files) {
                val filename = file.name!!
                newList.add(filename)
            }
            newList.sort()
            if (p.session.sharedPlaylistIndex == -1) {
                retrieveFile(newList.first())
                p.sendPacket(sendPlaylistChange(p.session.sharedPlaylist + newList))
                p.sendPacket(sendPlaylistIndex(0))
            } else {
                p.sendPacket(sendPlaylistChange(p.session.sharedPlaylist + newList))
            }
        }.start()
    }

    @JvmStatic
    fun RoomActivity.saveFolderPathAsMediaDirectory(uri: Uri) {
        val list = DA.getFolderList(p.gson, DA.prefKey, this)
        list.add(uri.toString())
        DA.writeFolderList(list, p.gson, DA.prefKey, this)
    }

    /** This is to send a playlist selection change to the server */
    @JvmStatic
    fun RoomActivity.sendPlaylistSelection(index: Int) {
        p.sendPacket(sendPlaylistIndex(index))
    }

    /** This is to change playlist selection in response other users' selection */
    @JvmStatic
    fun RoomActivity.changePlaylistSelection(index: Int) {
        if (p.session.sharedPlaylist.size < (index + 1)) return /* In case this was called on an empty list */
        if (index != p.session.sharedPlaylistIndex) {
            /* If the file on that index isn't playing, play the file */
            retrieveFile(p.session.sharedPlaylist[index])
        }
    }

    /** This will search all media directories specified by the user in settings to look for a file
     * name and load it into ExoPlayer. This is executed on a separate thread since the IO operation
     * is heavy.
     */
    @JvmStatic
    fun RoomActivity.retrieveFile(fileName: String) {
        Thread {
            /** First, we search our media directories which were added by the user in settings */
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            val folderJson = sp.getString("SHARED_PLAYLIST_MEDIA_DIRECTORIES", "[]")
            val paths = p.gson.fromJson<List<String>>(folderJson, List::class.java).toMutableList()

            if (paths.isEmpty()) {
                broadcastMessage(getString(R.string.room_shared_playlist_no_directories), false)
            }

            var fileUri2Play: Uri? = null
            /** We iterate through the media directory paths spreading their children tree **/
            for (path in paths) {
                val uri = Uri.parse(path)

                /** Will NOT work if Uri hasn't been declared persistent upon retrieving it.
                 * @see [com.chromaticnoob.syncplay.main.DirectoriesActivity] */
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    uri,
                    DocumentsContract.getTreeDocumentId(uri)
                )

                /** Obtaining the children tree from the path **/
                val tree = DocumentFile.fromTreeUri(this, childrenUri) ?: return@Thread
                val files = tree.listFiles()

                /** We iterate through the children file tree, and we search for the specific file */
                for (file in files) {
                    val filename = file.name!!
                    if (filename == fileName) {
                        fileUri2Play = tree.findFile(filename)?.uri ?: return@Thread
                        /** Changing current file variable **/
                        p.file = MediaFile()
                        p.file?.uri = fileUri2Play
                        p.file?.collectInfo(this@retrieveFile)
                        /** Loading the file into our player **/
                        runOnUiThread {
                            injectVideo(fileUri2Play)
                        }
                        break
                    }
                }
            }
            if (fileUri2Play == null) {
                if (p.file?.fileName != fileName) {
                    toasty(getString(R.string.room_shared_playlist_not_found))
                    broadcastMessage(getString(R.string.room_shared_playlist_not_found), false)
                }
            }
        }.start()
    }

    @JvmStatic
    fun RoomActivity.deleteItemFromPlaylist(i: Int) {
        p.session.sharedPlaylist.removeAt(i)
        sharedplaylistPopup.update()
        p.sendPacket(sendPlaylistChange(p.session.sharedPlaylist))
        if (p.session.sharedPlaylist.isEmpty()) {
            p.session.sharedPlaylistIndex = -1
        }
    }
}