package com.tvapp.livetv.main

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.tvapp.livetv.R

/**
 * Faz 5: MainActivity bunyesindeki AlertDialog olusturma ve baglama yardimcilari.
 *
 * Davranis degisikligi yok: diyalog layout'lari, stilleri ve kumanda odak
 * dinleyicileri birebir korunmustur.
 */
object MainDialogs {

    /** Quick Hub 2x4 kisa yol tablosu diyalogu. */
    fun createQuickHubDialog(
        activity: Activity,
        onLiveTv: () -> Unit,
        onVod: () -> Unit,
        onIptv: () -> Unit,
        onXmltv: () -> Unit,
        onBrowser: () -> Unit,
        onGuide: () -> Unit,
        onMultiview: () -> Unit,
        onSettings: () -> Unit,
    ): AlertDialog {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_quick_hub, null)
        val hintText = dialogView.findViewById<TextView>(R.id.hub_hint_text)

        val itemLiveTv = dialogView.findViewById<View>(R.id.hub_item_live_tv)
        val itemVod = dialogView.findViewById<View>(R.id.hub_item_vod)
        val itemIptv = dialogView.findViewById<View>(R.id.hub_item_iptv)
        val itemXmltv = dialogView.findViewById<View>(R.id.hub_item_xmltv)
        val itemBrowser = dialogView.findViewById<View>(R.id.hub_item_browser)
        val itemGuide = dialogView.findViewById<View>(R.id.hub_item_guide)
        val itemMultiview = dialogView.findViewById<View>(R.id.hub_item_multiview)
        val itemSettings = dialogView.findViewById<View>(R.id.hub_item_settings)

        fun bindFocusHint(view: View, hintResId: Int) {
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    hintText.setText(hintResId)
                }
            }
        }

        bindFocusHint(itemLiveTv, R.string.quick_hub_live_tv_hint)
        bindFocusHint(itemVod, R.string.quick_hub_vod_hint)
        bindFocusHint(itemIptv, R.string.quick_hub_iptv_hint)
        bindFocusHint(itemXmltv, R.string.quick_hub_xmltv_hint)
        bindFocusHint(itemBrowser, R.string.quick_hub_browser_hint)
        bindFocusHint(itemGuide, R.string.quick_hub_guide_hint)
        bindFocusHint(itemMultiview, R.string.quick_hub_multiview_hint)
        bindFocusHint(itemSettings, R.string.quick_hub_settings_hint)

        val dialog = AlertDialog.Builder(activity, R.style.Theme_TVApp_Dialog)
            .setView(dialogView)
            .create()

        itemLiveTv.setOnClickListener {
            dialog.dismiss()
            onLiveTv()
        }
        itemVod.setOnClickListener {
            dialog.dismiss()
            onVod()
        }
        itemIptv.setOnClickListener {
            dialog.dismiss()
            onIptv()
        }
        itemXmltv.setOnClickListener {
            dialog.dismiss()
            onXmltv()
        }
        itemBrowser.setOnClickListener {
            dialog.dismiss()
            onBrowser()
        }
        itemGuide.setOnClickListener {
            dialog.dismiss()
            onGuide()
        }
        itemMultiview.setOnClickListener {
            dialog.dismiss()
            onMultiview()
        }
        itemSettings.setOnClickListener {
            dialog.dismiss()
            onSettings()
        }

        return dialog
    }

    /** Oynatma modu ve eylemleri secim diyalogu. */
    fun showPlaybackModeDialog(
        context: Context,
        actions: List<Pair<String, () -> Unit>>,
    ) {
        AlertDialog.Builder(context, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.playback_mode_title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.second?.invoke()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** Kanal arama metin giris diyalogu. */
    fun showChannelSearchDialog(
        context: Context,
        currentQuery: String,
        onApply: (String) -> Unit,
        onClear: () -> Unit,
    ) {
        val input = EditText(ContextThemeWrapper(context, R.style.Theme_TVApp_Dialog)).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = context.getString(R.string.channel_search_hint)
            setText(currentQuery)
            selectAll()
        }
        val dialog = AlertDialog.Builder(context, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.search_channels)
            .setView(input)
            .setPositiveButton(R.string.apply, null)
            .setNeutralButton(R.string.clear_search, null)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                onApply(input.text.toString())
                dialog.dismiss()
            }
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                onClear()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Genel metin editoru diyalogu. */
    fun showTextEditor(
        context: Context,
        title: String,
        input: EditText,
        onSave: (String) -> Unit,
    ) {
        val dialog = AlertDialog.Builder(context, R.style.Theme_TVApp_Dialog)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                onSave(input.text.toString())
            }
        }
        dialog.show()
    }
}
