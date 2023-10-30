package me.magnum.rcheevosapi

import me.magnum.rcheevosapi.model.RAUserAuth

interface RAUserAuthStore {
    suspend fun storeUserAuth(userAuth: RAUserAuth)
    suspend fun getUserAuth(): RAUserAuth?
    suspend fun clearUserAuth()
}