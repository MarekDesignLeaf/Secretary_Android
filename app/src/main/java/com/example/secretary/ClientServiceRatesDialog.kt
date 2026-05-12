package com.example.secretary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ServiceRateField(
    val key: String,
    val label: String,
    val hint: String? = null,
)

private fun formatServiceRate(value: Double?): String {
    val number = value ?: 0.0
    return if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()
}

/** Build ServiceRateField list from server rate-type rows. Falls back to keys in service_rates if empty. */
private fun buildFields(
    rateTypes: List<Map<String, Any?>>,
    serviceRates: Map<String, Double>
): List<ServiceRateField> {
    if (rateTypes.isNotEmpty()) {
        return rateTypes.mapNotNull { rt ->
            val key = rt["rate_type"]?.toString()?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
            val label = rt["description"]?.toString()?.trim()
                .takeIf { !it.isNullOrBlank() }
                ?: key.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
            ServiceRateField(key = key, label = label)
        }
    }
    // fallback: build from whatever the server returned in service_rates
    return serviceRates.keys.map { key ->
        ServiceRateField(key = key, label = key.replace("_", " ").replaceFirstChar { it.uppercaseChar() })
    }
}

@Composable
fun ClientServiceRatesSummary(
    detail: ClientDetail,
    rateTypes: List<Map<String, Any?>> = emptyList()
) {
    val fields = buildFields(rateTypes, detail.service_rates)
    val labelMap = fields.associate { it.key to it.label }

    val overrideCount = detail.service_rate_overrides.size
    if (overrideCount > 0) {
        Text(
            Strings.clientServiceRatesSummary(overrideCount),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
    // Show all non-zero rates using dynamic labels
    detail.service_rates.forEach { (key, value) ->
        if (value > 0) {
            val label = labelMap[key]
                ?: key.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
            InfoRow(label, "£${formatServiceRate(value)}")
        }
    }
}

@Composable
fun ClientServiceRatesDialog(
    client: Client,
    detail: ClientDetail,
    viewModel: SecretaryViewModel,
    rateTypes: List<Map<String, Any?>> = emptyList(),
    onDismiss: () -> Unit,
) {
    val fields = remember(rateTypes, detail.service_rates) {
        buildFields(rateTypes, detail.service_rates)
    }

    var values by remember(detail, fields) {
        mutableStateOf(
            fields.associate { field ->
                field.key to formatServiceRate(detail.service_rates[field.key])
            }
        )
    }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun buildPayload(): Map<String, Any?> {
        return buildMap {
            fields.forEach { field ->
                val parsed = values[field.key]?.replace(",", ".")?.toDoubleOrNull()
                if (parsed != null && parsed > 0) {
                    put(field.key, parsed)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("${Strings.individualServiceRates} · ${client.display_name}") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(Strings.individualServiceRatesHint, fontSize = 12.sp, color = Color.Gray)
                    if (detail.has_individual_service_rates) {
                        Spacer(Modifier.height(4.dp))
                        Text(Strings.individualServiceRatesActive, fontSize = 12.sp, color = Color.Gray)
                    }
                    if (fields.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.noRateTypesConfigured, fontSize = 13.sp, color = Color.Gray)
                    }
                }
                items(fields) { field ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = values[field.key].orEmpty(),
                            onValueChange = { next ->
                                values = values.toMutableMap().apply {
                                    put(field.key, next.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' })
                                }
                                error = null
                            },
                            label = { Text(field.label) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !submitting,
                        )
                        field.hint?.let { Text(it, fontSize = 11.sp, color = Color.Gray) }
                    }
                }
                if (error != null) {
                    item { Text(error!!, color = Color.Red, fontSize = 12.sp) }
                }
                if (fields.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        ClientServiceRatesSummary(
                            detail = detail.copy(
                                service_rates = fields.associate { field ->
                                    field.key to (values[field.key]?.replace(",", ".")?.toDoubleOrNull() ?: 0.0)
                                },
                                service_rate_overrides = detail.service_rate_overrides
                            ),
                            rateTypes = rateTypes
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    error = null
                    viewModel.updateClientServiceRates(client.id, buildPayload()) { ok, message ->
                        submitting = false
                        if (ok) onDismiss()
                        else error = message ?: Strings.backendActionFailed(Strings.individualServiceRates, 0)
                    }
                },
                enabled = !submitting
            ) {
                Text(if (submitting) Strings.processing else Strings.save)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail.has_individual_service_rates) {
                    TextButton(
                        onClick = {
                            submitting = true
                            error = null
                            viewModel.updateClientServiceRates(client.id, emptyMap()) { ok, message ->
                                submitting = false
                                if (ok) onDismiss()
                                else error = message ?: Strings.backendActionFailed(Strings.resetToCompanyRates, 0)
                            }
                        },
                        enabled = !submitting
                    ) {
                        Text(Strings.resetToCompanyRates)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss, enabled = !submitting) { Text(Strings.cancel) }
            }
        }
    )
}
