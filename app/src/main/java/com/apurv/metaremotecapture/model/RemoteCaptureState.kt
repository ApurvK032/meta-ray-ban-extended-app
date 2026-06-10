package com.apurv.metaremotecapture.model

import android.graphics.Bitmap
import android.net.Uri
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState

internal data class RemoteCaptureState(
    val androidPermissionsGranted: Boolean = false,
    val datInitialized: Boolean = false,
    val registrationState: RegistrationState? = null,
    val hasDevice: Boolean = false,
    val selectedDeviceId: DeviceIdentifier? = null,
    val deviceSummary: String = "Device: --",
    val sessionState: DeviceSessionState? = null,
    val streamState: StreamState = StreamState.STOPPED,
    val previewBitmap: Bitmap? = null,
    val previewFrameCount: Int = 0,
    val isCapturing: Boolean = false,
    val lastSavedUri: Uri? = null,
    val status: String = "Grant permissions, then register the glasses.",
    val error: String? = null,
)
