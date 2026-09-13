package io.github.aofeiliu.charsorter.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.aofeiliu.charsorter.app.ui.CharSorterTheme
import io.github.aofeiliu.charsorter.app.ui.EditListScreen
import io.github.aofeiliu.charsorter.app.ui.ListChartScreen
import io.github.aofeiliu.charsorter.app.ui.ListPickerScreen
import io.github.aofeiliu.charsorter.app.ui.LoginScreen
import io.github.aofeiliu.charsorter.app.ui.RankingScreen
import io.github.aofeiliu.charsorter.app.ui.SortScreen
import io.github.aofeiliu.charsorter.app.ui.TrendScreen
import io.github.aofeiliu.charsorter.app.ui.charSorterBackground

@Composable
fun CharSorterApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    // Every screen but the two roots unwinds one step; at a root the handler
    // stays disabled so back leaves the app rather than doing nothing.
    BackHandler(
        enabled = state.screen !is Screen.Login && state.screen !is Screen.PickList
    ) {
        viewModel.back()
    }

    CharSorterTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            Box(modifier = Modifier.fillMaxSize().charSorterBackground()) {
                when (val screen = state.screen) {
                    is Screen.Login -> LoginScreen(
                        busy = state.busy,
                        onLogin = viewModel::login
                    )
                    is Screen.PickList -> ListPickerScreen(
                        lists = state.lists,
                        busy = state.busy,
                        onSort = viewModel::openForSorting,
                        onViewRanking = viewModel::openForRanking,
                        onEdit = viewModel::openForEditing,
                        onCreateList = viewModel::createList,
                        onLogout = viewModel::logout
                    )
                    is Screen.Sorting -> SortScreen(
                        list = screen.list,
                        pending = state.pending,
                        busy = state.busy,
                        canUndo = state.undoStack.isNotEmpty(),
                        onAnswer = { verdict ->
                            val pair = state.pending
                            val char1 = pair?.char1?.id
                            val char2 = pair?.char2?.id
                            if (char1 != null && char2 != null) {
                                viewModel.answer(screen.list, char1, char2, verdict)
                            }
                        },
                        onUndo = { viewModel.undo(screen.list) },
                        onRetry = { viewModel.loadNext(screen.list) },
                        onBack = viewModel::backToLists
                    )
                    is Screen.Ranking -> RankingScreen(
                        list = screen.list,
                        ranking = state.ranking,
                        spreads = state.spreads,
                        busy = state.busy,
                        onOpenCharacter = { viewModel.openForTrend(screen.list, it) },
                        onOpenChart = { viewModel.openForChart(screen.list) },
                        onRetry = { viewModel.loadRanking(screen.list) },
                        onBack = viewModel::backToLists
                    )
                    is Screen.ListChart -> ListChartScreen(
                        list = screen.list,
                        graph = state.graph,
                        busy = state.busy,
                        onRetry = { viewModel.loadGraph(screen.list) },
                        onBack = { viewModel.back() }
                    )
                    is Screen.CharacterTrend -> TrendScreen(
                        list = screen.list,
                        character = screen.character,
                        history = state.history,
                        busy = state.busy,
                        onRetry = {
                            viewModel.loadHistory(screen.list, screen.character.id)
                        },
                        onBack = { viewModel.back() }
                    )
                    is Screen.EditList -> EditListScreen(
                        list = screen.list,
                        characters = state.characters,
                        ranking = state.ranking,
                        byScore = state.editByScore,
                        busy = state.busy,
                        onAddCharacter = { name, fandom ->
                            viewModel.addCharacter(screen.list, name, fandom)
                        },
                        onUpdateCharacter = { id, name, fandom ->
                            viewModel.updateCharacter(screen.list, id, name, fandom)
                        },
                        onDeleteCharacter = { viewModel.deleteCharacter(screen.list, it) },
                        onRenameList = { viewModel.renameList(screen.list, it) },
                        onDeleteList = { viewModel.deleteList(screen.list) },
                        onSetSort = { viewModel.setEditSort(screen.list, it) },
                        onRetry = { viewModel.loadCharacters(screen.list) },
                        onBack = viewModel::backToLists
                    )
                }

                state.error?.let { message ->
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        action = {
                            TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                        }
                    ) {
                        Text(message)
                    }
                }
            }
        }
    }
}
