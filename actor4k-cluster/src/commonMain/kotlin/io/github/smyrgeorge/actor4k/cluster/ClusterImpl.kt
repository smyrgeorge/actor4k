package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.cluster.rpc.ClusterMessage
import io.github.smyrgeorge.actor4k.cluster.rpc.RpcReceiveService
import io.github.smyrgeorge.actor4k.cluster.rpc.RpcSendService
import io.github.smyrgeorge.actor4k.cluster.rpc.RpcWebSocketSession
import io.github.smyrgeorge.actor4k.cluster.util.ClusterNode
import io.github.smyrgeorge.actor4k.cluster.util.http.HttpClientUtils
import io.github.smyrgeorge.actor4k.cluster.util.http.HttpServerUtils
import io.github.smyrgeorge.actor4k.util.Logger
import io.ktor.client.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.math.absoluteValue

/**
 * Implementation of the `Cluster` interface for managing and orchestrating distributed systems.
 *
 * This class is responsible for setting up the communication infrastructure, handling RPC,
 * and managing lifecycle operations (startup, shutdown) across a cluster of nodes.
 * It provides utilities for interacting with other nodes in the cluster, sending requests,
 * and receiving responses in a structured manner.
 *
 * @constructor Creates a new instance of `ClusterImpl` with the specified parameters.
 *
 * @param nodes The list of `ClusterNode` instances representing all the nodes in the cluster.
 * @param current The current node in the cluster, representing the local instance of this implementation.
 * @param proxy Indicates whether the current node operates as a proxy; if true, it excludes the current node from the list of managed nodes.
 * @param loggerFactory A factory for creating loggers used throughout the cluster implementation.
 * @param routing A configuration block for setting up routing within the HTTP server.
 * @param serialization A configuration block for customizing the serialization module.
 */
