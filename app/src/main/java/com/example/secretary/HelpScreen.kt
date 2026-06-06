package com.example.secretary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(viewModel: SecretaryViewModel, navController: NavHostController) {
    val scroll = rememberScrollState()
    var expanded by remember { mutableStateOf<String?>(null) }
    var sections by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        sections = viewModel.fetchVoiceHelp()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.helpTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(scroll).padding(16.dp)
        ) {
            Text(Strings.helpIntro, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            when {
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(Strings.loading)
                    }
                }
                sections.isNullOrEmpty() -> {
                    Text(Strings.cantReachServer, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    sections!!.forEach { section ->
                        val key = section["key"]?.toString() ?: ""
                        val title = section["title"]?.toString() ?: key
                        val commands = (section["commands"] as? List<Map<String, Any?>>) ?: emptyList()
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                .clickable { expanded = if (expanded == key) null else key }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val liveCount = commands.count { it["live"] == true }
                                    if (liveCount > 0) {
                                        AssistChip(onClick = {}, label = { Text("$liveCount ${Strings.helpLive}") })
                                    }
                                }
                                if (expanded == key) {
                                    Spacer(Modifier.height(10.dp))
                                    commands.forEach { cmd ->
                                        val phrase = cmd["phrase"]?.toString() ?: ""
                                        val live = cmd["live"] == true
                                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                            Text(if (live) "\u2713 " else "\u2022 ")
                                            Text(
                                                phrase,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (live) MaterialTheme.colorScheme.onSurface
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
