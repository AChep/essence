package com.artemchep.essence.domain.adapters.weather.accuweather.beans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WindBean(
    @SerialName("Speed")
    val speed: WindSpeedBean
)
