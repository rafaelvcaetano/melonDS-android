package me.magnum.melonds

import java.util.*
import kotlin.reflect.KClass

object ServiceLocator {
    private interface ServiceResolver<T> {
        fun resolveService(): T
    }

    private class SingletonServiceResolver<T>(private val instance: T) : ServiceResolver<T> {
        override fun resolveService(): T {
            return instance
        }
    }

    private val serviceResolverMapper = HashMap<KClass<*>, ServiceResolver<*>>()

    fun <T : Any> bindSingleton(instance: T) {
        serviceResolverMapper[instance::class] = SingletonServiceResolver(instance)
    }

    fun <T : Any, U : T> bindSingleton(kClass: KClass<T>, instance: U) {
        serviceResolverMapper[kClass] = SingletonServiceResolver<T>(instance)
    }

    operator fun <T : Any> get(kClass: KClass<T>): T {
        val resolver = serviceResolverMapper[kClass] as ServiceResolver<T>? ?: throw NullPointerException("Service of type " + kClass.simpleName + " cannot be found")
        return resolver.resolveService()
    }
}