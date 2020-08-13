package com.anatawa12.decompiler.processor

import com.anatawa12.decompiler.signatures.SClassLoaderEnvironment
import com.anatawa12.decompiler.signatures.URLSClassLoader

class ProcessorContext(
    val env: SClassLoaderEnvironment,
    val scl: URLSClassLoader,
) {
}
