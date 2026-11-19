package com.example.telecom

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class NetworkQualitySample(
    val timestampMs: Long,
    val transportLabel: String,
    val latencyMs: Int,
    val jitterMs: Int,
    val packetLossPct: Float,
    val mosScore: Float,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
    val codecLabel: String,
    val encrypted: Boolean
)

/**
 * Live network diagnostics engine for SIM cellular calling:
 * - Samples Android ConnectivityManager transport & bandwidth + live TCP RTT
 * - Computes ITU-T G.107 E-Model Mean Opinion Score (MOS 1.0 - 4.5)
 */
class NetworkQualityEngine(private val context: Context) {

    /**
     * Samples live Android network capabilities and executes 3 quick TCP connect RTT
     * probes to compute real latency, jitter, packet loss %, and ITU-T E-model MOS score.
     */
    suspend fun sampleLiveNetworkQuality(): NetworkQualitySample = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null

        val transportLabel = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular LTE/5G"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Calling"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Active Network"
        }
        val downstreamKbps = caps?.linkDownstreamBandwidthKbps ?: 0
        val upstreamKbps = caps?.linkUpstreamBandwidthKbps ?: 0

        val probeHost = "1.1.1.1"
        val probePort = 443

        val rtts = mutableListOf<Int>()
        var failures = 0
        repeat(3) {
            val start = System.nanoTime()
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(probeHost, probePort), 1400)
                }
                val rtt = ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
                rtts.add(rtt)
            } catch (_: Exception) {
                failures++
            }
        }

        val avgLatency = if (rtts.isNotEmpty()) rtts.average().toInt() else 0
        val jitter = if (rtts.size >= 2) {
            var diffSum = 0
            for (i in 1 until rtts.size) {
                diffSum += abs(rtts[i] - rtts[i - 1])
            }
            diffSum / (rtts.size - 1)
        } else 0

        val packetLossPct = (failures.toFloat() / 3f) * 100f
        val mos = if (rtts.isEmpty() || caps == null) {
            1.0f
        } else {
            calculateMos(
                latencyMs = avgLatency,
                jitterMs = jitter,
                packetLossPct = packetLossPct
            )
        }

        NetworkQualitySample(
            timestampMs = System.currentTimeMillis(),
            transportLabel = transportLabel,
            latencyMs = avgLatency,
            jitterMs = jitter,
            packetLossPct = packetLossPct,
            mosScore = mos,
            downstreamKbps = downstreamKbps,
            upstreamKbps = upstreamKbps,
            codecLabel = "AMR-WB HD • VoLTE",
            encrypted = false
        )
    }

    /**
     * ITU-T G.107 E-Model approximation converting effective latency, jitter, and packet loss
     * into a standard 1.0 - 4.5 Mean Opinion Score (MOS).
     */
    fun calculateMos(latencyMs: Int, jitterMs: Int, packetLossPct: Float): Float {
        val effectiveLatency = latencyMs + (jitterMs * 2.0) + 10.0
        var rFactor = if (effectiveLatency < 160.0) {
            93.2 - (effectiveLatency / 40.0)
        } else {
            93.2 - ((effectiveLatency - 120.0) / 10.0)
        }
        rFactor -= packetLossPct * 2.5
        rFactor = max(0.0, min(100.0, rFactor))
        val mos = 1.0 + 0.035 * rFactor + 0.000007 * rFactor * (rFactor - 60.0) * (100.0 - rFactor)
        return mos.toFloat().coerceIn(1.0f, 4.5f)
    }
}
