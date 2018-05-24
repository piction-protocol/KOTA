package com.iota.iri.model

import com.iota.iri.controllers.TransactionViewModel
import com.iota.iri.controllers.TransactionViewModelTest
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.utils.Converter
import org.junit.Assert
import org.junit.Test
import java.util.*

/**
 * Created by paul on 4/29/17.
 */
class HashTest {
    @Test
    @Throws(Exception::class)
    fun calculate() {
        val hash = Hash.calculate(SpongeFactory.Mode.CURLP81, TransactionViewModelTest.randomTransactionTrits)
        Assert.assertNotEquals(0, hash.hashCode().toLong())
        Assert.assertNotEquals(null, hash.bytes())
        Assert.assertNotEquals(null, hash.trits())
    }

    @Test
    @Throws(Exception::class)
    fun calculate1() {
        val hash = Hash.calculate(TransactionViewModelTest.randomTransactionTrits, 0, 729, SpongeFactory.create(SpongeFactory.Mode.CURLP81)!!)
        Assert.assertNotEquals(null, hash.bytes())
        Assert.assertNotEquals(0, hash.hashCode().toLong())
        Assert.assertNotEquals(null, hash.trits())
    }

    @Test
    @Throws(Exception::class)
    fun calculate2() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        val bytes = Converter.allocateBytesForTrits(trits.size)
        Converter.bytes(trits, bytes)
        val hash = Hash.calculate(bytes, TransactionViewModel.TRINARY_SIZE, SpongeFactory.create(SpongeFactory.Mode.CURLP81))
        Assert.assertNotEquals(0, hash.hashCode().toLong())
        Assert.assertNotEquals(null, hash.bytes())
        Assert.assertNotEquals(null, hash.trits())
    }

    @Test
    @Throws(Exception::class)
    fun trailingZeros() {
        val hash = Hash.NULL_HASH
        Assert.assertEquals(Hash.SIZE_IN_TRITS.toLong(), hash.trailingZeros().toLong())
    }

    @Test
    @Throws(Exception::class)
    fun trits() {
        val hash = Hash.calculate(SpongeFactory.Mode.CURLP81, TransactionViewModelTest.randomTransactionTrits)
        Assert.assertFalse(Arrays.equals(IntArray(Hash.SIZE_IN_TRITS), hash.trits()))
    }

    @Test
    @Throws(Exception::class)
    fun equals() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        val hash = Hash.calculate(SpongeFactory.Mode.CURLP81, trits)
        val hash1 = Hash.calculate(SpongeFactory.Mode.CURLP81, trits)
        Assert.assertTrue(hash == hash1)
        Assert.assertFalse(hash == Hash.NULL_HASH)
        Assert.assertFalse(hash == Hash.calculate(SpongeFactory.Mode.CURLP81, TransactionViewModelTest.randomTransactionTrits))
    }

    @Test
    @Throws(Exception::class)
    fun hashCodeTest() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        val hash = Hash.calculate(SpongeFactory.Mode.CURLP81, trits)
        Assert.assertNotEquals(hash.hashCode().toLong(), 0)
        Assert.assertEquals(Hash.NULL_HASH.hashCode().toLong(), -240540129)
    }

    @Test
    @Throws(Exception::class)
    fun toStringTest() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        val hash = Hash.calculate(SpongeFactory.Mode.CURLP81, trits)
        Assert.assertEquals(Hash.NULL_HASH.toString(), "999999999999999999999999999999999999999999999999999999999999999999999999999999999")
        Assert.assertNotEquals(hash.toString(), "999999999999999999999999999999999999999999999999999999999999999999999999999999999")
        Assert.assertNotEquals(hash.toString().length.toLong(), 0)

    }

    @Test
    @Throws(Exception::class)
    fun bytes() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        val hash = Hash.calculate(SpongeFactory.Mode.CURLP81, trits)
        Assert.assertTrue(Arrays.equals(ByteArray(Hash.SIZE_IN_BYTES), Hash.NULL_HASH.bytes()))
        Assert.assertFalse(Arrays.equals(ByteArray(Hash.SIZE_IN_BYTES), hash.bytes()))
        Assert.assertNotEquals(0, hash.bytes().size.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun compareTo() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        val hash = Hash.calculate(SpongeFactory.Mode.CURLP81, trits)
        Assert.assertEquals(hash.compareTo(Hash.NULL_HASH).toLong(), (-Hash.NULL_HASH.compareTo(hash)).toLong())
    }
}