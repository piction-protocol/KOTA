package com.iota.iri.service

class ValidationException : Exception {

    /**
     * Initializes a new instance of the ValidationException.
     */
    constructor() : super("Invalid parameters are passed")

    /**
     * Initializes a new instance of the ValidationException with the specified detail message.
     */
    constructor(msg: String) : super(msg)
}
