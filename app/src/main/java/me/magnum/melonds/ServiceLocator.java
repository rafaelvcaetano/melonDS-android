package me.magnum.melonds;

import java.util.HashMap;

public final class ServiceLocator {
    private interface ServiceResolver<T> {
        T resolveService();
    }

    private static class SingletonServiceResolver<T> implements ServiceResolver<T> {
        private final T instance;

        public SingletonServiceResolver(T instance) {
            this.instance = instance;
        }

        @Override
        public T resolveService() {
            return this.instance;
        }
    }

    private static final HashMap<Class<?>, ServiceResolver<?>> serviceResolverMapper = new HashMap<>();

    private ServiceLocator() {
    }

    public static <T> void bindSingleton(T instance) {
        serviceResolverMapper.put(instance.getClass(), new SingletonServiceResolver<>(instance));
    }

    public static <T, U extends T> void bindSingleton(Class<T> tClass, U instance) {
        serviceResolverMapper.put(tClass, new SingletonServiceResolver<T>(instance));
    }

    public static <T> T get(Class<T> tClass) {
        ServiceResolver<T> resolver = (ServiceResolver<T>) serviceResolverMapper.get(tClass);
        if (resolver == null)
            throw new NullPointerException("Service of type " + tClass.getName() + " cannot be found");

        return resolver.resolveService();
    }
}
