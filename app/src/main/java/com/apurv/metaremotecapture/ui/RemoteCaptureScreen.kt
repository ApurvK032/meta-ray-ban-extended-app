package com.apurv.metaremotecapture.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apurv.metaremotecapture.model.RemoteCaptureState
import com.meta.wearable.dat.camera.types.StreamState

@Composable
internal fun RemoteCaptureScreen(
    state: RemoteCaptureState,
    onRegister: () -> Unit,
    onRequestCamera: () -> Unit,
    onStartPreview: () -> Unit,
    onCapture: () -> Unit,
    onStop: () -> Unit,
) {
  Surface(color = Color(0xFFF6F7F9), modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
          text = "MREx",
          style = MaterialTheme.typography.headlineSmall,
          color = Color(0xFF1F2A33),
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = state.status,
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFF53606A),
      )

      ActionButtons(
          state = state,
          onRegister = onRegister,
          onRequestCamera = onRequestCamera,
          onStartPreview = onStartPreview,
          onCapture = onCapture,
          onStop = onStop,
      )

      PreviewSurface(state.previewBitmap, state.previewFrameCount)

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatusChip("Android", state.androidPermissionsGranted)
        StatusChip("DAT", state.datInitialized)
        StatusChip("Device", state.hasDevice)
      }

      Text(
          text = "Registration: ${state.registrationState ?: "--"}",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF53606A),
      )
      Text(
          text = state.deviceSummary,
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF53606A),
      )
      Text(
          text = "Stream: ${state.streamState}",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF53606A),
      )

      state.error?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB42318),
        )
      }

      state.lastSavedUri?.let {
        Text(
            text = "Last saved: $it",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF176A43),
        )
      }

      Spacer(modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun ActionButtons(
    state: RemoteCaptureState,
    onRegister: () -> Unit,
    onRequestCamera: () -> Unit,
    onStartPreview: () -> Unit,
    onCapture: () -> Unit,
    onStop: () -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(onClick = onRegister, modifier = Modifier.weight(1f)) { Text("Register") }
    OutlinedButton(onClick = onRequestCamera, modifier = Modifier.weight(1f)) {
      Text("Allow Camera")
    }
  }

  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(onClick = onStartPreview, modifier = Modifier.weight(1f)) {
      Text("Start Preview")
    }
    OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop") }
  }

  Button(
      onClick = onCapture,
      enabled = state.streamState == StreamState.STREAMING && !state.isCapturing,
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A7CFF)),
      modifier = Modifier.fillMaxWidth().height(52.dp),
  ) {
    Text(if (state.isCapturing) "Capturing..." else "Capture Photo")
  }
}

@Composable
private fun PreviewSurface(bitmap: Bitmap?, frameCount: Int) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .heightIn(min = 220.dp, max = 360.dp)
              .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF18212A)),
      contentAlignment = Alignment.Center,
  ) {
    if (bitmap == null) {
      Text("Preview appears here", color = Color(0xFFB8C0C8))
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

@Composable
private fun StatusChip(label: String, enabled: Boolean) {
  val background = if (enabled) Color(0xFFE6F4EA) else Color(0xFFE9ECEF)
  val foreground = if (enabled) Color(0xFF176A43) else Color(0xFF53606A)
  Row(
      modifier =
          Modifier.clip(RoundedCornerShape(99.dp))
              .background(background)
              .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier =
            Modifier.width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(foreground)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(label, color = foreground, style = MaterialTheme.typography.labelMedium)
  }
}
