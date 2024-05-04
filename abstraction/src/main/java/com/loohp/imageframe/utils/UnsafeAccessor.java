package com.loohp.imageframe.utils;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeAccessor {

    private static final Field unsafeField;
    private static Unsafe unsafe;

    static {
        try {
            unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static Unsafe getUnsafe() {
        if (unsafe != null) {
            return unsafe;
        }
        try {
            unsafeField.setAccessible(true);
            return unsafe = (Unsafe) unsafeField.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
