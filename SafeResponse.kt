package uz.uzum.tezkor.courier.network

sealed class SafeResponse<SuccessBody : Any> {

    data class Response<Body : Any> internal constructor(
        val deserializer: SafeResponseDeserializer,
        val code: Int,
        val responseBody: String?,
    ) : SafeResponse<Body>()

    data class NetworkError<PrimaryBody : Any> internal constructor(
        val cause: Throwable,
    ) : SafeResponse<PrimaryBody>()
}
