package dev.tsnanh.vku.viewmodels

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import dev.tsnanh.vku.domain.usecases.RetrieveTeachersUseCase

class TeacherEvaluationViewModel @ViewModelInject constructor(
    useCase: RetrieveTeachersUseCase
) : ViewModel() {
    var teachers = useCase.getTeachersLiveData()
}