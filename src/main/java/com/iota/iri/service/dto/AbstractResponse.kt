package com.iota.iri.service.dto

import org.apache.commons.lang3.builder.EqualsBuilder
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

abstract class AbstractResponse {

    var duration: Int? = null

    private class Emptyness : AbstractResponse()

    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE)
    }

    override fun hashCode(): Int {
        return HashCodeBuilder.reflectionHashCode(this, false)
    }

    override fun equals(obj: Any?): Boolean {
        return EqualsBuilder.reflectionEquals(this, obj, false)
    }

    companion object {
        fun createEmptyResponse(): AbstractResponse {
            return Emptyness()
        }
    }
}
