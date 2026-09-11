package io.github.aofeiliu.charsorter.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.aofeiliu.charsorter.client.ApiException
import io.github.aofeiliu.charsorter.client.CharSorterClient
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
}

data class UiState(
    val screen: Screen = Screen.Login,
    val busy: Boolean = false,
    val error: String? = null,
    val lists: List<CharacterList> = emptyList(),
    val pending: NextComparison? = null,
    val ranking: Ranking? = null,
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

    fun answer(list: CharacterList, char1: Int, char2: Int, verdict: Verdict) = runApiCall {
        val record = client.submitComparison(list.id, char1, char2, verdict)
        _state.update { it.copy(undoStack = it.undoStack + record) }
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
     */
    fun undo(list: CharacterList) {
        val record = _state.value.undoStack.lastOrNull() ?: return
        runApiCall {
            client.deleteComparison(list.id, record.id)
            _state.update { it.copy(undoStack = it.undoStack.dropLast(1)) }
            loadNextBlocking(list)
        }
    }

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
