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
import io.github.aofeiliu.charsorter.app.ui.PasteScreen
import io.github.aofeiliu.charsorter.app.ui.PastePreviewScreen
import io.github.aofeiliu.charsorter.app.ui.RankingScreen
import io.github.aofeiliu.charsorter.app.ui.SortScreen
import io.github.aofeiliu.charsorter.app.ui.TrendScreen
import io.github.aofeiliu.charsorter.app.ui.charSorterBackground
import io.github.aofeiliu.charsorter.client.parsePaste
import io.github.aofeiliu.charsorter.client.reviewPaste

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
                        onMoveList = viewModel::moveList,
                        onDeleteList = viewModel::deleteList,
                        onLogout = viewModel::logout
                    )
                    is Screen.Sorting -> SortScreen(
                        list = screen.list,
                        pending = state.pending,
                        busy = state.busy,
                        canUndo = state.undoStack.isNotEmpty(),
                        focus = state.focus,
                        onAnswer = { verdict ->
                            val pair = state.pending
                            val char1 = pair?.char1?.id
                            val char2 = pair?.char2?.id
                            if (char1 != null && char2 != null) {
                                viewModel.answer(screen.list, char1, char2, verdict)
                            }
                        },
                        onUndo = { viewModel.undo(screen.list) },
                        onStartFocus = { viewModel.startFocus(screen.list, it) },
                        onStopFocus = { viewModel.stopFocus() },
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
                        onPasteMany = { viewModel.openPaste(screen.list) },
                        onRetry = { viewModel.loadCharacters(screen.list) },
                        onBack = viewModel::backToLists
                    )
                    is Screen.PasteCharacters -> PasteScreen(
                        list = screen.list,
                        text = state.pasteText,
                        parse = parsePaste(state.pasteText),
                        caretLine = state.pasteCaret,
                        busy = state.busy,
                        onTextChange = viewModel::setPasteText,
                        onCaretHandled = viewModel::pasteCaretHandled,
                        onPreview = { viewModel.openPastePreview(screen.list) },
                        onCancel = { viewModel.back() }
                    )
                    is Screen.PastePreview -> {
                        val parse = parsePaste(state.pasteText)
                        PastePreviewScreen(
                            list = screen.list,
                            reviewed = reviewPaste(parse.entries, state.characters.orEmpty()),
                            skipped = parse.skipped,
                            writes = state.pasteWrites,
                            busy = state.busy,
                            onEditText = { viewModel.editPasteText(screen.list) },
                            onJumpToLine = { viewModel.editPasteText(screen.list, it) },
                            onConfirm = { viewModel.addPasted(screen.list, it) },
                            onDone = { viewModel.closePasteReport(screen.list) }
                        )
                    }
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
