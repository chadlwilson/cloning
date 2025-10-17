package com.rits.cloning;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link Accessor Functions} for {@link #ACCESSOR accessing} {@link Field}s.
 *
 * <p>The access method can be specified via the {@code com.rits.cloning.Fields.accessor} system-property setting to:
 * <ul>
 *     <li>{@code auto} - (default) auto-select best available accessor</li>
 *     <li>{@code handles} - fast, but requires {@code --add-opens} to access JDK internal fields; falls back on {@code reflection} to set {@code final}s</li>
 *     <li>{@code reflection} - legacy approach, but requires {@code --add-opens} to access JDK internal fields</li>
 * </ul>
 *
 * <p>These APIs are considered low level and is intentionally restricted to package-private access. Mismatching objects
 * and fields, or {@link Accessor#getCookie cookies} can lead to undefined behavior.
 *
 * @author mark.falco
 * @since December 2024
 */
class Fields {
    /**
     * Available {@link Accessor} types.
     */
    private enum AccessorType {
        AUTO,
        HANDLES,
        REFLECTION;

        /**
         * @return the {@link Accessor} of the specified {@link AccessorType} or closest available match.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Accessor<Object> resolve(AccessorType type) {
            switch (type) {
                case AUTO:
                case HANDLES:
                    return (Accessor) VarHandleAccessor.INSTANCE;
                case REFLECTION:
                    return (Accessor) ReflectionAccessor.INSTANCE;
                default:
                    throw new IllegalArgumentException("Unknown accessor type: " + type);
            }
        }
    }

    /**
     * The configured {@link Accessor} for accessing fields.
     */
    static final Accessor<Object> ACCESSOR = AccessorType.resolve(AccessorType.valueOf(System.getProperty(
            Fields.class.getName() + ".accessor", AccessorType.HANDLES.name()).trim().toUpperCase()));

    /**
     * Blocked constructor.
     */
    private Fields() {}

    /**
     * Interface for accessing fields of objects.
     *
     * @param <C> the cookie type for this accessor
     */
    interface Accessor<C> {
        /**
         * Return the cookie for a given field.
         *
         * @param field the field to get a cookie for
         * @return the cookie
         */
        C getCookie(Field field);

        /**
         * Return the specified field from the source object.
         *
         * @param field the field to get
         * @param cookie the field {@link #getCookie cookie}
         * @param src the object to get the field value from
         * @return the field value
         */
        Object get(Field field, C cookie, Object src) throws IllegalAccessException;

        /**
         * Set the specified field in the destination object.
         *
         * @param field the field to set
         * @param cookie the field {@link #getCookie cookie}
         * @param dst the object to set the field in
         * @param value the new value for the field
         */
        void set(Field field, C cookie, Object dst, Object value) throws IllegalAccessException;

        /**
         * Copy (shallow clone) the specified field from the source to destination object.
         *
         * @param field the field to copy
         * @param cookie the field {@link #getCookie cookie}
         * @param src the source object
         * @param dst the destination object
         */
        void copy(Field field, C cookie, Object src, Object dst) throws IllegalAccessException;
    }

    @FunctionalInterface
    interface ThrowingSupplier<R> {
        R apply() throws IllegalAccessException;
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws IllegalAccessException;
    }

    /**
     * Legacy reflection based {@link Accessor}.
     */
    private static class ReflectionAccessor implements Accessor<Field> {
        private static final ReflectionAccessor INSTANCE = new ReflectionAccessor();

        @Override
        public Field getCookie(Field field) {
            handleWithContext(field, field::trySetAccessible);
            return field;
        }

        @Override
        public Object get(Field field, Field cookie, Object src) throws IllegalAccessException {
            return cookie.get(src);
        }

        @Override
        public void set(Field field, Field cookie, Object dst, Object value) throws IllegalAccessException {
            cookie.set(dst, value);
        }

        @Override
        public void copy(Field field, Field cookie, Object src, Object dst) throws IllegalAccessException {
            Class<?> t = field.getType();
            if      (!t.isPrimitive()  ) cookie.set       (dst, cookie.get       (src));
            else if (t == int.class    ) cookie.setInt    (dst, cookie.getInt    (src));
            else if (t == long.class   ) cookie.setLong   (dst, cookie.getLong   (src));
            else if (t == boolean.class) cookie.setBoolean(dst, cookie.getBoolean(src));
            else if (t == double.class ) cookie.setDouble (dst, cookie.getDouble (src));
            else if (t == float.class  ) cookie.setFloat  (dst, cookie.getFloat  (src));
            else if (t == char.class   ) cookie.setChar   (dst, cookie.getChar   (src));
            else if (t == byte.class   ) cookie.setByte   (dst, cookie.getByte   (src));
            else if (t == short.class  ) cookie.setShort  (dst, cookie.getShort  (src));
            else                         cookie.set       (dst, cookie.get       (src));
        }
    }

    /**
     * {@link VarHandle} implementation of {@link Accessor} avoiding per invocation access checks made with reflection.
     */
    private static class VarHandleAccessor implements Accessor<VarHandle> {
        private static final VarHandleAccessor INSTANCE = new VarHandleAccessor();

        /**
         * Mapping of fields to their {@link VarHandle} for a given class.
         */
        private final ClassValue<Map<Field, VarHandle>> handleByField = new ClassValue<>() {
            /**
             * The {@link MethodHandles.Lookup} used to find {@link VarHandle}s.
             */
            private final MethodHandles.Lookup lookup = MethodHandles.lookup();

            @Override
            protected Map<Field, VarHandle> computeValue(Class<?> clz) {
                Map<Field, VarHandle> map = new HashMap<>();
                try {
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(clz, this.lookup);
                    for (Field f : clz.getDeclaredFields()) {
                        f.trySetAccessible();
                        map.put(f, lookup.unreflectVarHandle(f));
                    }
                } catch (IllegalAccessException e) {
                    throw new CloningException(String.format("No access to class %s from context %s", clz.getName(), lookup.lookupClass().getModule()), e);
                }

                return map;
            }
        };

        @Override
        public VarHandle getCookie(Field field) {
            return handleWithContext(field, () -> handleByField.get(field.getDeclaringClass()).get(field));
        }

        @Override
        public Object get(Field field, VarHandle h, Object src) {
            boolean v = Modifier.isVolatile(field.getModifiers());
            return src == null
                    ? v ? h.getVolatile()    : h.get()
                    : v ? h.getVolatile(src) : h.get(src);
        }

        @Override
        public void set(Field field, VarHandle h, Object dst, Object value) throws IllegalAccessException {
            // note we don't need volatile writes during cloning as dst is not yet visible to other threads
            if (Modifier.isFinal(field.getModifiers())) { // VarHandle can't update finals; fall back on reflection
                ReflectionAccessor.INSTANCE.set(field, ReflectionAccessor.INSTANCE.getCookie(field), dst, value);
            } else if (dst == null) {
                h.set(value);
            } else {
                h.set(dst, value);
            }
        }

        @Override
        public void copy(Field field, VarHandle hand, Object src, Object dst) throws IllegalAccessException {
            int mods = field.getModifiers();
            if (Modifier.isFinal(mods)) { // VarHandle can't update finals; fall back on reflection
                ReflectionAccessor.INSTANCE.copy(field, ReflectionAccessor.INSTANCE.getCookie(field), src, dst);
                return;
            }

            // note: we don't need volatile writes during cloning as dst is not yet visible to other threads; volatile
            // reads are still performed as the source may be visible to other threads
            Class<?> t = field.getType();
            boolean v = Modifier.isVolatile(mods);

            // the seemingly needless casts allow VarHandle to optimize out the autoboxing and its garbage
            if      (!t.isPrimitive()  ) hand.set(dst, v ?           hand.getVolatile(src) :           hand.get(src));
            else if (t == int.class    ) hand.set(dst, v ?     (int) hand.getVolatile(src) :     (int) hand.get(src));
            else if (t == long.class   ) hand.set(dst, v ?    (long) hand.getVolatile(src) :    (long) hand.get(src));
            else if (t == boolean.class) hand.set(dst, v ? (boolean) hand.getVolatile(src) : (boolean) hand.get(src));
            else if (t == double.class ) hand.set(dst, v ?  (double) hand.getVolatile(src) :  (double) hand.get(src));
            else if (t == float.class  ) hand.set(dst, v ?   (float) hand.getVolatile(src) :   (float) hand.get(src));
            else if (t == char.class   ) hand.set(dst, v ?    (char) hand.getVolatile(src) :    (char) hand.get(src));
            else if (t == byte.class   ) hand.set(dst, v ?    (byte) hand.getVolatile(src) :    (byte) hand.get(src));
            else if (t == short.class  ) hand.set(dst, v ?   (short) hand.getVolatile(src) :   (short) hand.get(src));
            else                         hand.set(dst, v ?           hand.getVolatile(src) :           hand.get(src));
        }
    }

    static <T> T handleWithContext(Field field, ThrowingSupplier<T> action) {
        try {
            return action.apply();
        } catch (SecurityException | IllegalArgumentException | ReflectiveOperationException e) {
            throw new CloningException(String.format("No access to field [%s] [%s] within class [%s]", field.getType(), field.getName(), field.getDeclaringClass()), e);
        } catch (CloningException e) {
            throw new CloningException(String.format("No access to field [%s] [%s] within class [%s]", field.getType(), field.getName(), field.getDeclaringClass()), e.getCause());
        }
    }

    static void handleWithContext(Field field, ThrowingRunnable action) {
        try {
            action.run();
        } catch (SecurityException | IllegalArgumentException | ReflectiveOperationException e) {
            throw new CloningException(String.format("No access to field [%s] [%s] within class [%s]", field.getType(), field.getName(), field.getDeclaringClass()), e);
        } catch (CloningException e) {
            throw new CloningException(String.format("No access to field [%s] [%s] within class [%s]", field.getType(), field.getName(), field.getDeclaringClass()), e.getCause());
        }
    }
}