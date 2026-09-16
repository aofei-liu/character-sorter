package io.github.aofeiliu.charsorter.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.aofeiliu.charsorter.app.ui.RatingSpread
import io.github.aofeiliu.charsorter.client.ApiException
import io.github.aofeiliu.charsorter.client.CharSorterClient
import io.github.aofeiliu.charsorter.client.Character
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.Comparison
import io.github.aofeiliu.charsorter.client.Graph
import io.github.aofeiliu.charsorter.client.InvalidRequestException
import io.github.aofeiliu.charsorter.client.ListOrder
import io.github.aofeiliu.charsorter.client.NextComparison
import io.github.aofeiliu.charsorter.client.NotAuthenticatedException
import io.github.aofeiliu.charsorter.client.ParsedEntry
import io.github.aofeiliu.charsorter.client.RankedCharacter
import io.github.aofeiliu.charsorter.client.Ranking
import io.github.aofeiliu.charsorter.client.RatingHistory
import io.github.aofeiliu.charsorter.client.Verdict
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One of the screens the prototype offers; see ROADMAP.md, "Scope". */
sealed interface Screen {
    data object Login : Screen
    data object PickList : Screen
    data class Sorting(val list: CharacterList) : Screen
    data class Ranking(val list: CharacterList) : Screen
    data class EditList(val list: CharacterList) : Screen
    data class CharacterTrend(
        val list: CharacterList,
        val character: RankedCharacter
    ) : Screen
    data class ListChart(val list: CharacterList) : Screen
    data class PasteCharacters(val list: CharacterList) : Screen
    data class PastePreview(val list: CharacterList) : Screen
}

/** What happened to one entry in a batch write. */
enum class WriteResult { ADDED, FAILED, NOT_SENT }

data class PasteWrite(val name: String, val fandom: String, val result: WriteResult)

/**
 * A comparison this run posted, with the question that produced it.
 *
 * The question is null only if one was somehow answered without a pair on
 * screen; undo then falls back to asking for a fresh one.
 */
data class UndoEntry(val record: Comparison, val question: NextComparison?)

data class UiState(
    val screen: Screen = Screen.Login,
    val busy: Boolean = false,
    val error: String? = null,
    val lists: List<CharacterList> = emptyList(),
    val pending: NextComparison? = null,
    val ranking: Ranking? = null,
    /**
     * The characters being edited, or null when they have not loaded.
     *
     * Null rather than empty on purpose: a list with no characters yet and a
     * list whose fetch failed are different states, and rendering both as an
     * empty screen would hide the failure with no way to retry.
     */
    val characters: List<Character>? = null,
    /**
     * Whether the edit screen orders by the list's ranking rather than by
     * when each character was added.
     *
     * Off by default because it is the expensive one: `GET /characters` just
     * serializes rows, while the ranking replays the list's whole comparison
     * history server-side. Opting in costs what opening the ranking screen
     * costs; leaving it off costs nothing.
     */
    val editByScore: Boolean = false,
    /**
     * Comparisons this run has posted, oldest first, each still undoable.
     *
     * The API has no `GET` on `/comparisons`, so a record id is only ever
     * seen in the `201` from our own `POST`. Undo therefore reaches back
     * exactly as far as this process does and no further — the HTML page's
     * Undo button, which re-queries the database, has no equivalent here.
     */
    val undoStack: List<UndoEntry> = emptyList(),
    /**
     * The character being ranked against the whole list, or null for the
     * default behaviour, where `/next` picks whoever is least certain.
     */
    val focus: Character? = null,
    /**
     * Each ranked character's rating and 2 * rd, keyed by character id.
     *
     * The ranking's own annotation is `rating - 2 * rd` with the uncertainty
     * already folded in, so the spread it was derived from has to come from
     * the graph endpoint separately. Null while it has not loaded, or when
     * the list's controller has no graph to fetch.
     */
    val spreads: Map<Int, RatingSpread>? = null,
    /** The character whose history the trend screen is showing, if loaded. */
    val history: RatingHistory? = null,
    /** Every character's rating and spread, for the whole-list chart. */
    val graph: Graph? = null,
    /**
     * The text in the paste editor.
     *
     * Held here, not in the editor composable, so leaving for the preview and
     * coming back restores it exactly — including the lines the parser held
     * back, which are the reason to come back at all.
     */
    val pasteText: String = "",
    /** A source line the editor should move the caret to, once. */
    val pasteCaret: Int? = null,
    /** Per-entry outcome of the last batch write, or null before one runs. */
    val pasteWrites: List<PasteWrite>? = null
)

