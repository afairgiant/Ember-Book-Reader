package com.ember.reader.core.network

import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class CoverAuthInterceptorTest {

    @MockK
    private lateinit var tokenManager: GrimmoryTokenManager

    private lateinit var interceptor: CoverAuthInterceptor

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        interceptor = CoverAuthInterceptor(tokenManager)
    }

    private fun grimmoryServer(id: Long = 1L, url: String = "https://grimmory.test") = Server(
        id = id,
        name = "Grimmory",
        url = url,
        opdsUsername = "",
        opdsPassword = "",
        kosyncUsername = "",
        kosyncPassword = "",
        isGrimmory = true,
    )

    private fun opdsServer(id: Long = 2L, url: String = "https://opds.test") = Server(
        id = id,
        name = "OPDS",
        url = url,
        opdsUsername = "reader",
        opdsPassword = "hunter2",
        kosyncUsername = "",
        kosyncPassword = "",
        isGrimmory = false,
    )

    private fun request(url: String): Request = Request.Builder().url(url).build()

    /** Runs [request] through the interceptor and returns whatever it handed to chain.proceed(). */
    private fun intercept(request: Request): Request {
        val chain = mockk<Interceptor.Chain>()
        val captured = slot<Request>()
        every { chain.request() } returns request
        every { chain.proceed(capture(captured)) } answers {
            Response.Builder()
                .request(captured.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        interceptor.intercept(chain)

        return captured.captured
    }

    @Test
    fun `appends live token for grimmory cover request`() {
        interceptor.updateServers(listOf(grimmoryServer()))
        every { tokenManager.getAccessToken(1L) } returns "token-v1"

        val proceeded = intercept(request("https://grimmory.test/api/v1/media/book/9/cover"))

        assertEquals("token-v1", proceeded.url.queryParameter("token"))
    }

    @Test
    fun `looks up the token live instead of a snapshot taken at updateServers time`() {
        // Regression test for the staleness bug: updateServers() used to snapshot the
        // access token once. GrimmoryTokenManager.withAuth() rotates the token
        // independently (on a 401 during an unrelated API call) without the Server
        // row changing, so a snapshot taken before that rotation was never refreshed
        // and cover requests kept authenticating with a dead token indefinitely.
        interceptor.updateServers(listOf(grimmoryServer()))
        every { tokenManager.getAccessToken(1L) } returns "stale-token"
        intercept(request("https://grimmory.test/api/v1/media/book/9/cover"))

        // Token rotates on the token manager's side; updateServers() is NOT called again.
        every { tokenManager.getAccessToken(1L) } returns "rotated-token"
        val proceeded = intercept(request("https://grimmory.test/api/v1/media/book/9/cover"))

        assertEquals("rotated-token", proceeded.url.queryParameter("token"))
    }

    @Test
    fun `passes request through unmodified when grimmory server has no stored token`() {
        interceptor.updateServers(listOf(grimmoryServer()))
        every { tokenManager.getAccessToken(1L) } returns null

        val original = request("https://grimmory.test/api/v1/media/book/9/cover")
        val proceeded = intercept(original)

        assertNull(proceeded.url.queryParameter("token"))
        assertEquals(original.url, proceeded.url)
    }

    @Test
    fun `adds basic auth header for opds cover request`() {
        interceptor.updateServers(listOf(opdsServer()))

        val proceeded = intercept(request("https://opds.test/opds/cover/1"))

        assertEquals(basicAuthHeader("reader", "hunter2"), proceeded.header("Authorization"))
    }

    @Test
    fun `does not overwrite an existing authorization header`() {
        interceptor.updateServers(listOf(opdsServer()))

        val original = Request.Builder()
            .url("https://opds.test/opds/cover/1")
            .header("Authorization", "Bearer preexisting")
            .build()
        val proceeded = intercept(original)

        assertEquals("Bearer preexisting", proceeded.header("Authorization"))
    }

    @Test
    fun `does not overwrite an existing token query param`() {
        interceptor.updateServers(listOf(grimmoryServer()))
        every { tokenManager.getAccessToken(1L) } returns "token-v1"

        val original = request("https://grimmory.test/api/v1/media/book/9/cover?token=manual")
        val proceeded = intercept(original)

        assertEquals("manual", proceeded.url.queryParameter("token"))
    }

    @Test
    fun `passes through requests to unknown origins unmodified`() {
        interceptor.updateServers(listOf(grimmoryServer()))

        val original = request("https://other.test/cover.jpg")
        val proceeded = intercept(original)

        assertEquals(original.url, proceeded.url)
        assertNull(proceeded.header("Authorization"))
    }
}
