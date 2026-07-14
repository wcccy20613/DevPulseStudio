package com.chunyan.devpulsestudio

import android.app.Application
import androidx.room.Room
import com.chunyan.devpulsestudio.data.PulseRepository
import com.chunyan.devpulsestudio.data.local.PulseDatabase
import com.chunyan.devpulsestudio.data.remote.PulseApi
import com.chunyan.devpulsestudio.data.remote.InsightGatewayApi
import com.chunyan.devpulsestudio.data.remote.StaticCatalogApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson

class PulseApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }

    inner class AppContainer {
        private val database = Room.databaseBuilder(
            this@PulseApplication,
            PulseDatabase::class.java,
            "dev_pulse_studio.db",
        )
            .addMigrations(
                PulseDatabase.MIGRATION_1_2,
                PulseDatabase.MIGRATION_2_3,
                PulseDatabase.MIGRATION_3_4,
                PulseDatabase.MIGRATION_4_5,
                PulseDatabase.MIGRATION_5_6,
            )
            .build()

        private val api = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PulseApi::class.java)

        private val gson = Gson()
        private val insightGateway = BuildConfig.AI_GATEWAY_URL
            .takeIf { it.isNotBlank() }
            ?.let { configuredUrl ->
                Retrofit.Builder()
                    .baseUrl(if (configuredUrl.endsWith('/')) configuredUrl else "$configuredUrl/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(InsightGatewayApi::class.java)
            }
        private val staticCatalog = BuildConfig.STATIC_CATALOG_BASE_URL
            .takeIf { it.isNotBlank() }
            ?.let { configuredUrl ->
                Retrofit.Builder()
                    .baseUrl(if (configuredUrl.endsWith('/')) configuredUrl else "$configuredUrl/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(StaticCatalogApi::class.java)
            }
        val repository = PulseRepository(
            appContext = this@PulseApplication,
            api = api,
            savedArticleDao = database.savedArticleDao(),
            discoveryCacheDao = database.discoveryCacheDao(),
            readmeAnalysisDao = database.readmeAnalysisDao(),
            ignoredRecommendationDao = database.ignoredRecommendationDao(),
            searchHistoryDao = database.searchHistoryDao(),
            insightGatewayApi = insightGateway,
            staticCatalogApi = staticCatalog,
            gson = gson,
        )
    }
}
