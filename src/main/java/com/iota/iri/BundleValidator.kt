package com.iota.iri

import com.iota.iri.controllers.TransactionViewModel
import com.iota.iri.hash.ISS
import com.iota.iri.hash.ISSInPlace
import com.iota.iri.hash.Sponge
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.model.Hash
import com.iota.iri.storage.Tangle
import com.iota.iri.utils.Converter
import java.util.*

object BundleValidator {

    @Throws(Exception::class)
    fun validate(tangle: Tangle, tailHash: Hash): List<List<TransactionViewModel>> {
        val tail = TransactionViewModel.fromHash(tangle, tailHash)
        val transactions = LinkedList<List<TransactionViewModel>>()
        if (tail.currentIndex != 0L) {
            return transactions
        }
        val bundleTransactions = loadTransactionsFromTangle(tangle, tail)

        for (transactionViewModel in bundleTransactions.values) {

            if (transactionViewModel.currentIndex == 0L && transactionViewModel.validity >= 0) {

                var instanceTransactionViewModels = LinkedList<TransactionViewModel>()

                val lastIndex = transactionViewModel.lastIndex()
                var bundleValue: Long = 0
                var i = 0
                val curlInstance = SpongeFactory.create(SpongeFactory.Mode.KERL)
                val addressInstance = SpongeFactory.create(SpongeFactory.Mode.KERL)

                val addressTrits = IntArray(TransactionViewModel.ADDRESS_TRINARY_SIZE)
                val bundleHashTrits = IntArray(TransactionViewModel.BUNDLE_TRINARY_SIZE)
                val normalizedBundle = IntArray(Sponge.HASH_LENGTH / ISS.TRYTE_WIDTH)
                val digestTrits = IntArray(Sponge.HASH_LENGTH)

                MAIN_LOOP@ while (true) {

                    instanceTransactionViewModels.add(transactionViewModel)


                    bundleValue = Math.addExact(bundleValue, transactionViewModel.value())
                    if (transactionViewModel.currentIndex != i.toLong()
                            || transactionViewModel.lastIndex() != lastIndex
                            || bundleValue < -TransactionViewModel.SUPPLY || bundleValue > TransactionViewModel.SUPPLY) {
                        instanceTransactionViewModels[0].setValidity(tangle, -1)
                        break
                    }

                    if (transactionViewModel.value() != 0L && transactionViewModel.addressHash.trits()[Sponge.HASH_LENGTH - 1] != 0) {
                        instanceTransactionViewModels[0].setValidity(tangle, -1)
                        break
                    }

                    if (i++.toLong() == lastIndex) { // It's supposed to become -3812798742493 after 3812798742493 and to go "down" to -1 but we hope that noone will create such long bundles

                        if (bundleValue == 0L) {

                            if (instanceTransactionViewModels[0].validity == 0) {
                                curlInstance!!.reset()
                                for (transactionViewModel2 in instanceTransactionViewModels) {
                                    curlInstance.absorb(transactionViewModel2.trits(), TransactionViewModel.ESSENCE_TRINARY_OFFSET, TransactionViewModel.ESSENCE_TRINARY_SIZE)
                                }
                                curlInstance.squeeze(bundleHashTrits, 0, bundleHashTrits.size)
                                if (Arrays.equals(instanceTransactionViewModels[0].bundleHash.trits(), bundleHashTrits)) {

                                    ISSInPlace.normalizedBundle(bundleHashTrits, normalizedBundle)

                                    var j = 0
                                    while (j < instanceTransactionViewModels.size) {

                                        val transactionViewModel = instanceTransactionViewModels[j]
                                        if (transactionViewModel.value() < 0) { // let's recreate the address of the transactionViewModel.
                                            addressInstance!!.reset()
                                            var offset = 0
                                            var offsetNext = 0
                                            do {
                                                offsetNext = (offset + ISS.NUMBER_OF_FRAGMENT_CHUNKS - 1) % (Sponge.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE) + 1
                                                ISSInPlace.digest(SpongeFactory.Mode.KERL,
                                                        normalizedBundle,
                                                        offset % (Sponge.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE),
                                                        instanceTransactionViewModels[j].trits(),
                                                        TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET,
                                                        digestTrits)
                                                addressInstance.absorb(digestTrits, 0, Sponge.HASH_LENGTH)
                                                offset = offsetNext
                                            } while (++j < instanceTransactionViewModels.size
                                                    && instanceTransactionViewModels[j].addressHash == transactionViewModel.addressHash
                                                    && instanceTransactionViewModels[j].value() == 0L)

                                            addressInstance.squeeze(addressTrits, 0, addressTrits.size)
                                            //if (!Arrays.equals(Converter.bytes(addressTrits, 0, TransactionViewModel.ADDRESS_TRINARY_SIZE), transactionViewModel.getAddress().getHash().bytes())) {
                                            if (!Arrays.equals(transactionViewModel.addressHash.trits(), addressTrits)) {
                                                instanceTransactionViewModels[0].setValidity(tangle, -1)
                                                break@MAIN_LOOP
                                            }
                                        } else {
                                            j++
                                        }
                                    }

                                    instanceTransactionViewModels[0].setValidity(tangle, 1)
                                    transactions.add(instanceTransactionViewModels)
                                } else {
                                    instanceTransactionViewModels[0].setValidity(tangle, -1)
                                }
                            } else {
                                transactions.add(instanceTransactionViewModels)
                            }
                        } else {
                            instanceTransactionViewModels[0].setValidity(tangle, -1)
                        }
                        break

                    } else {
                        if (bundleTransactions[transactionViewModel.trunkTransactionHash] == null) {
                            break
                        }
                    }
                }
            }
        }
        return transactions
    }

    fun isInconsistent(transactionViewModels: List<TransactionViewModel>): Boolean {
        var value: Long = 0
        for (bundleTransactionViewModel in transactionViewModels) {
            if (bundleTransactionViewModel.value() != 0L) {
                value += bundleTransactionViewModel.value()
                /*
                if(!milestone && bundleTransactionViewModel.getAddressHash().equals(Hash.NULL_HASH) && bundleTransactionViewModel.snapshotIndex() == 0) {
                    return true;
                }
                */
            }
        }
        return value != 0L || transactionViewModels.size == 0
    }

    private fun loadTransactionsFromTangle(tangle: Tangle, tail: TransactionViewModel): Map<Hash, TransactionViewModel> {
        val bundleTransactions = HashMap<Hash, TransactionViewModel>()
        val bundleHash = tail.bundleHash
        try {
            var tx = tail
            var i: Long = 0
            val end = tx.lastIndex()
            do {
                bundleTransactions.put(tx.hash, tx)
                tx = tx.getTrunkTransaction(tangle)
            } while (i++ < end && tx.currentIndex != 0L && tx.bundleHash == bundleHash)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return bundleTransactions
    }
}
