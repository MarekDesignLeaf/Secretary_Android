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
            (it.email_primary ?: "").contains(searchQuery, ignoreCase = true)
    }

    // Split sections into parent (no parent_section_code) and children
    val sortedSections = state.contactSections.sortedBy { it.sort_order }
    val parentSections = sortedSections.filter { it.parent_section_code == null }
    val childSections = sortedSections.filter { it.parent_section_code != null }

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
                parentSections.forEach { parent ->
                    val siblings = childSections.filter { it.parent_section_code == parent.section_code }

                    if (siblings.isEmpty()) {
                        // Leaf parent: show contacts directly under the parent header
                        val parentContacts = filteredContacts.filter { it.section_code == parent.section_code }
                        if (parentContacts.isNotEmpty()) {
                            item {
                                Text(
                                    Strings.localizeContactSection(parent.section_code, parent.display_name),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, top = 12.dp, bottom = 4.dp)
                                )
                                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            }
                            items(parentContacts, key = { it.id }) { contact ->
                                ContactCard(contact) { editContact = contact }
                            }
                        }
                    } else {
                        // Parent with subcategories: show parent heading, then subcategory subheadings
                        val allParentContacts = filteredContacts.filter { contact ->
                            contact.section_code == parent.section_code ||
                                siblings.any { it.section_code == contact.section_code }
                        }
                        if (allParentContacts.isNotEmpty()) {
                            item {
                                Text(
                                    Strings.localizeContactSection(parent.section_code, parent.display_name),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, top = 12.dp, bottom = 4.dp)
                                )
                                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            }
                            siblings.forEach { child ->
                                val childContacts = filteredContacts.filter { it.section_code == child.section_code }
                                if (childContacts.isNotEmpty()) {
                                    item {
                                        Text(
                                            Strings.localizeContactSection(child.section_code, child.display_name),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 2.dp)
                                        )
                                    }
                                    items(childContacts, key = { it.id }) { contact ->
                                        ContactCard(contact, indent = true) { editContact = contact }
                                    }
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
private fun ContactCard(contact: SharedContact, indent: Boolean = false, onClick: () -> Unit) {
    val startPad = if (indent) 24.dp else 12.dp
    val nextContactColor = remember(contact.next_contact_at) {
        contact.next_contact_at?.let {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val date = sdf.parse(it)
                val now = java.util.Date()
                val diffDays = ((date!!.time - now.time) / 86400000L).toInt()
                when {
                    diffDays < 0 -> "overdue"
                    diffDays <= 3 -> "soon"
                    else -> "ok"
                }
            } catch (_: Exception) { "ok" }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPad, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(contact.display_name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            contact.company_name?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
            contact.phone_primary?.let { Text("☎ $it", fontSize = 13.sp) }
            contact.email_primary?.let { Text("✉ $it", fontSize = 13.sp, color = Color.Gray) }
            contact.notes?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            contact.next_contact_at?.let { dateStr ->
                val methodIcon = when (contact.next_contact_method) {
                    "phone" -> "☎"
                    "email" -> "✉"
                    "whatsapp" -> "💬"
                    "in_person" -> "🤝"
                    "sms" -> "✉"
                    else -> "📅"
                }
                val color = when (nextContactColor) {
                    "overdue" -> Color(0xFFD32F2F)
                    "soon" -> Color(0xFFE65100)
                    else -> Color(0xFF1976D2)
                }
                Text(
                    "$methodIcon $dateStr",
                    fontSize = 12.sp,
                    color = color,
                    fontWeight = if (nextContactColor == "overdue") FontWeight.Bold else FontWeight.Normal
                )
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
    // Only show leaf sections (those that have a parent, or parents with no children) as selectable targets
    val selectableSections = remember(sections) {
        val parentCodes = sections.filter { it.parent_section_code == null }.map { it.section_code }.toSet()
        val codesWithChildren = sections.mapNotNull { it.parent_section_code }.toSet()
        sections.filter { sec ->
            sec.parent_section_code != null || sec.section_code !in codesWithChildren
        }.sortedBy { it.sort_order }
    }

    var displayName by remember(contact) { mutableStateOf(contact?.display_name ?: "") }
    var companyName by remember(contact) { mutableStateOf(contact?.company_name ?: "") }
    var phone by remember(contact) { mutableStateOf(contact?.phone_primary ?: "") }
    var email by remember(contact) { mutableStateOf(contact?.email_primary ?: "") }
    var notes by remember(contact) { mutableStateOf(contact?.notes ?: "") }
    var nextContactAt by remember(contact) { mutableStateOf(contact?.next_contact_at ?: "") }
    var nextContactMethod by remember(contact) { mutableStateOf(contact?.next_contact_method ?: "") }
    var sectionCode by remember(contact, selectableSections) {
        mutableStateOf(contact?.section_code ?: selectableSections.firstOrNull()?.section_code.orEmpty())
    }
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
                            value = selectableSections.firstOrNull { it.section_code == sectionCode }
                                ?.let { sec ->
                                    val parentName = sections.firstOrNull { it.section_code == sec.parent_section_code }
                                        ?.let { Strings.localizeContactSection(it.section_code, it.display_name) + " / " } ?: ""
                                    parentName + Strings.localizeContactSection(sec.section_code, sec.display_name)
                                }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(Strings.sectionLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectionExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = sectionExpanded, onDismissRequest = { sectionExpanded = false }) {
                            selectableSections.forEach { section ->
                                val parentPrefix = sections.firstOrNull { it.section_code == section.parent_section_code }
                                    ?.let { Strings.localizeContactSection(it.section_code, it.display_name) + " / " } ?: ""
                                DropdownMenuItem(
                                    text = { Text(parentPrefix + Strings.localizeContactSection(section.section_code, section.display_name)) },
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
                item { OutlinedTextField(notes, { notes = it }, label = { Text(Strings.notesLabel) }, modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !submitting) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            nextContactAt, { nextContactAt = it },
                            label = { Text(Strings.nextContactDate) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("yyyy-mm-dd", fontSize = 11.sp) },
                            enabled = !submitting
                        )
                        OutlinedTextField(
                            nextContactMethod, { nextContactMethod = it },
                            label = { Text(Strings.nextContactMethodLabel) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("phone/email/…", fontSize = 11.sp) },
                            enabled = !submitting
                        )
                    }
                }
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
                        "notes" to notes.ifBlank { null },
                        "next_contact_at" to nextContactAt.ifBlank { null },
                        "next_contact_method" to nextContactMethod.ifBlank { null }
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
    // Only leaf sections selectable (same logic as SharedContactDialog)
    val selectableSections = remember(sections) {
        val codesWithChildren = sections.mapNotNull { it.parent_section_code }.toSet()
        sections.filter { sec ->
            sec.parent_section_code != null || sec.section_code !in codesWithChildren
        }.sortedBy { it.sort_order }
    }

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
                                        contact.phone?.let { Text("☎ $it", fontSize = 12.sp) }
                                        contact.email?.let { Text("✉ $it", fontSize = 12.sp, color = Color.Gray) }
                                    }
                                }
                                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                    OutlinedTextField(
                                        value = selectableSections.firstOrNull { it.section_code == contact.section_code }
                                            ?.let { sec ->
                                                val parentName = sections.firstOrNull { it.section_code == sec.parent_section_code }
                                                    ?.let { Strings.localizeContactSection(it.section_code, it.display_name) + " / " } ?: ""
                                                parentName + Strings.localizeContactSection(sec.section_code, sec.display_name)
                                            }.orEmpty(),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(Strings.sectionLabel) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                                        enabled = contact.selected
                                    )
                                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        selectableSections.forEach { section ->
                                            val parentPrefix = sections.firstOrNull { it.section_code == section.parent_section_code }
                                                ?.let { Strings.localizeContactSection(it.section_code, it.display_name) + " / " } ?: ""
                                            DropdownMenuItem(
                                                text = { Text(parentPrefix + Strings.localizeContactSection(section.section_code, section.display_name)) },
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
