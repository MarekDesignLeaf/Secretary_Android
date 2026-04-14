package com.example.secretary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File

private data class PlantCaptureSlot(
    val id: Int,
    val organ: String,
    val file: File? = null,
    val isRequired: Boolean = false
)

private fun defaultPlantSlots(): List<PlantCaptureSlot> = listOf(
    PlantCaptureSlot(id = 0, organ = "auto", isRequired = true),
    PlantCaptureSlot(id = 1, organ = "leaf"),
    PlantCaptureSlot(id = 2, organ = "flower")
)

private fun slotLabel(slotId: Int): String = when (slotId) {
    0 -> Strings.plantWholePlant
    1 -> Strings.plantLeafDetail
    else -> Strings.plantFlowerOrFruit
}

@Composable
fun PlantRecognitionTab(state: UiState, viewModel: SecretaryViewModel) {
    val context = LocalContext.current
    var slots by remember { mutableStateOf(defaultPlantSlots()) }
    var targetSlotId by remember { mutableStateOf<Int?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var organMenuSlotId by remember { mutableStateOf<Int?>(null) }
    var lastAutoLaunchRequestId by remember { mutableStateOf<Long?>(null) }
    val isVoiceCaptureActive by rememberUpdatedState(state.isPlantVoiceCaptureActive)

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        var updatedSlots = slots
        if (success) {
            val slotId = targetSlotId
            val file = pendingCameraFile
            if (slotId != null && file != null) {
                updatedSlots = slots.map { slot -> if (slot.id == slotId) slot.copy(file = file) else slot }
                slots = updatedSlots
            }
        }
        val autoReadyPhotos = updatedSlots.mapNotNull { slot ->
            slot.file?.let { file -> PlantPhotoUpload(file = file, organ = slot.organ, label = slotLabel(slot.id)) }
        }
        val shouldAutoIdentify = success && isVoiceCaptureActive && autoReadyPhotos.isNotEmpty()
        viewModel.consumePendingPlantCaptureRequest(resumeHotword = !shouldAutoIdentify)
        if (shouldAutoIdentify) {
            viewModel.identifyPlant(autoReadyPhotos)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val slotId = targetSlotId
        var updatedSlots = slots
        if (uri != null && slotId != null) {
            val file = File(context.cacheDir, "plant_gallery_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            updatedSlots = slots.map { slot -> if (slot.id == slotId) slot.copy(file = file) else slot }
            slots = updatedSlots
        }
        val autoReadyPhotos = updatedSlots.mapNotNull { slot ->
            slot.file?.let { file -> PlantPhotoUpload(file = file, organ = slot.organ, label = slotLabel(slot.id)) }
        }
        val shouldAutoIdentify = uri != null && isVoiceCaptureActive && autoReadyPhotos.isNotEmpty()
        viewModel.consumePendingPlantCaptureRequest(resumeHotword = !shouldAutoIdentify)
        if (shouldAutoIdentify) {
            viewModel.identifyPlant(autoReadyPhotos)
        }
    }

    fun launchCamera(slotId: Int) {
        val file = File(context.cacheDir, "plant_photo_${System.currentTimeMillis()}.jpg")
        targetSlotId = slotId
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        cameraLauncher.launch(uri)
    }

    fun launchGallery(slotId: Int) {
        targetSlotId = slotId
        galleryLauncher.launch("image/*")
    }

    LaunchedEffect(state.pendingPlantCaptureRequestId) {
        val requestId = state.pendingPlantCaptureRequestId
        if (requestId != null && requestId != lastAutoLaunchRequestId) {
            lastAutoLaunchRequestId = requestId
            delay(350)
            val nextSlot = slots.firstOrNull { it.file == null } ?: slots.last()
            launchCamera(nextSlot.id)
        }
    }

    val readyPhotos = slots.mapNotNull { slot ->
        slot.file?.let { file -> PlantPhotoUpload(file = file, organ = slot.organ, label = slotLabel(slot.id)) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(Strings.plantRecognitionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(Strings.plantRecognitionHint)
                    if (state.isPlantVoiceCaptureActive) {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.plantVoiceBanner, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(Strings.plantRecognitionVoiceGuide)
                    }
                }
            }
        }

        items(slots, key = { it.id }) { slot ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(slotLabel(slot.id), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (slot.isRequired) Strings.requiredPhoto else Strings.optionalPhoto,
                        color = if (slot.isRequired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (slot.id == 2) {
                        Box {
                            OutlinedButton(onClick = { organMenuSlotId = slot.id }) {
                                Text("${Strings.type}: ${Strings.localizePlantOrgan(slot.organ)}")
                            }
                            DropdownMenu(expanded = organMenuSlotId == slot.id, onDismissRequest = { organMenuSlotId = null }) {
                                listOf("flower", "fruit", "bark", "auto").forEach { organ ->
                                    DropdownMenuItem(
                                        text = { Text(Strings.localizePlantOrgan(organ)) },
                                        onClick = {
                                            slots = slots.map { current ->
                                                if (current.id == slot.id) current.copy(organ = organ) else current
                                            }
                                            organMenuSlotId = null
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text("${Strings.type}: ${Strings.localizePlantOrgan(slot.organ)}")
                    }
                    Text(slot.file?.name ?: Strings.notProvided, color = if (slot.file == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launchCamera(slot.id) }) {
                            Text(Strings.useCamera)
                        }
                        OutlinedButton(onClick = { launchGallery(slot.id) }) {
                            Text(Strings.chooseFromGallery)
                        }
                        if (slot.file != null) {
                            TextButton(onClick = {
                                slots = slots.map { current ->
                                    if (current.id == slot.id) current.copy(file = null) else current
                                }
                            }) {
                                Text(Strings.removePhoto)
                            }
                        }
                    }
                }
            }
        }

        item {
            if (state.plantRecognitionLoading) {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(Strings.plantRecognitionLoading)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.identifyPlant(readyPhotos) },
                        enabled = readyPhotos.isNotEmpty()
                    ) {
                        Text(Strings.identifyPlantAction)
                    }
                    TextButton(onClick = {
                        slots = defaultPlantSlots()
                        viewModel.clearPlantRecognitionResult()
                    }) {
                        Text(Strings.cancel)
                    }
                }
            }
        }

        state.plantRecognitionError?.let { error ->
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }

        item {
            val result = state.selectedPlantRecognition
            if (result == null) {
                Text(Strings.plantNoResultYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Strings.plantBestMatch, fontWeight = FontWeight.Bold)
                        Text(result.display_name.ifBlank { result.scientific_name }, style = MaterialTheme.typography.titleMedium)
                        if (result.scientific_name.isNotBlank() && result.display_name != result.scientific_name) {
                        Text(result.scientific_name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${Strings.confidence}: ${(result.score * 100).toInt()}%")
                    result.family?.takeIf { it.isNotBlank() }?.let { Text("${Strings.familyLabel}: $it") }
                        Text("${Strings.databaseLabel}: ${result.database}")
                        result.guidance?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.plantGuidance, fontWeight = FontWeight.SemiBold)
                            Text(it)
                        }
                        if (result.suggestions.size > 1) {
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.plantAlternatives, fontWeight = FontWeight.SemiBold)
                            result.suggestions.drop(1).forEach { suggestion ->
                                HorizontalDivider()
                                Text("${suggestion.display_name.ifBlank { suggestion.scientific_name }} ${(suggestion.score * 100).toInt()}%")
                            }
                        }
                    }
                }
            }
        }
    }
}
