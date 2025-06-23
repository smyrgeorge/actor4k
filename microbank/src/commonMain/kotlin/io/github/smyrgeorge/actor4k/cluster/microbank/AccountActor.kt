package io.github.smyrgeorge.actor4k.cluster.microbank

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.cluster.microbank.AccountActor.Protocol
import kotlinx.serialization.Serializable

class AccountActor(key: String) : Actor<Protocol, Protocol.Response>(key) {

    private val account = Account(key, Int.MIN_VALUE)

    override suspend fun onActivate(m: Protocol) {
        // Initialize the account balance here.
        // E.g., fetch the data from the DB.
        // In this case we will assume that the balance is equal to '0'.
        account.balance = 0
        log.info("Activated: $account")
    }

    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Response> {
        val res = when (m) {
            is Protocol.GetAccount -> account
            is Protocol.ApplyTx -> {
                account.balance += m.value
                account
            }
        }

        return Behavior.Respond(Protocol.Account(res))
    }

    @Serializable
    data class Account(val accountNo: String, var balance: Int)

    sealed interface Protocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : Protocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        @Serializable
        data class GetAccount(val accountNo: String) : Message<Account>()

        @Serializable
        data class ApplyTx(val accountNo: String, val value: Int) : Message<Account>()

        @Serializable
        data class Account(val account: AccountActor.Account) : Response()
    }
}