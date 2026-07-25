package com.cleanroommc.relauncher.util.enums;

import java.util.Collection;
import java.util.EnumSet;

/**
 * JVM flags the relauncher manages on the user's behalf.
 * <p>
 * Each constant carries its own applicability rules, and {@link #render} walks every constant.
 * So a new entry only needs adding here: it is picked up by the settings checkboxes, the generated
 * argument string, and the defaults used on a fresh install, with no other file to touch.
 */
public enum ArgsEnum {

    /** Implicit, and only needed before Java 25 made the experimental options below it standard. */
    UnlockExperimentalOptions(
            "-XX:+UnlockExperimentalVMOptions",
            "",
            "",
            false,
            Java.ANY,
            25
    ),
    AlwaysPreTouch(
            "-XX:+AlwaysPreTouch",
            "Always Pre-Touch",
            "Initializes the heap at the very start to avoid expansion after-the-fact.",
            false
    ),
    CompactObjectHeaders(
            "-XX:+UseCompactObjectHeaders",
            "Compact Object Headers",
            "Reduces memory overhead (Available from Java 24 onwards).",
            true,
            24,
            Java.ANY
    ),
    ZGC(
            "-XX:+UseZGC",
            "Z Garbage Collector",
            "Low-latency GC intended for stronger CPUs, more memory needs to be allocated.",
            false
    );

    static final class Java {

        static final int ANY = 0;

        private Java() { }

    }

    /**
     * Renders the managed portion of an argument string.
     * Every implicit flag that applies, plus every selected flag that applies, in declaration order.
     *
     * @param selected     the flags the user has ticked
     * @param majorVersion the target Java major version, or null if unknown
     */
    public static String render(Collection<ArgsEnum> selected, Integer majorVersion) {
        StringBuilder builder = new StringBuilder();
        for (ArgsEnum arg : values()) {
            boolean wanted = !arg.isUserSelectable() || (selected != null && selected.contains(arg));
            if (wanted && arg.appliesTo(majorVersion)) {
                builder.append(arg.getArg()).append(' ');
            }
        }
        return builder.toString();
    }

    /** The flags ticked out of the box, for a config that has no arguments saved yet. */
    public static EnumSet<ArgsEnum> defaults() {
        EnumSet<ArgsEnum> defaults = EnumSet.noneOf(ArgsEnum.class);
        for (ArgsEnum arg : values()) {
            if (arg.isUserSelectable() && arg.isSelectedByDefault()) {
                defaults.add(arg);
            }
        }
        return defaults;
    }

    private final String arg, title, description;
    private final boolean selectedByDefault;
    private final int minimumJava, belowJava;

    ArgsEnum(String arg, String title, String description, boolean selectedByDefault) {
        this(arg, title, description, selectedByDefault, Java.ANY, Java.ANY);
    }

    /**
     * @param minimumJava lowest Java major this flag exists on, or {@link Java#ANY}
     * @param belowJava   first Java major this flag is no longer needed on, or {@link Java#ANY}
     */
    ArgsEnum(String arg, String title, String description, boolean selectedByDefault, int minimumJava, int belowJava) {
        this.arg = arg;
        this.title = title;
        this.description = description;
        this.selectedByDefault = selectedByDefault;
        this.minimumJava = minimumJava;
        this.belowJava = belowJava;
    }

    public String getArg() {
        return arg;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSelectedByDefault() {
        return selectedByDefault;
    }

    /**
     * Whether the user picks this flag themselves.
     * Implicit flags carry no title and are added automatically whenever they {@link #appliesTo apply}.
     */
    public boolean isUserSelectable() {
        return !title.isEmpty();
    }

    /**
     * Whether this flag is valid on the given Java major version.
     *
     * @param majorVersion the target runtime, or null when it could not be determined.
     */
    public boolean appliesTo(Integer majorVersion) {
        if (majorVersion == null) {
            return belowJava == Java.ANY;
        }
        if (minimumJava != Java.ANY && majorVersion < minimumJava) {
            return false;
        }
        return belowJava == Java.ANY || majorVersion < belowJava;
    }

}
