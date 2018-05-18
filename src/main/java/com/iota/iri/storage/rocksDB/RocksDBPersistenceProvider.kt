package com.iota.iri.storage.rocksDB

import com.iota.iri.model.*
import com.iota.iri.storage.Indexable
import com.iota.iri.storage.Persistable
import com.iota.iri.storage.PersistenceProvider
import com.iota.iri.utils.IotaIOUtils
import com.iota.iri.utils.Pair
import org.apache.commons.lang3.SystemUtils
import org.rocksdb.*
import org.rocksdb.util.SizeUnit
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.io.File
import java.nio.file.Paths
import java.security.SecureRandom
import java.util.*
import java.util.stream.Collectors

class RocksDBPersistenceProvider(private val dbPath: String, private val logPath: String, private val cacheSize: Int) : PersistenceProvider {

    private val columnFamilyNames = Arrays.asList(
            String(RocksDB.DEFAULT_COLUMN_FAMILY),
            "transaction",
            "transaction-metadata",
            "milestone",
            "stateDiff",
            "address",
            "approvee",
            "bundle",
            "obsoleteTag",
            "tag"
    )

    private val columnFamilyHandles = ArrayList<ColumnFamilyHandle>()
    private val seed = SecureRandom()

    private lateinit var transactionHandle: ColumnFamilyHandle
    private lateinit var transactionMetadataHandle: ColumnFamilyHandle
    private lateinit var milestoneHandle: ColumnFamilyHandle
    private lateinit var stateDiffHandle: ColumnFamilyHandle
    private lateinit var addressHandle: ColumnFamilyHandle
    private lateinit var approveeHandle: ColumnFamilyHandle
    private lateinit var bundleHandle: ColumnFamilyHandle
    private lateinit var obsoleteTagHandle: ColumnFamilyHandle
    private lateinit var tagHandle: ColumnFamilyHandle

    private var classTreeMap: Map<Class<*>, ColumnFamilyHandle>? = null
    private var metadataReference: Map<Class<*>, ColumnFamilyHandle>? = null

    private lateinit var db: RocksDB
    // DBOptions is only used in initDB(). However, it is closeable - so we keep a reference for shutdown.
    private lateinit var options: DBOptions
    private lateinit var bloomFilter: BloomFilter
    private var available: Boolean = false

    override fun init() {
        log.info("Initializing Database Backend... ")
        initDB(dbPath, logPath)
        initClassTreeMap()
        available = true
        log.info("RocksDB persistence provider initialized.")
    }

    override fun isAvailable(): Boolean {
        return this.available
    }

    private fun initClassTreeMap() {
        val classMap = LinkedHashMap<Class<*>, ColumnFamilyHandle>()
        classMap.put(Transaction::class.java, transactionHandle)
        classMap.put(Milestone::class.java, milestoneHandle)
        classMap.put(StateDiff::class.java, stateDiffHandle)
        classMap.put(Address::class.java, addressHandle)
        classMap.put(Approvee::class.java, approveeHandle)
        classMap.put(Bundle::class.java, bundleHandle)
        classMap.put(ObsoleteTag::class.java, obsoleteTagHandle)
        classMap.put(Tag::class.java, tagHandle)
        classTreeMap = classMap

        val metadataHashMap = HashMap<Class<*>, ColumnFamilyHandle>()
        metadataHashMap.put(Transaction::class.java, transactionMetadataHandle)
        metadataReference = metadataHashMap
    }

    override fun shutdown() {
        for (columnFamilyHandle in columnFamilyHandles) {
            IotaIOUtils.closeQuietly(columnFamilyHandle)
        }
        IotaIOUtils.closeQuietly(db, options, bloomFilter)
    }

    @Throws(Exception::class)
    override fun save(thing: Persistable, index: Indexable): Boolean {
        val handle = classTreeMap!![thing.javaClass]
        db.put(handle, index.bytes(), thing.bytes())

        val referenceHandle = metadataReference!![thing.javaClass]
        if (referenceHandle != null) {
            db.put(referenceHandle, index.bytes(), thing.metadata())
        }
        return true
    }

