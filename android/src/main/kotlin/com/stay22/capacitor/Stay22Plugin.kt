package com.stay22.capacitor

import android.app.Application
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.stay22.sdk.Stay22
import com.stay22.sdk.Stay22Event
import com.stay22.sdk.Stay22EventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

@CapacitorPlugin(name = "Stay22")
class Stay22Plugin : Plugin() {
    private val queryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val eventListener = Stay22EventListener(::forwardEvent)

    override fun load() {
        super.load()
        initializeFromManifest()
        Stay22.advanced.setEventListener(eventListener)
    }

    override fun handleOnDestroy() {
        queryScope.cancel()
        Stay22.advanced.setEventListener(null)
        super.handleOnDestroy()
    }

    @PluginMethod
    fun initialize(call: PluginCall) = resolve(call) {
        if (!Stay22.isInitialized) {
            val application = context.applicationContext as? Application
                ?: throw PluginFailure(
                    "native_failure",
                    "Stay22 needs an Application context to initialize.",
                )
            Stay22.initialize(application, call.requireString("aid"))
        }
        null
    }

    @PluginMethod
    fun isInitialized(call: PluginCall) = resolve(call) { value(Stay22.isInitialized) }

    @PluginMethod
    fun setEnabled(call: PluginCall) = resolve(call) {
        val application = context.applicationContext as? Application
            ?: throw PluginFailure(
                "native_failure",
                "Stay22 needs an Application context to save consent.",
            )
        Stay22.setEnabled(application, call.requireBoolean("enabled"))
        null
    }

    @PluginMethod
    fun isEnabled(call: PluginCall) = resolve(call) {
        val application = context.applicationContext as? Application
            ?: throw PluginFailure(
                "native_failure",
                "Stay22 needs an Application context to read consent.",
            )
        value(Stay22.readEnabled(application))
    }

    @PluginMethod
    fun setCampaignId(call: PluginCall) = resolve(call) {
        val campaignId = call.getString("campaignId")?.trim()
        if (campaignId != null && campaignId.isEmpty()) {
            throw PluginFailure(
                "invalid_argument",
                "Argument 'campaignId' must not be blank when provided.",
            )
        }
        Stay22.campaignId = campaignId
        null
    }

    @PluginMethod
    fun hasNotificationPermission(call: PluginCall) = resolveInitialized(call) {
        value(Stay22.hasNotificationPermission())
    }

    @PluginMethod
    fun requestNotificationPermission(call: PluginCall) {
        onMain(call) {
            if (rejectIfUninitialized(call)) return@onMain
            if (activity == null) {
                // JS contract: resolve the current state when no prompt can be shown.
                call.resolve(value(Stay22.hasNotificationPermission()))
                return@onMain
            }

            // Capacitor tears down a plugin call after resolution, so protect it from any
            // accidental second callback even though the native SDK promises exactly once.
            val delivered = AtomicBoolean(false)
            Stay22.requestNotificationPermission { granted ->
                if (delivered.compareAndSet(false, true)) call.resolve(value(granted))
            }
        }
    }

    @PluginMethod
    fun ensureNotification(call: PluginCall) = resolve(call) {
        notificationCommand(call, "ensureNotification")
    }

    @PluginMethod
    fun updateNotification(call: PluginCall) = resolve(call) {
        notificationCommand(call, "updateNotification")
    }

    @PluginMethod
    fun replaceNotification(call: PluginCall) = resolve(call) {
        notificationCommand(call, "replaceNotification")
    }

    @PluginMethod
    fun cancelNotification(call: PluginCall) = resolve(call) {
        notificationCommand(call, "cancelNotification")
    }

    @PluginMethod
    fun scheduleDiagnosticNotification(call: PluginCall) = resolve(call) {
        notificationCommand(call, "scheduleDiagnosticNotification")
    }

    @PluginMethod
    fun getNotificationState(call: PluginCall) {
        onMain(call) {
            // This scope owns the read only; destroying the bridge cannot revoke an offer.
            queryScope.launch {
                try {
                    call.resolve(JSObject(JSONObject(NotificationBridgeCodec.map(Stay22.notifications.current())).toString()))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    call.reject(failure.message ?: "Notification query failed.", "native_failure", failure)
                }
            }
        }
    }

