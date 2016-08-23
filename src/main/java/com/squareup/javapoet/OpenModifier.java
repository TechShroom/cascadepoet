package com.squareup.javapoet;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

import javax.lang.model.element.Modifier;

/**
 * Basically {@link Modifier}, but new modifiers can be added by anybody.
 */
public interface OpenModifier {

    /**
     * Orders java modifiers before custom modifiers, and java modifiers by the
     * enum ordering.
     */
    Comparator<OpenModifier> COMPARATOR = (a, b) -> {
        boolean aJava = a instanceof Java;
        boolean bJava = b instanceof Java;
        if (aJava && bJava) {
            return ((Java) a).wrapped.compareTo(((Java) b).wrapped);
        }
        if (aJava) {
            return 1;
        }
        if (bJava) {
            return -1;
        }
        return 0;
    };

    OpenModifier PUBLIC = Java.valueOf(Modifier.PUBLIC);
    OpenModifier PROTECTED = Java.valueOf(Modifier.PROTECTED);
    OpenModifier PRIVATE = Java.valueOf(Modifier.PRIVATE);
    OpenModifier ABSTRACT = Java.valueOf(Modifier.ABSTRACT);
    OpenModifier DEFAULT = Java.valueOf(Modifier.DEFAULT);
    OpenModifier STATIC = Java.valueOf(Modifier.STATIC);
    OpenModifier FINAL = Java.valueOf(Modifier.FINAL);
    OpenModifier TRANSIENT = Java.valueOf(Modifier.TRANSIENT);
    OpenModifier VOLATILE = Java.valueOf(Modifier.VOLATILE);
    OpenModifier SYNCHRONIZED = Java.valueOf(Modifier.SYNCHRONIZED);
    OpenModifier NATIVE = Java.valueOf(Modifier.NATIVE);
    OpenModifier STRICTFP = Java.valueOf(Modifier.STRICTFP);

    final class Java implements OpenModifier {

        private static final Map<Modifier, Java> modifiers;
        static {
            Map<Modifier, Java> map = new EnumMap<>(Modifier.class);
            for (Modifier mod : Modifier.values()) {
                map.put(mod, new Java(mod));
            }
            modifiers = Collections.unmodifiableMap(map);
        }

        public static Java valueOf(Modifier javaMod) {
            return modifiers.get(javaMod);
        }

        private final Modifier wrapped;

        private Java(Modifier wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public String toString() {
            return this.wrapped.toString();
        }

        @Override
        public String name() {
            return this.wrapped.name();
        }

    }

    /**
     * Must return the value in the specified format.
     * 
     * @return {@code name().toLowerCase(java.util.Locale.US)}
     */
    @Override
    String toString();

    String name();

    /**
     * OpenModifiers should use instance equality comparison, since they should
     * also be singletons.
     */
    @Override
    boolean equals(Object obj);

}
