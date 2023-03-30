package io.github.smaugfm.monobudget.ynab

import io.github.smaugfm.monobank.model.MonoWebhookResponseData
import io.github.smaugfm.monobudget.common.misc.SimpleCache
import io.github.smaugfm.monobudget.common.model.ynab.YnabCleared
import io.github.smaugfm.monobudget.common.model.ynab.YnabSaveTransaction
import io.github.smaugfm.monobudget.common.model.ynab.YnabTransactionDetail
import io.github.smaugfm.monobudget.common.mono.MonoAccountsService
import io.github.smaugfm.monobudget.common.mono.MonoTransferBetweenAccountsDetector.MaybeTransfer
import io.github.smaugfm.monobudget.common.transaction.TransactionFactory
import mu.KotlinLogging
import org.koin.core.annotation.Single
import org.koin.core.component.inject

private val log = KotlinLogging.logger {}

@Single
class YnabTransactionFactory : TransactionFactory<YnabTransactionDetail, YnabSaveTransaction>() {
    private val api: YnabApi by inject()
    private val monoAccountsService: MonoAccountsService by inject()

    private val transferPayeeIdsCache = SimpleCache<String, String> {
        api.getAccount(it).transferPayeeId
    }

    override suspend fun create(maybeTransfer: MaybeTransfer<YnabTransactionDetail>) = when (maybeTransfer) {
        is MaybeTransfer.Transfer -> processTransfer(maybeTransfer.webhookResponse, maybeTransfer.processed())
        is MaybeTransfer.NotTransfer -> maybeTransfer.consume(::processSingle)
    }

    private suspend fun processTransfer(
        newWebhookResponse: MonoWebhookResponseData,
        existingTransaction: YnabTransactionDetail
    ): YnabTransactionDetail {
        log.debug {
            "Processing transfer transaction: $newWebhookResponse. " +
                "Existing YnabTransactionDetail: $existingTransaction"
        }

        val transferPayeeId =
            transferPayeeIdsCache.get(monoAccountsService.getBudgetAccountId(newWebhookResponse.account)!!)

        val existingTransactionUpdated = api
            .updateTransaction(
                existingTransaction.id,
                existingTransaction
                    .toSaveTransaction()
                    .copy(payeeId = transferPayeeId, memo = "Переказ між рахунками")
            )

        val transfer = api.getTransaction(existingTransactionUpdated.transferTransactionId!!)

        return api.updateTransaction(
            transfer.id,
            transfer.toSaveTransaction().copy(cleared = YnabCleared.Cleared)
        )
    }

    private suspend fun processSingle(webhookResponse: MonoWebhookResponseData): YnabTransactionDetail {
        log.debug { "Processing transaction: $webhookResponse" }

        val transaction = newTransactionFactory.create(webhookResponse)

        return api.createTransaction(transaction)
    }
}
