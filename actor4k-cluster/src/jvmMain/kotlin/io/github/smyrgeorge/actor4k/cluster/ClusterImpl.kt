package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.cluster.ClusterRpcService.ClusterMessage
import io.github.smyrgeorge.actor4k.util.Logger
import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.krpc.ktor.client.KtorRpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.serialization.protobuf.protobuf
import kotlinx.rpc.withService
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as clientCIO
import io.ktor.server.cio.CIO as serverCIO
import kotlinx.rpc.krpc.ktor.client.rpc as clientRpc
import kotlinx.rpc.krpc.ktor.server.rpc as serverRpc

class ClusterImpl(
    nodes: List<ClusterNode>,
    val current: ClusterNode,
    val loggerFactory: Logger.Factory,
    val routing: Routing.() -> Unit = {},
    val serialization: SerializersModuleBuilder.() -> Unit = {}
) : Cluster {

    private val log: Logger = loggerFactory.getLogger(this::class)

    init {
        if (current !in nodes) error("Current node must be part of the cluster nodes.")
        log.info("Nodes: $nodes")
    }

    /**
     * An array containing all nodes in the cluster.
     *
     * This array represents the collection of `ClusterNode` instances that are part of the cluster.
     * It is used to manage and interact with the individual nodes constituting the cluster.
     */
    val nodes: Array<ClusterNode> = nodes.toTypedArray()

    private val serializersModule: SerializersModule = SerializersModule {
        serialization()
        polymorphic(ClusterMessage.Response::class) {
            subclass(ClusterMessage.Response.Empty::class, ClusterMessage.Response.Empty.serializer())
            subclass(ClusterMessage.Response.Echo::class, ClusterMessage.Response.Echo.serializer())
            subclass(ClusterMessage.Response.Status::class, ClusterMessage.Response.Status.serializer())
            subclass(ClusterMessage.Response.Stats::class, ClusterMessage.Response.Stats.serializer())
            subclass(ClusterMessage.Response.Success::class, ClusterMessage.Response.Success.serializer())
            subclass(ClusterMessage.Response.Failure::class, ClusterMessage.Response.Failure.serializer())
        }
    }

    private val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> by lazy {
        createServer()
    }

    private val clients: Array<KtorRpcClient?> by lazy {
        nodes.map { node ->
            // Do not create a client for current node.
            if (node.alias == current.alias) null
            else createClient(node)
        }.toTypedArray()
    }

    /**
     * Represents an array of lazy-initialized `ClusterRpcService` instances, corresponding to client services
     * within the cluster.
     *
     * The array is constructed by mapping over the `clients` field of the containing class and invoking the
     * `withService` method to retrieve an instance of `ClusterRpcService` for each client. If a client is null,
     * the corresponding array entry is also null.
     *
     * This property is typically used to facilitate interaction with remote services in the cluster through
     * RPC mechanisms, supporting communication and operation management between different nodes.
     */
    val services: Array<ClusterRpcService?> by lazy {
        clients.map { it?.withService<ClusterRpcService>() }.toTypedArray()
    }

    /**
     * Provides a lazily initialized instance of `ClusterRpcService` for the current cluster node.
     *
     * The `self` variable enables communication with the cluster through Remote Procedure Calls (RPC),
     * offering functionality such as sending messages, querying cluster status, and shutting down nodes.
     * The initialization uses the cluster's current node information to create an RPC client, which is
     * configured to interact with services defined for the cluster.
     */
    val self: ClusterRpcService by lazy {
        createClient(current).withService<ClusterRpcService>()
    }

    /**
     * Starts the underlying HTTP server, transitioning it to an operational state.
     *
     * This method ensures that the server is properly initialized and begins listening
     * for incoming requests. The `wait` parameter determines whether the method should block
     * the current thread or return immediately after initiating the startup process.
     *
     * @param wait If `true`, the method blocks until the server is fully started.
     *             If `false`, it returns immediately after invoking the server's start mechanism.
     */
    override fun start(wait: Boolean) {
        server.start(wait)
    }

    /**
     * Shuts down the cluster, transitioning it to a non-operational state.
     *
     * This method ensures the proper termination of the cluster by stopping the
     * underlying HTTP server. The server's stop operation allows for graceful
     * shutdown, waiting for in-flight tasks to complete within the specified
     * timeouts before fully stopping.
     */
    override fun shutdown() {
        server.stop(1, 2, TimeUnit.SECONDS)
    }

    // https://kotlin.github.io/kotlinx-rpc/transport.html#ktor-transport
    private fun createClient(node: ClusterNode): KtorRpcClient = runBlocking {
        HttpClient(clientCIO) {
            installKrpc { waitForServices = true }
        }.clientRpc("ws://${node.address}/cluster") {
            rpcConfig {
                waitForServices = false
                serialization {
                    @OptIn(ExperimentalSerializationApi::class)
                    protobuf {
                        serializersModule = this@ClusterImpl.serializersModule
                    }
                }
            }
        }
    }

    private fun createServer(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
        embeddedServer(serverCIO, current.port) {
            install(Krpc)
            routing {
                routing(this)
                serverRpc("/cluster") {
                    rpcConfig {
                        waitForServices = false
                        serialization {
                            @OptIn(ExperimentalSerializationApi::class)
                            protobuf {
                                serializersModule = this@ClusterImpl.serializersModule
                            }
                        }
                    }

                    registerService<ClusterRpcService> { ctx -> ClusterRpcService.Impl(ctx) }
                }
            }
        }
}