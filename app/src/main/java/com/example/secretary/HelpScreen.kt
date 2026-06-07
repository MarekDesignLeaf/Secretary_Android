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
    var expandedModule by remember { mutableStateOf<String?>(null) }
    var modules by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        modules = viewModel.fetchCommandTree()
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
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp)); Text(Strings.loading)
                }
                modules.isNullOrEmpty() -> Text(Strings.cantReachServer, color = MaterialTheme.colorScheme.error)
                else -> modules!!.forEach { module ->
                    val mkey = module["key"]?.toString() ?: ""
                    val mtitle = module["title"]?.toString() ?: mkey
                    val branches = (module["branches"] as? List<Map<String, Any?>>) ?: emptyList()
                    val liveTotal = branches.sumOf { br ->
                        ((br["commands"] as? List<Map<String, Any?>>) ?: emptyList()).count { it["live"] == true }
                    }
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            .clickable { expandedModule = if (expandedModule == mkey) null else mkey }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(mtitle, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (liveTotal > 0) AssistChip(onClick = {}, label = { Text("$liveTotal ${Strings.helpLive}") })
                            }
                            if (expandedModule == mkey) {
                                Spacer(Modifier.height(10.dp))
                                branches.forEach { br ->
                                    val btitle = br["title"]?.toString() ?: ""
                                    val cmds = (br["commands"] as? List<Map<String, Any?>>) ?: emptyList()
                                    // Branch header (shown even if empty - reserved for future growth)
                                    Text(btitle, style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                                    if (cmds.isEmpty()) {
                                        Text(Strings.helpBranchEmpty, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 12.dp))
                                    } else cmds.forEach { cmd ->
                                        val phrase = cmd["phrase"]?.toString() ?: ""
                                        val live = cmd["live"] == true
                                        Row(Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.Top) {
                                            Text(if (live) "\u2713 " else "\u2022 ")
                                            Text(phrase, style = MaterialTheme.typography.bodyMedium,
                                                color = if (live) MaterialTheme.colorScheme.onSurface
                                                        else MaterialTheme.colorScheme.onSurfaceVariant)
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
