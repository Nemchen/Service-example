package com.desperate.gromov_clo_bot.bot

import com.desperate.gromov_clo_bot.bot.handler.Handler
import com.desperate.gromov_clo_bot.model.User
import com.desperate.gromov_clo_bot.repository.UserRepository
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.Serializable
import java.util.*
import java.util.function.Predicate
import java.util.function.Supplier


@Component
class UpdateReceiver(val handlers: List<Handler>,val userRepository: UserRepository) {

    // Analyzing received update
    fun handle(update: Update): List<PartialBotApiMethod<out Serializable>> {
        // try-catch in order to return empty list on unsupported command
        return try {
            // Checking if Update is a message with text
            if (isMessageWithText(update)) {
                // Getting Message from Update
                val message: Message = update.message
                // Getting chatId
                val chatId: Int = message.from.id

                // Getting user from repository. If user is not presented in repository - create new and return him
                // For this reason we have a one arg constructor in User.class
                val user = userRepository.getByChatId(chatId)
                        .orElseGet { userRepository.save(User(chatId)) }
                // Looking for suitable handler
                return getHandlerByState(user.botState).handle(user, message.text)
                // Same workflow but for CallBackQuery
            } else if (update.hasCallbackQuery()) {
                val callbackQuery = update.callbackQuery
                val chatId = callbackQuery.from.id
                val user = userRepository.getByChatId(chatId)
                        .orElseGet { userRepository.save(User(chatId)) }
                return getHandlerByCallBackQuery(callbackQuery.data).handle(user, callbackQuery.data)
            }
            throw UnsupportedOperationException()
        } catch (e: UnsupportedOperationException) {
            Collections.emptyList()
        }
    }

    private fun getHandlerByState(state: State): Handler {
        return handlers.stream()
                .filter { h: Handler -> h.operatedBotState() != null }
                .filter { h: Handler -> h.operatedBotState() == state }
                .findAny()
                .orElseThrow { UnsupportedOperationException() }
    }

    private fun getHandlerByCallBackQuery(query: String): Handler {
        return handlers.stream()
                .filter { h: Handler ->
                    h.operatedCallBackQuery().stream()
                            .anyMatch { prefix: String -> query.startsWith(prefix) }
                }
                .findAny()
                .orElseThrow { UnsupportedOperationException() }
    }

    private fun isMessageWithText(update: Update): Boolean {
        return !update.hasCallbackQuery() && update.hasMessage() && update.message.hasText()
    }
}
