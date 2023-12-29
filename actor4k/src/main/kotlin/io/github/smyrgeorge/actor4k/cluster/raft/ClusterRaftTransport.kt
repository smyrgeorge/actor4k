package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcClient
import io.github.smyrgeorge.actor4k.cluster.grpc.toProto
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.RaftEndpoint
import io.microraft.model.message.RaftMessage
import io.microraft.transport.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.util.Timeout
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap


class ClusterRaftTransport(
    private val self: ClusterRaftEndpoint,
    private val grpcClients: ConcurrentHashMap<String, GrpcClient>
) : Transport {

    private val log = KotlinLogging.logger {}

    private val http = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig
                .custom()
                .setRedirectsEnabled(false)
                .setConnectTimeout(Timeout.of(Duration.ofSeconds(2)))
                .setConnectionRequestTimeout(Timeout.of(Duration.ofSeconds(2)))
                .setResponseTimeout(Timeout.of(Duration.ofSeconds(5)))
                .build()
        ).build()
    private val client = ApacheClient(http)

    override fun send(target: RaftEndpoint, message: RaftMessage) {
        target as ClusterRaftEndpoint

        if (self.alias == target.alias) {
            ActorSystem.cluster.raft.handle(message)
            return
        }

        runBlocking {
            launch(Dispatchers.IO) {
                val msg = ClusterRaftMessage.RaftProtocol(message)
                try {
                    log.debug { "Sending $message to $target" }
                    val body = msg.toProto().toByteArray().inputStream()
                    val req = Request(Method.POST, "http://${target.host}:9000/api/raft/protocol").body(body)
                    val res = client(req)
                    log.debug { "Res.status = ${res.status}, for $message to $target" }
                    // Find the gRPC client.
//                    val client = grpcClients.getOrPut(target.alias) {
//                        GrpcClient(target.host, target.port)
//                    }.request(msg)
                } catch (e: Exception) {
                    log.warn { "Could not send ${message::class.simpleName} to ${target.alias}. Reason: ${e.message}" }
                }
            }
        }
    }

    override fun isReachable(endpoint: RaftEndpoint): Boolean {
        endpoint as ClusterRaftEndpoint
        return try {
            log.debug { "Sending ping request to $endpoint" }
            val req = Request(Method.GET, "http://${endpoint.host}:9000/api/raft/ping")
            client(req).status == Status.OK
        } catch (e: Exception) {
            log.warn { "Could not ping ${endpoint.alias}. Reason: ${e.message}" }
            false
        }
    }
}