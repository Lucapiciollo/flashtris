package com.lucapiciollo.filetto;

/**
 * Lifecycle of a hosted {@link TournamentEngine} table for one session.
 * Transitions are one-way: OPEN -&gt; CLOSED -&gt; FINISHED. Once CLOSED or
 * FINISHED, the table can never go back to OPEN.
 */
public enum TableState {
    /** Accepting new guest connections/enrollment. */
    OPEN,

    /**
     * Enrollment is closed: no new guest can be admitted, but the tournament
     * continues with whoever is already connected (queued or active). Already
     * admitted participants are never disconnected as a result of closing
     * enrollment.
     */
    CLOSED,

    /**
     * The tournament is over: a champion has been declared (or everyone left
     * after closure). No further moves, rotations or admissions are allowed.
     */
    FINISHED
}
