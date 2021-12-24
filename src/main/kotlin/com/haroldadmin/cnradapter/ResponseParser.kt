package com.haroldadmin.cnradapter

import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Maps a [Response] to a [NetworkResponse].
 *
 * @param errorConverter Retrofit provided body converter to parse the error body of the response
 * @return A subtype of [NetworkResponse] based on the response of the network request
 */
internal fun <S, E> Response<S>.asNetworkResponse(
    errorConverter: Converter<ResponseBody, E>
) = when {
    isSuccessful -> NetworkResponse.Success(body(), this)
    else -> parseUnsuccessfulResponse(this, errorConverter)
}

/**
 * Maps an unsuccessful [Response] to [NetworkResponse.Error].
 *
 * Control flow:
 * 1 Try to parse the error body using [errorConverter].
 * 2. If error body is parsed successfully, return it as [NetworkResponse.ServerError]
 * 3 Otherwise, assume we ran into an unknown error (probably related to serialization)
 * and return [NetworkResponse.UnknownError]
 *
 * @param response Unsuccessful response
 * @param errorConverter Retrofit [Converter] to parse the error body
 * @return A subtype of [NetworkResponse.Error]
 */
private fun <S, E> parseUnsuccessfulResponse(
    response: Response<S>,
    errorConverter: Converter<ResponseBody, E>
): NetworkResponse.Error<E> {
    val errorBody: ResponseBody =
        response.errorBody() ?: return NetworkResponse.ServerError(null, response)

    return try {
        val convertedBody = errorConverter.convert(errorBody)
        NetworkResponse.ServerError(convertedBody, response)
    } catch (error: Throwable) {
        NetworkResponse.UnknownError(error)
    }
}

/**
 * Maps a [Throwable] to a [NetworkResponse].
 *
 * - If the error is [IOException], return [NetworkResponse.NetworkError].
 * - If the error is [HttpException], attempt to parse the underlying response and return the result
 * - Else return [NetworkResponse.UnknownError] that wraps the original error
 */
internal fun <E> Throwable.asNetworkResponse(
    errorConverter: Converter<ResponseBody, E>,
) = when (this) {
    is IOException -> NetworkResponse.NetworkError(this)
    is HttpException -> response()
        ?.asNetworkResponse(errorConverter).let {
            when (it) {
                is NetworkResponse.Error -> it
                else -> null
            }
        }
        ?: NetworkResponse.ServerError(null, null)
    else -> NetworkResponse.UnknownError(this)
}
