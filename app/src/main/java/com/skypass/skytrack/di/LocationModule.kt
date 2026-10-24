package com.skypass.skytrack.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.skypass.skytrack.data.location.DeviceLocationProvider
import com.skypass.skytrack.data.location.FusedDeviceLocationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideDeviceLocationProvider(
        fusedDeviceLocationProvider: FusedDeviceLocationProvider
    ): DeviceLocationProvider = fusedDeviceLocationProvider
}
