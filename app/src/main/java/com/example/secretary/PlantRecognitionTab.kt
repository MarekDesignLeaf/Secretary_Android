package com.example.secretary

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PlantCaptureSlot(
    val id: Int,
    val organ: String,
    val file: File? = null,
    val isRequired: Boolean = false
)

private fun defaultPlantSlots(mode: String): List<PlantCaptureSlot> = when (mode) {
    "health" -> listOf(
        PlantCaptureSlot(id = 0, organ = "auto", isRequired = true),
        PlantCaptureSlot(id = 1, organ = "leaf"),
        PlantCaptureSlot(id = 2, organ = "auto")
    )
    "mushroom" -> listOf(
        PlantCaptureSlot(id = 0, organ = "cap", isRequired = true),
        PlantCaptureSlot(id = 1, organ = "underside"),
        PlantCaptureSlot(id = 2, organ = "stem"),
        PlantCaptureSlot(id = 3, organ = "habitat")
    )
    else -> listOf(
        PlantCaptureSlot(id = 0, organ = "auto", isRequired = true),
        PlantCaptureSlot(id = 1, organ = "leaf"),
        PlantCaptureSlot(id = 2, organ = "flower")
    )
}

private fun slotLabel(slotId: Int, mode: String): String = when {
    mode == "mushroom" && slotId == 0 -> Strings.mushroomWhole
    mode == "mushroom" && slotId == 1 -> Strings.mushroomUnderside
    mode == "mushroom" && slotId == 2 -> Strings.mushroomStemBase
    mode == "mushroom" -> Strings.mushroomHabitat
    mode == "health" && slotId == 0 -> Strings.plantDiseaseCloseup
    mode == "health" && slotId == 1 -> Strings.plantDiseaseLeafDetail
    mode == "health" -> Strings.plantDiseaseContext
    slotId == 0 -> Strings.plantWholePlant
    slotId == 1 -> Strings.plantLeafDetail
    else -> Strings.plantFlowerOrFruit
}

private fun formatHistoryTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.replace("T", " ").replace("Z", "").take(16)
}

private fun formatHistoryCoordinates(entry: RecognitionHistoryEntry): String {
    val lat = entry.latitude ?: return Strings.coordinatesUnavailable
    val lon = entry.longitude ?: return Strings.coordinatesUnavailable
    val coords = String.format(Locale.US, "%.5f, %.5f", lat, lon)
    val accuracy = entry.accuracy_meters?.let { " ±${it.toInt()}m" } ?: ""
    return coords + accuracy
}

@SuppressLint("MissingPermission")
private fun buildRecognitionCaptureContext(context: Context): RecognitionCaptureContext {
    val capturedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.UK).format(Date())
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return RecognitionCaptureContext(capturedAt = capturedAt)
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return RecognitionCaptureContext(capturedAt = capturedAt)
    val providers = buildList {
        if (hasFine) add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.PASSIVE_PROVIDER)
    }
    val bestLocation: Location? = providers
        .distinct()
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
    return RecognitionCaptureContext(
        capturedAt = capturedAt,
        latitude = bestLocation?.latitude,
        longitude = bestLocation?.longitude,
        accuracyMeters = bestLocation?.takeIf { it.hasAccuracy() }?.accuracy,
        locationSource = bestLocation?.provider
    )
}