    @Throws(Exception::class)
    override fun delete(model: Class<*>, index: Indexable) {
        db.delete(classTreeMap!![model], index.bytes())
    }

    @Throws(Exception::class)
    override fun exists(model: Class<*>, key: Indexable): Boolean {
        val handle = classTreeMap!![model]
        return handle != null && db.get(handle, key.bytes()) != null
    }

    @Throws(Exception::class)
    override fun keysWithMissingReferences(model: Class<*>, other: Class<*>): Set<Indexable> {
        val handle = classTreeMap!![model]
        val otherHandle = classTreeMap!![other]

        db.newIterator(handle).use { iterator ->
            var indexables: MutableSet<Indexable>? = null

            iterator.seekToFirst()
            while (iterator.isValid) {
                if (db.get(otherHandle, iterator.key()) == null) {
                    indexables = if (indexables == null) HashSet() else indexables
                    indexables.add(Hash(iterator.key()))
                }
                iterator.next()
            }
            return if (indexables == null) emptySet() else Collections.unmodifiableSet(indexables)
        }
    }

    @Throws(Exception::class)
    override fun get(model: Class<*>, index: Indexable?): Persistable {
        val `object` = model.newInstance() as Persistable
        `object`.read(db.get(classTreeMap!![model], index?.bytes() ?: ByteArray(0)))

        val referenceHandle = metadataReference!![model]
        if (referenceHandle != null) {
            `object`.readMetadata(db.get(referenceHandle, index?.bytes() ?: ByteArray(0)))
        }
        return `object`
    }

    override fun mayExist(model: Class<*>, index: Indexable): Boolean {
        val handle = classTreeMap!![model]
        return db.keyMayExist(handle, index.bytes(), StringBuilder())
    }

    @Throws(Exception::class)
    override fun count(model: Class<*>): Long {
        return getCountEstimate(model)
    }

    @Throws(RocksDBException::class)
    private fun getCountEstimate(model: Class<*>): Long {
        val handle = classTreeMap!![model]
        return db.getLongProperty(handle, "rocksdb.estimate-num-keys")
    }

    override fun keysStartingWith(modelClass: Class<*>, value: ByteArray): Set<Indexable> {
        Objects.requireNonNull(value, "value byte[] cannot be null")
        val handle = classTreeMap!![modelClass]
        var keys: MutableSet<Indexable>? = null
        if (handle != null) {
            db.newIterator(handle).use { iterator ->
                iterator.seek(Hash(value, 0, value.size).bytes())

                var found: ByteArray = iterator.key()
                while (iterator.isValid && keyStartsWithValue(value, found)) {
                    found = iterator.key()
                    keys = if (keys == null) HashSet() else keys
                    keys!!.add(Hash(found))
                    iterator.next()
                }
            }
        }
        return if (keys == null) emptySet() else Collections.unmodifiableSet(keys!!)
    }

    @Throws(Exception::class)
    override fun seek(model: Class<*>, key: ByteArray): Persistable {
        val hashes = keysStartingWith(model, key)
        if (hashes.isEmpty()) {
            return get(model, null)
        }
        return if (hashes.size == 1) {
            get(model, hashes.toTypedArray()[0])
        } else get(model, hashes.toTypedArray()[seed.nextInt(hashes.size)])
    }

    @Throws(InstantiationException::class, IllegalAccessException::class, RocksDBException::class)
    private fun modelAndIndex(model: Class<*>, index: Class<out Indexable>, iterator: RocksIterator): Pair<Indexable?, Persistable?> {

        if (!iterator.isValid) {
            return PAIR_OF_NULLS
        }

        try {
            val indexable = index.newInstance()
            indexable.read(iterator.key())

            val opersistable = model.newInstance()

            if (opersistable is Persistable) {
                opersistable.read(iterator.value())

                val referenceHandle = metadataReference!![model]
                if (referenceHandle != null) {
                    opersistable.readMetadata(db.get(referenceHandle, iterator.key()))
                }
                return Pair(indexable, opersistable)
            } else
                return Pair(indexable, null)
        } catch (e: InstantiationException) {

        }
        return Pair(null, null)
    }

