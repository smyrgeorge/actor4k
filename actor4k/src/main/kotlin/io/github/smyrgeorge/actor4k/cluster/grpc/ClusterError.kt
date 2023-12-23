package io.github.smyrgeorge.actor4k.cluster.grpc

class ClusterError(val code: Envelope.Response.Error.Code, override val message: String) : RuntimeException(message)