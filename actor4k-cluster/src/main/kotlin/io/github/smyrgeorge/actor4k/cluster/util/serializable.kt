package io.github.smyrgeorge.actor4k.cluster.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInput
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

@Suppress("UNCHECKED_CAST")
fun <T : Serializable> ByteArray.toInstance(): T {
    val byteArrayInputStream = ByteArrayInputStream(this)
    val objectInput: ObjectInput = ObjectInputStream(byteArrayInputStream)
    val result = objectInput.readObject() as T
    objectInput.close()
    byteArrayInputStream.close()
    return result
}

fun Serializable.toByteArray(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
    objectOutputStream.writeObject(this)
    objectOutputStream.flush()
    val result = byteArrayOutputStream.toByteArray()
    byteArrayOutputStream.close()
    objectOutputStream.close()
    return result
}
