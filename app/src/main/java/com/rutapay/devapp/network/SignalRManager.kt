package com.rutapay.devapp.network

import android.util.Log
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SignalRManager(private val hubUrl: String) {
    private var hubConnection: HubConnection? = null
    
    private val _paymentConfirmations = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val paymentConfirmations = _paymentConfirmations.asSharedFlow()

    fun start() {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) return

        hubConnection = HubConnectionBuilder.create(hubUrl)
            .build()

        hubConnection?.on("ReceivePaymentConfirmation", { message: String ->
            Log.d("SignalR", "Received: $message")
            _paymentConfirmations.tryEmit(message)
        }, String::class.java)

        hubConnection?.onClosed { exception ->
            Log.e("SignalR", "Connection closed", exception)
        }

        try {
            hubConnection?.start()?.blockingAwait()
            Log.d("SignalR", "Connected to $hubUrl")
        } catch (e: Exception) {
            Log.e("SignalR", "Error connecting", e)
        }
    }

    fun stop() {
        hubConnection?.stop()
    }
}
