package uz.uzum.tezkor.courier.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

@Suppress("LocalVariableName")
suspend inline fun <reified SuccessResponse : Any, T> SafeResponse<SuccessResponse>.fold(
    noinline `200`: suspend (SuccessResponse) -> T,
    vararg mappers: SafeResponseMapper<T>,
    noinline unknownError: suspend (Throwable) -> T,
    noinline networkError: suspend (Throwable) -> T = unknownError,
    noinline unmentionedStatus: suspend (Int) -> T = { code ->
        unknownError.invoke(UnmentionedStatusException(code))
    },
): T {
    return withContext(Dispatchers.Default) {
        when (this@fold) {
            is SafeResponse.Response -> {
                val mapper: SafeResponseMapper<T>? = if (this@fold.code == 200) {
                    if (SuccessResponse::class == Unit::class) {
                        200 noBody {
                            `200`.invoke(Unit as SuccessResponse)
                        }
                    } else {
                        200 withBody { response: SuccessResponse ->
                            `200`.invoke(response)
                        }
                    }
                } else {
                    mappers.find { it.code == this@fold.code }
                }

                when (mapper) {
                    is SafeResponseMapper.BodyMapper<*, T> -> {
                        try {
                            val responseBody = this@fold.responseBody
                                ?: throw IllegalArgumentException("No body in response for ${mapper.code} status code")

                            val body = this@fold.deserializer.deserializeOrThrow(
                                responseBody,
                                mapper.inputClass.java
                            )

                            mapper.invokeOnAnyUnchecked(body)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (throwable: Throwable) {
                            unknownError.invoke(throwable)
                        }
                    }

                    is SafeResponseMapper.EmptyResponseMapper<T> -> {
                        mapper.func.invoke()
                    }

                    null -> {
                        unmentionedStatus.invoke(this@fold.code)
                    }
                }
            }

            is SafeResponse.NetworkError -> {
                networkError.invoke(this@fold.cause)
            }
        }
    }
}

sealed interface SafeResponseMapper<Output> {

    val code: Int

    data class BodyMapper<Input : Any, Output>(
        override val code: Int,
        val inputClass: KClass<Input>,
        val func: suspend (Input) -> Output,
    ) : SafeResponseMapper<Output> {
        @Suppress("UNCHECKED_CAST")
        suspend fun invokeOnAnyUnchecked(any: Any): Output = func(any as Input)
    }

    data class EmptyResponseMapper<Output>(
        override val code: Int,
        val func: suspend () -> Output,
    ) : SafeResponseMapper<Output>
}

inline infix fun <reified Input : Any, Output> Int.withBody(
    noinline func: suspend (Input) -> Output,
): SafeResponseMapper<Output> {
    return SafeResponseMapper.BodyMapper(this, Input::class, func)
}

infix fun <Output> Int.noBody(
    func: suspend () -> Output,
): SafeResponseMapper<Output> {
    return SafeResponseMapper.EmptyResponseMapper(this) {
        func.invoke()
    }
}

data class UnmentionedStatusException(val code: Int) : RuntimeException("Status $code")
