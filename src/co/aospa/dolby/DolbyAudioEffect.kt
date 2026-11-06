/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby

import android.media.audiofx.AudioEffect
import android.util.Log
import co.aospa.dolby.DolbyConstants.Companion.dlog
import co.aospa.dolby.DolbyConstants.DsParam
import java.util.UUID

class DolbyAudioEffect(priority: Int, audioSession: Int) :
    AudioEffect(EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, priority, audioSession) {

    var dsOn: Boolean
        get() = getIntParam(EFFECT_PARAM_ENABLE) == 1
        set(value) {
            try {
                enabled = value
                setIntParam(EFFECT_PARAM_ENABLE, if (value) 1 else 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set dsOn: ${e.message}")
            }
        }

    var profile: Int
        get() = getIntParam(EFFECT_PARAM_PROFILE)
        set(value) {
            setIntParam(EFFECT_PARAM_PROFILE, value)
        }

    private fun setIntParam(param: Int, value: Int) {
        dlog(TAG, "setIntParam($param, $value)")
        val buf = ByteArray(12)
        int32ToByteArray(param, buf, 0)
        int32ToByteArray(1, buf, 4)
        int32ToByteArray(value, buf, 8)
        try {
            val status = setParameter(EFFECT_PARAM_CPDP_VALUES, buf)
            if (status < 0) {
                dlog(TAG, "setIntParam($param, $value) returned status: $status")
            }
        } catch (e: Exception) {
            dlog(TAG, "setIntParam($param, $value) failed: ${e.message}")
        }
    }

    private fun getIntParam(param: Int): Int {
        val buf = ByteArray(12)
        int32ToByteArray(param, buf, 0)
        return try {
            val status = getParameter(EFFECT_PARAM_CPDP_VALUES + param, buf)
            if (status >= 0) {
                byteArrayToInt32(buf).also { dlog(TAG, "getIntParam($param): $it") }
            } else {
                dlog(TAG, "getIntParam($param) returned status: $status")
                -1
            }
        } catch (e: Exception) {
            dlog(TAG, "getIntParam($param) failed: ${e.message}")
            -1
        }
    }

    fun resetProfileSpecificSettings(profile: Int = this.profile) {
        dlog(TAG, "resetProfileSpecificSettings: profile=$profile")
        try {
            setIntParam(EFFECT_PARAM_RESET_PROFILE_SETTINGS, profile)
        } catch (e: Exception) {
            dlog(TAG, "resetProfileSpecificSettings failed: ${e.message}")
        }
    }

    fun setDapParameter(param: DsParam, values: IntArray, profile: Int = this.profile) {
        dlog(TAG, "setDapParameter: profile=$profile param=$param")
        val length = values.size
        val buf = ByteArray((length + 4) * 4)
        int32ToByteArray(EFFECT_PARAM_SET_PROFILE_PARAMETER, buf, 0)
        int32ToByteArray(length + 1, buf, 4)
        int32ToByteArray(profile, buf, 8)
        int32ToByteArray(param.id, buf, 12)
        int32ArrayToByteArray(values, buf, 16)
        try {
            val status = setParameter(EFFECT_PARAM_CPDP_VALUES, buf)
            if (status < 0) {
                dlog(TAG, "setDapParameter($param) returned status: $status")
            }
        } catch (e: Exception) {
            dlog(TAG, "setDapParameter($param) failed: ${e.message}")
        }
    }

    fun setDapParameter(param: DsParam, enable: Boolean, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(if (enable) 1 else 0), profile)

    fun setDapParameter(param: DsParam, value: Int, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(value), profile)

    fun getDapParameter(param: DsParam, profile: Int = this.profile): IntArray {
        dlog(TAG, "getDapParameter: profile=$profile param=$param")
        val length = param.length
        val buf = ByteArray((length + 2) * 4)
        val p = (param.id shl 16) + (profile shl 8) + EFFECT_PARAM_GET_PROFILE_PARAMETER
        return try {
            val status = getParameter(p, buf)
            if (status >= 0) {
                byteArrayToInt32Array(buf, length)
            } else {
                dlog(TAG, "getDapParameter($param) returned status: $status")
                IntArray(length)
            }
        } catch (e: Exception) {
            dlog(TAG, "getDapParameter($param) failed: ${e.message}")
            IntArray(length)
        }
    }

    fun getDapParameterBool(param: DsParam, profile: Int = this.profile): Boolean =
        getDapParameter(param, profile)[0] == 1

    fun getDapParameterInt(param: DsParam, profile: Int = this.profile): Int =
        getDapParameter(param, profile)[0]

    companion object {
        private const val TAG = "DolbyAudioEffect"
        private val EFFECT_TYPE_DAP = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")

        private const val EFFECT_PARAM_ENABLE = 0
        private const val EFFECT_PARAM_CPDP_VALUES = 5
        private const val EFFECT_PARAM_PROFILE = 0xA000000
        private const val EFFECT_PARAM_SET_PROFILE_PARAMETER = 0x1000000
        private const val EFFECT_PARAM_GET_PROFILE_PARAMETER = 0x1000005
        private const val EFFECT_PARAM_RESET_PROFILE_SETTINGS = 0xC000000

        private fun int32ToByteArray(value: Int, dst: ByteArray, index: Int) {
            var idx = index
            dst[idx++] = (value and 0xff).toByte()
            dst[idx++] = ((value ushr 8) and 0xff).toByte()
            dst[idx++] = ((value ushr 16) and 0xff).toByte()
            dst[idx] = ((value ushr 24) and 0xff).toByte()
        }

        private fun byteArrayToInt32(ba: ByteArray): Int {
            return ((ba[3].toInt() and 0xff) shl 24) or
                ((ba[2].toInt() and 0xff) shl 16) or
                ((ba[1].toInt() and 0xff) shl 8) or
                (ba[0].toInt() and 0xff)
        }

        private fun int32ArrayToByteArray(src: IntArray, dst: ByteArray, index: Int) {
            var idx = index
            for (x in src) {
                dst[idx++] = (x and 0xff).toByte()
                dst[idx++] = ((x ushr 8) and 0xff).toByte()
                dst[idx++] = ((x ushr 16) and 0xff).toByte()
                dst[idx++] = ((x ushr 24) and 0xff).toByte()
            }
        }

        private fun byteArrayToInt32Array(ba: ByteArray, dstLength: Int): IntArray {
            val srcLength = ba.size shr 2
            val dst = IntArray(dstLength.coerceAtMost(srcLength))
            for (i in dst.indices) {
                dst[i] =
                    ((ba[i * 4 + 3].toInt() and 0xff) shl 24) or
                        ((ba[i * 4 + 2].toInt() and 0xff) shl 16) or
                        ((ba[i * 4 + 1].toInt() and 0xff) shl 8) or
                        (ba[i * 4].toInt() and 0xff)
            }
            return dst
        }
    }
}
