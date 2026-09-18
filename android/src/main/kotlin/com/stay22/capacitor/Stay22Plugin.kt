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
import com.stay22.sdk.TravelContext
import java.util.concurrent.atomic.AtomicBoolean

@CapacitorPlugin(name = "Stay22")
class Stay22Plugin : Plugin() {
    private val eventListener = Stay22EventListener(::forwardEvent)

    override fun load() {
        super.load()
        initializeFromManifest()
        Stay22.advanced.setEventListener(eventListener)
    }

    override fun handleOnDestroy() {
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
        Stay22.isEnabled = call.requireBoolean("enabled")
        null
    }

    @PluginMethod
    fun isEnabled(call: PluginCall) = resolve(call) { value(Stay22.isEnabled) }

    @PluginMethod
    fun setMedium(call: PluginCall) = resolve(call) {
        Stay22.medium = call.requireString("medium")
        null
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
    fun setTravelContext(call: PluginCall) = resolveInitialized(call) {
        Stay22.setTravelContext(
            TravelContext(
                address = call.getString("address"),
                latitude = call.getDouble("latitude"),
                longitude = call.getDouble("longitude"),
                checkinDate = call.getString("checkinDate"),
                checkoutDate = call.getString("checkoutDate"),
                hotelName = call.getString("hotelName"),
                adults = call.getInt("adults"),
                children = call.getInt("children"),
            ),
        )
        null
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

    @PluginMethod
    fun forceNotification(call: PluginCall) = resolveInitialized(call) {
        Stay22.advanced.scheduleNotification(force = true)
        null
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
        val payload = JSObject()
        when (event) {
            is Stay22Event.LocationUpdated -> {
                payload.put("type", "locationUpdated")
                payload.put("description", "Location: ${event.destination}")
            }
            is Stay22Event.NotificationScheduled -> {
                payload.put("type", "notificationScheduled")
                payload.put("description", "Scheduled: ${event.destination} in ${event.delay.toInt()}s")
            }
            is Stay22Event.NotificationShown -> {
                payload.put("type", "notificationShown")
                payload.put("description", "Shown: ${event.destination}")
            }
            is Stay22Event.NotificationClicked -> {
                payload.put("type", "notificationClicked")
                payload.put("description", "Clicked: ${event.destination}")
                payload.put("url", event.url)
            }
            is Stay22Event.NotificationBlocked -> {
                payload.put("type", "notificationBlocked")
                payload.put("description", "Blocked: ${event.reason}")
            }
            is Stay22Event.NotificationSkipped -> {
                payload.put("type", "notificationSkipped")
                payload.put("description", "Skipped: ${event.reason}")
            }
            is Stay22Event.NotificationCancelled -> {
                payload.put("type", "notificationCancelled")
                payload.put("description", "Cancelled: ${event.reason}")
            }
            is Stay22Event.EnabledChanged -> {
                payload.put("type", "enabledChanged")
                payload.put("description", "SDK enabled: ${event.isEnabled}")
            }
            Stay22Event.TravelContextCleared -> {
                payload.put("type", "travelContextCleared")
                payload.put("description", "Travel context cleared")
            }
        }

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
