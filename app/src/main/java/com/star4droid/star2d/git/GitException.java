package com.star4droid.star2d.git;

/** Any failure of a git / GitHub operation, with a coarse kind so the UI can offer the right next step. */
public class GitException extends Exception {
    public enum Kind {AUTH, NETWORK, REJECTED, CONFLICT, NOT_FOUND, DIRTY, OTHER}

    public final Kind kind;

    public GitException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public GitException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }
}
