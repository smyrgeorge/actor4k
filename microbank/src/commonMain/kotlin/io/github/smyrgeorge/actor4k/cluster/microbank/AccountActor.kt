package io.github.smyrgeorge.actor4k.cluster.microbank

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.microbank.AccountActor.Protocol
import kotlinx.serialization.Serializable

class AccountActor(
    override val key: String
) : Actor<Protocol, Protocol.Response>(key) {

    private val account = Account(key, Int.MIN_VALUE)

    override suspend fun onActivate(m: Protocol) {
        // Initialize the account balance here.
        // E.g. fetch the data from the DB.
        // In this case we will assume that the balance is equal to '0'.
        account.balance = 0
        log.info("Activated: $account")
    }

    override suspend fun onReceive(m: Protocol): Protocol.Response {
        val res = when (m) {
            is Protocol.GetAccount -> account
            is Protocol.ApplyTx -> {
                account.balance += m.value
                account
            }
        }

        return Protocol.Account(res)
    }

    @Serializable
    data class Account(val accountNo: String, var balance: Int)

    sealed class Protocol : Message() {
        sealed class Response : Message.Response()

        @Serializable
        data class GetAccount(val accountNo: String) : Protocol()

        @Serializable
        data class ApplyTx(val accountNo: String, val value: Int) : Protocol()

        @Serializable
        data class Account(val account: AccountActor.Account) : Response()
    }
}