/**
 * Owns the one [CharSorterClient] instance and every prototype screen's
 * state. The client's calls are blocking by design, so each one runs on
 * [Dispatchers.IO]; nothing here touches the UI thread except the state
 * update itself.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val client = CharSorterClient()
    private val sessionStore = SessionStore(application)
    private val orderStore = ListOrderStore(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val cookies = sessionStore.restore()
        if (cookies.isNotEmpty()) {
            client.cookieJar.restore(cookies)
        }
        if (client.isLoggedIn) {
            _state.update { it.copy(screen = Screen.PickList) }
            loadLists()
        }
    }

    fun login(username: String, password: String) = runApiCall {
        client.login(username, password)
        sessionStore.save(client.cookieJar.save())
        _state.update { it.copy(screen = Screen.PickList) }
        loadListsBlocking()
    }

    fun loadLists() = runApiCall { loadListsBlocking() }

    fun openForSorting(list: CharacterList) {
        _state.update {
            it.copy(
                screen = Screen.Sorting(list), pending = null,
                undoStack = emptyList(), focus = null
            )
        }
        runApiCall { loadNextBlocking(list) }
    }

    /**
     * Answers the pending comparison, then asks for the next one.
     *
     * The answered pair is dropped in the same update that banks the record,
     * and only once the `POST` has succeeded. If the *following* `/next` call
     * fails, the screen must not be left offering the pair that was just
     * committed: answering it again would write a second `SortRecord` for the
     * same comparison, which `compute_ratings` would replay — silently
     * skewing the list. A failed `POST` leaves [UiState.pending] alone, since
     * nothing was recorded and re-answering is then correct.
     */
    fun answer(list: CharacterList, char1: Int, char2: Int, verdict: Verdict) = runApiCall {
        val record = client.submitComparison(list.id, char1, char2, verdict)
        _state.update {
            it.copy(
                pending = null,
                undoStack = it.undoStack + UndoEntry(record, it.pending)
            )
        }
        loadNextBlocking(list)
    }

    /**
     * Deletes the most recent comparison this run posted and puts its pair
     * back on screen.
     *
     * The pair is the cached question, not a fresh `/next`: Glicko samples
     * `/next` from a softmax, so re-fetching would ask something else and the
     * answer just taken back could never be reconsidered. Its progress string
     * is correct again too, the delete having restored the state it was
     * computed in. The entry is popped only once the server has accepted the
     * delete, so a failed undo stays on the stack and can be retried.
     */
    fun undo(list: CharacterList) {
        val entry = _state.value.undoStack.lastOrNull() ?: return
        runApiCall {
            client.deleteComparison(list.id, entry.record.id)
            _state.update {
                it.copy(pending = entry.question, undoStack = it.undoStack.dropLast(1))
            }
            if (entry.question == null) {
                loadNextBlocking(list)
            }
        }
    }

    fun openForEditing(list: CharacterList) {
        _state.update {
            it.copy(screen = Screen.EditList(list), characters = null, ranking = null)
        }
        runApiCall { loadCharactersBlocking(list) }
    }

    /**
     * Switches the edit screen between insertion order and ranked order.
     *
     * Turning it on fetches the ranking if this list's is not already held.
     * Turning it off keeps whatever was fetched, so toggling back does not
     * pay for the replay twice.
     */
    fun setEditSort(list: CharacterList, byScore: Boolean) {
        _state.update { it.copy(editByScore = byScore) }
        if (byScore && _state.value.ranking?.id != list.id) {
            runApiCall { loadRankingBlocking(list) }
        }
    }

    /** Re-reads the characters after a failed fetch left the screen empty. */
    fun loadCharacters(list: CharacterList) = runApiCall { loadCharactersBlocking(list) }

    fun addCharacter(list: CharacterList, name: String, fandom: String) = runApiCall {
        client.addCharacter(list.id, name, fandom)
        loadCharactersBlocking(list)
    }

    fun updateCharacter(
        list: CharacterList,
        charId: Int,
        name: String,
        fandom: String
    ) = runApiCall {
        client.updateCharacter(list.id, charId, name = name, fandom = fandom)
        loadCharactersBlocking(list)
    }

    /**
     * Deletes a character, and with it every comparison it took part in.
     *
     * `SortRecord.char1` and `char2` both cascade, so this rewrites the
     * list's history and changes where every *other* character ranks. The
     * confirmation in the UI says so; this is not an undoable edit.
     */
    fun deleteCharacter(list: CharacterList, charId: Int) = runApiCall {
        client.deleteCharacter(list.id, charId)
        loadCharactersBlocking(list)
    }

    fun openPaste(list: CharacterList) {
        _state.update {
            it.copy(
                screen = Screen.PasteCharacters(list),
                pasteText = "",
                pasteCaret = null,
                pasteWrites = null
            )
        }
    }

    fun setPasteText(text: String) = _state.update { it.copy(pasteText = text) }

    fun openPastePreview(list: CharacterList) =
        _state.update { it.copy(screen = Screen.PastePreview(list)) }

    /** Returns to the editor, optionally with the caret on [line]. */
    fun editPasteText(list: CharacterList, line: Int? = null) {
        _state.update {
            it.copy(screen = Screen.PasteCharacters(list), pasteCaret = line)
        }
    }

    fun pasteCaretHandled() = _state.update { it.copy(pasteCaret = null) }

    /**
     * Posts each entry, then refetches once.
     *
     * `addCharacter` refetches the character list — and the ranking, when the
     * by-score chip is on — after every single write, which a paste would pay
     * N times over. This posts N and reads back at the end instead.
     *
     * The run stops at the first failure: whatever broke the fifth request
     * will almost certainly break the sixth, and per-entry results are kept so
     * the screen can say which landed. Re-running the paste afterwards is safe
     * without any bookkeeping, because the refetch makes `reviewPaste` mark
     * the ones that got through as already in the list.
     */
    fun addPasted(list: CharacterList, entries: List<ParsedEntry>) = runApiCall {
        val writes = entries.map {
            PasteWrite(it.name, it.fandom, WriteResult.NOT_SENT)
        }.toMutableList()
        _state.update { it.copy(pasteWrites = writes.toList()) }

        var failure: ApiException? = null
        for (index in entries.indices) {
            try {
                client.addCharacter(list.id, entries[index].name, entries[index].fandom)
                writes[index] = writes[index].copy(result = WriteResult.ADDED)
            } catch (err: ApiException) {
                writes[index] = writes[index].copy(result = WriteResult.FAILED)
                failure = err
            }
            _state.update { it.copy(pasteWrites = writes.toList()) }
            if (failure != null) {
                break
            }
        }

        loadCharactersBlocking(list)
        if (failure == null) {
            _state.update { it.copy(pasteText = "") }
        } else {
            throw failure
        }
    }

    /** Leaves the write report and returns to the list being edited. */
    fun closePasteReport(list: CharacterList) {
        _state.update {
            it.copy(screen = Screen.EditList(list), pasteWrites = null)
        }
    }

    fun createList(title: String, controllerType: String) = runApiCall {
        client.createList(title, controllerType)
        loadListsBlocking()
    }

    fun renameList(list: CharacterList, title: String) = runApiCall {
        client.updateList(list.id, title = title)
        loadListsBlocking()
        _state.update { it.copy(screen = Screen.EditList(list.copy(title = title))) }
    }

    /** Deletes a list, its characters and its entire comparison history. */
    fun deleteList(list: CharacterList) = runApiCall {
        client.deleteList(list.id)
        _state.update { it.copy(screen = Screen.PickList, characters = null) }
        loadListsBlocking()
    }

    /**
     * Re-reads the characters from the server after every write.
     *
     * A round trip per edit, deliberately: the server owns ids, ordering and
     * validation, and these are not hot paths. Patching the local list
     * instead would invent a second source of truth for the sake of a
     * request nobody is waiting on.
     */
    private fun loadCharactersBlocking(list: CharacterList) {
        _state.update { it.copy(characters = client.characters(list.id)) }
        // Ratings move under every write -- a delete takes that character's
        // comparisons with it and re-ranks everyone else -- so a held ranking
        // is stale the moment anything changes.
        if (_state.value.editByScore) {
            loadRankingBlocking(list)
        }
    }

    private fun loadRankingBlocking(list: CharacterList) {
        _state.update { it.copy(ranking = client.ranking(list.id)) }
    }

    /** Re-asks for a comparison after a failed fetch left the screen empty. */
    fun loadNext(list: CharacterList) = runApiCall { loadNextBlocking(list) }

    private fun loadNextBlocking(list: CharacterList) {
        val focus = _state.value.focus
        val next = client.nextComparison(list.id, focus = focus?.id)
        _state.update { it.copy(pending = next) }
    }

    /** Ranks [character] against the whole list until stopped. */
    fun startFocus(list: CharacterList, character: Character) {
        _state.update { it.copy(focus = character) }
        runApiCall { loadNextBlocking(list) }
    }

    /**
     * Leaves focus, keeping the pair already on screen.
     *
     * Dropping it would throw away a question the user can still answer, and
     * the answer is equally valid either way — focus changes which question
     * is asked, not what a comparison means.
     */
    fun stopFocus() {
        _state.update { it.copy(focus = null) }
    }

    /**
     * Opens the ranking, then fills the spreads in behind it.
     *
     * Both calls replay the list's whole history server-side, so waiting for
     * the second before drawing anything doubled the time to first paint on a
     * long list. The ranking is published as soon as it lands and the spreads
     * appear under the scores when they follow.
     */
    fun openForRanking(list: CharacterList) = runApiCall {
        _state.update {
            it.copy(screen = Screen.Ranking(list), ranking = null, spreads = null)
        }
        val ranking = client.ranking(list.id)
        _state.update { it.copy(ranking = ranking) }
        val spreads = spreadsFor(ranking)
        _state.update { it.copy(spreads = spreads) }
    }

    /** Re-reads the ranking after a failed fetch left the screen empty. */
    fun loadRanking(list: CharacterList) = runApiCall {
        val ranking = client.ranking(list.id)
        _state.update { it.copy(ranking = ranking) }
        val spreads = spreadsFor(ranking)
        _state.update { it.copy(spreads = spreads) }
    }

    /**
     * The spread behind each ranked character, or null if it cannot be had.
     *
     * The graph endpoint returns parallel arrays in the ranking's own order,
     * so they zip by position. A length mismatch means the two views of the
     * list disagree — a character added between the two calls — and the
     * spreads are dropped rather than shown against the wrong names. A list
     * whose controller has no graph 404s, which is not an error worth
     * surfacing: the ranking itself is fine without it.
     */
    private fun spreadsFor(ranking: Ranking): Map<Int, RatingSpread>? = try {
        val graph = client.graph(ranking.id)
        if (graph.ratings.size != ranking.characters.size ||
            graph.doubleRds.size != ranking.characters.size
        ) {
            null
        } else {
            ranking.characters.mapIndexed { index, char ->
                char.id to RatingSpread(graph.ratings[index], graph.doubleRds[index])
            }.toMap()
        }
    } catch (err: ApiException) {
        null
    }

    fun openForTrend(list: CharacterList, character: RankedCharacter) = runApiCall {
        _state.update {
            it.copy(screen = Screen.CharacterTrend(list, character), history = null)
        }
        val history = client.characterHistory(list.id, character.id)
        _state.update { it.copy(history = history) }
    }

    /** Re-asks for a history after a failed fetch left the screen empty. */
    fun loadHistory(list: CharacterList, charId: Int) = runApiCall {
        val history = client.characterHistory(list.id, charId)
        _state.update { it.copy(history = history) }
    }

    fun openForChart(list: CharacterList) = runApiCall {
        _state.update { it.copy(screen = Screen.ListChart(list), graph = null) }
        val graph = client.graph(list.id)
        _state.update { it.copy(graph = graph) }
    }

    /** Re-asks for the graph after a failed fetch left the chart empty. */
    fun loadGraph(list: CharacterList) = runApiCall {
        val graph = client.graph(list.id)
        _state.update { it.copy(graph = graph) }
    }

    /**
     * Unwinds one screen, for the system back gesture.
     *
     * Returns false when there is nowhere left to go, so the caller can let
     * the system handle it and leave the app rather than trapping the user.
     */
    fun back(): Boolean {
        val screen = _state.value.screen
        return when (screen) {
            is Screen.CharacterTrend -> {
                _state.update {
                    it.copy(screen = Screen.Ranking(screen.list), history = null)
                }
                true
            }
            is Screen.ListChart -> {
                _state.update {
                    it.copy(screen = Screen.Ranking(screen.list), graph = null)
                }
                true
            }
            // Back out of the preview is the same call as its "Edit text"
            // button, so the gesture cannot be the one path that loses a paste.
            is Screen.PastePreview -> {
                editPasteText(screen.list)
                true
            }
            is Screen.PasteCharacters -> {
                _state.update {
                    it.copy(screen = Screen.EditList(screen.list), pasteWrites = null)
                }
                true
            }
            is Screen.Sorting, is Screen.Ranking, is Screen.EditList -> {
                backToLists()
                true
            }
            Screen.Login, Screen.PickList -> false
        }
    }

    fun backToLists() {
        _state.update {
            it.copy(
                screen = Screen.PickList,
                pending = null,
                ranking = null,
                characters = null,
                spreads = null,
                history = null,
                graph = null,
                pasteText = "",
                pasteCaret = null,
                pasteWrites = null,
                undoStack = emptyList()
            )
        }
        loadLists()
    }

    fun logout() {
        client.logout()
        sessionStore.clear()
        _state.update { UiState(screen = Screen.Login) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /**
     * Reads the lists and applies the caller's saved order.
     *
     * The reconciled order is written back when it differs, which is what
     * prunes an id whose list is gone — deleted here, or on the website. Every
     * path that changes the lists already funnels through here, so nothing
     * else has to remember to prune.
     */
    private fun loadListsBlocking() {
        val saved = orderStore.restore()
        val lists = ListOrder.apply(client.lists(), saved)
        val ids = lists.map { it.id }
        if (ids != saved) {
            orderStore.save(ids)
        }
        _state.update { it.copy(lists = lists) }
    }

    /** Shifts a list one place in the local order. No server call: the order is ours. */
    fun moveList(list: CharacterList, offset: Int) {
        val ordered = _state.value.lists
        val ids = ListOrder.moved(ordered.map { it.id }, list.id, offset)
        orderStore.save(ids)
        _state.update { it.copy(lists = ListOrder.apply(ordered, ids)) }
    }

    /**
     * Runs [block] on [Dispatchers.IO], surfacing any failure as [UiState.error].
     *
     * [NotAuthenticatedException] additionally drops the client back to the
     * login screen: the stored session is stale, so retrying without a fresh
     * login would just repeat the same 401.
     */
    private fun runApiCall(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (err: NotAuthenticatedException) {
                sessionStore.clear()
                _state.update { UiState(screen = Screen.Login, error = err.message) }
            } catch (err: ApiException) {
                _state.update { it.copy(error = describe(err)) }
            } catch (err: IOException) {
                _state.update { it.copy(error = "Network error: ${err.message}") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Renders an [ApiException] for the Snackbar.
     *
     * [InvalidRequestException.fields] names which field a `ModelForm`
     * rejected and why; folding it into the message is the difference
     * between "The request was rejected." and "title: This field is
     * required."
     */
    private fun describe(err: ApiException): String {
        if (err !is InvalidRequestException || err.fields.isEmpty()) {
            return err.message ?: "Something went wrong."
        }
        return err.fields.entries.joinToString("; ") { (field, reasons) ->
            "$field: ${reasons.joinToString(", ")}"
        }
    }
}