@Composable
fun PlantRecognitionTab(
    state: UiState,
    viewModel: SecretaryViewModel,
    showModeSwitcher: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mode = state.plantCaptureMode
    val isHealthMode = mode == "health"
    val isMushroomMode = mode == "mushroom"
    val canManageNatureHistory = state.currentUserPermissions["manage_users"] == true || state.currentUserRole == "admin"
    var slots by remember(mode) { mutableStateOf(defaultPlantSlots(mode)) }
    var targetSlotId by remember { mutableStateOf<Int?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var organMenuSlotId by remember { mutableStateOf<Int?>(null) }
    var lastAutoLaunchRequestId by remember { mutableStateOf<Long?>(null) }
    var selectedHistoryOwner by remember { mutableStateOf<String?>(null) }
    val isVoiceCaptureActive by rememberUpdatedState(state.isPlantVoiceCaptureActive)
    val currentMode by rememberUpdatedState(mode)
    val currentIsHealthMode by rememberUpdatedState(isHealthMode)
    val currentIsMushroomMode by rememberUpdatedState(isMushroomMode)
    var launcherError by remember(mode) { mutableStateOf<String?>(null) }

    LaunchedEffect(mode) {
        slots = defaultPlantSlots(mode)
        targetSlotId = null
        pendingCameraFile = null
        organMenuSlotId = null
        launcherError = null
    }

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
            slot.file?.let { file -> PlantPhotoUpload(file = file, organ = slot.organ, label = slotLabel(slot.id, currentMode)) }
        }
        val shouldAutoIdentify = success && isVoiceCaptureActive && autoReadyPhotos.isNotEmpty()
        viewModel.consumePendingPlantCaptureRequest(resumeHotword = !shouldAutoIdentify)
        if (shouldAutoIdentify) {
            val captureContext = buildRecognitionCaptureContext(context)
            when {
                currentIsMushroomMode -> viewModel.identifyMushroom(autoReadyPhotos, captureContext)
                currentIsHealthMode -> viewModel.assessPlantHealth(autoReadyPhotos, captureContext)
                else -> viewModel.identifyPlant(autoReadyPhotos, captureContext)
            }
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
            slot.file?.let { file -> PlantPhotoUpload(file = file, organ = slot.organ, label = slotLabel(slot.id, currentMode)) }
        }
        val shouldAutoIdentify = uri != null && isVoiceCaptureActive && autoReadyPhotos.isNotEmpty()
        viewModel.consumePendingPlantCaptureRequest(resumeHotword = !shouldAutoIdentify)
        if (shouldAutoIdentify) {
            val captureContext = buildRecognitionCaptureContext(context)
            when {
                currentIsMushroomMode -> viewModel.identifyMushroom(autoReadyPhotos, captureContext)
                currentIsHealthMode -> viewModel.assessPlantHealth(autoReadyPhotos, captureContext)
                else -> viewModel.identifyPlant(autoReadyPhotos, captureContext)
            }
        }
    }

    fun launchCamera(slotId: Int): Boolean {
        val file = File(context.cacheDir, "plant_photo_${System.currentTimeMillis()}.jpg")
        targetSlotId = slotId
        pendingCameraFile = file
        launcherError = null
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            cameraLauncher.launch(uri)
            true
        } catch (error: Throwable) {
            Log.e("PlantRecognitionTab", "Failed to launch camera for mode=$currentMode slot=$slotId", error)
            launcherError = Strings.cameraOpenFailed
            false
        }
    }

    fun launchGallery(slotId: Int) {
        targetSlotId = slotId
        launcherError = null
        galleryLauncher.launch("image/*")
    }

    LaunchedEffect(state.pendingPlantCaptureRequestId, mode) {
        val requestId = state.pendingPlantCaptureRequestId
        if (requestId != null && requestId != lastAutoLaunchRequestId) {
            lastAutoLaunchRequestId = requestId
            delay(500)
            val nextSlot = slots.firstOrNull { it.file == null } ?: slots.last()
            val launched = launchCamera(nextSlot.id)
            if (!launched) {
                viewModel.consumePendingPlantCaptureRequest(resumeHotword = true)
            }
        }
    }

    val readyPhotos = slots.mapNotNull { slot ->
        slot.file?.let { file -> PlantPhotoUpload(file = file, organ = slot.organ, label = slotLabel(slot.id, mode)) }
    }
    val historyOwners = state.recognitionHistory
        .map { entry ->
            val key = entry.owner_user_id?.toString() ?: entry.owner_email.ifBlank { entry.owner_display_name }
            key to (entry.owner_display_name.ifBlank { entry.owner_email.ifBlank { Strings.unknown } })
        }
        .filter { it.first.isNotBlank() }
        .distinctBy { it.first }
    val effectiveHistoryOwner = selectedHistoryOwner?.takeIf { selected ->
        historyOwners.any { it.first == selected }
    }
    val filteredHistory = if (!canManageNatureHistory || effectiveHistoryOwner == null) {
        state.recognitionHistory
    } else {
        state.recognitionHistory.filter { entry ->
            val key = entry.owner_user_id?.toString() ?: entry.owner_email.ifBlank { entry.owner_display_name }
            key == effectiveHistoryOwner
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (showModeSwitcher) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.setPlantCaptureMode("identify") }) { Text(Strings.plantModeRecognition) }
                            OutlinedButton(onClick = { viewModel.setPlantCaptureMode("health") }) { Text(Strings.plantModeHealth) }
                            OutlinedButton(onClick = { viewModel.setPlantCaptureMode("mushroom") }) { Text(Strings.mushroomModeRecognition) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        when {
                            isMushroomMode -> Strings.mushroomRecognitionTitle
                            isHealthMode -> Strings.plantHealthTitle
                            else -> Strings.plantRecognitionTitle
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            isMushroomMode -> Strings.mushroomRecognitionHint
                            isHealthMode -> Strings.plantHealthHint
                            else -> Strings.plantRecognitionHint
                        }
                    )
                    if (state.isPlantVoiceCaptureActive) {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.plantVoiceBanner, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                isMushroomMode -> Strings.mushroomRecognitionVoiceGuide
                                isHealthMode -> Strings.plantHealthVoiceGuide
                                else -> Strings.plantRecognitionVoiceGuide
                            }
                        )
                    }
                }
            }
        }

        items(slots, key = { it.id }) { slot ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(slotLabel(slot.id, mode), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (slot.isRequired) Strings.requiredPhoto else Strings.optionalPhoto,
                        color = if (slot.isRequired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isHealthMode && !isMushroomMode && slot.id == 2) {
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
            val loading = when {
                isMushroomMode -> state.mushroomRecognitionLoading
                isHealthMode -> state.plantDiseaseLoading
                else -> state.plantRecognitionLoading
            }
            if (loading) {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            isMushroomMode -> Strings.mushroomRecognitionLoading
                            isHealthMode -> Strings.plantHealthLoading
                            else -> Strings.plantRecognitionLoading
                        }
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val captureContext = buildRecognitionCaptureContext(context)
                            when {
                                isMushroomMode -> viewModel.identifyMushroom(readyPhotos, captureContext)
                                isHealthMode -> viewModel.assessPlantHealth(readyPhotos, captureContext)
                                else -> viewModel.identifyPlant(readyPhotos, captureContext)
                            }
                        },
                        enabled = readyPhotos.isNotEmpty()
                    ) {
                        Text(
                            when {
                                isMushroomMode -> Strings.identifyMushroomAction
                                isHealthMode -> Strings.assessPlantHealthAction
                                else -> Strings.identifyPlantAction
                            }
                        )
                    }
                    TextButton(onClick = {
                        slots = defaultPlantSlots(mode)
                        viewModel.clearPlantRecognitionResult()
                    }) {
                        Text(Strings.cancel)
                    }
                }
            }
        }

        launcherError?.let { error ->
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }

        (when {
            isMushroomMode -> state.mushroomRecognitionError
            isHealthMode -> state.plantDiseaseError
            else -> state.plantRecognitionError
        })?.let { error ->
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }

        item {
            val plantResult = state.selectedPlantRecognition
            val diseaseResult = state.selectedPlantDisease
            val mushroomResult = state.selectedMushroomRecognition
            if (isMushroomMode && mushroomResult == null) {
                Text(Strings.mushroomNoResultYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!isHealthMode && !isMushroomMode && plantResult == null) {
                Text(Strings.plantNoResultYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (isHealthMode && diseaseResult == null) {
                Text(Strings.plantHealthNoResultYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (isMushroomMode && mushroomResult != null) {
                val result = mushroomResult
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Strings.plantBestMatch, fontWeight = FontWeight.Bold)
                        Text(result.display_name.ifBlank { result.scientific_name }, style = MaterialTheme.typography.titleMedium)
                        if (result.scientific_name.isNotBlank() && result.display_name != result.scientific_name) {
                            Text(result.scientific_name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (result.common_names.isNotEmpty()) {
                            Text(result.common_names.joinToString(", "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${Strings.confidence}: ${(result.probability * 100).toInt()}%")
                        result.family?.takeIf { it.isNotBlank() }?.let { Text("${Strings.familyLabel}: $it") }
                        result.edibility?.takeIf { it.isNotBlank() }?.let { Text("${Strings.edibilityLabel}: ${Strings.localizeMushroomEdibility(it)}") }
                        Strings.localizeMushroomPsychoactive(result.psychoactive).takeIf { it.isNotBlank() }?.let {
                            Text("${Strings.psychoactiveLabel}: $it")
                        }
                        Text("${Strings.databaseLabel}: ${result.database}")
                        result.guidance?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.mushroomGuidance, fontWeight = FontWeight.SemiBold)
                            Text(it)
                        }
                        if (result.characteristics.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.characteristicsLabel, fontWeight = FontWeight.SemiBold)
                            Text(result.characteristics.joinToString(", "))
                        }
                        if (result.look_alikes.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.lookAlikesLabel, fontWeight = FontWeight.SemiBold)
                            Text(result.look_alikes.joinToString(", "))
                        }
                        Text(Strings.mushroomSafetyNote, color = MaterialTheme.colorScheme.error)
                        if (result.suggestions.size > 1) {
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.mushroomAlternatives, fontWeight = FontWeight.SemiBold)
                            result.suggestions.drop(1).forEach { suggestion ->
                                HorizontalDivider()
                                Text("${suggestion.name} ${(suggestion.probability * 100).toInt()}%")
                            }
                        }
                    }
                }
            } else if (!isHealthMode && plantResult != null) {
                val result = plantResult
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
            } else {
                val result = diseaseResult!!
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Strings.plantHealthBestMatch, fontWeight = FontWeight.Bold)
                        Text(
                            if (result.is_healthy) Strings.plantHealthyLabel else (result.top_issue_name ?: Strings.plantHealthTitle),
                            style = MaterialTheme.typography.titleMedium
                        )
                        val shownProbability = if (result.is_healthy) result.health_probability else result.top_issue_probability
                        Text("${Strings.confidence}: ${(shownProbability * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Strings.databaseLabel}: ${result.database}")
                        result.guidance?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.plantHealthSummary, fontWeight = FontWeight.SemiBold)
                            Text(it)
                        }
                        if (result.suggestions.size > 1) {
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.plantHealthAlternatives, fontWeight = FontWeight.SemiBold)
                            result.suggestions.drop(1).forEach { suggestion ->
                                HorizontalDivider()
                                Text("${suggestion.name} ${(suggestion.probability * 100).toInt()}%")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(Strings.recognitionHistoryTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (canManageNatureHistory && historyOwners.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = if (effectiveHistoryOwner == null) 0 else historyOwners.indexOfFirst { it.first == effectiveHistoryOwner } + 1,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = effectiveHistoryOwner == null,
                        onClick = { selectedHistoryOwner = null },
                        text = { Text(Strings.allUsersLabel) }
                    )
                    historyOwners.forEach { owner ->
                        Tab(
                            selected = effectiveHistoryOwner == owner.first,
                            onClick = { selectedHistoryOwner = owner.first },
                            text = { Text(owner.second) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (filteredHistory.isEmpty()) {
                Text(Strings.recognitionHistoryEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(filteredHistory, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Strings.localizeRecognitionType(entry.recognition_type), fontWeight = FontWeight.Bold)
                    Text(entry.display_name.ifBlank { entry.scientific_name }, style = MaterialTheme.typography.titleMedium)
                    if (entry.scientific_name.isNotBlank() && entry.display_name != entry.scientific_name) {
                        Text(entry.scientific_name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entry.confidence > 0.0) {
                        Text("${Strings.confidence}: ${(entry.confidence * 100).toInt()}%")
                    }
                    if (canManageNatureHistory) {
                        val ownerText = entry.owner_display_name.ifBlank { entry.owner_email.ifBlank { Strings.unknown } }
                        Text("${Strings.capturedByLabel}: $ownerText")
                    }
                    Text("${Strings.capturedAtLabel}: ${formatHistoryTimestamp(entry.captured_at)}")
                    Text("${Strings.savedAtLabel}: ${formatHistoryTimestamp(entry.created_at)}")
                    Text("${Strings.locationLabel}: ${formatHistoryCoordinates(entry)}")
                    entry.guidance?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    if (entry.photos.isNotEmpty()) {
                        Text(Strings.photosLabel, fontWeight = FontWeight.SemiBold)
                        entry.photos.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { photo ->
                                    Box(Modifier.weight(1f).aspectRatio(1f).padding(4.dp)) {
                                        CoilImage(
                                            imageModel = { photo.url },
                                            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                                        )
                                    }
                                }
                                repeat(3 - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
