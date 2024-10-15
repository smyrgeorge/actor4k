package io.github.smyrgeorge.actor4k.microbank.client

import arrow.fx.coroutines.parMap
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.smyrgeorge.actor4k.microbank.client.serde.Jackson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.time.measureTime

class MicroBankClient

data class ApplyTx(val accountNo: String, val value: Int)
data class Account(val accountNo: String, var balance: Int)

fun main() {
    val log: Logger = LoggerFactory.getLogger("microbank-client")

    val om = Jackson.create()
    val client = ApacheClient()

    val txValue = 10
    val noOfAccounts = 100
    val transactionsPerWorker = 1_000
    val concurrency = 16

    fun getAccounts() = runBlocking {
        (1..noOfAccounts)
            .parMap { id ->
                withContext(Dispatchers.IO) {
                    val req = Request(Method.GET, "http://localhost:9000/api/account/ACC-$id")
                    val res = client(req)
                    id to om.readValue<Account>(res.body.stream)
                }
            }
    }.toMap()

    log.info("Creating accounts...")
    getAccounts()
    log.info("Created $noOfAccounts accounts.")

    log.info("Sending ${concurrency * transactionsPerWorker} transactions using $concurrency workers...")
    val time = measureTime {
        runBlocking {
            (1..concurrency)
                .parMap {
                    (1..transactionsPerWorker).forEach { _ ->
                        val sign = if (it % 2 == 0) 1 else -1
                        val accountId = Random.nextInt(1, noOfAccounts)
                        val accountNo = "ACC-$accountId"
                        val body = om.writeValueAsString(ApplyTx(accountNo, sign * txValue))
                        val req = Request(Method.POST, "http://localhost:9000/api/account/ACC-$accountId").body(body)
                        val res = client(req)
                        om.readValue<Account>(res.body.stream)
                    }
                }
        }
    }
    log.info("Finished sending transactions in $time. (~${concurrency * transactionsPerWorker / time.inWholeSeconds}tx/sec)")

    val accounts: Map<Int, Account> = getAccounts()
    val total: Int = accounts.entries.fold(0) { acc, e ->
        val new = acc + e.value.balance
        new
    }

    println("Balance in all accounts = $total. Expected = 0")
}
