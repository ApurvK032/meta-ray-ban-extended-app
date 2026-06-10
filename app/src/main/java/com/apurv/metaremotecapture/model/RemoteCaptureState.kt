package com.apurv.metaremotecapture.model

import android.graphics.Bitmap
import android.net.Uri
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState

internal data class RemoteCaptureState(
    val selectedSection: AppSection = AppSection.CAPTURE,
    val captureMode: CaptureMode = CaptureMode.SINGLE,
    val burstCount: Int = 3,
    val intervalSeconds: Int = 5,
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
    val isIntervalRunning: Boolean = false,
    val captureProgress: String? = null,
    val lastSavedUri: Uri? = null,
    val galleryPhotos: List<GalleryPhoto> = emptyList(),
    val isGalleryLoading: Boolean = false,
    val galleryError: String? = null,
    val status: String = "Grant permissions, then register the glasses.",
    val error: String? = null,
)
