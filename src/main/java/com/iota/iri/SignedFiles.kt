package com.iota.iri

import com.iota.iri.hash.ISS
import com.iota.iri.hash.Sponge
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.utils.Converter
import org.apache.commons.lang3.ArrayUtils
import java.io.*
import java.util.*

object SignedFiles {

    @Throws(IOException::class)
    fun isFileSignatureValid(filename: String, signatureFilename: String, publicKey: String, depth: Int, index: Int): Boolean {
        val signature = digestFile(filename, SpongeFactory.create(SpongeFactory.Mode.KERL)!!)
        return validateSignature(signatureFilename, publicKey, depth, index, signature)
    }

    @Throws(IOException::class)
    private fun validateSignature(signatureFilename: String, publicKey: String, depth: Int, index: Int, digest: IntArray): Boolean {
        //validate signature
        val mode = SpongeFactory.Mode.CURLP81
        var digests = IntArray(0)
        val bundle = ISS.normalizedBundle(digest)
        var root: IntArray? = null
        var i: Int

        SignedFiles::class.java.getResourceAsStream(signatureFilename).use { inputStream ->
            BufferedReader(if (inputStream == null)
                FileReader(signatureFilename)
            else
                InputStreamReader(inputStream)).use { reader ->

                var line: String
                i = 0
                while (i < 3) {
                    line = reader.readLine()
                    if(line == null) break;
                    val lineTrits = Converter.allocateTritsForTrytes(line.length)
                    Converter.trits(line, lineTrits, 0)
                    val normalizedBundleFragment = Arrays.copyOfRange(bundle, i * ISS.NORMALIZED_FRAGMENT_LENGTH, (i + 1) * ISS.NORMALIZED_FRAGMENT_LENGTH)
                    val issDigest = ISS.digest(mode, normalizedBundleFragment, lineTrits)
                    digests = ArrayUtils.addAll(digests, *issDigest)
                    i++
                }

                line = reader.readLine()
                line?.let {
                    val lineTrits = Converter.allocateTritsForTrytes(line.length)
                    Converter.trits(line, lineTrits, 0)
                    root = ISS.getMerkleRoot(mode, ISS.address(mode, digests), lineTrits, 0, index, depth)
                } ?: let { root = ISS.address(mode, digests) }

                val pubkeyTrits = Converter.allocateTritsForTrytes(publicKey.length)
                Converter.trits(publicKey, pubkeyTrits, 0)
                return Arrays.equals(pubkeyTrits, root) // valid
            }
        }
    }

    @Throws(IOException::class)
    private fun digestFile(filename: String, curl: Sponge): IntArray {
        try {
            SignedFiles::class.java.getResourceAsStream(filename).use { inputStream ->
                BufferedReader(if (inputStream == null)
                    FileReader(filename)
                else
                    InputStreamReader(inputStream)).use { reader ->

                    val buffer = IntArray(Sponge.HASH_LENGTH * 3)

                    reader.lines().forEach { line ->
                        val trytes = Converter.asciiToTrytes(line) ?: throw IllegalArgumentException("TRYTES IS NULL. INPUT= '$line'") // can return a null
                        Converter.trits(trytes, buffer, 0)
                        curl.absorb(buffer, 0, buffer.size)
                        Arrays.fill(buffer, 0)
                    }

                    val signature = IntArray(Sponge.HASH_LENGTH)
                    curl.squeeze(signature, 0, Sponge.HASH_LENGTH)
                    return signature
                }
            }
        } catch (e: UncheckedIOException) {
            e.printStackTrace()
        }
        return IntArray(0)
    }
}