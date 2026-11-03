package me.lampu.lampcord.shared.api

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class JoinErrorTest {

    @Test
    fun `the server limit has a clear message`() {
        assertEquals(
            "You are in 100 servers, the maximum for a Discord account. Leave a server and try again.",
            joinErrorMessage(30001, "Maximum number of guilds reached (100)", HttpStatusCode.BadRequest),
        )
    }

    @Test
    fun `join restrictions and invite errors are mapped`() {
        assertEquals(
            "Discord has limited your account from joining new servers. Try again later.",
            joinErrorMessage(340015, null, HttpStatusCode.Forbidden),
        )
        assertEquals(
            "That invite is invalid or has expired.",
            joinErrorMessage(10006, "Unknown Invite", HttpStatusCode.NotFound),
        )
        assertEquals(
            "You are banned from that server.",
            joinErrorMessage(40007, "You are banned from this guild.", HttpStatusCode.Forbidden),
        )
    }

    @Test
    fun `unknown errors fall back to the discord message and status`() {
        assertEquals(
            "Something specific happened",
            joinErrorMessage(99999, "Something specific happened", HttpStatusCode.BadRequest),
        )
        assertEquals(
            "Discord is rate limiting this action. Wait a moment and try again.",
            joinErrorMessage(null, null, HttpStatusCode.TooManyRequests),
        )
        assertEquals(
            "Discord is having trouble right now (HTTP 502). Try again in a moment.",
            joinErrorMessage(null, null, HttpStatusCode.BadGateway),
        )
    }
}
