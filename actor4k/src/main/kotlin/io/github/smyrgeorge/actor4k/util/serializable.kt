package io.github.smyrgeorge.actor4k.util

import java.io.*

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