package com.example.vaultix

import java.security.Key

object SessionManager {
    private var masterKey: Key? = null

    fun setMasterKey(key: Key) {
        masterKey = key
    }

    fun getMasterKey(): Key? = masterKey

    fun isUnlocked(): Boolean = masterKey != null

    fun clear() {
        masterKey = null
    }
}
