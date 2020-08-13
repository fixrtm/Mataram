package com.anatawa12.decompiler.signatures

import java.io.IOException
import java.net.URL

open class URLSClassLoader : SClassLoader {
    val urls: Iterable<URL>

    constructor(environment: SClassLoaderEnvironment, parent: SClassLoader, urls: Iterable<URL>) : super(
        environment,
        parent
    ) {
        this.urls = urls
    }

    constructor(environment: SClassLoaderEnvironment, urls: Iterable<URL>) : super(environment,) {
        this.urls = urls
    }

    override fun findClass(name: String): SClass? {
        val path = name.replace('.', '/') + ".class"
        for (url in urls) {
            try {
                return defineClass(name, URL(url.toString() + path).openStream().readBytes())
            } catch (e: IOException) {
                // ignore
            }
        }
        return null
    }
}
