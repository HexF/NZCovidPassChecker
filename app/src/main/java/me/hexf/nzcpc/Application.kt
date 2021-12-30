package me.hexf.nzcpc

import android.app.Application
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Application : Application() {
    val executorService: ExecutorService = Executors.newFixedThreadPool(4)
}