    @Throws(Exception::class)
    override fun next(model: Class<*>, index: Indexable): Pair<Indexable?, Persistable?> {
        db.newIterator(classTreeMap!![model]).use { iterator ->
            iterator.seek(index.bytes())
            iterator.next()
            return modelAndIndex(model, index.javaClass, iterator)
        }
    }

    @Throws(Exception::class)
    override fun previous(model: Class<*>, index: Indexable): Pair<Indexable?, Persistable?> {
        db.newIterator(classTreeMap!![model]).use { iterator ->
            iterator.seek(index.bytes())
            iterator.prev()
            return modelAndIndex(model, index.javaClass, iterator)
        }
    }

    @Throws(Exception::class)
    override fun latest(model: Class<*>, indexModel: Class<*>): Pair<Indexable?, Persistable?> {
        db.newIterator(classTreeMap!![model]).use { iterator ->
            iterator.seekToLast()
            return modelAndIndex(model, indexModel as Class<Indexable>, iterator)
        }
    }

    @Throws(Exception::class)
    override fun first(model: Class<*>, index: Class<*>): Pair<Indexable?, Persistable?> {
        db.newIterator(classTreeMap!![model]).use { iterator ->
            iterator.seekToFirst()
            return modelAndIndex(model, index as Class<Indexable>, iterator)
        }
    }

    // 2018 March 28 - Unused code
    @Throws(Exception::class)
    fun merge(model: Persistable, index: Indexable): Boolean {
        val exists = mayExist(model.javaClass, index)
        db.merge(classTreeMap!![model.javaClass], index.bytes(), model.bytes())
        return exists
    }

    @Throws(Exception::class)
    override fun saveBatch(models: List<Pair<Indexable, Persistable>>): Boolean {
        WriteBatch().use { writeBatch ->
            WriteOptions().use { writeOptions ->

                for (entry in models) {

                    val key = entry.low
                    val value = entry.high

                    val handle = classTreeMap!![value.javaClass]
                    val referenceHandle = metadataReference!![value.javaClass]

                    if (value.merge()) {
                        writeBatch.merge(handle, key.bytes(), value.bytes())
                    } else {
                        writeBatch.put(handle, key.bytes(), value.bytes())
                    }
                    if (referenceHandle != null) {
                        writeBatch.put(referenceHandle, key.bytes(), value.metadata())
                    }
                }

                db.write(writeOptions, writeBatch)
                return true
            }
        }
    }

    @Throws(Exception::class)
    override fun clear(column: Class<*>) {
        log.info("Deleting: {} entries", column.simpleName)
        flushHandle(classTreeMap!![column]!!)
    }

    @Throws(Exception::class)
    override fun clearMetadata(column: Class<*>) {
        log.info("Deleting: {} metadata", column.simpleName)
        flushHandle(metadataReference!![column]!!)
    }

    @Throws(RocksDBException::class)
    private fun flushHandle(handle: ColumnFamilyHandle) {
        val itemsToDelete = ArrayList<ByteArray>()
        db.newIterator(handle).use { iterator ->

            iterator.seekToLast()
            while (iterator.isValid) {
                itemsToDelete.add(iterator.key())
                iterator.prev()
            }
        }
        if (itemsToDelete.size > 0) {
            log.info("Amount to delete: " + itemsToDelete.size)
        }
        var counter = 0
        for (itemToDelete in itemsToDelete) {
            if (++counter % 10000 == 0) {
                log.info("Deleted: {}", counter)
            }
            db.delete(handle, itemToDelete)
        }
    }

    @Throws(Exception::class)
    override fun update(thing: Persistable, index: Indexable, item: String): Boolean {
        val referenceHandle = metadataReference!![thing.javaClass]
        if (referenceHandle != null) {
            db.put(referenceHandle, index.bytes(), thing.metadata())
        }
        return false
    }

