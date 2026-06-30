package com.kannod.virtualcloset

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TryOnViewModel(private val repo: StyleSnapRepository) : ViewModel() {

    private val _resultBitmap = MutableLiveData<Bitmap?>()
    val resultBitmap: LiveData<Bitmap?> = _resultBitmap

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun generate(person: Bitmap, garment: Bitmap, apiKey: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            repo.virtualTryOn(person, garment, apiKey)
                .onSuccess { bitmap ->
                    _resultBitmap.value = bitmap
                }
                .onFailure { exception ->
                    _error.value = exception.message
                    _resultBitmap.value = null
                }
            
            _isLoading.value = false
        }
    }
}

class TryOnViewModelFactory(private val repo: StyleSnapRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TryOnViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TryOnViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
