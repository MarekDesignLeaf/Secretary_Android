package com.example.secretary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ========== JOB EDIT DIALOG ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditJobDialog(job: Job, onDismiss: () -> Unit, onSave: (Map<String, Any?>) -> Unit) {
    var title by remember { mutableStateOf(job.job_title) }
    var startDate by remember { mutableStateOf(job.start_date_planned ?: "") }
    var plannedStart by remember { mutableStateOf(job.planned_start_at ?: "") }
    var plannedEnd by remember { mutableStateOf(job.planned_end_at ?: "") }
    var assignedTo by remember { mutableStateOf(job.assigned_to ?: "") }
    var handoverNote by remember { mutableStateOf(job.handover_note ?: "") }
    var calendarSync by remember { mutableStateOf(job.calendar_sync_enabled) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.editJob) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) { item {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(Strings.jobTitle) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("${Strings.plannedStart} (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = plannedStart, onValueChange = { plannedStart = it }, label = { Text("${Strings.plannedStart} (YYYY-MM-DDTHH:MM:SS)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = plannedEnd, onValueChange = { plannedEnd = it }, label = { Text("${Strings.plannedEnd} (YYYY-MM-DDTHH:MM:SS)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = assignedTo, onValueChange = { assignedTo = it }, label = { Text(Strings.assigned) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = handoverNote, onValueChange = { handoverNote = it }, label = { Text(Strings.handoverNote) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = calendarSync, onCheckedChange = { calendarSync = it })
                    Text(Strings.syncCalendar)
                }
            } }
        },
        confirmButton = { Button(onClick = {
            onSave(
                mapOf(
                    "job_title" to title,
                    "start_date_planned" to startDate.ifBlank { null },
                    "planned_start_at" to plannedStart.ifBlank { null },
                    "planned_end_at" to plannedEnd.ifBlank { null },
                    "assigned_to" to assignedTo.ifBlank { null },
                    "handover_note" to handoverNote.ifBlank { null },
                    "calendar_sync_enabled" to calendarSync
                )
            )
        }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== JOB ADD NOTE DIALOG ==========
@Composable
fun AddJobNoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.addJobNote) },
        text = { TextField(value = note, onValueChange = { note = it }, label = { Text(Strings.noteLabel) }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
        confirmButton = { Button(onClick = { if (note.isNotBlank()) onSave(note) }, enabled = note.isNotBlank()) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== LEAD EDIT DIALOG ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLeadDialog(lead: Lead, onDismiss: () -> Unit, onSave: (Map<String, Any?>) -> Unit) {
    var contactName by remember { mutableStateOf(lead.contact_name ?: "") }
    var contactEmail by remember { mutableStateOf(lead.contact_email ?: "") }
    var contactPhone by remember { mutableStateOf(lead.contact_phone ?: "") }
    var description by remember { mutableStateOf(lead.description ?: "") }
    var notes by remember { mutableStateOf(lead.notes ?: "") }
    var source by remember { mutableStateOf(lead.lead_source ?: "telefon") }
    var status by remember { mutableStateOf(lead.status) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    val sources = listOf("checkatrade","web","telefon","doporuceni","jiny")
    val statuses = listOf("new","kvalifikovany","nabidka_odeslana","schvaleno","zamitnuto","preveden_na_klienta","preveden_na_zakazku")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.editLead) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) { item {
                OutlinedTextField(value = contactName, onValueChange = { contactName = it }, label = { Text("${Strings.contactName} *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = contactEmail, onValueChange = { contactEmail = it }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = contactPhone, onValueChange = { contactPhone = it }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = sourceExpanded, onExpandedChange = { sourceExpanded = it }) {
                    OutlinedTextField(value = Strings.localizeLeadSource(source), onValueChange = {}, readOnly = true, label = { Text(Strings.source) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sourceExpanded) })
                    ExposedDropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                        sources.forEach { s -> DropdownMenuItem(text = { Text(Strings.localizeLeadSource(s)) }, onClick = { source = s; sourceExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }) {
                    OutlinedTextField(value = Strings.localizeStatus(status), onValueChange = {}, readOnly = true, label = { Text(Strings.status) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) })
                    ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        statuses.forEach { s -> DropdownMenuItem(text = { Text(Strings.localizeStatus(s)) }, onClick = { status = s; statusExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(Strings.description) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(Strings.notes) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            } }
        },
        confirmButton = { Button(onClick = {
            onSave(mapOf("contact_name" to contactName, "contact_email" to contactEmail.ifBlank { null },
                "contact_phone" to contactPhone.ifBlank { null }, "description" to description.ifBlank { null },
                "notes" to notes.ifBlank { null }, "lead_source" to source, "status" to status))
        }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== INVOICE CREATE DIALOG ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceDialog(clients: List<Client>, onDismiss: () -> Unit, onConfirm: (Long?, Double, String?) -> Unit) {
    var selectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedClientName by remember { mutableStateOf<String?>(null) }
    var clientExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.newInvoice) },
        text = {
            Column {
                if (clients.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = clientExpanded, onExpandedChange = { clientExpanded = it }) {
                        OutlinedTextField(value = selectedClientName ?: Strings.selectClient, onValueChange = {}, readOnly = true, label = { Text("${Strings.client} *") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) })
                        ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                            clients.forEach { c -> DropdownMenuItem(text = { Text(c.display_name) }, onClick = { selectedClientId = c.id; selectedClientName = c.display_name; clientExpanded = false }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("${Strings.amount} (£)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("${Strings.dueDate} (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = {
            val a = amount.replace(",",".").toDoubleOrNull() ?: 0.0
            onConfirm(selectedClientId, a, dueDate.ifBlank { null })
        }, enabled = selectedClientId != null) { Text(Strings.create) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== INVOICE STATUS DIALOG ==========
@Composable
fun InvoiceStatusDialog(currentStatus: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val statuses = listOf("draft","odeslaná","částečně_uhrazená","uhrazená","po_splatnosti","stornována")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.changeInvoiceStatus) },
        text = { LazyColumn { items(statuses) { s ->
            val isCurrent = s == currentStatus
            ListItem(headlineContent = { Text(Strings.localizeStatus(s), fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                modifier = Modifier.clickable { onSelect(s) })
        } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings.close) } }
    )
}

// ========== WORK REPORT MANUAL CREATE DIALOG ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkReportDialog(clients: List<Client>, onDismiss: () -> Unit, onConfirm: (Long?, String, Double, Double, String?) -> Unit) {
    var selectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedClientName by remember { mutableStateOf<String?>(null) }
    var clientExpanded by remember { mutableStateOf(false) }
    var workDate by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())) }
    var totalHours by remember { mutableStateOf("") }
    var totalPrice by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.newWorkReport) },
        text = {
            LazyColumn(Modifier.heightIn(max = 350.dp)) { item {
                if (clients.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = clientExpanded, onExpandedChange = { clientExpanded = it }) {
                        OutlinedTextField(value = selectedClientName ?: Strings.selectClient, onValueChange = {}, readOnly = true, label = { Text("${Strings.client} *") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) })
                        ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                            clients.forEach { c -> DropdownMenuItem(text = { Text(c.display_name) }, onClick = { selectedClientId = c.id; selectedClientName = c.display_name; clientExpanded = false }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = workDate, onValueChange = { workDate = it }, label = { Text("${Strings.workDate} (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = totalHours, onValueChange = { totalHours = it }, label = { Text(Strings.totalHours) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = totalPrice, onValueChange = { totalPrice = it }, label = { Text("${Strings.totalPrice} £") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(Strings.notes) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            } }
        },
        confirmButton = { Button(onClick = {
            val hrs = totalHours.replace(",",".").toDoubleOrNull() ?: 0.0
            val price = totalPrice.replace(",",".").toDoubleOrNull() ?: 0.0
            onConfirm(selectedClientId, workDate, hrs, price, notes.ifBlank { null })
        }, enabled = selectedClientId != null && totalHours.isNotBlank()) { Text(Strings.create) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== GLOBAL LOG COMMUNICATION DIALOG ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalLogCommDialog(clients: List<Client>, onDismiss: () -> Unit, onSave: (Long?, String, String, String, String) -> Unit) {
    var selectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedClientName by remember { mutableStateOf<String?>(null) }
    var clientExpanded by remember { mutableStateOf(false) }
    var commType by remember { mutableStateOf("telefon") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("outbound") }
    var typeExpanded by remember { mutableStateOf(false) }
    val types = listOf("telefon","email","sms","whatsapp","checkatrade","osobne")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.logCommunication) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) { item {
                if (clients.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = clientExpanded, onExpandedChange = { clientExpanded = it }) {
                        OutlinedTextField(value = selectedClientName ?: Strings.selectClient, onValueChange = {}, readOnly = true, label = { Text(Strings.client) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) })
                        ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                            clients.forEach { c -> DropdownMenuItem(text = { Text(c.display_name) }, onClick = { selectedClientId = c.id; selectedClientName = c.display_name; clientExpanded = false }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(value = Strings.localizeCommType(commType), onValueChange = {}, readOnly = true, label = { Text(Strings.typeLabel) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) })
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        types.forEach { t -> DropdownMenuItem(text = { Text(Strings.localizeCommType(t)) }, onClick = { commType = t; typeExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == "outbound", onClick = { direction = "outbound" }, label = { Text(Strings.outgoing) })
                    FilterChip(selected = direction == "inbound", onClick = { direction = "inbound" }, label = { Text(Strings.incoming) })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text(Strings.subject) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text(Strings.summary) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            } }
        },
        confirmButton = { Button(onClick = { onSave(selectedClientId, commType, subject, message, direction) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}


// ========== QUOTES LIST TAB ==========
@Composable
fun QuotesListTab(quotes: List<Quote>, viewModel: SecretaryViewModel) {
    var showAddItem by remember { mutableStateOf<Quote?>(null) }
    var showApprove by remember { mutableStateOf<Quote?>(null) }
    if (quotes.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noQuotes, color = Color.Gray) }
    } else {
        LazyColumn {
            items(quotes) { q ->
                val statusColor = when(q.status) { "schvaleno"->Color(0xFF4CAF50); "draft"->Color(0xFFFF9800); "odeslano"->Color(0xFF2196F3); else->Color.Gray }
                Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(q.quote_title ?: q.quote_number ?: "${Strings.quote} #${q.id}", fontWeight = FontWeight.Bold)
                                Text(q.client_name ?: "${Strings.client} #${q.client_id}", fontSize = 13.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("£${"%.2f".format(q.grand_total)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                                    Text(Strings.localizeStatus(q.status), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = statusColor)
                                }
                            }
                        }
                        if (q.status == "draft" || q.status == "odeslano") {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showAddItem = q }, modifier = Modifier.weight(1f)) { Text("+ ${Strings.addItem}") }
                                Button(onClick = { showApprove = q }, modifier = Modifier.weight(1f)) { Text("✅ ${Strings.approve}") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAddItem != null) {
        AddQuoteItemDialog(onDismiss = { showAddItem = null },
            onSave = { desc, qty, price -> viewModel.addQuoteItem(showAddItem!!.id, desc, qty, price); showAddItem = null })
    }
    if (showApprove != null) {
        AlertDialog(onDismissRequest = { showApprove = null },
            title = { Text(Strings.approveQuoteQuestion) },
            text = { Text("${showApprove!!.quote_title}\n£${"%.2f".format(showApprove!!.grand_total)}\n\n${Strings.approveCreatesJob}") },
            confirmButton = { Button(onClick = { viewModel.approveQuote(showApprove!!.id); showApprove = null }) { Text(Strings.approveAndCreateJob) } },
            dismissButton = { TextButton(onClick = { showApprove = null }) { Text(Strings.cancel) } }
        )
    }
}

// ========== CREATE QUOTE DIALOG ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateQuoteDialog(clients: List<Client>, onDismiss: () -> Unit, onConfirm: (Long?, String) -> Unit) {
    var selectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedClientName by remember { mutableStateOf<String?>(null) }
    var clientExpanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.newQuote) },
        text = {
            Column {
                if (clients.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = clientExpanded, onExpandedChange = { clientExpanded = it }) {
                        OutlinedTextField(value = selectedClientName ?: Strings.selectClient, onValueChange = {}, readOnly = true, label = { Text("${Strings.client} *") }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) })
                        ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                            clients.forEach { c -> DropdownMenuItem(text = { Text(c.display_name) }, onClick = { selectedClientId = c.id; selectedClientName = c.display_name; clientExpanded = false }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(Strings.quote) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selectedClientId, title.ifBlank { Strings.quote }) }, enabled = selectedClientId != null) { Text(Strings.create) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== ADD QUOTE ITEM DIALOG ==========
@Composable
fun AddQuoteItemDialog(onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    var description by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unitPrice by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.addItem) },
        text = {
            Column {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("${Strings.description} *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text(Strings.quantity) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = it }, label = { Text("${Strings.unitPrice} (£)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = {
            val qty = quantity.replace(",",".").toDoubleOrNull() ?: 1.0
            val price = unitPrice.replace(",",".").toDoubleOrNull() ?: 0.0
            onSave(description, qty, price)
        }, enabled = description.isNotBlank()) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}


// ========== CLIENT EDIT DIALOG ==========
@Composable
fun ClientEditDialog(client: Client, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var displayName by remember { mutableStateOf(client.display_name) }
    var firstName by remember { mutableStateOf(client.first_name ?: "") }
    var lastName by remember { mutableStateOf(client.last_name ?: "") }
    var email by remember { mutableStateOf(client.email_primary ?: "") }
    var phone by remember { mutableStateOf(client.phone_primary ?: "") }
    var status by remember { mutableStateOf(client.status ?: "active") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.editClient) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    Column {
                        OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("${Strings.displayName} *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text(Strings.firstName) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text(Strings.lastName) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        Text("${Strings.status}:", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("active", "inactive", "archived").forEach { s ->
                                FilterChip(selected = status == s, onClick = { status = s },
                                    label = { Text(Strings.localizeStatus(s), fontSize = 12.sp) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = {
            onSave(mapOf("display_name" to displayName, "first_name" to firstName, "last_name" to lastName,
                "email_primary" to email, "phone_primary" to phone, "status" to status))
        }, enabled = displayName.isNotBlank()) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== JOB EDIT DIALOG ==========
@Composable
fun JobEditDialog(job: Job, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var title by remember { mutableStateOf(job.job_title) }
    var status by remember { mutableStateOf(job.job_status) }
    var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.editJob) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    Column {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(Strings.jobTitle) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        Text("${Strings.status}:", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val statuses = listOf("new","in_progress","waiting_client","waiting_material","scheduled","active","completed","invoiced","closed","cancelled")
                            items(statuses.size) { i ->
                                FilterChip(selected = status == statuses[i], onClick = { status = statuses[i] },
                                    label = { Text(Strings.localizeStatus(statuses[i]), fontSize = 11.sp) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(Strings.noteLabel) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = {
            onSave(mapOf("job_title" to title, "job_status" to status, "notes" to notes))
        }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}


// ========== COMMUNICATION EDIT DIALOG ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationEditDialog(onDismiss: () -> Unit, clients: List<Client>, onSave: (Map<String, String>) -> Unit) {
    var commType by remember { mutableStateOf("phone") }
    var direction by remember { mutableStateOf("outbound") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedClientName by remember { mutableStateOf<String?>(null) }
    var clientExpanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.newCommunication) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    Column {
                        if (clients.isNotEmpty()) {
                            ExposedDropdownMenuBox(expanded = clientExpanded, onExpandedChange = { clientExpanded = it }) {
                                OutlinedTextField(value = selectedClientName ?: Strings.selectClient, onValueChange = {}, readOnly = true, label = { Text(Strings.client) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(clientExpanded) })
                                ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                                    clients.forEach { c -> DropdownMenuItem(text = { Text(c.display_name) }, onClick = { selectedClientId = c.id; selectedClientName = c.display_name; clientExpanded = false }) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Text("${Strings.typeLabel}:", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val types = listOf("phone","email","sms","whatsapp","checkatrade","osobne")
                            items(types.size) { i ->
                                FilterChip(selected = commType == types[i], onClick = { commType = types[i] },
                                    label = { Text(Strings.localizeCommType(types[i]), fontSize = 12.sp) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${Strings.directionLabel}:", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = direction == "outbound", onClick = { direction = "outbound" }, label = { Text(Strings.outgoing) })
                            FilterChip(selected = direction == "inbound", onClick = { direction = "inbound" }, label = { Text(Strings.incoming) })
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text(Strings.subject) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text(Strings.messageLabel) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = {
            onSave(mapOf("comm_type" to commType, "direction" to direction, "subject" to subject,
                "message_summary" to message, "client_id" to (selectedClientId?.toString() ?: "")))
        }, enabled = subject.isNotBlank() || message.isNotBlank()) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ========== IMPORT CONTACTS DIALOG ==========
@Composable
fun ImportContactsDialog(
    onDismiss: () -> Unit,
    onImport: (onlyUkNumbers: Boolean, skipWithoutPhone: Boolean, skipWithoutName: Boolean, removeDuplicates: Boolean, includeEmail: Boolean) -> Unit
) {
    var onlyUkNumbers by remember { mutableStateOf(true) }
    var skipWithoutPhone by remember { mutableStateOf(true) }
    var skipWithoutName by remember { mutableStateOf(true) }
    var removeDuplicates by remember { mutableStateOf(true) }
    var includeEmail by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.importTitle) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.onlyUkNumbers)
                    Switch(checked = onlyUkNumbers, onCheckedChange = { onlyUkNumbers = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.skipWithoutPhone)
                    Switch(checked = skipWithoutPhone, onCheckedChange = { skipWithoutPhone = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.skipWithoutName)
                    Switch(checked = skipWithoutName, onCheckedChange = { skipWithoutName = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.removeDuplicates)
                    Switch(checked = removeDuplicates, onCheckedChange = { removeDuplicates = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.includeEmail)
                    Switch(checked = includeEmail, onCheckedChange = { includeEmail = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onImport(onlyUkNumbers, skipWithoutPhone, skipWithoutName, removeDuplicates, includeEmail) }) {
                Text(Strings.importContacts)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}
