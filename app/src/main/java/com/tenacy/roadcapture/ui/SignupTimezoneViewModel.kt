package com.tenacy.roadcapture.ui

import android.content.res.Configuration
import android.icu.util.TimeZone
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tenacy.roadcapture.ui.dto.SearchableTimezone
import com.tenacy.roadcapture.ui.dto.Timezone
import com.tenacy.roadcapture.util.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SignupTimezoneViewModel @Inject constructor(
    private val resourcesProvider: ResourceProvider
) : BaseViewModel() {

    private val supportedLocales = listOf(
        Locale.ENGLISH, Locale.KOREAN, Locale.SIMPLIFIED_CHINESE,
        Locale("es"), Locale("pt"), Locale.JAPANESE, Locale.GERMAN,
        Locale.FRENCH, Locale("ru"), Locale("in"), Locale("hi"), Locale("ar")
    )

    val searchQuery = MutableStateFlow("")

    private var _originalTimezones: List<Timezone> = emptyList()

    private val _timezones = MutableStateFlow<List<SearchableTimezone>>(emptyList())
    val timezones = _timezones.asStateFlow()

    val anySelected = _timezones.map { timezones ->
        timezones.any { it.isSelected }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    init {
        loadTimezones()
        observeSearchQuery()
    }

    private fun loadTimezones() {
        viewModelScope.launch {
            try {
                val timezones = loadTimezonesFromAssets().also { _originalTimezones = it }
                val searchableTimezones = timezones.map { timezone ->
                    SearchableTimezone(
                        id = timezone.id,
                        originalValue = timezone.value,
                        originalText = timezone.text,
                        flag = timezone.flag,
                        localizedName = getLocalizedName(timezone.key),
                        utcText = timezone.text,
                        allLocalizedNames = getAllLocalizedNames(timezone.key),
                        isSelected = timezone.utc.contains(TimeZone.getDefault().id),
                    )
                }
                _timezones.emit(searchableTimezones)
            } catch (e: Exception) {
                _timezones.emit(emptyList())
            }
        }
    }

    private suspend fun loadTimezonesFromAssets(): List<Timezone> = withContext(Dispatchers.IO) {
        try {
            val json = resourcesProvider.getConfigurationContext().assets.open("timezones.json").bufferedReader().use { it.readText() }
            val gson = Gson()
            val type = object : TypeToken<List<Timezone>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getLocalizedName(key: String, locale: Locale = resourcesProvider.getConfigurationContext().resources.configuration.locales[0]): String {
        val configuration = Configuration(resourcesProvider.getConfigurationContext().resources.configuration).apply {
            setLocale(locale)
        }
        val localizedContext = resourcesProvider.getConfigurationContext().createConfigurationContext(configuration)
        val resourceId = localizedContext.resources.getIdentifier(key, "string", resourcesProvider.getConfigurationContext().packageName)

        return if (resourceId != 0) {
            localizedContext.getString(resourceId)
        } else {
            key
        }
    }

    private fun getAllLocalizedNames(key: String): Map<String, String> {
        return supportedLocales.associate { locale ->
            locale.language to getLocalizedName(key, locale)
        }.filterValues { it != key }
    }

    private fun observeSearchQuery() {
        viewModelScope.launch {
            searchQuery
                .debounce(300L)
                .collect { query ->
                    filterTimezones(query)
                }
        }
    }

    private fun filterTimezones(query: String) {
        val allTimezones = _timezones.value

        val filteredTimezones = if (query.isBlank()) {
            allTimezones.map { it.copy(isFiltered = true) }
        } else {
            allTimezones.map { it.copy(isFiltered = it.matchesQuery(query)) }
        }

        _timezones.update { filteredTimezones }
    }

    fun selectTimezone(timezoneId: Int) {
        _timezones.update {
            it.map { timezone ->
                timezone.copy(isSelected = timezoneId == timezone.id)
            }
        }
    }

    fun onNextClick() {
        val selectedTimezoneId = _timezones.value.find { it.isSelected }?.id ?: return
        val selectedTimezone = _originalTimezones.find { it.id == selectedTimezoneId } ?: return
        viewEvent(SignupTimezoneViewEvent.Next(selectedTimezone))
    }
}