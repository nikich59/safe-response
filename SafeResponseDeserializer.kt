package uz.uzum.tezkor.courier.network

interface SafeResponseDeserializer {
    fun <R> deserializeOrThrow(string: String, clazz: Class<R>): R
}
