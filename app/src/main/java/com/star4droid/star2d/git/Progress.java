package com.star4droid.star2d.git;

/** Progress of a long git operation; called on the worker thread. */
public interface Progress {
    Progress NONE = new Progress() {
        public void update(String task, int done, int total) {}
        public boolean cancelled() { return false; }
    };

    /** total may be 0 when unknown. */
    void update(String task, int done, int total);

    boolean cancelled();
}
