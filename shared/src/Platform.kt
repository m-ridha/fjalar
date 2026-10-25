package com.muhammadridha.fjalar

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
