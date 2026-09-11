package io.github.aofeiliu.charsorter.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.aofeiliu.charsorter.client.ApiException
import io.github.aofeiliu.charsorter.client.CharSorterClient
import io.github.aofeiliu.charsorter.client.Character
import io.github.aofeiliu.charsorter.client.CharacterList
import io.github.aofeiliu.charsorter.client.Comparison
import io.github.aofeiliu.charsorter.client.NextComparison
import io.github.aofeiliu.charsorter.client.NotAuthenticatedException
import io.github.aofeiliu.charsorter.client.Ranking
import io.github.aofeiliu.charsorter.client.Verdict
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One of the four screens the P1 prototype offers; see ROADMAP.md, "Scope". */
sealed interface Screen {
    data object Login : Screen
    data object PickList : Screen
    data class Sorting(val list: CharacterList) : Screen
    data class Ranking(val list: CharacterList) : Screen
    data class EditList(val list: CharacterList) : Screen
}

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
    val undoStack: List<Comparison> = emptyList()
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
            it.copy(screen = Screen.Sorting(list), pending = null, undoStack = emptyList())
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
        _state.update { it.copy(pending = null, undoStack = it.undoStack + record) }
        loadNextBlocking(list)
    }

    /**
     * Deletes the most recent comparison this run posted.
     *
     * The pair that comes back afterwards is a *different* question for a
     * Glicko list, which samples `/next` from a softmax — undoing takes the
     * record back, it does not re-ask what was just answered. The entry is
     * popped only once the server has accepted the delete, so a failed undo
     * stays on the stack and can be retried.
     *
     * The pending pair is dropped alongside the pop for the same reason as in
     * [answer], though the risk here is weaker: a pair left over from a failed
     * `/next` was never answered, so re-answering it would write a first
     * record rather than a duplicate. It is cleared anyway, because that pair
     * was chosen against ratings the undo has since changed.
     */
    fun undo(list: CharacterList) {
        val record = _state.value.undoStack.lastOrNull() ?: return
        runApiCall {
            client.deleteComparison(list.id, record.id)
            _state.update { it.copy(pending = null, undoStack = it.undoStack.dropLast(1)) }
            loadNextBlocking(list)
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
        val next = client.nextComparison(list.id)
        _state.update { it.copy(pending = next) }
    }

    fun openForRanking(list: CharacterList) = runApiCall {
        _state.update { it.copy(screen = Screen.Ranking(list)) }
        val ranking = client.ranking(list.id)
        _state.update { it.copy(ranking = ranking) }
    }

    fun backToLists() {
        _state.update {
            it.copy(
                screen = Screen.PickList,
                pending = null,
                ranking = null,
                characters = null,
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

    private fun loadListsBlocking() {
        val lists = client.lists()
        _state.update { it.copy(lists = lists) }
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
                _state.update { it.copy(error = err.message) }
            } catch (err: IOException) {
                _state.update { it.copy(error = "Network error: ${err.message}") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
