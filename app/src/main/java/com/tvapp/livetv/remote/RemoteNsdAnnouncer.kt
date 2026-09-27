package com.tvapp.livetv.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.Locale

/**
 * Source-management sprint: advertises the embedded management server over
 * NSD/mDNS as `_tvapp._tcp.` so the phone client can discover
 * `http://<tv-ip>:<port>` without the user typing the address. Registration
 * failures are silently swallowed — discovery is a convenience, the manual
 * address entry keeps working.
 */
class RemoteNsdAnnouncer(context: Context, private val port: Int) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun register() {
        if (registrationListener != null) return
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        runCatching { nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun unregister() {
        val listener = registrationListener ?: return
        runCatching { nsdManager.unregisterService(listener) }
        registrationListener = null
    }

    companion object {
        private const val SERVICE_NAME = "TVApp"
        private const val SERVICE_TYPE = "_tvapp._tcp."

        /** Canonical type string, exposed for tests and the phone client. */
        fun advertisedType(): String = SERVICE_TYPE.lowercase(Locale.ROOT)
    }
}
