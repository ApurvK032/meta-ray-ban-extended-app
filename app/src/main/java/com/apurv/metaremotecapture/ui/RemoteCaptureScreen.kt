package com.apurv.metaremotecapture.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apurv.metaremotecapture.model.AppSection
import com.apurv.metaremotecapture.model.CaptureMode
import com.apurv.metaremotecapture.model.GalleryPhoto
import com.apurv.metaremotecapture.model.RemoteCaptureState
import com.meta.wearable.dat.camera.types.StreamState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RemoteCaptureScreen(
    state: RemoteCaptureState,
    onRegister: () -> Unit,
    onRequestCamera: () -> Unit,
    onStartPreview: () -> Unit,
    onCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onStop: () -> Unit,
    onSelectSection: (AppSection) -> Unit,
    onSelectCaptureMode: (CaptureMode) -> Unit,
    onSelectBurstCount: (Int) -> Unit,
    onSelectIntervalSeconds: (Int) -> Unit,
    onRefreshGallery: () -> Unit,
    onOpenPhoto: (Uri) -> Unit,
) {
  Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AppHeader(state)
      SectionTabs(state.selectedSection, onSelectSection)

      when (state.selectedSection) {
        AppSection.CAPTURE ->
            CaptureSection(
                state = state,
                onCapture = onCapture,
                onStopCapture = onStopCapture,
                onSelectCaptureMode = onSelectCaptureMode,
                onSelectBurstCount = onSelectBurstCount,
                onSelectIntervalSeconds = onSelectIntervalSeconds,
            )
        AppSection.GALLERY ->
            GallerySection(
                state = state,
                onRefreshGallery = onRefreshGallery,
                onOpenPhoto = onOpenPhoto,
            )
        AppSection.CONNECTION ->
            ConnectionSection(
                state = state,
                onRegister = onRegister,
                onRequestCamera = onRequestCamera,
                onStartPreview = onStartPreview,
                onStop = onStop,
            )
      }
    }
  }
}

@Composable
private fun AppHeader(state: RemoteCaptureState) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = "MREx",
          style = MaterialTheme.typography.headlineMedium,
          color = AppColors.TextPrimary,
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = state.status,
          style = MaterialTheme.typography.bodySmall,
          color = AppColors.TextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    LiveBadge(state.streamState == StreamState.STREAMING)
  }
}

@Composable
private fun SectionTabs(selected: AppSection, onSelectSection: (AppSection) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(AppColors.Panel)
              .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    AppSection.entries.forEach { section ->
      val isSelected = section == selected
      Box(
          modifier =
              Modifier.weight(1f)
                  .height(40.dp)
                  .clip(RoundedCornerShape(7.dp))
                  .background(if (isSelected) AppColors.PanelSelected else Color.Transparent)
                  .clickable { onSelectSection(section) },
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = section.label,
            color = if (isSelected) AppColors.TextPrimary else AppColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun CaptureSection(
    state: RemoteCaptureState,
    onCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onSelectCaptureMode: (CaptureMode) -> Unit,
    onSelectBurstCount: (Int) -> Unit,
    onSelectIntervalSeconds: (Int) -> Unit,
) {
  Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    PreviewSurface(
        bitmap = state.previewBitmap,
        frameCount = state.previewFrameCount,
        modifier = Modifier.fillMaxWidth().weight(1f),
    )

    ModePanel(
        state = state,
        onSelectCaptureMode = onSelectCaptureMode,
        onSelectBurstCount = onSelectBurstCount,
        onSelectIntervalSeconds = onSelectIntervalSeconds,
    )

    state.captureProgress?.let { ProgressLine(it) }
    state.error?.let { ErrorLine(it) }

    ShutterButton(
        state = state,
        onCapture = onCapture,
        onStopCapture = onStopCapture,
    )
  }
}

@Composable
private fun ModePanel(
    state: RemoteCaptureState,
    onSelectCaptureMode: (CaptureMode) -> Unit,
    onSelectBurstCount: (Int) -> Unit,
    onSelectIntervalSeconds: (Int) -> Unit,
) {
  Surface(
      color = AppColors.Panel,
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, AppColors.Border),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CaptureMode.entries.forEach { mode ->
          OptionChip(
              label = mode.label,
              selected = state.captureMode == mode,
              enabled = !state.isCapturing,
              onClick = { onSelectCaptureMode(mode) },
          )
        }
      }

      when (state.captureMode) {
        CaptureMode.SINGLE -> Text("Single shot", color = AppColors.TextMuted)
        CaptureMode.BURST ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(3, 5).forEach { count ->
                OptionChip(
                    label = "$count photos",
                    selected = state.burstCount == count,
                    enabled = !state.isCapturing,
                    onClick = { onSelectBurstCount(count) },
                )
              }
            }
        CaptureMode.INTERVAL ->
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf(5, 10, 30).forEach { seconds ->
                OptionChip(
                    label = "${seconds}s",
                    selected = state.intervalSeconds == seconds,
                    enabled = !state.isIntervalRunning,
                    onClick = { onSelectIntervalSeconds(seconds) },
                )
              }
            }
      }
    }
  }
}

