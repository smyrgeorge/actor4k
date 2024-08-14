package io.github.smyrgeorge.actor4k.cluster

interface ICluster {
    val serde: Serde

    fun start(): ICluster
    fun registerShard(shard: String)
    fun unregisterShard(shard: String)
    fun shardIsLocked(shard: String): Error?
    fun shutdown()

    data class Error(
        val code: Code,
        val message: String
    ) {
        enum class Code {
            SHARD_ACCESS_ERROR,
            UNKNOWN
        }

        fun ex(): Nothing = throw ClusterError(code, message)
        data class ClusterError(val code: Code, override val message: String) : RuntimeException(message)
    }
}
