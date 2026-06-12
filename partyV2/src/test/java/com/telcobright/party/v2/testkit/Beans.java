package com.telcobright.party.v2.testkit;

import java.lang.reflect.Field;

/** Assigns @Inject fields directly — unit tests wire fakes without a CDI container. */
public final class Beans {

    private Beans() {}

    public static <T> T set(T target, String field, Object value) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return target;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("no field '" + field + "' on " + target.getClass());
    }
}
