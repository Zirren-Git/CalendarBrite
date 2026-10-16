package com.dayflow.planner.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dayflow.planner.DayFlowApp

/**
 * Shared plumbing: view models are created from the app container
 * (repository + settings) with a tiny factory DSL.
 */
inline fun <reified VM : ViewModel> containerFactory(
    context: Context,
    crossinline create: (app: DayFlowApp) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(DayFlowApp.from(context.applicationContext)) }
}