@Composable
private fun ShutterButton(
    state: RemoteCaptureState,
    onCapture: () -> Unit,
    onStopCapture: () -> Unit,
) {
  val canCapture = state.streamState == StreamState.STREAMING
  val isStopAction = state.captureMode == CaptureMode.INTERVAL && state.isIntervalRunning
  val label =
      when {
        isStopAction -> "Stop Interval"
        state.isCapturing -> "Capturing..."
        state.captureMode == CaptureMode.BURST -> "Capture ${state.burstCount}"
        state.captureMode == CaptureMode.INTERVAL -> "Start Interval"
        else -> "Capture"
      }
  Button(
      onClick = if (isStopAction) onStopCapture else onCapture,
      enabled = canCapture && (!state.isCapturing || isStopAction),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = if (isStopAction) AppColors.Danger else AppColors.Accent,
              contentColor = AppColors.AccentText,
              disabledContainerColor = AppColors.Disabled,
              disabledContentColor = AppColors.TextMuted,
          ),
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier.fillMaxWidth().height(76.dp),
  ) {
    Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun GallerySection(
    state: RemoteCaptureState,
    onRefreshGallery: () -> Unit,
    onOpenPhoto: (Uri) -> Unit,
) {
  val grouped = remember(state.galleryPhotos) { state.galleryPhotos.groupBy { dateLabel(it) } }
  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = photoCountLabel(state.galleryPhotos.size),
          color = AppColors.TextPrimary,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
      TextButton(onClick = onRefreshGallery) { Text("Refresh", color = AppColors.Accent) }
    }

    state.galleryError?.let { ErrorLine(it) }

    if (state.isGalleryLoading && state.galleryPhotos.isEmpty()) {
      EmptyPanel("Loading gallery")
    } else if (state.galleryPhotos.isEmpty()) {
      EmptyPanel("No MREx photos yet")
    } else {
      LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        grouped.forEach { (date, photos) ->
          item(key = date) { DateHeader(date) }
          items(photos, key = { it.uri.toString() }) { photo ->
            GalleryRow(photo = photo, onOpenPhoto = onOpenPhoto)
          }
        }
      }
    }
  }
}

