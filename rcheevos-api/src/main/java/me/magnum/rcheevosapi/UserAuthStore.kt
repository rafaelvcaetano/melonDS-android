package me.magnum.rcheevosapi

import me.magnum.rcheevosapi.model.RAUserAuth

interface UserAuthStore {
    suspend fun storeUserAuth(userAuth: RAUserAuth)
    suspend fun getUserAuth(): RAUserAuth?
}