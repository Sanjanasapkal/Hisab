package com.example.hisab.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface HisabApiService {

    // --- Authentication ---

    @POST("api/auth/register")
    suspend fun register(
        @Body req: RegisterRequest
    ): Response<ApiResponse<RegisterResponseData>>

    @POST("api/auth/verify-email-otp")
    suspend fun verifyEmailOtp(
        @Body req: VerifyOtpRequest
    ): Response<ApiResponse<Unit>>

    @POST("api/auth/resend-email-otp")
    suspend fun resendEmailOtp(
        @Body req: ResendOtpRequest
    ): Response<ApiResponse<Unit>>

    @POST("api/auth/login")
    suspend fun login(
        @Body req: LoginRequest
    ): Response<ApiResponse<LoginResponseData>>

    @POST("api/auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>

    @GET("api/auth/me")
    suspend fun getMe(): Response<ApiResponse<UserProfileDto>>

    @POST("api/auth/forgot-username")
    suspend fun forgotUsername(
        @Body req: ForgotUsernameRequest
    ): Response<ApiResponse<Unit>>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(
        @Body req: ForgotPasswordRequest
    ): Response<ApiResponse<Unit>>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(
        @Body req: ResetPasswordRequest
    ): Response<ApiResponse<Unit>>

    // --- Cloud Ledger Persistence ---

    @GET("api/people")
    suspend fun getPeople(): Response<PeopleResponse>

    @GET("api/people/{id}/history")
    suspend fun getPersonHistory(
        @Path("id") personId: String
    ): Response<ApiResponse<PersonHistoryResponseData>>

    @POST("api/people")
    suspend fun createPerson(
        @Body req: CreatePersonRequest
    ): Response<ApiResponse<PersonDto>>

    @PATCH("api/people/{id}")
    suspend fun updatePerson(
        @Path("id") personId: String,
        @Body req: CreatePersonRequest
    ): Response<ApiResponse<PersonDto>>

    @DELETE("api/people/{id}")
    suspend fun deletePerson(
        @Path("id") personId: String
    ): Response<ApiResponse<Unit>>

    @POST("api/transactions")
    suspend fun addTransaction(
        @Body req: CreateTransactionRequest
    ): Response<ApiResponse<TransactionResponseData>>

    @DELETE("api/transactions/{id}")
    suspend fun deleteTransaction(
        @Path("id") transactionId: String
    ): Response<ApiResponse<Unit>>

    @POST("api/transactions/settle")
    suspend fun settleHisab(
        @Body req: SettleRequest
    ): Response<ApiResponse<Any>>
}