@Composable
private fun ConnectionSection(
    state: RemoteCaptureState,
    onRegister: () -> Unit,
    onRequestCamera: () -> Unit,
    onStartPreview: () -> Unit,
    onStop: () -> Unit,
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatusTile("Android", state.androidPermissionsGranted, Modifier.weight(1f))
        StatusTile("DAT", state.datInitialized, Modifier.weight(1f))
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatusTile("Device", state.hasDevice, Modifier.weight(1f))
        StatusTile("Stream", state.streamState == StreamState.STREAMING, Modifier.weight(1f))
      }
    }
    item {
      Surface(
          color = AppColors.Panel,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, AppColors.Border),
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          DetailLine("Registration", state.registrationState?.toString() ?: "--")
          DetailLine("Session", state.sessionState?.toString() ?: "--")
          DetailLine("Stream", state.streamState.toString())
          DetailLine("Selected", state.selectedDeviceId?.toString()?.takeLast(8) ?: "--")
        }
      }
    }
    item {
      Surface(
          color = AppColors.Panel,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, AppColors.Border),
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
            text = state.deviceSummary,
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(14.dp),
        )
      }
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedAction("Register", onRegister, Modifier.weight(1f))
          OutlinedAction("Allow Camera", onRequestCamera, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedAction("Start Stream", onStartPreview, Modifier.weight(1f))
          OutlinedAction("Stop", onStop, Modifier.weight(1f))
        }
      }
    }
    state.error?.let { item { ErrorLine(it) } }
  }
}

