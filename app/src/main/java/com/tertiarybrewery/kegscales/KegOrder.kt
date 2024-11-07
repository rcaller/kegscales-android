package com.tertiarybrewery.kegscales

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Collections.swap

class KegOrder (prefs: SharedPreferences) {

    private var preferences: SharedPreferences = prefs
    private var kegOrder: MutableList<String> = mutableListOf<String>()
    init {
        Log.i("KegOrder", "initialising")
        if (preferences.contains("kegOrder") && preferences.getString("kegOrder", "") != "") {
            setKegOrder(preferences.getString("kegOrder", "")!!.split("|")
                .toMutableList())
        }
    }
    fun setKegOrder(kegs: MutableList<String>) {
        kegOrder = kegs
    }

    fun getKegIdInPosition(kegNumber: Int): String {
        return kegOrder[kegNumber]
    }

    fun contains(kegId: String): Boolean {
        return kegOrder.contains(kegId)
    }

    fun appendKeg(kegId: String) {
        kegOrder.add(kegId)
    }

    fun indexOf(kegId: String): Int {
        return kegOrder.indexOf(kegId)
    }
    fun store() {
        with (preferences.edit()) {
            val kegDataString = kegOrder.joinToString(separator = "|")
            Log.i("scancomplete", "kegdatastring: "+kegDataString)
            putString("kegOrder", kegDataString)
            apply()
        }
    }

    fun clearInactive(localKegList: MutableList<String>) {
        kegOrder.retainAll(localKegList)
    }

    fun size(): Int {
        return kegOrder.size
    }

    fun moveKegLeft(kegNumber: Int) {
        swap(kegOrder, kegNumber, kegNumber-1)
        store()
    }
    fun moveKegRight(kegNumber: Int) {
        swap(kegOrder, kegNumber, kegNumber+1)
        store()
    }
}