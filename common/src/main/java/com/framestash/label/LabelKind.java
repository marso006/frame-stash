package com.framestash.label;

/** What a frame says about its container. */
public enum LabelKind {
    /** Normal item frame: first choice for the item. */
    PRIMARY,
    /** Glow item frame: used only once every primary container for the item is full. */
    OVERFLOW,
    /** Frame holding the catch-all item: takes leftovers. */
    CATCH_ALL
}