class ClusterImpl(
    nodes: List<ClusterNode>,
    val current: ClusterNode,
    val proxy: Boolean = false,
    val loggerFactory: Logger.Factory,
    val registry: ClusterActorRegistry,
    val routing: Routing.() -> Unit = {},
    val serialization: SerializersModuleBuilder.() -> Unit = {}
) : Cluster {

    private val log: Logger = loggerFactory.getLogger(this::class)

    private lateinit var client: HttpClient
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private val serializersModule: SerializersModule = SerializersModule {
        serialization()
        polymorphic(ClusterMessage.Request::class) {
            subclass(ClusterMessage.Request.Echo::class, ClusterMessage.Request.Echo.serializer())
            subclass(ClusterMessage.Request.Tell::class, ClusterMessage.Request.Tell.serializer())
            subclass(ClusterMessage.Request.Ask::class, ClusterMessage.Request.Ask.serializer())
            subclass(ClusterMessage.Request.Status::class, ClusterMessage.Request.Status.serializer())
            subclass(ClusterMessage.Request.Stats::class, ClusterMessage.Request.Stats.serializer())
            subclass(ClusterMessage.Request.Shutdown::class, ClusterMessage.Request.Shutdown.serializer())
        }
        polymorphic(ClusterMessage.Response::class) {
            subclass(ClusterMessage.Response.Empty::class, ClusterMessage.Response.Empty.serializer())
            subclass(ClusterMessage.Response.Echo::class, ClusterMessage.Response.Echo.serializer())
            subclass(ClusterMessage.Response.Status::class, ClusterMessage.Response.Status.serializer())
            subclass(ClusterMessage.Response.Stats::class, ClusterMessage.Response.Stats.serializer())
            subclass(ClusterMessage.Response.Success::class, ClusterMessage.Response.Success.serializer())
            subclass(ClusterMessage.Response.Failure::class, ClusterMessage.Response.Failure.serializer())
        }
    }

    @ExperimentalSerializationApi
    private val protoBuf: ProtoBuf = ProtoBuf {
        serializersModule = this@ClusterImpl.serializersModule
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val receive = RpcReceiveService(loggerFactory, protoBuf, registry)

    /**
     * An array containing all nodes in the cluster.
     *
     * This array represents the collection of `ClusterNode` instances that are part of the cluster.
     * It is used to manage and interact with the individual nodes constituting the cluster.
     */
    private val nodes: Array<ClusterNode>

    /**
     * Represents an array of RPC send services used within the cluster for
     * communication. Each element corresponds to a specific instance of the
     * `RpcSendService`, facilitating interactions such as sending messages,
     * querying status, or managing the lifecycle of the cluster nodes.
     */
    private lateinit var services: Array<RpcSendService?>

    /**
     * Handles communication with other nodes in the cluster using remote procedure calls (RPC) over WebSocket.
     *
     * This property lazily initializes an instance of [RpcSendService] responsible for sending
     * requests and receiving responses between cluster nodes. It is constructed using a WebSocket session
     * and serialization utilities.
     *
     * The initialization uses the provided logger factory, client instance, and current cluster node address
     * to establish the WebSocket session. This session allows asynchronous communication and ensures reliability
     * using retries for both connection establishment and message delivery.
     *
     * The [RpcSendService] implementation wraps the session and provides an abstraction over various RPC
     * operations including messaging, node status inquiries, and cluster-level operations (e.g., shutdown).
     */
    val self: RpcSendService by lazy {
        val session = RpcWebSocketSession(loggerFactory, client, current)
        @OptIn(ExperimentalSerializationApi::class)
        RpcSendService(loggerFactory, protoBuf, session)
    }

    init {
        registry.register(this) // Register the cluster to the actor-registry.
        if (nodes.isEmpty()) error("The cluster must have at least one node.")
        if (current !in nodes) error("The current node '${current.alias}' should also be in the list of nodes.")
        val nodes = if (proxy) nodes.filter { it.alias != current.alias } else nodes
        // IMPORTANT: All nodes must have the same exact (sorted) list of nodes.
        // This is very crucial for correct routing of the cluster messages.
        this.nodes = nodes.sortedBy { it.hashCode() }.toTypedArray()
        if (this.nodes.size == 1) log.warn("The cluster has only one node. This is not recommended for production.")
        log.info("Proxy only: $proxy")
        log.info("Current node: $current")
        log.info("Number of nodes: ${this.nodes.size}")
        log.info("Cluster nodes: ${this.nodes.joinToString(", ") { it.toString() }}")
    }

    /**
     * Starts the cluster by initializing the HTTP client and HTTP server components.
     *
     * This method sets up the necessary infrastructure to support the cluster's operations,
     * including creating an HTTP client for interactions and a server to handle incoming requests.
     *
     * @param wait Specifies whether the method should block until the server stops.
     *             If true, the method will wait for the server to finish running.
     *             If false, it will return immediately after starting the server.
     */
    override fun start(wait: Boolean) {
        log.info("Starting cluster with ${nodes.size} nodes...")
        client = HttpClientUtils.create()
        server = HttpServerUtils.create(current.port, routing, receive)

        services = nodes.map { node ->
            // Do not create a client for current node.
            if (node.alias == current.alias) null
            else {
                val session = RpcWebSocketSession(loggerFactory, client, node)
                @OptIn(ExperimentalSerializationApi::class)
                RpcSendService(loggerFactory, protoBuf, session)
            }
        }.toTypedArray()

        server.start(wait)
    }

    /**
     * Retrieves the RPC send service associated with a given address.
     *
     * The method determines the appropriate service by computing the hash of the address key
     * and using it to shard across the available nodes in the cluster.
     *
     * @param address The address used to locate the associated RPC send service.
     *                It contains the unique key and name identifying the actor within the system.
     * @return The corresponding [RpcSendService] if a matching service is found, or null if no service exists for the given address.
     */
    fun getServiceFor(address: Address): RpcSendService? {
        // We use the key's hash-code to achieve efficient sharding between different actor types they share the same key.
        val nodeIdx = address.key.hashCode().absoluteValue % nodes.size
        return services[nodeIdx]
    }

    /**
     * Shuts down the cluster and its associated components.
     *
     * This method closes all active services, stops the HTTP server, and closes the underlying client,
     * ensuring a clean and orderly shutdown of the cluster. The shutdown process is encapsulated within a
     * coroutine to handle suspending operations and is wrapped in a try-catch block to log any errors that occur.
     *
     * Key operations performed during shutdown:
     * - Gracefully closes the `self` session, representing the local node.
     * - Iterates through and closes all registered services.
     * - Stops the HTTP server, providing a specific timeout for graceful termination.
     * - Closes the associated client to free up resources.
     *
     * Logs any exceptions encountered during the shutdown process as warnings, providing details for troubleshooting.
     */
    override fun shutdown() {
        try {
            runBlocking {
                services.forEach { it?.close() }
            }
            server.stop(1_000, 2_000)
            client.close()
        } catch (e: Exception) {
            log.warn("Error while shutting down the cluster. (${e.message})", e)
        }
    }
}