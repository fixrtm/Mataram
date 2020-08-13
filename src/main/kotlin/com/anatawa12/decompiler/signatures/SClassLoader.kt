package com.anatawa12.decompiler.signatures

import java.util.concurrent.ConcurrentHashMap

abstract class SClassLoader(
    internal val environment: SClassLoaderEnvironment,
    val parent: SClassLoader? = environment.systemClassLoader
) {
    protected fun defineClass(name: String, b: ByteArray): SClass {
        val sClass = SClass(environment, this.takeUnless { it == environment.boostrapLoaderImpl }, b)
        loadedClasses[name] = sClass
        return sClass
    }

    protected open fun findClass(name: String): SClass? {
        return null
    }

    private val loadedClasses = ConcurrentHashMap<String, SClass>()
    protected open fun findLoadedClass(name: String): SClass? {
        return loadedClasses[name]
    }

    @Synchronized
    open fun loadClassOrNull(name: String): SClass? {
        val loaded = findLoadedClass(name)
        if (loaded != null) return loaded
        val parentLoad = (parent ?: environment.boostrapLoaderImpl).loadClassOrNull(name)
        if (parentLoad != null) return parentLoad
        return findClass(name)
    }

    /**
     * @throws SClassNotFoundException if not found
     */
    fun loadClass(name: String): SClass = loadClassOrNull(name) ?: throw SClassNotFoundException(name)
}

/*
 */
