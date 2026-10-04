package com.example.hisab.api

import com.google.gson.annotations.SerializedName

/**
 * Standard API Response envelope matching backend controllers.
 */
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("errors") val errors: List<String>? = null,
    @SerializedName("remainingAttempts") val remainingAttempts: Int? = null,
    @SerializedName("requiresVerification") val requiresVerification: Boolean? = null,
    @SerializedName("unverifiedEmail") val unverifiedEmail: String? = null
)

// --- Auth DTOs ---

data class RegisterRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("confirmPassword") val confirmPassword: String
)

data class RegisterResponseData(
    @SerializedName("userId") val userId: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("expiresInSeconds") val expiresInSeconds: Int,
    @SerializedName("resendCooldownSeconds") val resendCooldownSeconds: Int
)

data class VerifyOtpRequest(
    @SerializedName("email") val email: String,
    @SerializedName("otp") val otp: String
)

data class ResendOtpRequest(
    @SerializedName("email") val email: String
)

data class LoginRequest(
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String
)

data class UserProfileDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String,
    @SerializedName("emailVerified") val emailVerified: Boolean,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class LoginResponseData(
    @SerializedName("token") val token: String,
    @SerializedName("expiresIn") val expiresIn: String,
    @SerializedName("user") val user: UserProfileDto
)

data class ForgotUsernameRequest(
    @SerializedName("email") val email: String
)

data class ForgotPasswordRequest(
    @SerializedName("identifier") val identifier: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null
)

data class ResetPasswordRequest(
    @SerializedName("token") val token: String,
    @SerializedName("newPassword") val newPassword: String,
    @SerializedName("confirmPassword") val confirmPassword: String,
    @SerializedName("email") val email: String? = null
)

// --- Ledger DTOs ---

data class PersonDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("currentBalancePaise") val currentBalancePaise: Long,
    @SerializedName("clientLocalId") val clientLocalId: Long? = null
)

data class DashboardSummaryDto(
    @SerializedName("totalOthersOweMe") val totalOthersOweMe: Long,
    @SerializedName("totalIOweOthers") val totalIOweOthers: Long,
    @SerializedName("netBalance") val netBalance: Long,
    @SerializedName("activePeopleCount") val activePeopleCount: Int
)

data class PeopleResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("summary") val summary: DashboardSummaryDto?,
    @SerializedName("data") val data: List<PersonDto>?
)

data class CreatePersonRequest(
    @SerializedName("name") val name: String,
    @SerializedName("clientLocalId") val clientLocalId: Long? = null
)

data class CreateTransactionRequest(
    @SerializedName("personId") val personId: String,
    @SerializedName("amountPaise") val amountPaise: Long,
    @SerializedName("reason") val reason: String,
    @SerializedName("transactionDate") val transactionDate: Long? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("clientLocalId") val clientLocalId: Long? = null
)

data class SettleRequest(
    @SerializedName("personId") val personId: String,
    @SerializedName("note") val note: String? = null,
    @SerializedName("clientLocalId") val clientLocalId: Long? = null
)

data class RemoteTransactionDto(
    @SerializedName("_id") val id: String,
    @SerializedName("personId") val personId: String,
    @SerializedName("periodId") val periodId: String? = null,
    @SerializedName("amountPaise") val amountPaise: Long,
    @SerializedName("reason") val reason: String,
    @SerializedName("transactionDate") val transactionDate: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class PersonHistoryResponseData(
    @SerializedName("person") val person: PersonDto?,
    @SerializedName("transactions") val transactions: List<RemoteTransactionDto>?
)

data class TransactionResponseData(
    @SerializedName("_id") val id: String,
    @SerializedName("personId") val personId: String? = null,
    @SerializedName("amountPaise") val amountPaise: Long? = null,
    @SerializedName("reason") val reason: String? = null
)

// --- App Updates & Announcements DTO ---

data class AppUpdateDto(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("whatsNew") val whatsNew: List<String>? = null,
    @SerializedName("downloadUrl") val downloadUrl: String? = null,
    @SerializedName("isMandatory") val isMandatory: Boolean? = false
)


