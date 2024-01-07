package io.github.smyrgeorge.actor4k.microbank.bench.scenario

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import org.intellij.lang.annotations.Language
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Suppress("unused")
class SampleSimulation : Simulation() {

    private val numOfAccounts = 1000
    private val accounts = (0..numOfAccounts).map { "ACC-$it" }
    private fun randomAccount() = accounts[Random.nextInt(1, numOfAccounts)]

    @Language("json")
    fun bodyOf(accountNo: String) = """{"accountNo": "$accountNo", "value": 10}"""

    private val httpProtocol = http.baseUrl("http://localhost:9000/api/account")

    private val req = http("tx")
        .post {
            val accountNo = it.get<String>("accountNo")!!
            "/$accountNo"
        }.body(StringBody {
            val accountNo = it.get<String>("accountNo")!!
            bodyOf(accountNo)
        })

    private val scenario = scenario(SampleSimulation::class.java.simpleName)
        .exec { s -> s.set("accountNo", randomAccount()) }
        .exec(req)

    init {
        setUp(
            scenario.injectClosed(
                rampConcurrentUsers(10).to(100).during(30.seconds.toJavaDuration())
            )
        ).protocols(httpProtocol)
    }
}