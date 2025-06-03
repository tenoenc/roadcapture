package com.tenacy.roadcapture.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignupAgreementViewModel @Inject constructor(
): BaseViewModel() {

    private val _agreeTermsOfService = MutableStateFlow(false)
    val agreeTermsOfService = _agreeTermsOfService.asStateFlow()

    private val _agreePrivacyPolicy = MutableStateFlow(false)
    val agreePrivacyPolicy = _agreePrivacyPolicy.asStateFlow()

    val allChecked = combine(_agreeTermsOfService, _agreePrivacyPolicy) { agreeTermsOfService, agreePrivacyPolicy ->
        agreeTermsOfService && agreePrivacyPolicy
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    fun onAgreePrivacyPolicyClick() {
        viewModelScope.launch(Dispatchers.Default) {
            _agreePrivacyPolicy.update { !it }
        }
    }

    fun onAgreePrivacyPolicyDetailClick() {
        viewEvent(SignupAgreementViewEvent.NavigateToHtml(HtmlType.PrivacyPolicyAgreement))
    }

    fun onAgreeTermsOfServiceClick() {
        viewModelScope.launch(Dispatchers.Default) {
            _agreeTermsOfService.update { !it }
        }
    }

    fun onAgreeTermsOfServiceDetailClick() {
        viewEvent(SignupAgreementViewEvent.NavigateToHtml(HtmlType.TermsOfServiceAgreement))
    }

    fun onAgreeAllClick() {
        viewModelScope.launch(Dispatchers.Default) {
            val requireCheckAll = !_agreeTermsOfService.value || !_agreePrivacyPolicy.value
            _agreeTermsOfService.emit(requireCheckAll)
            _agreePrivacyPolicy.emit(requireCheckAll)
        }
    }

    fun onStartClick() {
        viewEvent(SignupAgreementViewEvent.Start)
    }
}