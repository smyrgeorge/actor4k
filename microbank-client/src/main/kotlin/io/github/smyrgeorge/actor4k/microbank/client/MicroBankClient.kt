package io.github.smyrgeorge.actor4k.microbank.client

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.microbank.client.serde.Jackson
import io.github.smyrgeorge.actor4k.util.chunked
import io.github.smyrgeorge.actor4k.util.forEachParallel
import io.github.smyrgeorge.actor4k.util.mapParallel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import kotlin.random.Random

class MicroBankClient

data class ApplyTx(val accountNo: String, val value: Int)
data class Account(val accountNo: String, var balance: Int)

fun main(args: Array<String>) {
    val log = KotlinLogging.logger {}

    val om = Jackson.create()
    val client = ApacheClient()

    val txValue = 10
    val noOfAccounts = 100
    val transactionsPerWorker = 80_000
    val concurrency = 8

    fun getAccounts() = runBlocking {
        (1..noOfAccounts)
            .chunked(noOfAccounts, concurrency)
            .mapParallel { l ->
                withContext(Dispatchers.IO) {
                    l.map { id ->
                        val req = Request(Method.GET, "http://localhost:9000/api/account/ACC-$id")
                        val res = client(req)
                        id to om.readValue<Account>(res.body.stream)
                    }
                }
            }.flatten()
    }.toMap()

    log.info { "Creating accounts..." }
    getAccounts()
    log.info { "Created $noOfAccounts accounts." }

    log.info { "Sending ${concurrency * transactionsPerWorker} transactions using $concurrency workers..." }
    runBlocking {
        (1..concurrency)
            .forEachParallel {
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
    log.info { "Finished sending transactions." }

    val accounts: Map<Int, Account> = getAccounts()
    val total: Int = accounts.entries.fold(0) { acc, e ->
        val new = acc + e.value.balance
        new
    }

    println("Balance in all accounts = $total. Expected = 0")
}
