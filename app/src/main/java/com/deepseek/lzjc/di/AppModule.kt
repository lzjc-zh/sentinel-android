package com.deepseek.lzjc.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.deepseek.lzjc.data.api.DeepSeekApi
import com.deepseek.lzjc.data.api.PlatformApi
import com.deepseek.lzjc.data.ark.ArkApi
import com.deepseek.lzjc.data.ark.ArkApiClient
import com.deepseek.lzjc.data.db.AppDatabase
import com.deepseek.lzjc.data.db.UsageDao
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.mimoDataStore: DataStore<Preferences> by preferencesDataStore(name = "mimo_settings")
private val Context.arkDataStore: DataStore<Preferences> by preferencesDataStore(name = "ark_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder().setLenient().create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("api")
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideDeepSeekApi(@Named("api") retrofit: Retrofit): DeepSeekApi {
        return retrofit.create(DeepSeekApi::class.java)
    }

    @Provides
    @Singleton
    @Named("platform")
    fun providePlatformOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Pixel 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Referer", "https://platform.deepseek.com/")
                    .header("Origin", "https://platform.deepseek.com")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("platform")
    fun providePlatformRetrofit(@Named("platform") client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://platform.deepseek.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePlatformApi(@Named("platform") retrofit: Retrofit): PlatformApi {
        return retrofit.create(PlatformApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "deepseek_balance.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    @Singleton
    fun provideUsageDao(db: AppDatabase): UsageDao {
        return db.usageDao()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    // ===== MiMo providers =====

    @Provides
    @Singleton
    @Named("mimo")
    fun provideMiMoDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.mimoDataStore
    }

    @Provides
    @Singleton
    @Named("mimo")
    fun provideMiMoOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
            .build()
    }

    // ===== Ark providers =====

    @Provides
    @Singleton
    @Named("ark")
    fun provideArkDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.arkDataStore
    }

    @Provides
    @Singleton
    @Named("ark")
    fun provideArkOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("ark")
    fun provideArkRetrofit(@Named("ark") client: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://ark.cn-beijing.volcengineapi.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideArkApi(@Named("ark") retrofit: Retrofit): ArkApi {
        return retrofit.create(ArkApi::class.java)
    }

    @Provides
    @Singleton
    fun provideArkApiClient(arkApi: ArkApi, gson: Gson): ArkApiClient {
        return ArkApiClient(arkApi, gson)
    }
}
