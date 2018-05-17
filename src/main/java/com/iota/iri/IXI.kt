package com.iota.iri

import com.google.gson.GsonBuilder
import com.iota.iri.service.CallableRequest
import com.iota.iri.service.dto.AbstractResponse
import com.sun.jmx.mbeanserver.Util.cast
import com.sun.nio.file.SensitivityWatchEventModifier
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.Reader
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.regex.Pattern
import javax.script.ScriptEngineManager
import javax.script.ScriptException

class IXI {

    private val gson = GsonBuilder().create()
    private val scriptEngine = ScriptEngineManager().getEngineByName("JavaScript")
    private val ixiAPI = HashMap<String, Map<String, CallableRequest<AbstractResponse>>>()
    private val ixiLifetime = HashMap<String, Map<String, Runnable>>()
    private val watchKeys = HashMap<WatchKey, Path>()
    private val loadedLastTime = HashMap<Path, Long>()

    private var watcher: WatchService? = null
    private var dirWatchThread: Thread? = null
    private var rootPath: Path? = null

    private var shutdown = false
    private val iota: Iota?

    constructor() {
        iota = null
    }

    constructor(iota: Iota) {
        this.iota = iota
    }

    @Throws(Exception::class)
    fun init(rootDir: String) {
        if (rootDir.isNotEmpty()) {
            watcher = FileSystems.getDefault().newWatchService()
            this.rootPath = Paths.get(rootDir)
            if (this.rootPath!!.toFile().exists() || this.rootPath!!.toFile().mkdir()) {
                registerRecursive(this.rootPath!!)
                dirWatchThread = Thread(Runnable { this.processWatchEvents() })
                dirWatchThread!!.start()
            }
        }
    }

