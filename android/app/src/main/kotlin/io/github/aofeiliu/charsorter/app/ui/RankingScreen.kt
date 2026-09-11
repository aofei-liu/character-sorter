package io.github.aofeiliu.charsorter.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.Ranking

@Composable
fun RankingScreen(list: CharacterList, ranking: Ranking?, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                list.title.uppercase(),
                style = CharSorterType.ScreenTitle,
                color = CharSorterColor.Ink,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            OutlinedButton(
                onClick = onBack,
                shape = CharSorterShape.Pill,
                border = BorderStroke(1.dp, CharSorterColor.AccentDark.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CharSorterColor.Link)
            ) {
                Text("Lists", style = CharSorterType.ButtonSecondary)
            }
        }
        ranking?.progress?.let {
            Row(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiamondAccent(size = 6.dp, fill = CharSorterColor.AccentDark)
                Text(it, style = CharSorterType.ProgressText, color = CharSorterColor.Muted)
            }
        }
        if (ranking == null) {
            CircularProgressIndicator(
                color = CharSorterColor.AccentDark,
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(ranking.characters) { char ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("${char.rank}. ${char.name}", style = CharSorterType.RowName, color = CharSorterColor.Ink)
                            Text(char.fandom, style = CharSorterType.FandomSmall, color = CharSorterColor.Muted)
                        }
                        char.annotation?.let {
                            Text(it, style = CharSorterType.RatingText, color = CharSorterColor.Link)
                        }
                    }
                    HorizontalDivider(color = CharSorterColor.AccentDark.copy(alpha = 0.22f))
                }
            }
        }
    }
}
