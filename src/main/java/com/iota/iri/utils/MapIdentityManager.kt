package com.iota.iri.utils

import com.iota.iri.hash.Sponge
import com.iota.iri.hash.SpongeFactory
import io.undertow.security.idm.Account
import io.undertow.security.idm.Credential
import io.undertow.security.idm.IdentityManager
import io.undertow.security.idm.PasswordCredential
import java.security.Principal
import java.util.*

class MapIdentityManager(private val users: Map<String, CharArray>) : IdentityManager {

    override fun verify(account: Account): Account {
        // An existing account so for testing assume still valid.
        return account
    }

    override fun verify(id: String, credential: Credential): Account? {
        val account = getAccount(id)
        return if (account != null && verifyCredential(account, credential)) {
            account
        } else null

    }

    override fun verify(credential: Credential): Account? {
        // TODO Auto-generated method stub
        return null
    }

    private fun verifyCredential(account: Account, credential: Credential): Boolean {
        if (credential is PasswordCredential) {
            val givenPassword = credential.password
            val trytes = Converter.asciiToTrytes(String(givenPassword))
            val in_trits = Converter.allocateTritsForTrytes(trytes!!.length)
            Converter.trits(trytes, in_trits, 0)
            val hash_trits = IntArray(Sponge.HASH_LENGTH)
            val curl: Sponge?
            curl = SpongeFactory.create(SpongeFactory.Mode.CURLP81)
            curl!!.absorb(in_trits, 0, in_trits.size)
            curl.squeeze(hash_trits, 0, Sponge.HASH_LENGTH)
            val out_trytes = Converter.trytes(hash_trits)
            val char_out_trytes = out_trytes.toCharArray()
            val expectedPassword = users[account.principal.name]
            var verified = Arrays.equals(givenPassword, expectedPassword)
            // Password can either be clear text or the hash of the password
            if (!verified) {
                verified = Arrays.equals(char_out_trytes, expectedPassword)
            }
            return verified
        }
        return false
    }

    private fun getAccount(id: String): Account? {
        return if (users.containsKey(id)) {
            object : Account {
                private val principal = Principal { id }

                override fun getPrincipal(): Principal {
                    return principal
                }

                override fun getRoles(): Set<String> {
                    return emptySet()
                }
            }
        } else null
    }
}