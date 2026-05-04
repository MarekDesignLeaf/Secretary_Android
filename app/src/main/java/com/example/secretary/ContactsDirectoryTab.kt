package com.example.secretary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContactsDirectoryTab(state: UiState, viewModel: SecretaryViewModel) {
    val ctx = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSectionDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importLoading by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importContacts by remember { mutableStateOf<List<ImportableSharedContact>>(emptyList()) }
    var editContact by remember { mutableStateOf<SharedContact?>(null) }

    if (showSectionDialog) {
        CreateContactSectionDialog(
            onDismiss = { showSectionDialog = false },
            onCreate = { name, onDone ->
                viewModel.createContactSection(name) { ok, msg ->
                    if (ok) showSectionDialog = false
                    onDone(ok, msg)
                }
            }
        )
    }
    if (showImportDialog) {
        ImportSharedContactsDialog(
            sections = state.contactSections,
            contacts = importContacts,
            error = importError,
            loading = importLoading,
            onDismiss = { showImportDialog = false },
            onChange = { importContacts = it },
            onSave = { contacts, onDone ->
                viewModel.importSharedContacts(contacts) { ok, msg ->
                    if (ok) showImportDialog = false
                    onDone(ok, msg)
                }
            }
        )
    }
    // Merge dialog
    val mergePending = state.pendingMergeDialog
    if (mergePending != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissMergeDialog() },
            title = { Text("Sloučit kontakty") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zachovat jako primární:")
                    val p1info = mergePending.name1 + if (!mergePending.phone1.isNullOrBlank()) " (${mergePending.phone1})" else ""
                    Text(p1info, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Smazat (data se sloučí):")
                    val p2info = mergePending.name2 + if (!mergePending.phone2.isNullOrBlank()) " (${mergePending.phone2})" else ""
                    Text(p2info)
                    Text("(Klepněte Přepnout pro opačné pořadí)", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.mergeContactsById(mergePending.id1, mergePending.id2)
                }) { Text("Sloučit") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        // Swap primary/secondary
                        viewModel.showMergeDialog(mergePending.copy(
                            id1 = mergePending.id2, name1 = mergePending.name2, phone1 = mergePending.phone2, section1 = mergePending.section2,
                            id2 = mergePending.id1, name2 = mergePending.name1, phone2 = mergePending.phone1, section2 = mergePending.section1
                        ))
                    }) { Text("Přepnout") }
                    TextButton(onClick = { viewModel.dismissMergeDialog() }) { Text("Zrušit") }
                }
            }
        )
    }

    if (editContact != null) {
        SharedContactDialog(
            sections = state.contactSections,
            contact = editContact,
            onDismiss = { editContact = null },
            onSave = { payload, contactId, onDone ->
                viewModel.saveSharedContact(payload, contactId) { ok, msg ->
                    if (ok) editContact = null
                    onDone(ok, msg)
                }
            },
            onDelete = { contactId, onDone ->
                viewModel.deleteSharedContact(contactId) { ok, msg ->
                    if (ok) editContact = null
                    onDone(ok, msg)
                }
            }
        )
    }

    val filteredContacts = state.sharedContacts.filter {
        searchQuery.isBlank() ||
            it.display_name.contains(searchQuery, ignoreCase = true) ||
            (it.company_name ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.phone_primary ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.email_primary ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.address ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.address_line1 ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.city ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.postcode ?: "").contains(searchQuery, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            Strings.sharedContactsHint,
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(Strings.contactsDirectory, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.currentUserPermissions["contacts_manage"] == true || state.currentUserRole == "admin") {
                TextButton(onClick = { viewModel.startContactSortingSession("ask") }) {
                    Text("🎙 Třídit")
                }
            }
            if (state.contactDuplicates.isNotEmpty()) {
                TextButton(onClick = {
                    viewModel.showMergeDialog(state.contactDuplicates.first())
                }) {
                    androidx.compose.material3.BadgedBox(
                        badge = { androidx.compose.material3.Badge { Text("${state.contactDuplicates.size}") } }
                    ) {
                        Text("⚡ Duplikáty")
                    }
                }
            }
            TextButton(onClick = { showSectionDialog = true }) { Text(Strings.addSection) }
            TextButton(
                onClick = {
                    importError = null
                    importLoading = true
                    viewModel.loadImportableSharedContacts(ctx, state.contactSections) { contacts, error ->
                        importLoading = false
                        importContacts = contacts
                        importError = error
                        showImportDialog = true
                    }
                }
            ) { Text(Strings.importToContacts) }
        }
        Spacer(Modifier.height(8.dp))
        if (state.contactSections.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noSectionsAvailable, color = Color.Gray) }
        } else if (filteredContacts.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text(Strings.noSharedContacts, color = Color.Gray) }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                state.contactSections.sortedBy { it.sort_order }.forEach { section ->
                    val sectionContacts = filteredContacts.filter { it.section_code == section.section_code }
                    if (sectionContacts.isNotEmpty()) {
                        item {
                            Text(
                                Strings.localizeContactSection(section.section_code, section.display_name),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        items(sectionContacts, key = { it.id }) { contact ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .clickable { editContact = contact },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            contact.display_name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        VoiceAliasButton(contact.display_name, viewModel, compact = true)
                                    }
                                    contact.company_name?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                                    contact.phone_primary?.let { Text("\u260E $it", fontSize = 13.sp) }
                                    contact.email_primary?.let { Text("\u2709 $it", fontSize = 13.sp, color = Color.Gray) }
                                    contact.fullAddress()?.let {
                                        Text("${Strings.address}: $it", fontSize = 12.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        AddressActionsRow(it, viewModel)
                                    }
                                    contact.notes?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateContactSectionDialog(
    onDismiss: () -> Unit,
    onCreate: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(Strings.createSection) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(Strings.sectionName) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !submitting
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    onCreate(name) { ok, msg ->
                        submitting = false
                        if (ok) onDismiss() else error = msg
                    }
                },
                enabled = !submitting && name.isNotBlank()
            ) { Text(if (submitting) Strings.processing else Strings.createSection) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(Strings.cancel) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedContactDialog(
    sections: List<ContactSection>,
    contact: SharedContact? = null,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any?>, Long?, (Boolean, String?) -> Unit) -> Unit,
    onDelete: ((Long, (Boolean, String?) -> Unit) -> Unit)? = null
) {
    var displayName by remember(contact) { mutableStateOf(contact?.display_name ?: "") }
    var companyName by remember(contact) { mutableStateOf(contact?.company_name ?: "") }
    var phone by remember(contact) { mutableStateOf(contact?.phone_primary ?: "") }
    var email by remember(contact) { mutableStateOf(contact?.email_primary ?: "") }
    var addressLine1 by remember(contact) { mutableStateOf(contact?.address_line1 ?: contact?.address ?: "") }
    var city by remember(contact) { mutableStateOf(contact?.city ?: "") }
    var postcode by remember(contact) { mutableStateOf(contact?.postcode ?: "") }
    var country by remember(contact) { mutableStateOf(contact?.country ?: "") }
    var notes by remember(contact) { mutableStateOf(contact?.notes ?: "") }
    var sectionCode by remember(contact, sections) { mutableStateOf(contact?.section_code ?: sections.firstOrNull()?.section_code.orEmpty()) }
    var sectionExpanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(if (contact == null) Strings.addContact else Strings.editContact) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(displayName, { displayName = it; error = null }, label = { Text(Strings.displayName) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting)
                }
                item {
                    ExposedDropdownMenuBox(expanded = sectionExpanded, onExpandedChange = { if (!submitting) sectionExpanded = it }) {
                        OutlinedTextField(
                            value = sections.firstOrNull { it.section_code == sectionCode }?.let { Strings.localizeContactSection(it.section_code, it.display_name) }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(Strings.sectionLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectionExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = sectionExpanded, onDismissRequest = { sectionExpanded = false }) {
                            sections.forEach { section ->
                                DropdownMenuItem(
                                    text = { Text(Strings.localizeContactSection(section.section_code, section.display_name)) },
                                    onClick = {
                                        sectionCode = section.section_code
                                        sectionExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                item { OutlinedTextField(companyName, { companyName = it }, label = { Text(Strings.companyNameLabel) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting) }
                item { OutlinedTextField(phone, { phone = it }, label = { Text(Strings.phone) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting) }
                item { OutlinedTextField(email, { email = it }, label = { Text(Strings.email) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting) }
                item { OutlinedTextField(addressLine1, { addressLine1 = it }, label = { Text(Strings.address) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting) }
                item { OutlinedTextField(city, { city = it }, label = { Text(Strings.city) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting) }
                item { OutlinedTextField(postcode, { postcode = it }, label = { Text(Strings.postcode) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting) }
                item { OutlinedTextField(country, { country = it }, label = { Text(Strings.country) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !submitting) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text(Strings.notesLabel) }, modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !submitting) }
                if (error != null) item { Text(error!!, color = Color.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    val payload = mapOf(
                        "display_name" to displayName,
                        "section_code" to sectionCode,
                        "company_name" to companyName.ifBlank { null },
                        "phone_primary" to phone.ifBlank { null },
                        "email_primary" to email.ifBlank { null },
                        "address" to listOf(addressLine1, city, postcode, country).joinContactAddressParts().ifBlank { null },
                        "address_line1" to addressLine1.ifBlank { null },
                        "city" to city.ifBlank { null },
                        "postcode" to postcode.ifBlank { null },
                        "country" to country.ifBlank { null },
                        "notes" to notes.ifBlank { null }
                    )
                    onSave(payload, contact?.id) { ok, msg ->
                        submitting = false
                        if (ok) onDismiss() else error = msg
                    }
                },
                enabled = !submitting && displayName.isNotBlank() && sectionCode.isNotBlank()
            ) { Text(if (submitting) Strings.processing else Strings.save) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (contact != null && onDelete != null) {
                    TextButton(
                        onClick = {
                            submitting = true
                            onDelete(contact.id) { ok, msg ->
                                submitting = false
                                if (ok) onDismiss() else error = msg
                            }
                        },
                        enabled = !submitting
                    ) { Text(Strings.delete) }
                }
                TextButton(onClick = onDismiss, enabled = !submitting) { Text(Strings.cancel) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSharedContactsDialog(
    sections: List<ContactSection>,
    contacts: List<ImportableSharedContact>,
    error: String?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onChange: (List<ImportableSharedContact>) -> Unit,
    onSave: (List<ImportableSharedContact>, (Boolean, String?) -> Unit) -> Unit
) {
    var submitting by remember { mutableStateOf(false) }
    var dialogError by remember(error) { mutableStateOf(error) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(Strings.importToContacts) },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(Strings.importContactsHint, fontSize = 12.sp, color = Color.Gray) }
                    items(contacts, key = { it.contact_key }) { contact ->
                        var expanded by remember(contact.contact_key) { mutableStateOf(false) }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = contact.selected,
                                        onCheckedChange = { checked ->
                                            onChange(contacts.map {
                                                if (it.contact_key == contact.contact_key) it.copy(selected = checked) else it
                                            })
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(contact.name, fontWeight = FontWeight.SemiBold)
                                        contact.phone?.let { Text("\u260E $it", fontSize = 12.sp) }
                                        contact.email?.let { Text("\u2709 $it", fontSize = 12.sp, color = Color.Gray) }
                                        contact.fullAddress()?.let { Text("${Strings.address}: $it", fontSize = 12.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                    OutlinedTextField(
                                        value = sections.firstOrNull { it.section_code == contact.section_code }?.let { Strings.localizeContactSection(it.section_code, it.display_name) }.orEmpty(),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(Strings.sectionLabel) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                                        enabled = contact.selected
                                    )
                                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        sections.forEach { section ->
                                            DropdownMenuItem(
                                                text = { Text(Strings.localizeContactSection(section.section_code, section.display_name)) },
                                                onClick = {
                                                    onChange(contacts.map {
                                                        if (it.contact_key == contact.contact_key) it.copy(section_code = section.section_code) else it
                                                    })
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    dialogError?.let { item { Text(it, color = Color.Red, fontSize = 12.sp) } }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    dialogError = null
                    onSave(contacts) { ok, msg ->
                        submitting = false
                        if (ok) onDismiss() else dialogError = msg
                    }
                },
                enabled = !loading && !submitting && contacts.any { it.selected && it.section_code.isNotBlank() }
            ) { Text(if (submitting) Strings.processing else Strings.saveImportedContacts) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(Strings.cancel) } }
    )
}

private fun SharedContact.fullAddress(): String? =
    address?.takeIf { it.isNotBlank() }
        ?: listOf(address_line1, city, postcode, country).joinContactAddressParts().takeIf { it.isNotBlank() }

private fun ImportableSharedContact.fullAddress(): String? =
    address?.takeIf { it.isNotBlank() }
        ?: listOf(address_line1, city, postcode, country).joinContactAddressParts().takeIf { it.isNotBlank() }

private fun List<String?>.joinContactAddressParts(): String =
    mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.joinToString(", ")
