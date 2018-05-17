package com.iota.iri.storage

import com.iota.iri.utils.Pair
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Consumer

/**
 * Created by paul on 3/3/17 for iri.
 */
class Tangle {

    private val persistenceProviders = ArrayList<PersistenceProvider>()

    fun addPersistenceProvider(provider: PersistenceProvider) {
        this.persistenceProviders.add(provider)
    }

    @Throws(Exception::class)
    fun init() {
        for (provider in this.persistenceProviders) {
            provider.init()
        }
    }

    @Throws(Exception::class)
    fun shutdown() {
        log.info("Shutting down Tangle Persistence Providers... ")
        this.persistenceProviders.forEach(Consumer<PersistenceProvider> { it.shutdown() })
        this.persistenceProviders.clear()
    }

    @Throws(Exception::class)
    fun load(model: Class<*>, index: Indexable): Persistable? {
        var out: Persistable? = null
        for (provider in this.persistenceProviders) {
            out = provider.get(model, index)
            if (out != null) {
                break
            }
        }
        return out
    }

    @Throws(Exception::class)
    fun saveBatch(models: List<Pair<Indexable, Persistable>>): Boolean? {
        var exists = false
        for (provider in persistenceProviders) {
            if (exists) {
                provider.saveBatch(models)
            } else {
                exists = provider.saveBatch(models)
            }
        }
        return exists
    }

    @Throws(Exception::class)
    fun save(model: Persistable, index: Indexable?): Boolean {
        var exists = false
        for (provider in persistenceProviders) {
            if (exists) {
                provider.save(model, index)
            } else {
                exists = provider.save(model, index)
            }
        }
        return exists
    }

    @Throws(Exception::class)
    fun delete(model: Class<*>, index: Indexable?) {
        for (provider in persistenceProviders) {
            provider.delete(model, index)
        }
    }

    @Throws(Exception::class)
    fun getLatest(model: Class<*>, index: Class<*>): Pair<Indexable, Persistable>? {
        var latest: Pair<Indexable, Persistable>? = null
        for (provider in persistenceProviders) {
            if (latest == null) {
                latest = provider.latest(model, index)
            }
        }
        return latest
    }

    @Throws(Exception::class)
    fun update(model: Persistable, index: Indexable, item: String): Boolean? {
        var success = false
        for (provider in this.persistenceProviders) {
            if (success) {
                provider.update(model, index, item)
            } else {
                success = provider.update(model, index, item)
            }
        }
        return success
    }

    @Throws(Exception::class)
    fun keysWithMissingReferences(modelClass: Class<*>, referencedClass: Class<*>): Set<Indexable>? {
        var output: Set<Indexable>? = null
        for (provider in this.persistenceProviders) {
            output = provider.keysWithMissingReferences(modelClass, referencedClass)
            if (output != null && output.size > 0) {
                break
            }
        }
        return output
    }

    fun keysStartingWith(modelClass: Class<*>, value: ByteArray): Set<Indexable>? {
        var output: Set<Indexable>? = null
        for (provider in this.persistenceProviders) {
            output = provider.keysStartingWith(modelClass, value)
            if (output!!.size != 0) {
                break
            }
        }
        return output
    }

    @Throws(Exception::class)
    fun exists(modelClass: Class<*>, hash: Indexable): Boolean? {
        for (provider in this.persistenceProviders) {
            if (provider.exists(modelClass, hash)) {
                return true
            }
        }
        return false
    }

    @Throws(Exception::class)
    fun maybeHas(model: Class<*>, index: Indexable): Boolean? {
        for (provider in this.persistenceProviders) {
            if (provider.mayExist(model, index)) {
                return true
            }
        }
        return false
    }

    @Throws(Exception::class)
    fun getCount(modelClass: Class<*>): Long? {
        var value: Long = 0
        for (provider in this.persistenceProviders) {
            value = provider.count(modelClass)
            if (value != 0L) {
                break
            }
        }
        return value
    }

    @Throws(Exception::class)
    fun find(model: Class<*>, key: ByteArray): Persistable? {
        var out: Persistable? = null
        for (provider in this.persistenceProviders) {
            out = provider.seek(model, key)
            if (out != null) {
                break
            }
        }
        return out
    }

    @Throws(Exception::class)
    fun next(model: Class<*>, index: Indexable?): Pair<Indexable, Persistable>? {
        var latest: Pair<Indexable, Persistable>? = null
        for (provider in persistenceProviders) {
            if (latest == null) {
                latest = provider.next(model, index)
            }
        }
        return latest
    }

    @Throws(Exception::class)
    fun previous(model: Class<*>, index: Indexable): Pair<Indexable, Persistable>? {
        var latest: Pair<Indexable, Persistable>? = null
        for (provider in persistenceProviders) {
            if (latest == null) {
                latest = provider.previous(model, index)
            }
        }
        return latest
    }

    @Throws(Exception::class)
    fun getFirst(model: Class<*>, index: Class<*>): Pair<Indexable, Persistable>? {
        var latest: Pair<Indexable, Persistable>? = null
        for (provider in persistenceProviders) {
            if (latest == null) {
                latest = provider.first(model, index)
            }
        }
        return latest
    }

    @Throws(Exception::class)
    fun clearColumn(column: Class<*>) {
        for (provider in persistenceProviders) {
            provider.clear(column)
        }
    }

    @Throws(Exception::class)
    fun clearMetadata(column: Class<*>) {
        for (provider in persistenceProviders) {
            provider.clearMetadata(column)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Tangle::class.java)
    }
}
