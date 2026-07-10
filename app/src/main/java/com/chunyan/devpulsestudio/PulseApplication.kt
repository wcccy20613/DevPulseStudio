package com.chunyan.devpulsestudio

import android.app.Application
import androidx.room.Room
import com.chunyan.devpulsestudio.data.PulseRepository
import com.chunyan.devpulsestudio.data.local.PulseDatabase
import com.chunyan.devpulsestudio.data.remote.PulseApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PulseApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }

    inner class AppContainer {
        private val database = Room.databaseBuilder(
            this@PulseApplication,
            PulseDatabase::class.java,
            "dev_pulse_studio.db",
        ).fallbackToDestructiveMigration().build()

        private val api = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PulseApi::class.java)

        val repository = PulseRepository(api, database.savedArticleDao())
    }
}
