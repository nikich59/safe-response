package uz.uzum.tezkor.courier.network

import okhttp3.RequestBody

interface SafeResponseSerializer {
    fun serialize(model: Any): RequestBody
}
