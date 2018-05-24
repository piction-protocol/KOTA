package com.iota.iri

import com.iota.iri.service.dto.AbstractResponse
import com.iota.iri.service.dto.IXIResponse
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Created by paul on 1/4/17.
 */
class IXITest {

    internal var ixiDir = TemporaryFolder()
    lateinit var ixi: IXI

    @BeforeClass
    @Throws(Exception::class)
    fun setUp() {
        ixiDir.create()
        ixi = IXI()
        ixi.init(ixiDir.root.absolutePath.toString())
    }

    @AfterClass
    @Throws(Exception::class)
    fun tearDown() {
        ixi.shutdown()
        ixiDir.delete()
    }

    @Test
    @Throws(Exception::class)
    fun init() {
        val response: AbstractResponse
        val ixiResponse: IXIResponse
    }

    @Test
    @Throws(Exception::class)
    fun processCommand() {

    }
}
