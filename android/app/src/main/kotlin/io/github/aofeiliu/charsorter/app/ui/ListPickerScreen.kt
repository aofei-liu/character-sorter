package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.CharacterList

@Composable
fun ListPickerScreen(
    lists: List<CharacterList>,
    busy: Boolean,
    onSort: (CharacterList) -> Unit,
    onViewRanking: (CharacterList) -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Your lists", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onLogout) { Text("Log out") }
        }
        if (busy && lists.isEmpty()) {
            CircularProgressIndicator()
        } else if (lists.isEmpty()) {
            Text("No character lists yet.")
        } else {
            LazyColumn {
                items(lists) { list ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(list.title, style = MaterialTheme.typography.titleMedium)
                            Row(modifier = Modifier.padding(top = 12.dp)) {
                                OutlinedButton(
                                    onClick = { onSort(list) },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Sort")
                                }
                                OutlinedButton(onClick = { onViewRanking(list) }) {
                                    Text("Ranking")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
