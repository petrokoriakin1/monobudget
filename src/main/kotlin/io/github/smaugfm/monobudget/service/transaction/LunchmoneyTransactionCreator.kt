package io.github.smaugfm.monobudget.service.transaction

import io.github.smaugfm.lunchmoney.api.LunchmoneyApi
import io.github.smaugfm.lunchmoney.model.LunchmoneyTransaction
import io.github.smaugfm.lunchmoney.request.transaction.LunchmoneyCreateTransactionGroupRequest
import io.github.smaugfm.lunchmoney.request.transaction.LunchmoneyGetSingleTransactionRequest
import io.github.smaugfm.lunchmoney.request.transaction.LunchmoneyInsertTransactionsRequest
import io.github.smaugfm.lunchmoney.request.transaction.params.LunchmoneyCreateTransactionGroupParams
import io.github.smaugfm.lunchmoney.request.transaction.params.LunchmoneyInsertTransactionRequestParams
import io.github.smaugfm.monobank.model.MonoWebhookResponseData
import io.github.smaugfm.monobudget.service.mono.MonoTransferBetweenAccountsDetector.MaybeTransfer
import io.github.smaugfm.monobudget.service.statement.MonoStatementToLunchmoneyTransactionTransformer
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

class LunchmoneyTransactionCreator(
    private val api: LunchmoneyApi,
    private val statementTransformer: MonoStatementToLunchmoneyTransactionTransformer
) : TransactionCreator<LunchmoneyTransaction>() {

    override suspend fun create(maybeTransfer: MaybeTransfer<LunchmoneyTransaction>) = when (maybeTransfer) {
        is MaybeTransfer.Transfer -> processTransfer(maybeTransfer.webhookResponse, maybeTransfer.processed())
        is MaybeTransfer.NotTransfer -> maybeTransfer.consume(::processSingle)
    }

    private suspend fun processTransfer(
        newWebhookResponse: MonoWebhookResponseData,
        existingTransaction: LunchmoneyTransaction
    ): LunchmoneyTransaction {
        log.debug {
            "Processed transfer transaction: $newWebhookResponse. " +
                "Existing LunchmoneyTransaction: $existingTransaction"
        }

        val newTransaction = processSingle(newWebhookResponse)

        val groupId = api.execute(
            LunchmoneyCreateTransactionGroupRequest(
                LunchmoneyCreateTransactionGroupParams(
                    newTransaction.date,
                    TRANSFER_PAYEE,
                    listOf(existingTransaction, newTransaction).map { it.id }
                )
            )
        ).awaitSingle()

        log.debug { "Created new Lunchmoney transaction group id=$groupId" }

        return newTransaction
    }

    private suspend fun processSingle(webhookResponse: MonoWebhookResponseData): LunchmoneyTransaction {
        log.debug { "Processing transaction: $webhookResponse" }

        val newTransaction = statementTransformer.transform(webhookResponse)
        return with(newTransaction) {

            val createdId = api.execute(
                LunchmoneyInsertTransactionsRequest(
                    LunchmoneyInsertTransactionRequestParams(
                        listOf(this),
                        applyRules = true,
                        checkForRecurring = true,
                        debitAsNegative = true
                    )
                )
            ).awaitSingle().ids.first()
            api.execute(LunchmoneyGetSingleTransactionRequest(createdId)).awaitSingle()
        }
    }

    companion object {
        private const val TRANSFER_PAYEE = "Transfer"
    }
}