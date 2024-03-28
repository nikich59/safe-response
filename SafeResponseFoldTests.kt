package uz.uzum.tezkor.courier.network

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import java.util.stream.Stream

class SafeResponseFoldTests {

    companion object {
        @JvmStatic
        fun provide200and204and422StatusesTestValues(): Stream<Arguments> = Stream.of(
            Arguments.of(
                TestConfig(MyResponseBody200(), 200),
            ),
            Arguments.of(
                TestConfig(MyResponseBody422(), 422),
            ),
            Arguments.of(
                TestConfig(null, 204),
            ),
        )
    }

    @Test
    fun `200 status code with body`() = runTest {
        val body = MyResponseBody200()
        val deserializer = object : SafeResponseDeserializer {
            override fun <R> deserializeOrThrow(string: String, clazz: Class<R>): R {
                @Suppress("UNCHECKED_CAST")
                return when (clazz) {
                    MyResponseBody200::class.java -> body as R
                    else -> throw IllegalArgumentException("Class: $clazz")
                }
            }
        }
        val response: SafeResponse<MyResponseBody200> = SafeResponse.Response(deserializer, 200, "")

        val (resultCode: Int, resultBody: Any) = response.fold(
            `200` = { responseBody: MyResponseBody200 ->
                200 to responseBody
            },
            unknownError = {
                throw it
            }
        )

        Assertions.assertEquals(
            200,
            resultCode,
        )
        Assertions.assertEquals(
            body,
            resultBody,
        )
    }

    @Test
    fun `unknown status`() = runTest {
        val body = MyResponseBody200()
        val deserializer = object : SafeResponseDeserializer {
            override fun <R> deserializeOrThrow(string: String, clazz: Class<R>): R {
                @Suppress("UNCHECKED_CAST")
                return when (clazz) {
                    MyResponseBody200::class.java -> body as R
                    else -> throw IllegalArgumentException("Class: $clazz")
                }
            }
        }
        val response: SafeResponse<MyResponseBody200> = SafeResponse.Response(deserializer, 222, "")

        val error: Throwable? = response.fold(
            `200` = {
                throw IllegalStateException("Status must not be 200")
            },
            211 noBody {
                null
            },
            unknownError = {
                it
            },
        )

        Assertions.assertEquals(
            UnmentionedStatusException(222),
            error,
        )
    }

    @Test
    fun `network error`() = runTest {
        val networkErrorException = IOException()
        val response: SafeResponse<MyResponseBody200> = SafeResponse.NetworkError(networkErrorException)

        val error: Throwable? = response.fold(
            `200` = { _: MyResponseBody200 ->
                null
            },
            unknownError = {
                it
            }
        )

        Assertions.assertEquals(
            networkErrorException,
            error,
        )
    }

    @Test
    fun `204 status code with no body`() = runTest {
        val deserializer = object : SafeResponseDeserializer {
            override fun <R> deserializeOrThrow(string: String, clazz: Class<R>): R {
                throw IllegalStateException("There is no body in this case: no deserialization expected")
            }
        }
        val response: SafeResponse<Unit> = SafeResponse.Response(deserializer, 204, "")

        val (resultCode: Int, resultBody: Any?) = response.fold(
            `200` = {
                200 to it
            },
            204 noBody {
                204 to null
            },
            unknownError = {
                throw it
            }
        )

        Assertions.assertEquals(
            204,
            resultCode,
        )
        Assertions.assertEquals(
            null as Any?,
            resultBody,
        )
    }

    @ParameterizedTest
    @MethodSource("provide200and204and422StatusesTestValues")
    fun `statuses 200, 204, 422`(testConfig: TestConfig) = runTest {
        val deserializer = object : SafeResponseDeserializer {
            override fun <R> deserializeOrThrow(string: String, clazz: Class<R>): R {
                @Suppress("UNCHECKED_CAST")
                return when {
                    testConfig.expectedBody == null -> {
                        throw IllegalArgumentException("There is no body in this case: no deserialization expected")
                    }

                    clazz == testConfig.expectedBody.javaClass -> {
                        testConfig.expectedBody as R
                    }

                    else -> {
                        throw IllegalArgumentException("Class: $clazz")
                    }
                }
            }
        }
        val response: SafeResponse<MyResponseBody200> = SafeResponse.Response(deserializer, testConfig.expectedCode, "")

        val (resultCode: Int, resultBody: Any?) = response.fold(
            `200` = {
                200 to it
            },
            204 noBody {
                204 to null
            },
            422 withBody { responseBody: MyResponseBody422 ->
                422 to responseBody
            },
            unknownError = {
                throw it
            }
        )

        Assertions.assertEquals(
            testConfig.expectedCode,
            resultCode,
        )
        Assertions.assertEquals(
            testConfig.expectedBody,
            resultBody,
        )
    }
}

private class MyResponseBody200
private class MyResponseBody422

data class TestConfig(
    val expectedBody: Any?,
    val expectedCode: Int,
)