    @Throws(IOException::class)
    private fun registerRecursive(root: Path) {
        Files.walkFileTree(root, EnumSet.allOf(FileVisitOption::class.java), MAX_TREE_DEPTH, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun preVisitDirectory(modulePath: Path, attrs: BasicFileAttributes): FileVisitResult {
                watch(modulePath)
                if (modulePath !== rootPath) {
                    loadModule(modulePath)
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun processWatchEvents() {
        while (!shutdown) {
            var key: WatchKey? = null
            try {
                key = watcher!!.poll(1000, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                log.error("Watcher interrupted: ", e)
            }

            if (key == null) {
                continue
            }
            val finalKey = key
            key.pollEvents().forEach { watchEvent ->
                val pathEvent = cast<WatchEvent<Path>>(watchEvent)
                val ixiEvent = IxiEvent.fromName(watchEvent.kind().name())
                val watchedPath = watchKeys[finalKey]
                if (watchedPath != null) {
                    handleModulePathEvent(watchedPath, ixiEvent, watchedPath.resolve(pathEvent.context()))
                }
            }
            key.reset()
        }
    }

    private fun getModuleName(modulePath: Path, checkIfIsDir: Boolean): String {
        return rootPath!!.relativize(if (!checkIfIsDir || Files.isDirectory(modulePath)) modulePath else modulePath.parent).toString()
    }

    private fun getRealPath(currentPath: Path): Path {
        return if (Files.isDirectory(currentPath.parent) && currentPath.parent != rootPath) {
            currentPath.parent
        } else {
            currentPath
        }
    }

    private fun handleModulePathEvent(watchedPath: Path, ixiEvent: IxiEvent, changedPath: Path) {
        if (watchedPath !== rootPath && Files.isDirectory(changedPath)) { // we are only interested in dir changes in tree depth level 2
            return
        }
        handlePathEvent(ixiEvent, changedPath)
    }

    private fun handlePathEvent(ixiEvent: IxiEvent, changedPath: Path) {
        when (ixiEvent) {
            IxiEvent.CREATE_MODULE -> if (checkOs() == OsVariants.Unix) {
                watch(changedPath)
                loadModule(changedPath)
            }
            IxiEvent.MODIFY_MODULE -> {
                val lastModification = loadedLastTime[getRealPath(changedPath)]
                if (lastModification == null || Instant.now().toEpochMilli() - lastModification > 50L) {
                    if (ixiLifetime.containsKey(getModuleName(changedPath, true))) {
                        unloadModule(changedPath)
                    }
                    loadedLastTime.put(getRealPath(changedPath), Instant.now().toEpochMilli())
                    loadModule(getRealPath(changedPath))
                }
            }
            IxiEvent.DELETE_MODULE -> {
                val realPath = getRealPath(changedPath)
                unwatch(realPath)
                if (ixiLifetime.containsKey(getModuleName(realPath, false))) {
                    unloadModule(changedPath)
                }
            }
        }
    }

    private fun watch(dir: Path) {
        try {
            val watchKey = dir.register(watcher, arrayOf<WatchEvent.Kind<*>>(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), SensitivityWatchEventModifier.HIGH)
            watchKeys.put(watchKey, dir)
        } catch (e: IOException) {
            log.error("Could not create watcher for path '$dir'.")
        }

    }

    private fun unwatch(dir: Path) {
        // TODO: Get watchkey for 'dir' in an optimized way
        val dirKey = watchKeys.keys.stream().filter { watchKey -> watchKeys[watchKey] == dir }.findFirst()
        if (dirKey.isPresent) {
            watchKeys.remove(dirKey.get())
            dirKey.get().cancel()
        }
    }

    private fun getPackagePath(modulePath: Path): Path {
        return modulePath.resolve("package.json")
    }

    fun processCommand(command: String, request: Map<String, Any>): AbstractResponse? {
        val pattern = Pattern.compile("^(.*)\\.(.*)$")
        val matcher = pattern.matcher(command)

        if (matcher.find()) {
            val ixiMap = ixiAPI[matcher.group(1)]
            if (ixiMap != null) {
                return ixiMap[matcher.group(2)]?.call(request)
            }
        }
        return null
    }

    private fun loadModule(modulePath: Path) {
        log.info("Searching: " + modulePath)
        val packageJsonPath = getPackagePath(modulePath)
        if (!Files.exists(packageJsonPath)) {
            log.info("No package.json found in " + modulePath)
            return
        }
        val packageJson: Map<*, *>?
        val packageJsonReader: Reader
        try {
            packageJsonReader = FileReader(packageJsonPath.toFile())
            packageJson = gson.fromJson(packageJsonReader, Map::class.java)
        } catch (e: FileNotFoundException) {
            log.error("Could not load " + packageJsonPath.toString())
            return
        }

        try {
            packageJsonReader.close()
        } catch (e: IOException) {
            log.error("Could not close file " + packageJsonPath.toString())
        }

        if (packageJson != null && packageJson["main"] != null) {
            log.info("Loading module: " + getModuleName(modulePath, true))
            val pathToMain = Paths.get(modulePath.toString(), packageJson["main"] as String)
            attach(pathToMain, getModuleName(modulePath, true))
        } else {
            log.info("No start script found")
        }
    }

    private fun unloadModule(moduleNamePath: Path) {
        log.debug("Unloading module: " + moduleNamePath)
        val realPath = getRealPath(moduleNamePath)
        val moduleName = getModuleName(realPath, false)
        detach(moduleName)
        ixiAPI.remove(moduleName)
    }

    private fun attach(pathToMain: Path, moduleName: String) {
        val ixiModuleReader: Reader
        try {
            ixiModuleReader = FileReader(pathToMain.toFile())
        } catch (e: FileNotFoundException) {
            log.error("Could not load " + pathToMain)
            return
        }

        log.info("Starting script: " + pathToMain)
        val ixiMap = HashMap<String, CallableRequest<AbstractResponse>>()
        val startStop = HashMap<String, Runnable>()

        val bindings = scriptEngine.createBindings()
        bindings.put("API", ixiMap)
        bindings.put("IXICycle", startStop)
        bindings.put("IOTA", iota)

        ixiAPI.put(moduleName, ixiMap)
        ixiLifetime.put(moduleName, startStop)
        try {
            scriptEngine.eval(ixiModuleReader, bindings)
        } catch (e: ScriptException) {
            log.error("Script error")
        }

        try {
            ixiModuleReader.close()
        } catch (e: IOException) {
            log.error("Could not close " + pathToMain)
        }

    }

    private fun detach(moduleName: String) {
        val ixiMap = ixiLifetime[moduleName]
        if (ixiMap != null) {
            val stop = ixiMap["shutdown"]
            stop?.run()
        }
        ixiLifetime.remove(moduleName)
    }

    @Throws(InterruptedException::class, IOException::class)
    fun shutdown() {
        if (dirWatchThread != null) {
            shutdown = true
            dirWatchThread!!.join()
            ixiAPI.keys.forEach(Consumer<String> { this.detach(it) })
            ixiAPI.clear()
            ixiLifetime.clear()
        }
    }

    companion object {

        private val log = LoggerFactory.getLogger(IXI::class.java)
        private val MAX_TREE_DEPTH = 2

        private fun checkOs(): OsVariants {
            val os = System.getProperty("os.name")
            return if (os.startsWith("Windows")) {
                OsVariants.Windows
            } else {
                OsVariants.Unix
            }
        }
    }
}
