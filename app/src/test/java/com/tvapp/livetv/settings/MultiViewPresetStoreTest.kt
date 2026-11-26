package com.tvapp.livetv.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiViewPresetStoreTest {

    private fun newStore(): MultiViewPresetStore = MultiViewPresetStore(InMemorySharedPreferences())

    @Test
    fun saveLoadAndDeletePresetsPersistCorrectly() {
        val store = newStore()
        assertTrue(store.loadAll().isEmpty())

        val first = store.save("Spor", listOf("tif:1", "iptv:2", "tif:1"))
        assertNotNull(first)
        assertEquals(listOf("tif:1", "iptv:2"), first?.channelKeys)

        val second = store.save("Haberler", listOf("iptv:3", "iptv:4", "iptv:5", "iptv:6", "iptv:7"))
        assertNotNull(second)
        assertEquals(4, second?.channelKeys?.size)

        val all = store.loadAll()
        assertEquals(2, all.size)
        assertEquals("Haberler", all[0].name)
        assertEquals("Spor", all[1].name)

        store.delete(first!!.id)
        val afterDelete = store.loadAll()
        assertEquals(1, afterDelete.size)
        assertEquals("Haberler", afterDelete[0].name)
    }

    @Test
    fun saveRejectsBlankNameOrEmptyKeysAndSuggestsReadableNames() {
        val store = newStore()
        assertNull(store.save("   ", listOf("tif:1")))
        assertNull(store.save("Bos", emptyList()))

        assertEquals("TRT 1 + ATV", MultiViewPresetStore.suggestName(listOf("TRT 1", "ATV")))
        assertEquals("TRT 1 + ATV +2", MultiViewPresetStore.suggestName(listOf("TRT 1", "ATV", "Show", "Kanal D")))
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = LinkedHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) values.clear()
                pending.forEach { (k, v) ->
                    if (v == null) values.remove(k) else values[k] = v
                }
            }
        }
    }
}
