package uz.uzum.tezkor.courier.network

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

fun createSafeRetrofitForGson(
    okHttpClient: OkHttpClient,
    baseUrl: String,
    gson: Gson,
): Retrofit {
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(
            SafeRequestConverterFactory(SafeResponseSerializerGsonAdapter(gson))
        )
        .addCallAdapterFactory(
            SafeRequestGsonAdapterFactory(SafeResponseDeserializerGsonAdapter(gson)),
        )
        .build()
}

private class SafeResponseDeserializerGsonAdapter(
    private val gson: Gson,
) : SafeResponseDeserializer {
    override fun <R> deserializeOrThrow(string: String, clazz: Class<R>): R {
        return gson.fromJson(string, clazz)
    }
}

private class SafeResponseSerializerGsonAdapter(
    private val gson: Gson,
) : SafeResponseSerializer {
    override fun serialize(model: Any): RequestBody {
        return gson.toJson(model).toRequestBody("application/json".toMediaType())
    }
}

private class SafeRequestGsonAdapterFactory(
    private val safeResponseDeserializer: SafeResponseDeserializer,
) : CallAdapter.Factory() {

    override fun get(returnType: Type, annotations: Array<out Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        val parameterizedType = returnType as? ParameterizedType

        return if (parameterizedType != null && parameterizedType.rawType == Call::class.java) {
            val responseType = getRawType(parameterizedType.actualTypeArguments.single())
            if (SafeResponse::class.java == responseType) {
                SafeRequestAdapter(safeResponseDeserializer)
            } else {
                null
            }
        } else {
            null
        }
    }
}

private data class SafeRequestCall<R>(
    val safeResponseDeserializer: SafeResponseDeserializer,
    val call: Call<R>,
) : Call<SafeResponse<*>> {
    override fun clone() = this.copy(safeResponseDeserializer = safeResponseDeserializer, call = call.clone())

    override fun execute(): Response<SafeResponse<*>> {
        throw IllegalStateException("This method should not be called")
    }

    override fun enqueue(callback: Callback<SafeResponse<*>>) {
        call.enqueue(SafeRequestCallback(this, safeResponseDeserializer, callback))
    }

    override fun isExecuted(): Boolean = call.isExecuted

    override fun cancel() = call.cancel()

    override fun isCanceled(): Boolean = call.isCanceled

    override fun request(): Request = call.request()

    override fun timeout(): Timeout = call.timeout()
}

private class SafeRequestCallback<R>(
    private val call: Call<SafeResponse<*>>,
    private val safeResponseDeserializer: SafeResponseDeserializer,
    private val original: Callback<SafeResponse<*>>,
) : Callback<R> {
    override fun onResponse(call: Call<R>, response: Response<R>) {
        val body: String? = response.body()
            ?.let { body ->
                body as? SafeResponseBody
                    ?: throw IllegalStateException("Body is expected to be converted to SafeResponseBody by SafeRequestConverter")
            }
            ?.value?.string()
            ?: response.errorBody()?.string()

        original.onResponse(
            this.call,
            try {
                Response.success(
                    SafeResponse.Response<Any>(
                        safeResponseDeserializer,
                        response.code(),
                        body,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                Response.success(SafeResponse.NetworkError<Any>(throwable))
            },
        )
    }

    override fun onFailure(call: Call<R>, throwable: Throwable) {
        original.onResponse(
            this.call,
            Response.success(SafeResponse.NetworkError<Any>(throwable)),
        )
    }
}

private class SafeRequestAdapter(
    private val safeResponseDeserializer: SafeResponseDeserializer,
) : CallAdapter<ResponseBody, Call<SafeResponse<*>>> {

    override fun responseType() = SafeResponse::class.java

    override fun adapt(call: Call<ResponseBody>): Call<SafeResponse<*>> {
        return SafeRequestCall(safeResponseDeserializer, call)
    }
}

private class SafeResponseBody(
    val value: ResponseBody,
)

private class SafeRequestResponseBodyConverter : Converter<ResponseBody, SafeResponseBody> {

    override fun convert(value: ResponseBody): SafeResponseBody {
        return SafeResponseBody(value)
    }
}

private class SafeRequestRequestBodyConverter(
    private val serializer: SafeResponseSerializer,
) : Converter<Any, RequestBody> {

    override fun convert(value: Any): RequestBody {
        return serializer.serialize(value)
    }
}

private class SafeRequestConverterFactory(
    private val serializer: SafeResponseSerializer,
) : Converter.Factory() {

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody> {
        return SafeRequestRequestBodyConverter(serializer)
    }

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        return SafeRequestResponseBodyConverter()
    }
}
