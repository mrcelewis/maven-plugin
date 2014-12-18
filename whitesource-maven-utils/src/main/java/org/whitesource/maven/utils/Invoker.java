package org.whitesource.maven.utils;

import java.lang.reflect.InvocationTargetException;

/**
 * Author: Itai Marko
 */
public final class Invoker {

    public static Object invoke(Object object, String method) {
        return invoke(object.getClass(), object, method);
    }

    public static Object invoke(Class<?> objectClazz, Object object, String method) {
        try {
            return objectClazz.getMethod(method).invoke( object );
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static Object invoke(Object object, String method, Class<?> clazz, Object arg) {
        final Class<?> objectClazz = object.getClass();
        try {
            return objectClazz.getMethod(method, clazz).invoke(object, arg);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    // prevent instantiation
    private Invoker() {}
}
