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
     * Orders comparable modifiers before other modifiers, and comparable modifiers by the
     * fully qualified name of the class.
     */
    Comparator<OpenModifier> COMPARATOR = (a, b) -> {
        if (a == null || b == null) {
            return 0;
        }
        if (a.getClass() == b.getClass() && Comparable.class.isAssignableFrom(a.getClass())) {
            return Util.doCompare(a, b);
        }
        boolean aComp = a instanceof Comparable;
        boolean bComp = b instanceof Comparable;
        if (aComp && bComp) {
            return a.getClass().getName().compareTo(b.getClass().getName());
        }
        if (aComp /* && !bComp implied */) {
            return 1;
        }
        if (bComp /* && !aComp implied */) {
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
    OpenModifier UTILITY = Cascade.UTILITY;

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
    
    enum Cascade implements OpenModifier {
        UTILITY;
        
        @Override
        public String toString() {
            return name().toLowerCase(java.util.Locale.US);
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
