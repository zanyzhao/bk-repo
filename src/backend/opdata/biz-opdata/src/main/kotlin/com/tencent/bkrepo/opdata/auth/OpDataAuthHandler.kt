package com.tencent.bkrepo.opdata.auth

import com.tencent.bkrepo.auth.api.ServiceAccountResource
import com.tencent.bkrepo.common.artifact.auth.AnonymousCredentials
import com.tencent.bkrepo.common.artifact.auth.AuthCredentials
import com.tencent.bkrepo.common.artifact.auth.BasicAuthCredentials
import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 *
 * @author: owenlxu
 * @date: 2020/01/03
 */
@Component
open class OpDataAuthHandler : ClientAuthHandler {

    @Autowired
    private lateinit var serviceAccountResource: ServiceAccountResource

    override fun extractAuthCredentials(request: HttpServletRequest): AuthCredentials {
        val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
        if (basicAuthHeader.isNullOrBlank()) return AnonymousCredentials()

        try {
            if (!basicAuthHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
            val encodedCredentials = basicAuthHeader.removePrefix(BASIC_AUTH_HEADER_PREFIX)
            val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedHeader.split(":")
            require(parts.size >= 2)
            return BasicAuthCredentials(parts[0], parts[1])
        } catch (exception: Exception) {
            throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
        }
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: AuthCredentials): String {
        with(authCredentials as BasicAuthCredentials) {
            val response = serviceAccountResource.checkCredential(username, password)
            if (response.data != null) {
                return response.data!!
            } else {
                throw ClientAuthException("Authorization value check failed.")
            }
        }
    }
}