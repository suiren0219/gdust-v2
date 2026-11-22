package com.menglimi.gdust.v2.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountLoginStateTest {
    @Test fun onlyAnIdentityResponseForTheSameAccountIsValid() {
        assertEquals(AccountLoginState.VALID, accountLoginState("demo-account", "demo-account", null))
        assertEquals(AccountLoginState.UNAVAILABLE, accountLoginState("demo-account", "another-account", null))
        assertEquals(AccountLoginState.UNAVAILABLE, accountLoginState("demo-account", "", null))
        assertEquals(AccountLoginState.UNAVAILABLE, accountLoginState("demo-account", null, null))
    }

    @Test fun networkOrParserFailureDoesNotLookLikeAValidLogin() {
        assertEquals(AccountLoginState.UNAVAILABLE, accountLoginState("demo-account", null, IOException("offline")))
        assertEquals(AccountLoginState.UNAVAILABLE, accountLoginState("demo-account", null, IllegalStateException("format")))
        assertEquals(AccountLoginState.EXPIRED, accountLoginState("demo-account", null, AuthFailed(401, "expired")))
    }

    @Test fun noAccountIsSignedOutRegardlessOfCachedEvidence() {
        assertEquals(AccountLoginState.SIGNED_OUT, accountLoginState("", "demo-account", null))
    }
}