    private fun notificationCommand(call: PluginCall, method: String): JSObject =
        JSObject(JSONObject(NotificationBridgeCodec.command(method, bridgeInput(call.data))).toString())

    // JSONObject.NULL must stay an explicit null, rather than becoming an invalid
    // object or disappearing. Lists and nested trips retain their complete shape.
    private fun bridgeInput(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { bridgeInput(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { bridgeInput(value.get(it)) }
        else -> value
    }

    @PluginMethod
    fun clearTravelContext(call: PluginCall) = resolveInitialized(call) {
        Stay22.clearTravelContext()
        null
    }

    @PluginMethod
    fun isNotificationHandlerInstalled(call: PluginCall) {
        // Android routes taps through the SDK's own Activity and has no shared delegate
        // or single Capacitor handler slot that another plugin can displace.
        resolve(call) { value(true) }
    }

    private fun initializeFromManifest() {
        if (Stay22.isInitialized) return

        val aid = runCatching {
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData
                ?.getString(MANIFEST_AID_KEY)
                ?.trim()
        }.getOrNull()
        if (aid.isNullOrEmpty()) return

        val application = context.applicationContext as? Application ?: return
        runCatching { Stay22.initialize(application, aid) }
            .onFailure { Log.w(TAG, "Manifest-declared Stay22 initialization failed", it) }
    }

    private fun rejectIfUninitialized(call: PluginCall): Boolean {
        if (Stay22.isInitialized) return false
        call.reject(
            "Stay22 is not initialized. Declare $MANIFEST_AID_KEY in AndroidManifest.xml "
                + "or call Stay22.initialize({ aid }) first.",
            "not_initialized",
        )
        return true
    }

    // Capacitor posts @PluginMethod onto HandlerThread("CapacitorPlugins"). Stay22.initialize
    // registers ProcessLifecycleOwner and must run on the main thread.
    private fun onMain(call: PluginCall, block: () -> Unit) {
        val task = Runnable {
            try {
                block()
            } catch (failure: PluginFailure) {
                call.reject(failure.message, failure.code)
            } catch (failure: IllegalArgumentException) {
                call.reject(failure.message ?: "Invalid argument.", "invalid_argument")
            } catch (failure: Throwable) {
                call.reject(
                    failure.message ?: "Unexpected native failure.",
                    "native_failure",
                    failure as? Exception ?: Exception(failure),
                )
            }
        }
        val pluginBridge = bridge
        if (Looper.myLooper() == Looper.getMainLooper() || pluginBridge == null) {
            task.run()
        } else {
            pluginBridge.executeOnMainThread(task)
        }
    }

    private fun resolveInitialized(call: PluginCall, block: () -> Any?) {
        onMain(call) {
            if (rejectIfUninitialized(call)) return@onMain
            val result = block()
            if (result == null) call.resolve() else call.resolve(result as JSObject)
        }
    }

    private fun resolve(call: PluginCall, block: () -> Any?) {
        onMain(call) {
            val result = block()
            if (result == null) call.resolve() else call.resolve(result as JSObject)
        }
    }

    private fun PluginCall.requireString(key: String): String =
        getString(key)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw PluginFailure("missing_argument", "Missing required argument '$key'.")

    private fun PluginCall.requireBoolean(key: String): Boolean =
        getBoolean(key)
            ?: throw PluginFailure("missing_argument", "Missing required argument '$key'.")

    private fun forwardEvent(event: Stay22Event) {
        val payload = JSObject(JSONObject(NotificationBridgeCodec.map(event)).toString())
        // The SDK can replay worker events before JavaScript subscribes.
        notifyListeners("stay22Event", payload, true)
    }

    private fun value(value: Boolean): JSObject = JSObject().put("value", value)

    private class PluginFailure(
        val code: String,
        override val message: String,
    ) : RuntimeException(message)

    private companion object {
        const val TAG = "Stay22Capacitor"
        const val MANIFEST_AID_KEY = "com.stay22.sdk.PartnerAID"
    }
}
