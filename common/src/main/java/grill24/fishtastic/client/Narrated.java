package grill24.fishtastic.client;

/** A UI element that can describe itself to the narrator and the keyboard-focus ring. */
public interface Narrated {
    /** Spoken description, or null if the element carries nothing worth reading. */
    String narration();
}
