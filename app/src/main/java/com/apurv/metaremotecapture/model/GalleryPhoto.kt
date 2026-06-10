package com.apurv.metaremotecapture.model

import android.net.Uri

internal data class GalleryPhoto(
    val uri: Uri,
    val displayName: String,
    val timestampMillis: Long,
    val sizeBytes: Long,
)
