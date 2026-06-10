package com.apurv.metaremotecapture

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.apurv.metaremotecapture.camera.YuvToBitmapConverter
import com.apurv.metaremotecapture.model.AppSection
import com.apurv.metaremotecapture.model.CaptureMode
import com.apurv.metaremotecapture.model.GalleryPhoto
import com.apurv.metaremotecapture.model.RemoteCaptureState
import com.apurv.metaremotecapture.ui.RemoteCaptureScreen
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addStream
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamError
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.meta.wearable.dat.core.types.Permission as WearablePermission

class MainActivity : ComponentActivity() {
  private var uiState by mutableStateOf(RemoteCaptureState())

  private var session: DeviceSession? = null
  private var stream: Stream? = null
  private var registrationJob: Job? = null
  private var devicesJob: Job? = null
  private val deviceMetadataJobs = mutableMapOf<DeviceIdentifier, Job>()
  private val devicesMetadata = mutableMapOf<DeviceIdentifier, Device>()
  private var sessionStateJob: Job? = null
  private var sessionErrorJob: Job? = null
  private var streamStateJob: Job? = null
  private var streamErrorJob: Job? = null
  private var videoJob: Job? = null
  private var captureJob: Job? = null
  private var galleryJob: Job? = null

  private val androidPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.all { it }
        uiState =
            uiState.copy(
                androidPermissionsGranted = granted,
                error = if (granted) null else "Android Bluetooth/camera permissions are required.",
            )
        if (granted) {
          initializeDat()
        }
      }

  private val wearablePermissionLauncher =
      registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        when (result.getOrDefault(PermissionStatus.Denied)) {
          PermissionStatus.Granted -> startPreview()
          PermissionStatus.Denied ->
              uiState = uiState.copy(error = "Glasses camera permission was denied.")
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        RemoteCaptureScreen(
            state = uiState,
            onRegister = { Wearables.startRegistration(this) },
            onRequestCamera = { requestWearableCamera() },
            onStartPreview = { startPreviewWithPermissionCheck() },
            onCapture = { capturePhoto() },
            onStopCapture = { stopCaptureSequence() },
            onStop = { stopPreview() },
            onSelectSection = { section -> uiState = uiState.copy(selectedSection = section) },
            onSelectCaptureMode = { mode -> uiState = uiState.copy(captureMode = mode) },
            onSelectBurstCount = { count -> uiState = uiState.copy(burstCount = count) },
            onSelectIntervalSeconds = { seconds -> uiState = uiState.copy(intervalSeconds = seconds) },
            onRefreshGallery = { refreshGallery() },
            onOpenPhoto = { uri -> openPhoto(uri) },
        )
      }
    }
    refreshGallery()
  }

  override fun onStart() {
    super.onStart()
    androidPermissionLauncher.launch(REQUIRED_ANDROID_PERMISSIONS)
  }

  override fun onDestroy() {
    stopPreview()
    registrationJob?.cancel()
    devicesJob?.cancel()
    galleryJob?.cancel()
    deviceMetadataJobs.values.forEach { it.cancel() }
    deviceMetadataJobs.clear()
    super.onDestroy()
  }

  private fun initializeDat() {
    if (uiState.datInitialized) {
      return
    }

    Wearables.initialize(this)
        .onSuccess {
          uiState =
              uiState.copy(
                  datInitialized = true,
                  status = "Turn on Meta AI Developer Mode, then register the glasses.",
                  error = null,
              )
          startWearablesMonitoring()
        }
        .onFailure { error, _ ->
          uiState = uiState.copy(error = "DAT init failed: ${error.description}")
        }
  }

  private fun startWearablesMonitoring() {
    registrationJob?.cancel()
    devicesJob?.cancel()

    registrationJob =
        lifecycleScope.launch {
          Wearables.registrationState.collect { state ->
            uiState =
                uiState.copy(
                    registrationState = state,
                    status =
                        if (state == RegistrationState.REGISTERED) {
                          "Registered. Start preview, then capture."
                        } else {
                          "Register the app with Meta AI."
                        },
                )
          }
        }

    devicesJob =
        lifecycleScope.launch {
          Wearables.devices.collect { devices ->
            refreshDeviceMetadataJobs(devices)
            updateDeviceUi()
          }
        }
  }

  private fun refreshDeviceMetadataJobs(deviceIds: Set<DeviceIdentifier>) {
    val removedIds = deviceMetadataJobs.keys - deviceIds
    removedIds.forEach { id ->
      deviceMetadataJobs.remove(id)?.cancel()
      devicesMetadata.remove(id)
    }

    val addedIds = deviceIds - deviceMetadataJobs.keys
    addedIds.forEach { id ->
      deviceMetadataJobs[id] =
          lifecycleScope.launch {
            Wearables.devicesMetadata[id]?.collect { device ->
              devicesMetadata[id] = device
              Log.d(
                  TAG,
                  "Device metadata: id=$id name=${device.name} type=${device.deviceType} " +
                      "link=${device.linkState} compatibility=${device.compatibility}",
              )
              updateDeviceUi()
            }
          }
    }
  }

  private fun updateDeviceUi() {
    val selected =
        devicesMetadata.entries.firstOrNull { it.value.linkState == LinkState.CONNECTED }
            ?: devicesMetadata.entries.firstOrNull()
    val summary =
        if (devicesMetadata.isEmpty()) {
          "Device: none visible to DAT"
        } else {
          devicesMetadata.entries.joinToString(separator = "\n") { (id, device) ->
            val shortId = id.toString().takeLast(5)
            "${device.name.ifEmpty { shortId }}: ${device.deviceType.description}, " +
                "${device.linkState}, ${device.compatibility}"
          }
        }

    uiState =
        uiState.copy(
            hasDevice = devicesMetadata.isNotEmpty(),
            selectedDeviceId = selected?.key,
            deviceSummary = summary,
        )
  }

  private fun requestWearableCamera() {
    wearablePermissionLauncher.launch(WearablePermission.CAMERA)
  }

  private fun startPreviewWithPermissionCheck() {
    lifecycleScope.launch {
      Wearables.checkPermissionStatus(WearablePermission.CAMERA)
          .onSuccess { status ->
            if (status == PermissionStatus.Granted) {
              startPreview()
            } else {
              requestWearableCamera()
            }
          }
          .onFailure { error, _ ->
            uiState = uiState.copy(error = "Camera permission check failed: ${error.description}")
          }
    }
  }

  private fun startPreview() {
    stopPreview()
    uiState = uiState.copy(status = "Starting glasses preview...", error = null)

    val selectedDeviceId = uiState.selectedDeviceId
    val selector =
        if (selectedDeviceId != null) {
          Log.d(TAG, "Starting session with specific device $selectedDeviceId")
          SpecificDeviceSelector(selectedDeviceId)
        } else {
          Log.d(TAG, "Starting session with auto device selector")
          AutoDeviceSelector()
        }

    Wearables.createSession(selector)
        .onSuccess { createdSession ->
          Log.d(TAG, "Created glasses session")
          session = createdSession
          collectSession(createdSession)
          createdSession.start()
        }
        .onFailure { error, _ ->
          uiState = uiState.copy(error = "Could not create glasses session: ${error.description}")
        }
  }

  private fun collectSession(createdSession: DeviceSession) {
    sessionErrorJob =
        lifecycleScope.launch {
          createdSession.errors.collect { error ->
            uiState = uiState.copy(error = "Session error: ${error.description}")
            stopPreview()
          }
        }

    sessionStateJob =
        lifecycleScope.launch {
          createdSession.state.collect { state ->
            Log.d(TAG, "Session state: $state")
            uiState = uiState.copy(sessionState = state)
            if (state == DeviceSessionState.STARTED && stream == null) {
              attachStream(createdSession)
            }
          }
        }
  }

  private fun attachStream(createdSession: DeviceSession) {
    Log.d(TAG, "Attaching camera stream")
    createdSession
        .addStream(StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 15))
        .onSuccess { addedStream ->
          Log.d(TAG, "Camera stream attached")
          stream = addedStream
          collectStream(addedStream)
          addedStream.start().onFailure { error, _ ->
            uiState = uiState.copy(error = "Could not start preview: ${error.description}")
          }
        }
        .onFailure { error, _ ->
          uiState = uiState.copy(error = "Could not attach camera stream: ${error.description}")
        }
  }

  private fun collectStream(addedStream: Stream) {
    streamStateJob =
        lifecycleScope.launch {
          addedStream.state.collect { state ->
            Log.d(TAG, "Stream state: $state")
            uiState =
                uiState.copy(
                    streamState = state,
                    status =
                        if (state == StreamState.STREAMING) {
                          "Preview live. Tap Capture when ready."
                        } else {
                          "Preview state: $state"
                        },
                )
          }
        }

    streamErrorJob =
        lifecycleScope.launch {
          addedStream.errorStream.collect { error ->
            Log.w(TAG, "Stream error: ${error.description}")
            if (error != StreamError.STREAM_ERROR) {
              uiState = uiState.copy(error = "Stream error: ${error.description}")
              stopPreview()
            }
          }
        }

    videoJob =
        lifecycleScope.launch(Dispatchers.Default) {
          addedStream.videoStream.collect { frame ->
            val bitmap = YuvToBitmapConverter.convert(frame.buffer, frame.width, frame.height)
            if (bitmap != null) {
              withContext(Dispatchers.Main) {
                uiState =
                    uiState.copy(
                        previewBitmap = bitmap,
                        previewFrameCount = uiState.previewFrameCount + 1,
                    )
              }
            }
          }
        }
  }

  private fun capturePhoto() {
    when (uiState.captureMode) {
      CaptureMode.SINGLE -> startSingleCapture()
      CaptureMode.BURST -> startBurstCapture()
      CaptureMode.INTERVAL -> {
        if (uiState.isIntervalRunning) {
          stopCaptureSequence()
        } else {
          startIntervalCapture()
        }
      }
    }
  }

  private fun startSingleCapture() {
    val currentStream = stream
    if (currentStream == null || uiState.streamState != StreamState.STREAMING) {
      uiState = uiState.copy(error = "Start the glasses preview before capturing.")
      return
    }

    captureJob?.cancel()
    captureJob =
        lifecycleScope.launch {
          uiState =
              uiState.copy(
                  isCapturing = true,
                  captureProgress = "Capturing",
                  error = null,
                  status = "Capturing...",
              )
          try {
            val savedUri = captureAndSaveOnce(currentStream)
            uiState =
                uiState.copy(
                    isCapturing = false,
                    captureProgress = null,
                    lastSavedUri = savedUri,
                    status = "Saved to Pictures/MREx.",
                    error = null,
                )
            refreshGallery()
          } catch (error: Exception) {
            uiState =
                uiState.copy(
                    isCapturing = false,
                    captureProgress = null,
                    error = "Capture failed: ${error.message ?: "Unknown error"}",
                )
          }
        }
  }

  private fun startBurstCapture() {
    val currentStream = stream
    if (currentStream == null || uiState.streamState != StreamState.STREAMING) {
      uiState = uiState.copy(error = "Start the glasses preview before capturing.")
      return
    }

    val total = uiState.burstCount
    captureJob?.cancel()
    captureJob =
        lifecycleScope.launch {
          var saved = 0
          var lastUri: Uri? = null
          uiState =
              uiState.copy(
                  isCapturing = true,
                  captureProgress = "Burst 0/$total",
                  error = null,
                  status = "Burst capture running...",
              )
          try {
            repeat(total) { index ->
              uiState =
                  uiState.copy(
                      captureProgress = "Burst ${index + 1}/$total",
                      status = "Capturing ${index + 1} of $total...",
                  )
              lastUri = captureAndSaveOnce(currentStream)
              saved++
              if (index < total - 1) {
                delay(BURST_CAPTURE_GAP_MS)
              }
            }
            uiState =
                uiState.copy(
                    isCapturing = false,
                    captureProgress = null,
                    lastSavedUri = lastUri,
                    status = "Saved $saved burst photos to Pictures/MREx.",
                    error = null,
                )
            refreshGallery()
          } catch (error: Exception) {
            uiState =
                uiState.copy(
                    isCapturing = false,
                    captureProgress = null,
                    lastSavedUri = lastUri,
                    status = if (saved > 0) "Saved $saved photos before burst stopped." else uiState.status,
                    error = "Burst failed: ${error.message ?: "Unknown error"}",
                )
            refreshGallery()
          }
        }
  }

  private fun startIntervalCapture() {
    val currentStream = stream
    if (currentStream == null || uiState.streamState != StreamState.STREAMING) {
      uiState = uiState.copy(error = "Start the glasses preview before capturing.")
      return
    }

    val intervalSeconds = uiState.intervalSeconds
    captureJob?.cancel()
    captureJob =
        lifecycleScope.launch {
          var saved = 0
          var lastUri: Uri? = null
          uiState =
              uiState.copy(
                  isCapturing = true,
                  isIntervalRunning = true,
                  captureProgress = "Interval ready",
                  error = null,
                  status = "Interval capture every ${intervalSeconds}s.",
              )
          try {
            while (isActive) {
              uiState =
                  uiState.copy(
                      captureProgress = "Interval ${saved + 1}",
                      status = "Interval shot ${saved + 1}...",
                  )
              lastUri = captureAndSaveOnce(currentStream)
              saved++
              uiState =
                  uiState.copy(
                      lastSavedUri = lastUri,
                      status = "Interval running. Saved $saved photos.",
                  )
              refreshGallery()
              delay(intervalSeconds * 1000L)
            }
          } catch (error: Exception) {
            uiState =
                uiState.copy(
                    error = "Interval failed: ${error.message ?: "Unknown error"}",
                )
          } finally {
            uiState =
                uiState.copy(
                    isCapturing = false,
                    isIntervalRunning = false,
                    captureProgress = null,
                    lastSavedUri = lastUri,
                    status =
                        if (saved > 0) {
                          "Interval stopped. Saved $saved photos."
                        } else {
                          "Interval stopped."
                        },
                )
          }
        }
  }

  private fun stopCaptureSequence() {
    captureJob?.cancel()
    captureJob = null
    uiState =
        uiState.copy(
            isCapturing = false,
            isIntervalRunning = false,
            captureProgress = null,
            status = "Capture stopped.",
        )
  }

  private suspend fun captureAndSaveOnce(currentStream: Stream): Uri? {
    var savedUri: Uri? = null
    var failure: String? = null
    currentStream
        .capturePhoto()
        .onSuccess { photo ->
          val bitmap = decodePhoto(photo)
          savedUri = withContext(Dispatchers.IO) { saveBitmap(bitmap) }
        }
        .onFailure { error, _ -> failure = error.description }

    failure?.let { throw IllegalStateException(it) }
    return savedUri
  }

  private fun decodePhoto(photo: PhotoData): Bitmap {
    return when (photo) {
      is PhotoData.Bitmap -> photo.bitmap
      is PhotoData.HEIC -> {
        val bytes = ByteArray(photo.data.remaining())
        photo.data.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        applyExifTransform(bitmap, bytes)
      }
    }
  }

  private fun applyExifTransform(bitmap: Bitmap, bytes: ByteArray): Bitmap {
    val matrix = Matrix()
    val exif =
        try {
          ByteArrayInputStream(bytes).use { ExifInterface(it) }
        } catch (_: Exception) {
          null
        }

    when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      else -> Unit
    }

    if (matrix.isIdentity) {
      return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }

  private fun saveBitmap(bitmap: Bitmap): Uri? {
    val name =
        "mrex_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
    val values =
        ContentValues().apply {
          put(MediaStore.Images.Media.DISPLAY_NAME, name)
          put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
          put(
              MediaStore.Images.Media.RELATIVE_PATH,
              "${Environment.DIRECTORY_PICTURES}/MREx",
          )
          put(MediaStore.Images.Media.IS_PENDING, 1)
        }

    val resolver = contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    resolver.openOutputStream(uri)?.use { output ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
    }

    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return uri
  }

  private fun refreshGallery() {
    galleryJob?.cancel()
    galleryJob =
        lifecycleScope.launch {
          uiState = uiState.copy(isGalleryLoading = true, galleryError = null)
          try {
            val photos = withContext(Dispatchers.IO) { loadGalleryPhotos() }
            uiState =
                uiState.copy(
                    galleryPhotos = photos,
                    isGalleryLoading = false,
                    galleryError = null,
                )
          } catch (error: Exception) {
            uiState =
                uiState.copy(
                    isGalleryLoading = false,
                    galleryError = error.message ?: "Could not load gallery.",
                )
          }
        }
  }

  private fun loadGalleryPhotos(): List<GalleryPhoto> {
    val projection =
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
        )
    val relativePath = "${Environment.DIRECTORY_PICTURES}/MREx"
    val selection =
        "${MediaStore.Images.Media.RELATIVE_PATH}=? OR ${MediaStore.Images.Media.RELATIVE_PATH}=?"
    val selectionArgs = arrayOf(relativePath, "$relativePath/")
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    val photos = mutableListOf<GalleryPhoto>()

    contentResolver
        .query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )
        ?.use { cursor ->
          val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
          val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
          val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
          val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
          val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

          while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn) ?: "MREx photo"
            val dateTaken = cursor.getLong(takenColumn)
            val dateAdded = cursor.getLong(addedColumn) * 1000L
            val timestamp = if (dateTaken > 0) dateTaken else dateAdded
            photos +=
                GalleryPhoto(
                    uri =
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        ),
                    displayName = name,
                    timestampMillis = timestamp,
                    sizeBytes = cursor.getLong(sizeColumn),
                )
          }
        }

    return photos.sortedByDescending { it.timestampMillis }
  }

  private fun openPhoto(uri: Uri) {
    val intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(intent)
  }

  private fun stopPreview() {
    captureJob?.cancel()
    videoJob?.cancel()
    streamStateJob?.cancel()
    streamErrorJob?.cancel()
    sessionStateJob?.cancel()
    sessionErrorJob?.cancel()
    videoJob = null
    captureJob = null
    streamStateJob = null
    streamErrorJob = null
    sessionStateJob = null
    sessionErrorJob = null

    stream?.stop()
    stream = null
    session?.stop()
    session = null

    uiState =
        uiState.copy(
            streamState = StreamState.STOPPED,
            sessionState = null,
            status = "Preview stopped.",
            isCapturing = false,
            isIntervalRunning = false,
            captureProgress = null,
        )
  }

  companion object {
    private const val TAG = "MetaRemoteCapture"
    private const val BURST_CAPTURE_GAP_MS = 900L

    private val REQUIRED_ANDROID_PERMISSIONS =
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
        )
  }
}
