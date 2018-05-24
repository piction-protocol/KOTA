package com.iota.iri.conf

import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Created by paul on 4/15/17.
 */
class ConfigurationTest {

    var confFolder = TemporaryFolder()
    lateinit var iniFile: File

    @BeforeClass
    @Throws(IOException::class)
    fun setupClas() {
        confFolder.create()
        iniFile = confFolder.newFile()
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {

    }

    @Test
    @Throws(Exception::class)
    fun getIniValue() {

    }

    @Test
    @Throws(Exception::class)
    fun allSettings() {

    }

    @Test
    @Throws(Exception::class)
    fun put() {

    }

    @Test
    @Throws(Exception::class)
    fun put1() {

    }

    @Test
    @Throws(Exception::class)
    fun floating() {

    }

    @Test
    @Throws(Exception::class)
    fun doubling() {

    }

    @Test
    @Throws(Exception::class)
    fun string() {

    }

    @Test
    @Throws(Exception::class)
    fun integer() {

    }

    @Test
    @Throws(Exception::class)
    fun booling() {

    }
}