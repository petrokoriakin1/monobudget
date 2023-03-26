package io.github.smaugfm.monobudget.model.ynab

import kotlinx.serialization.Serializable

@Serializable
data class YnabPayee(
    val id: String,
    val name: String,
    val transferAccountId: String?,
    val deleted: Boolean
)