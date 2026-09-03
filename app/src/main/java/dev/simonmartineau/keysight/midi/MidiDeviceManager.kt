package dev.simonmartineau.keysight.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.simonmartineau.keysight.timing.MonotonicClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Finds the keyboard, opens it, and turns what it sends into [events].
 *
 * V1 speaks MIDI 1.0 over USB. The first device that has an output port (the port a keyboard
 * sends on) is opened; hot plug and unplug go through the framework's device callback, so a
 * keyboard can be unplugged and plugged back without restarting the app. Everything here
 * runs on the main thread except the receiver, which the framework calls on its own thread.
 */
class MidiDeviceManager(
    context: Context,
    private val clock: MonotonicClock,
) {
    private val midiManager: MidiManager? = context.getSystemService(MidiManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _connection = MutableStateFlow<MidiConnection>(MidiConnection.NoDevice)
    val connection: StateFlow<MidiConnection> = _connection.asStateFlow()

    /**
     * Every event from the connected keyboard. The buffer is large enough that only a stalled
     * main thread could fill it, and a drop is logged as the bug it would be.
     */
    private val _events = MutableSharedFlow<MidiEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<MidiEvent> = _events.asSharedFlow()

    private var started = false
    private var openDevice: MidiDevice? = null
    private var openPort: MidiOutputPort? = null
    private var openInfo: MidiDeviceInfo? = null

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            if (openInfo == null && info.isEligible) connect(info)
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (info.id != openInfo?.id) return
            Log.i(TAG, "${info.displayName} removed")
            closeCurrent()
            _connection.value = MidiConnection.NoDevice
            connectToFirstEligible()
        }
    }

    fun start() {
        if (started) return
        val manager = midiManager ?: run {
            Log.e(TAG, "no MIDI service on this device")
            return
        }
        started = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, { handler.post(it) }, deviceCallback)
        } else {
            @Suppress("DEPRECATION")
            manager.registerDeviceCallback(deviceCallback, handler)
        }
        connectToFirstEligible()
    }

    fun stop() {
        if (!started) return
        started = false
        midiManager?.unregisterDeviceCallback(deviceCallback)
        closeCurrent()
        _connection.value = MidiConnection.NoDevice
    }

    private fun connectToFirstEligible() {
        val manager = midiManager ?: return
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
        } else {
            @Suppress("DEPRECATION")
            manager.getDevices().toList()
        }
        devices.firstOrNull { it.isEligible }?.let(::connect)
    }

    private fun connect(info: MidiDeviceInfo) {
        val manager = midiManager ?: return
        val name = info.displayName
        openInfo = info
        _connection.value = MidiConnection.Connecting(name)
        manager.openDevice(
            info,
            { device ->
                if (openInfo?.id != info.id) {
                    device?.close()
                    return@openDevice
                }
                val port = device?.openOutputPort(0)
                if (device == null || port == null) {
                    device?.close()
                    openInfo = null
                    _connection.value = MidiConnection.Failed(name, "could not open the device")
                    Log.e(TAG, "failed to open $name")
                    return@openDevice
                }
                openDevice = device
                openPort = port
                port.connect(CaptureReceiver(MidiCapture(clock, ::publish)))
                _connection.value = MidiConnection.Connected(name)
                Log.i(TAG, "connected to $name")
            },
            handler,
        )
    }

    private fun closeCurrent() {
        runCatching { openPort?.close() }
        runCatching { openDevice?.close() }
        openPort = null
        openDevice = null
        openInfo = null
    }

    private fun publish(event: MidiEvent) {
        if (!_events.tryEmit(event)) Log.e(TAG, "dropped MIDI event $event: the buffer of $EVENT_BUFFER is full")
    }

    private class CaptureReceiver(private val capture: MidiCapture) : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            capture.receive(msg, offset, count, timestamp)
        }
    }

    private val MidiDeviceInfo.isEligible: Boolean
        get() = outputPortCount > 0

    private val MidiDeviceInfo.displayName: String
        get() = properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: "MIDI device"

    private companion object {
        const val TAG = "MidiDeviceManager"
        const val EVENT_BUFFER = 4096
    }
}
