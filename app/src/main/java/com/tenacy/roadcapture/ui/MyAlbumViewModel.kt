package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.AggregateSource
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.ui.dto.User
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MyAlbumViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : BaseViewModel() {

    private val _refreshAllEvent = MutableSharedFlow<Unit>()
    val refreshAllEvent = _refreshAllEvent.asSharedFlow()
        .onEach {
            fetchData()
        }

    private val _user = MutableStateFlow<User?>(null)

    val displayName = _user.filterNotNull().map { it.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")
    val photoUrl = _user.filterNotNull().map { it.photoUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    val isUserVisible = _user
        .scan(false) { acc, user -> acc || user != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val totalCounts = _user.filterNotNull().map {
        mapOf(
            KEY_ALBUM_COUNT to it.albumCount,
            KEY_MEMORY_COUNT to it.memoryCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

//    val albumCount = totalCounts.filterNotNull().mapNotNull { it[KEY_ALBUM_COUNT] }
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)
//
//    val memoryCount = totalCounts.filterNotNull().mapNotNull { it[KEY_MEMORY_COUNT] }
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val scrapText = _user.filterNotNull().map {
        val (scrapCount, scrapCountUnit) = it.scrapCount.toReadableUnit()
        "앨범이 ${scrapCount.toFormattedDecimalText(hasZeroText = false)}${scrapCountUnit}번 스크랩 되었어요"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    init {
        fetchData()
    }

    fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val userId = UserPref.id
                val userRef = db.collection(FirebaseConstants.COLLECTION_USERS)
                    .document(userId)
                val user = userRef.get().await().toUser()

                val associate = listOf(
                    async {
                        KEY_ALBUM_COUNT to db.collection(FirebaseConstants.COLLECTION_ALBUMS)
                            .whereEqualTo("userRef", userRef)
                            .count().get(AggregateSource.SERVER).await().count
                    },
                    async {
                        KEY_MEMORY_COUNT to db.collection(FirebaseConstants.COLLECTION_MEMORIES)
                            .whereEqualTo("userRef", userRef)
                            .count().get(AggregateSource.SERVER).await().count
                    },
                ).awaitAll()
                    .associate { it.first to it.second }

                val albumCount = associate[KEY_ALBUM_COUNT] ?: 0L
                val memoryCount = associate[KEY_MEMORY_COUNT] ?: 0L

                emit(User.from(user, albumCount, memoryCount))
            }
                .catch { exception ->
                    Log.e("MyAlbumViewModel", "에러", exception)
                }
                .collect {
                    _user.emit(it)
                }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _refreshAllEvent.emit(Unit)
        }
    }

    fun onMoreClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(MyAlbumViewEvent.ShowMore)
        }
    }

    companion object {
        const val KEY_ALBUM_COUNT = "album_count"
        const val KEY_MEMORY_COUNT = "memory_count"
    }
}