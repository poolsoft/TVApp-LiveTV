package com.tvapp.livetv.main

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.tvapp.livetv.R
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.vod.VodPageResolver
import com.tvapp.livetv.vod.VodUiSupport

/**
 * MainActivity'den tasinan VOD diyaloglari (Faz 1).
 *
 * Davranis degisikligi yok: MainActivity'deki AlertDialog bloklarinin aynisi,
 * sadece Activity + callback uzerinden calisir. Renk tuslari/davranis kurallari
 * korunur (uzaktan kumandayla secim, Back ile kapatma).
 */
object VodDialogs {

    /**
     * Bolum secici (eski showEpisodePickerForActiveVod diyalogu).
     * Uzaktan kumandayla secim, Back ile kapatma davranisi aynidir.
     */
    fun showEpisodePicker(
        activity: Activity,
        title: String,
        labels: List<String>,
        selectedIndex: Int,
        onPick: (Int) -> Unit,
    ) {
        AlertDialog.Builder(activity, R.style.Theme_TVApp_Dialog)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { dialog, which ->
                onPick(which)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /**
     * Birden cok oynatilabilir aday bulundugunda uzaktan kumandayla secim sunar.
     * Tek aday yolu sessizce oynar; cok adayda ilk siradakini dayatmaz.
     */
    fun showVodStreamChoice(
        activity: Activity,
        resolved: VodPageResolver.Resolved,
        baseChannel: LiveChannel,
        onSelectChannel: (LiveChannel) -> Unit,
    ) {
        val candidates = resolved.candidates
        if (candidates.isEmpty()) {
            onSelectChannel(baseChannel)
            return
        }
        val labels = candidates.mapIndexed { index, item ->
            VodUiSupport.buildVodStreamLabel(index + 1, item.streamUrl)
        }.toTypedArray()
        AlertDialog.Builder(activity, R.style.Theme_TVApp_Dialog)
            .setTitle(activity.getString(R.string.browser_select_stream))
            .setItems(labels) { dialog, which ->
                dialog.dismiss()
                val chosen = candidates.getOrNull(which) ?: return@setItems
                onSelectChannel(resolved.applyTo(baseChannel, chosen))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
