package com.example.remotecompose.data.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.meizu.assistant.IGenDocumentService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 负责绑定个人助理（com.meizu.assistant）的 GenDocumentService，
 * 并将 RemoteCompose 的 .rc 二进制数据通过 AIDL 推送过去。
 *
 * 绑定是惰性的：第一次 [sendBytes] 时才发起 bind，
 * 服务断开后会缓存最近一次 pending bytes，连接恢复后自动重发。
 */
class GenDocumentClient(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var service: IGenDocumentService? = null

    private val binding = AtomicBoolean(false)

    /** 服务尚未连接前缓存的最新数据，连接成功后立即下发。 */
    @Volatile
    private var pendingBytes: ByteArray? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding.set(false)
            service = IGenDocumentService.Stub.asInterface(binder)
            Log.d(TAG, "Connected to $name")
            pendingBytes?.let {
                pendingBytes = null
                trySend(it)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Disconnected from $name")
            service = null
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "Binding died for $name")
            service = null
            unbindQuietly()
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.w(TAG, "Null binding for $name")
            service = null
            unbindQuietly()
        }
    }

    /**
     * 向个人助理推送一份文档字节数据。
     * 若服务尚未连接则触发绑定，并在连接完成后自动补发最新一份数据。
     */
    fun sendBytes(bytes: ByteArray) {
        val current = service
        if (current != null) {
            trySend(bytes)
        } else {
            pendingBytes = bytes
            ensureBound()
        }
    }

    /** 调用方在不再需要推送时（如 ViewModel#onCleared）调用以释放绑定。 */
    fun release() {
        pendingBytes = null
        service = null
        unbindQuietly()
    }

    private fun trySend(bytes: ByteArray) {
        val s = service ?: run {
            pendingBytes = bytes
            ensureBound()
            return
        }
        try {
            s.sendDocumentBytes(bytes)
        } catch (e: RemoteException) {
            Log.e(TAG, "sendDocumentBytes failed, will retry on reconnect", e)
            pendingBytes = bytes
            service = null
            unbindQuietly()
            ensureBound()
        }
    }

    private fun ensureBound() {
        if (service != null) return
        if (!binding.compareAndSet(false, true)) return

        val intent = Intent(ACTION).setPackage(TARGET_PACKAGE)
        val ok = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.e(TAG, "bindService SecurityException", e)
            false
        }
        if (!ok) {
            binding.set(false)
            Log.e(TAG, "bindService returned false; assistant service unavailable")
            unbindQuietly()
        }
    }

    private fun unbindQuietly() {
        try {
            appContext.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // 未绑定时调用 unbindService 会抛出该异常，忽略即可
        }
    }

    companion object {
        private const val TAG = "GenDocumentClient"
        private const val TARGET_PACKAGE = "com.meizu.assistant"
        private const val ACTION = "com.meizu.assistant.GEN_DOCUMENT_SERVICE"
    }
}
