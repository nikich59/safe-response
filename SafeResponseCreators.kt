package uz.uzum.tezkor.courier.network.test

import org.jetbrains.annotations.TestOnly
import uz.uzum.tezkor.courier.network.SafeResponse
import uz.uzum.tezkor.courier.network.SafeResponseDeserializer

@TestOnly
fun <T : Any> createSafeResponseResponse200(
    response: T,
): SafeResponse.Response<T> {
    return SafeResponse.Response(
        object : SafeResponseDeserializer {
            override fun <R> deserializeOrThrow(string: String, clazz: Class<R>): R {
                return if (response.javaClass == clazz) {
                    @Suppress("UNCHECKED_CAST")
                    response as R
                } else {
                    throw RuntimeException("Unexpected type: <${clazz}>")
                }
            }
        },
        200,
        "",
    )
}

@TestOnly
fun <T : Any> createSafeResponseResponse204(): SafeResponse.Response<T> {
    return SafeResponse.Response(
        object : SafeResponseDeserializer {
            override fun <R> deserializeOrThrow(string: String, clazz: Class<R>): R {
                throw RuntimeException("Must not be called for 204 HTTP status")
            }
        },
        204,
        null,
    )
}

@TestOnly
fun <T : Any> createSafeResponseNetworkError(throwable: Throwable): SafeResponse.NetworkError<T> {
    return SafeResponse.NetworkError(throwable)
}
