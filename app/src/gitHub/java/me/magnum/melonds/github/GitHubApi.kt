package me.magnum.melonds.github

import io.reactivex.Single
import me.magnum.melonds.github.dtos.ReleaseDto
import retrofit2.http.GET

interface GitHubApi {
    @GET("/repos/rafaelvcaetano/melonDS-android/releases/latest")
    fun getLatestRelease(): Single<ReleaseDto>

    @GET("/repos/rafaelvcaetano/melonDS-android/releases/tags/nightly-release")
    fun getLatestNightlyRelease(): Single<ReleaseDto>
}