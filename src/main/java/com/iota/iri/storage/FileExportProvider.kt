package com.iota.iri.storage

import com.iota.iri.controllers.TransactionViewModel.TRINARY_SIZE
import com.iota.iri.model.Transaction
import com.iota.iri.utils.Converter
import com.iota.iri.utils.Pair
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.io.UnsupportedEncodingException
import java.nio.file.Paths

/**
 * Created by paul on 4/18/17.
 */
class FileExportProvider : PersistenceProvider {

    @Throws(Exception::class)
    override fun init() {

    }

    override fun isAvailable(): Boolean {
        return false
    }

    override fun shutdown() {

    }

    @Throws(Exception::class)
    override fun save(model: Persistable, index: Indexable): Boolean {
        return false
    }

    @Throws(Exception::class)
    override fun delete(model: Class<*>, index: Indexable) {

    }

    @Throws(Exception::class)
    override fun update(model: Persistable, index: Indexable, item: String): Boolean {

        if (model is Transaction) {
            if (item.contains("sender")) {
                val path = Paths.get("export", fileNumber.toString() + ".tx")
                try {
                    PrintWriter(path.toString(), "UTF-8").use { writer ->
                        writer.println(index.toString())
                        writer.println(Converter.trytes(trits(model)))
                        writer.println(model.sender)
                        if (item == "height") {
                            writer.println("Height: " + model.height.toString())
                        } else {
                            writer.println("Height: ")
                        }
                        writer.close()
                        return true
                    }
                } catch (e: UnsupportedEncodingException) {
                    log.error("File export failed", e)
                } catch (e: FileNotFoundException) {
                    log.error("File export failed", e)
                } catch (e: Exception) {
                    log.error("Transaction load failed. ", e)
                }

            }
        }
        return false
    }

    @Throws(Exception::class)
    override fun exists(model: Class<*>, key: Indexable): Boolean {
        return false
    }

    @Throws(Exception::class)
    override fun latest(model: Class<*>, indexModel: Class<*>): Pair<Indexable, Persistable>? {
        return null
    }


    @Throws(Exception::class)
    override fun keysWithMissingReferences(modelClass: Class<*>, other: Class<*>): Set<Indexable>? {
        return null
    }

    @Throws(Exception::class)
    override fun get(model: Class<*>, index: Indexable): Persistable? {
        return null
    }

    @Throws(Exception::class)
    override fun mayExist(model: Class<*>, index: Indexable): Boolean {
        return false
    }

    @Throws(Exception::class)
    override fun count(model: Class<*>): Long {
        return 0
    }

    override fun keysStartingWith(modelClass: Class<*>, value: ByteArray): Set<Indexable>? {
        return null
    }

    @Throws(Exception::class)
    override fun seek(model: Class<*>, key: ByteArray): Persistable? {
        return null
    }

    @Throws(Exception::class)
    override fun next(model: Class<*>, index: Indexable): Pair<Indexable, Persistable>? {
        return null
    }

    @Throws(Exception::class)
    override fun previous(model: Class<*>, index: Indexable): Pair<Indexable, Persistable>? {
        return null
    }

    @Throws(Exception::class)
    override fun first(model: Class<*>, indexModel: Class<*>): Pair<Indexable, Persistable>? {
        return null
    }

    @Throws(Exception::class)
    fun merge(model: Persistable, index: Indexable): Boolean {
        return false
    }

    @Throws(Exception::class)
    override fun saveBatch(models: List<Pair<Indexable, Persistable>>): Boolean {
        return false
    }

    @Throws(Exception::class)
    override fun clear(column: Class<*>) {

    }

    @Throws(Exception::class)
    override fun clearMetadata(column: Class<*>) {

    }

    internal fun trits(transaction: Transaction): IntArray {
        val trits = IntArray(TRINARY_SIZE)
        if (transaction.bytes != null) {
            Converter.getTrits(transaction.bytes, trits)
        }
        return trits
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileExportProvider::class.java)

        private var lastFileNumber = 0L
        private val lock = Any()

        val fileNumber: Long
            get() {
                val now = System.currentTimeMillis() * 1000
                synchronized(lock) {
                    if (now <= lastFileNumber) {
                        return ++lastFileNumber
                    }
                    lastFileNumber = now
                }
                return now
            }
    }
}
