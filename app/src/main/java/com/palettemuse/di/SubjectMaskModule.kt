package com.palettemuse.di

import com.palettemuse.core.StubSubjectMaskProvider
import com.palettemuse.core.SubjectMaskProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding the [SubjectMaskProvider] interface to its concrete
 * implementation.
 *
 * Currently bound to [StubSubjectMaskProvider] (deterministic center-crop
 * mask). Slice #51 swaps this to [RealSubjectMaskProvider] (InSPyReNet via
 * ONNX Runtime Mobile, NNAPI EP) once the on-device spike clears.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SubjectMaskModule {

    @Binds
    @Singleton
    abstract fun bindSubjectMaskProvider(
        impl: StubSubjectMaskProvider
    ): SubjectMaskProvider
}
