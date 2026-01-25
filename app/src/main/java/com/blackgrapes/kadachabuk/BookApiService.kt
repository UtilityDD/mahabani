package com.blackgrapes.kadachabuk

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface BookApiService {
    @GET
    suspend fun getBookCsvData(@Url url: String): Response<String>
}
