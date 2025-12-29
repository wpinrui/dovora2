package com.wpinrui.dovora.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wpinrui.dovora.data.model.SearchResult
import com.wpinrui.dovora.data.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedResult = MutableStateFlow<SearchResult?>(null)
    val selectedResult: StateFlow<SearchResult?> = _selectedResult.asStateFlow()

    private val _hasPerformedSearch = MutableStateFlow(false)
    val hasPerformedSearch: StateFlow<Boolean> = _hasPerformedSearch.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _hasPerformedSearch.value = false
        }
    }

    fun setInitialQuery(query: String) {
        if (query.isNotBlank()) {
            // Always clear previous state and set new query when navigating with a search param
            _searchQuery.value = query
            _searchResults.value = emptyList()
            _hasPerformedSearch.value = false
            performSearch()
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _hasPerformedSearch.value = false
    }

    fun performSearch() {
        val query = _searchQuery.value.trim()
        viewModelScope.launch {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                _isLoading.value = false
                return@launch
            }

            _isLoading.value = true
            _hasPerformedSearch.value = true
            searchRepository.search(query).collect { results ->
                _searchResults.value = results
                _isLoading.value = false
            }
        }
    }

    fun onResultClick(result: SearchResult) {
        _selectedResult.value = result
    }

    fun dismissDownloadDialog() {
        _selectedResult.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                return SearchViewModel(SearchRepository.getInstance(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
