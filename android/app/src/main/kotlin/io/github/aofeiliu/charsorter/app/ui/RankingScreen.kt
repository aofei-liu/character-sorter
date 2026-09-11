package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.Ranking

@Composable
fun RankingScreen(list: CharacterList, ranking: Ranking?, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(list.title, style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Lists") }
        }
        ranking?.progress?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        if (ranking == null) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
        } else {
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(ranking.characters) { char ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${char.rank}. ${char.name}", style = MaterialTheme.typography.titleMedium)
                            Text(char.fandom, style = MaterialTheme.typography.bodySmall)
                        }
                        char.annotation?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
