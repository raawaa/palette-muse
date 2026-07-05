package com.palettemuse.di

import com.palettemuse.core.RealSubjectMaskProvider
import com.palettemuse.core.SubjectMaskProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bound to [RealSubjectMaskProvider] (InSPyReNet via ONNX Runtime Mobile,
 * NNAPI EP). To fall back to the deterministic stub (e.g. for JVM tests
 * without the model), swap the binding to [StubSubjectMaskProvider].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SubjectMaskModule {

    @Binds
    @Singleton
    abstract fun bindSubjectMaskProvider(
        impl: RealSubjectMaskProvider
    ): SubjectMaskProvider
}
