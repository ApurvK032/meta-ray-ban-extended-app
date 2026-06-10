package com.apurv.metaremotecapture

import android.Manifest
import android.app.Activity
import android.content.ContentValues
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
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
            onStop = { stopPreview() },
        )
      }
    }
  }

  override fun onStart() {
    super.onStart()
    androidPermissionLauncher.launch(REQUIRED_ANDROID_PERMISSIONS)
  }

  override fun onDestroy() {
    stopPreview()
    registrationJob?.cancel()
    devicesJob?.cancel()
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
    val currentStream = stream
    if (currentStream == null || uiState.streamState != StreamState.STREAMING) {
      uiState = uiState.copy(error = "Start the glasses preview before capturing.")
      return
    }

    uiState = uiState.copy(isCapturing = true, error = null, status = "Capturing...")
    lifecycleScope.launch {
      currentStream
          .capturePhoto()
          .onSuccess { photo ->
            val bitmap = decodePhoto(photo)
            val savedUri = withContext(Dispatchers.IO) { saveBitmap(bitmap) }
            uiState =
                uiState.copy(
                    isCapturing = false,
                    lastSavedUri = savedUri,
                    status = "Saved to Pictures/MREx.",
                    error = null,
                )
          }
          .onFailure { error, _ ->
            uiState =
                uiState.copy(
                    isCapturing = false,
                    error = "Capture failed: ${error.description}",
                )
          }
    }
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
        "meta_remote_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
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

  private fun stopPreview() {
    videoJob?.cancel()
    streamStateJob?.cancel()
    streamErrorJob?.cancel()
    sessionStateJob?.cancel()
    sessionErrorJob?.cancel()
    videoJob = null
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
        )
  }

  companion object {
    private const val TAG = "MetaRemoteCapture"

    private val REQUIRED_ANDROID_PERMISSIONS =
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
        )
  }
}
