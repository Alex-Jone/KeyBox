package com.keybox.app

import android.app.Application
import com.keybox.app.data.crypto.CryptoManager
import com.keybox.app.data.db.KeyBoxDatabase
import com.keybox.app.data.repository.PasswordRepository

class KeyBoxApp : Application() {

    lateinit var crypto: CryptoManager
        private set
    lateinit var repository: PasswordRepository
        private set

    override fun onCreate() {
        super.onCreate()
        crypto = CryptoManager(this)
        val db = KeyBoxDatabase.getInstance(this)
        repository = PasswordRepository(db, crypto)
    }
}