    private fun initDB(path: String, logPath: String) {
        try {
            try {
                RocksDB.loadLibrary()
            } catch (e: Exception) {
                if (SystemUtils.IS_OS_WINDOWS) {
                    log.error("Error loading RocksDB library. Please ensure that " +
                            "Microsoft Visual C++ 2015 Redistributable Update 3 " +
                            "is installed and updated")
                }
                throw e
            }

            val pathToLogDir = Paths.get(logPath).toFile()
            if (!pathToLogDir.exists() || !pathToLogDir.isDirectory) {
                val success = pathToLogDir.mkdir()
                if (!success) {
                    log.warn("Unable to make directory: {}", pathToLogDir)
                }
            }

            val numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2)
            RocksEnv.getDefault()
                    .setBackgroundThreads(numThreads, RocksEnv.FLUSH_POOL)
                    .setBackgroundThreads(numThreads, RocksEnv.COMPACTION_POOL)

            options = DBOptions()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true)
                    .setDbLogDir(logPath)
                    .setMaxLogFileSize(SizeUnit.MB)
                    .setMaxManifestFileSize(SizeUnit.MB)
                    .setMaxOpenFiles(10000)
                    .setMaxBackgroundCompactions(1)

            options.setMaxSubcompactions(Runtime.getRuntime().availableProcessors())

            bloomFilter = BloomFilter(BLOOM_FILTER_BITS_PER_KEY)

            val blockBasedTableConfig = BlockBasedTableConfig().setFilter(bloomFilter)
            blockBasedTableConfig
                    .setFilter(bloomFilter)
                    .setCacheNumShardBits(2)
                    .setBlockSizeDeviation(10)
                    .setBlockRestartInterval(16)
                    .setBlockCacheSize(cacheSize * SizeUnit.KB)
                    .setBlockCacheCompressedNumShardBits(10)
                    .setBlockCacheCompressedSize(32 * SizeUnit.KB)

            options.setAllowConcurrentMemtableWrite(true)

            val mergeOperator = StringAppendOperator()
            val columnFamilyOptions = ColumnFamilyOptions()
                    .setMergeOperator(mergeOperator)
                    .setTableFormatConfig(blockBasedTableConfig)
                    .setMaxWriteBufferNumber(2)
                    .setWriteBufferSize(2 * SizeUnit.MB)

            val columnFamilyDescriptors = ArrayList<ColumnFamilyDescriptor>()
            for (name in columnFamilyNames) {
                columnFamilyDescriptors.add(ColumnFamilyDescriptor(name.toByteArray(), columnFamilyOptions))
            }

            db = RocksDB.open(options, path, columnFamilyDescriptors, columnFamilyHandles)
            db.enableFileDeletions(true)

            fillModelColumnHandles()

        } catch (e: Exception) {
            log.error("Error while initializing RocksDb", e)
            IotaIOUtils.closeQuietly(db)
        }

    }

    @Throws(Exception::class)
    private fun fillModelColumnHandles() {
        var i = 0
        transactionHandle = columnFamilyHandles[++i]
        transactionMetadataHandle = columnFamilyHandles[++i]
        milestoneHandle = columnFamilyHandles[++i]
        stateDiffHandle = columnFamilyHandles[++i]
        addressHandle = columnFamilyHandles[++i]
        approveeHandle = columnFamilyHandles[++i]
        bundleHandle = columnFamilyHandles[++i]
        obsoleteTagHandle = columnFamilyHandles[++i]
        tagHandle = columnFamilyHandles[++i]

        while (++i < columnFamilyHandles.size) {
            db.dropColumnFamily(columnFamilyHandles[i])
        }
    }

    companion object {

        private val log = LoggerFactory.getLogger(RocksDBPersistenceProvider::class.java)
        private val BLOOM_FILTER_BITS_PER_KEY = 10

        private val PAIR_OF_NULLS = Pair<Indexable?, Persistable?>(null, null)

        /**
         * @param value What we are looking for.
         * @param key   The bytes we are searching in.
         * @return true If the `key` starts with the `value`.
         */
        private fun keyStartsWithValue(value: ByteArray, key: ByteArray?): Boolean {
            if (key == null || key.size < value.size) {
                return false
            }
            for (n in value.indices) {
                if (value[n] != key[n]) {
                    return false
                }
            }
            return true
        }
    }
}