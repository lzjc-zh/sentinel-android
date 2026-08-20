package com.deepseek.lzjc.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deepseek.lzjc.data.repository.MiMoRepository
import com.deepseek.lzjc.data.repository.UsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: UsageRepository,
    private val mimoRepository: MiMoRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Refresh DeepSeek data
            val apiResult = repository.refreshAndRecord()

            // Also refresh MiMo data if logged in
            try {
                if (mimoRepository.isLoggedIn()) {
                    mimoRepository.refreshAndFetch()
                }
            } catch (_: Exception) {
                // MiMo refresh failure is non-fatal
            }

            if (apiResult.isSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