@Composable
private fun PreviewSurface(bitmap: Bitmap?, frameCount: Int, modifier: Modifier = Modifier) {
  Surface(
      color = AppColors.Preview,
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, AppColors.Border),
      modifier = modifier.heightIn(min = 280.dp),
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (bitmap == null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Box(
              modifier =
                  Modifier.size(54.dp)
                      .clip(CircleShape)
                      .background(AppColors.PanelSelected),
              contentAlignment = Alignment.Center,
          ) {
            Text("MR", color = AppColors.Accent, fontWeight = FontWeight.Bold)
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text("Preview", color = AppColors.TextMuted)
        }
      } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Glasses camera preview frame $frameCount",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}

@Composable
private fun GalleryRow(photo: GalleryPhoto, onOpenPhoto: (Uri) -> Unit) {
  Surface(
      color = AppColors.Panel,
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, AppColors.Border),
      modifier = Modifier.fillMaxWidth().clickable { onOpenPhoto(photo.uri) },
  ) {
    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      GalleryThumbnail(photo.uri)
      Column(
          modifier = Modifier.fillMaxHeight().weight(1f),
          verticalArrangement = Arrangement.Center,
      ) {
        Text(
            text = photo.displayName,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${timeLabel(photo.timestampMillis)}  •  ${sizeLabel(photo.sizeBytes)}",
            color = AppColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun GalleryThumbnail(uri: Uri) {
  val context = LocalContext.current
  var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(uri) {
    bitmap =
        withContext(Dispatchers.IO) {
          runCatching { context.contentResolver.loadThumbnail(uri, Size(180, 180), null) }
              .getOrNull()
        }
  }

  Box(
      modifier =
          Modifier.size(72.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(AppColors.Preview),
      contentAlignment = Alignment.Center,
  ) {
    bitmap?.let {
      Image(
          bitmap = it.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
      )
    } ?: Text("IMG", color = AppColors.TextMuted, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun DateHeader(label: String) {
  Text(
      text = label,
      color = AppColors.TextPrimary,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
private fun OptionChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
  val background =
      when {
        selected -> AppColors.Accent
        else -> AppColors.PanelAlt
      }
  val foreground =
      when {
        !enabled -> AppColors.TextMuted
        selected -> AppColors.AccentText
        else -> AppColors.TextSecondary
      }
  Box(
      modifier =
          Modifier.height(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(if (enabled) background else AppColors.Disabled)
              .clickable(enabled = enabled, onClick = onClick)
              .padding(horizontal = 14.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(label, color = foreground, style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
}

@Composable
private fun StatusTile(label: String, enabled: Boolean, modifier: Modifier = Modifier) {
  Surface(
      color = AppColors.Panel,
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, AppColors.Border),
      modifier = modifier.height(78.dp),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
      Text(label, color = AppColors.TextMuted, style = MaterialTheme.typography.labelMedium)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(9.dp)
                    .clip(CircleShape)
                    .background(if (enabled) AppColors.Success else AppColors.TextMuted)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (enabled) "Ready" else "Waiting",
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun DetailLine(label: String, value: String) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = AppColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    Text(
        value,
        color = AppColors.TextPrimary,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun OutlinedAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  OutlinedButton(
      onClick = onClick,
      modifier = modifier.height(48.dp),
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, AppColors.BorderStrong),
      colors =
          ButtonDefaults.outlinedButtonColors(
              contentColor = AppColors.TextPrimary,
              containerColor = AppColors.Panel,
          ),
  ) {
    Text(label, maxLines = 1)
  }
}

@Composable
private fun LiveBadge(isLive: Boolean) {
  Row(
      modifier =
          Modifier.clip(RoundedCornerShape(8.dp))
              .background(if (isLive) AppColors.LiveBg else AppColors.Panel)
              .padding(horizontal = 10.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier =
            Modifier.size(8.dp)
                .clip(CircleShape)
                .background(if (isLive) AppColors.Success else AppColors.TextMuted)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(if (isLive) "LIVE" else "IDLE", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun ProgressLine(text: String) {
  Text(
      text = text,
      color = AppColors.Accent,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
  )
}

@Composable
private fun ErrorLine(text: String) {
  Surface(
      color = AppColors.ErrorBg,
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, AppColors.ErrorBorder),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Text(text = text, color = AppColors.ErrorText, modifier = Modifier.padding(12.dp))
  }
}

@Composable
private fun EmptyPanel(text: String) {
  Surface(
      color = AppColors.Panel,
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, AppColors.Border),
      modifier = Modifier.fillMaxWidth().height(180.dp),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      Text(text, color = AppColors.TextMuted)
    }
  }
}

private fun dateLabel(photo: GalleryPhoto): String {
  val calendar = Calendar.getInstance().apply { timeInMillis = photo.timestampMillis }
  val today = Calendar.getInstance()
  val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
  return when {
    sameDay(calendar, today) -> "Today"
    sameDay(calendar, yesterday) -> "Yesterday"
    else -> SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(photo.timestampMillis))
  }
}

private fun sameDay(left: Calendar, right: Calendar): Boolean {
  return left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
      left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)
}

private fun timeLabel(timestampMillis: Long): String {
  return SimpleDateFormat("h:mm a", Locale.US).format(Date(timestampMillis))
}

private fun sizeLabel(sizeBytes: Long): String {
  if (sizeBytes <= 0) return "--"
  val mb = sizeBytes / (1024f * 1024f)
  return if (mb >= 1f) {
    String.format(Locale.US, "%.1f MB", mb)
  } else {
    "${sizeBytes / 1024} KB"
  }
}

private fun photoCountLabel(count: Int): String {
  return if (count == 1) "1 photo" else "$count photos"
}

private object AppColors {
  val Background = Color(0xFF101820)
  val Panel = Color(0xFF17222D)
  val PanelAlt = Color(0xFF20303D)
  val PanelSelected = Color(0xFF243746)
  val Preview = Color(0xFF0B1117)
  val Border = Color(0xFF263948)
  val BorderStrong = Color(0xFF3A5364)
  val TextPrimary = Color(0xFFE9F0F5)
  val TextSecondary = Color(0xFFB8C6D1)
  val TextMuted = Color(0xFF7F93A3)
  val Accent = Color(0xFF22D3EE)
  val AccentText = Color(0xFF041318)
  val Success = Color(0xFF39E58C)
  val LiveBg = Color(0xFF123326)
  val Danger = Color(0xFFE85D75)
  val Disabled = Color(0xFF27323B)
  val ErrorBg = Color(0xFF351820)
  val ErrorBorder = Color(0xFF6D2A39)
  val ErrorText = Color(0xFFFFB7C3)
}
