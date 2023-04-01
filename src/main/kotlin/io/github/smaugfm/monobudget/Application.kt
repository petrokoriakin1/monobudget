package io.github.smaugfm.monobudget

import io.github.smaugfm.monobudget.common.mono.MonoTransferBetweenAccountsDetector
import io.github.smaugfm.monobudget.common.mono.MonoWebhookListenerServer
import io.github.smaugfm.monobudget.common.telegram.TelegramApi
import io.github.smaugfm.monobudget.common.telegram.TelegramCallbackHandler
import io.github.smaugfm.monobudget.common.telegram.TelegramErrorUnknownErrorHandler
import io.github.smaugfm.monobudget.common.telegram.TelegramMessageSender
import io.github.smaugfm.monobudget.common.telegram.TelegramWebhookResponseChecker
import io.github.smaugfm.monobudget.common.transaction.TransactionFactory
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter
import io.github.smaugfm.monobudget.common.verify.ApplicationStartupVerifier
import io.ktor.util.logging.error
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.URI
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

class Application<TTransaction, TNewTransaction> :
    KoinComponent {
    private val telegramApi by inject<TelegramApi>()
    private val webhooksListener by inject<MonoWebhookListenerServer>()

    private val transactionCreator by inject<TransactionFactory<TTransaction, TNewTransaction>>()
    private val messageFormatter by inject<TransactionMessageFormatter<TTransaction>>()
    private val monoTransferDetector by inject<MonoTransferBetweenAccountsDetector<TTransaction>>()

    private val telegramCallbackHandler by inject<TelegramCallbackHandler<TTransaction>>()
    private val processError by inject<TelegramErrorUnknownErrorHandler>()
    private val telegramMessageSender by inject<TelegramMessageSender>()
    private val webhookResponseChecker by inject<TelegramWebhookResponseChecker>()

    suspend fun run(setWebhook: Boolean, monoWebhookUrl: URI, webhookPort: Int) {
        runStartupChecks()

        if (setWebhook) {
            log.info { "Setting up mono webhooks." }
            if (!webhooksListener.setupWebhook(monoWebhookUrl, webhookPort)) {
                log.error { "Error settings up webhooks. Exiting application..." }
                exitProcess(1)
            }
        } else {
            log.info { "Skipping mono webhook setup." }
        }

        val webhookJob = webhooksListener.start(
            monoWebhookUrl,
            webhookPort
        ) handler@{ responseData ->
            try {
                if (!webhookResponseChecker.check(responseData)) {
                    return@handler
                }

                val maybeTransfer =
                    monoTransferDetector.checkTransfer(responseData)

                val transaction = transactionCreator.create(maybeTransfer)
                val message = messageFormatter.format(
                    responseData,
                    transaction
                )

                telegramMessageSender.send(responseData.account, message)
            } catch (e: Throwable) {
                log.error(e)
                processError()
            }
        }
        val telegramJob = telegramApi.start(telegramCallbackHandler::handle)
        log.info { "Listening for Monobank webhooks and Telegram callbacks..." }
        webhookJob.join()
        telegramJob.join()
    }

    private suspend fun runStartupChecks() {
        try {
            getKoin().getAll<ApplicationStartupVerifier>().forEach {
                it.verify()
            }
        } catch (e: Throwable) {
            log.error(e) { "Failed to start application. Exiting..." }
            exitProcess(1)
        }
    }
}
