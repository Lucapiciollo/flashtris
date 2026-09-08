package com.lucapiciollo.filetto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Pure JUnit tests for {@link TournamentEngine}. No Android/Robolectric
 * dependency needed: the engine is plain Java by design.
 *
 * Notes on scope: REMATCH_REQUEST gating (valid only in CLASSIC_P2P) is
 * enforced entirely in MainActivity's message routing, since the engine
 * never models the classic 1-vs-1 mode or the rematch protocol message at
 * all -- it is out of scope for these engine-level tests and is instead
 * verified by code review / manual on-device testing (see final summary).
 */
public class TournamentEngineTest {

    private static final int MAX_GUESTS = 5;

    private TournamentEngine newEngineWithHostSeated(int winsToAdvance) {
        TournamentEngine engine = new TournamentEngine(winsToAdvance, MAX_GUESTS);
        engine.seatHostFirst("Host", 'X');
        return engine;
    }

    // ------------------------------------------------------------- FIFO order

    @Test
    public void firstGuestIsSeatedImmediately_othersQueueInFifoOrder_3guests() {
        TournamentEngine engine = newEngineWithHostSeated(2);

        assertEquals(TournamentEngine.AdmitOutcome.SEATED_AS_CHALLENGER, engine.admitGuest("g1", "Alice"));
        assertEquals("g1", engine.seatBId());

        assertEquals(TournamentEngine.AdmitOutcome.QUEUED, engine.admitGuest("g2", "Bob"));
        assertEquals(TournamentEngine.AdmitOutcome.QUEUED, engine.admitGuest("g3", "Carol"));

        assertEquals(1, engine.queuePositionOf("g2"));
        assertEquals(2, engine.queuePositionOf("g3"));
        assertEquals(2, engine.queueLength());
    }

    @Test
    public void fourGuests_queueOrderPreservedAfterRotation() {
        TournamentEngine engine = newEngineWithHostSeated(1);
        engine.admitGuest("g1", "Alice");
        engine.admitGuest("g2", "Bob");
        engine.admitGuest("g3", "Carol");
        engine.admitGuest("g4", "Dan");

        assertEquals("g1", engine.seatBId());
        assertEquals(3, engine.queueLength());

        // Host (seat A) wins the series against g1: g1 eliminated, g2 promoted.
        engine.recordGameResult('X');
        TournamentEngine.RotationOutcome rot = engine.concludeSeriesAndRotate();
        assertEquals("g1", rot.eliminatedId);
        assertEquals("g2", rot.promotedChallengerId);
        assertEquals("g2", engine.seatBId());
        assertEquals(2, engine.queueLength());
        assertEquals(1, engine.queuePositionOf("g3"));
        assertEquals(2, engine.queuePositionOf("g4"));
    }

    @Test
    public void fiveGuests_fillsToCapacity_sixthIsRejected() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        for (int i = 1; i <= 5; i++) {
            assertNotEquals("guest " + i + " should be admitted", TournamentEngine.AdmitOutcome.REJECTED_FULL,
                    engine.admitGuest("g" + i, "Guest" + i));
        }
        assertEquals(6, engine.totalParticipantCount()); // host + 5 guests
        assertEquals(TournamentEngine.AdmitOutcome.REJECTED_FULL, engine.admitGuest("g6", "Overflow"));
    }

    private static void assertNotEquals(String message, Object unexpected, Object actual) {
        assertFalse(message, unexpected.equals(actual));
    }

    // ------------------------------------------------------------ winsToAdvance

    @Test
    public void winsToAdvance1_singleWinDecidesSeries() {
        TournamentEngine engine = newEngineWithHostSeated(1);
        engine.admitGuest("g1", "Alice");
        TournamentEngine.MatchOutcome outcome = engine.recordGameResult(engine.seatASymbol());
        assertTrue(outcome.seriesDecided);
    }

    @Test
    public void winsToAdvance2_needsTwoWins() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "Alice");
        char aSymbol = engine.seatASymbol();
        assertFalse(engine.recordGameResult(aSymbol).seriesDecided);
        assertTrue(engine.recordGameResult(aSymbol).seriesDecided);
    }

    @Test
    public void winsToAdvance5_needsFiveWins() {
        TournamentEngine engine = newEngineWithHostSeated(5);
        engine.admitGuest("g1", "Alice");
        char aSymbol = engine.seatASymbol();
        for (int i = 0; i < 4; i++) {
            assertFalse(engine.recordGameResult(aSymbol).seriesDecided);
        }
        assertTrue(engine.recordGameResult(aSymbol).seriesDecided);
    }

    // -------------------------------------------------------------------- draws

    @Test
    public void draw_doesNotChangeScoreOrDecideSeries() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "Alice");
        TournamentEngine.MatchOutcome outcome = engine.recordGameResult(' ');
        assertFalse(outcome.seriesDecided);
        assertEquals(0, engine.seatAWins());
        assertEquals(0, engine.seatBWins());
    }

    // ------------------------------------------------------------------ closure

    @Test
    public void closeEnrollment_isIrreversible_andRejectsNewGuests() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "Alice");

        assertTrue(engine.closeEnrollment());
        assertEquals(TableState.CLOSED, engine.tableState());
        assertFalse("closing an already-closed table is a no-op", engine.closeEnrollment());

        assertEquals(TournamentEngine.AdmitOutcome.REJECTED_CLOSED, engine.admitGuest("g2", "Bob"));
    }

    @Test
    public void closure_doesNotDisconnectAlreadyAdmittedParticipants() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "Alice");
        engine.admitGuest("g2", "Bob");
        engine.closeEnrollment();

        assertEquals("g1", engine.seatBId());
        assertEquals(1, engine.queueLength());
        assertTrue(engine.isKnownParticipant("g1"));
        assertTrue(engine.isKnownParticipant("g2"));
    }

    @Test
    public void closureWithQueuedPlayers_rotationStillPromotesFromQueue() {
        TournamentEngine engine = newEngineWithHostSeated(1);
        engine.admitGuest("g1", "Alice");
        engine.admitGuest("g2", "Bob");
        engine.closeEnrollment();

        engine.recordGameResult(engine.seatASymbol());
        TournamentEngine.RotationOutcome rot = engine.concludeSeriesAndRotate();
        assertEquals("g2", rot.promotedChallengerId);
        assertFalse(rot.championDeclared);
        assertEquals(TableState.CLOSED, engine.tableState());
    }

    @Test
    public void closureWithOnePlayerLeft_declaresChampion() {
        TournamentEngine engine = newEngineWithHostSeated(1);
        engine.admitGuest("g1", "Alice");
        engine.closeEnrollment();

        engine.recordGameResult(engine.seatASymbol()); // host beats g1, queue empty
        TournamentEngine.RotationOutcome rot = engine.concludeSeriesAndRotate();

        assertTrue(rot.championDeclared);
        assertEquals(TournamentEngine.HOST_ID, rot.championId);
        assertEquals(TableState.FINISHED, engine.tableState());
        assertEquals(TournamentEngine.HOST_ID, engine.championId());
        assertNotNull(engine.finishReason());
    }

    @Test
    public void noChampionWhileTableStillOpen_evenIfAlone() {
        TournamentEngine engine = newEngineWithHostSeated(1);
        engine.admitGuest("g1", "Alice");
        // Table remains OPEN (never closed).
        engine.recordGameResult(engine.seatASymbol());
        TournamentEngine.RotationOutcome rot = engine.concludeSeriesAndRotate();

        assertFalse(rot.championDeclared);
        assertTrue(rot.waitingForChallenger);
        assertEquals(TableState.OPEN, engine.tableState());
        assertFalse(engine.isFinished());
    }

    // -------------------------------------------------------------- disconnection

    @Test
    public void queuedPlayerDisconnect_removedWithoutDisturbingOthersOrder() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "Alice");
        engine.admitGuest("g2", "Bob");
        engine.admitGuest("g3", "Carol");

        TournamentEngine.DisconnectOutcome outcome = engine.disconnect("g2");
        assertTrue(outcome.known);
        assertTrue(outcome.wasQueued);
        assertFalse(outcome.wasActiveSeat);
        assertEquals(1, engine.queueLength());
        assertEquals(1, engine.queuePositionOf("g3"));
    }

    @Test
    public void activePlayerDisconnect_isForfeit_opponentStaysAndIsPromoted() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "Alice");
        engine.admitGuest("g2", "Bob");

        TournamentEngine.DisconnectOutcome outcome = engine.disconnect("g1");
        assertTrue(outcome.wasActiveSeat);
        assertNotNull(outcome.rotation);
        assertEquals("g1", outcome.rotation.eliminatedId);
        assertEquals(TournamentEngine.HOST_ID, outcome.rotation.stayingId);
        assertEquals("g2", outcome.rotation.promotedChallengerId);
        assertEquals("g2", engine.seatBId());
        assertTrue(engine.participant("g1").isEliminated());
    }

    @Test
    public void activePlayerDisconnect_afterClosureWithOnlyOneRemaining_declaresChampion() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "Alice");
        engine.closeEnrollment();

        TournamentEngine.DisconnectOutcome outcome = engine.disconnect("g1");
        assertTrue(outcome.rotation.championDeclared);
        assertEquals(TournamentEngine.HOST_ID, outcome.rotation.championId);
        assertEquals(TableState.FINISHED, engine.tableState());
    }

    @Test
    public void unknownDisconnect_isNoOp() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        TournamentEngine.DisconnectOutcome outcome = engine.disconnect("ghost");
        assertFalse(outcome.known);
    }

    // --------------------------------------------------------------- duplicates

    @Test
    public void duplicateNickname_isIgnoredAndDoesNotRequeue() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "Alice");
        engine.admitGuest("g2", "Bob");

        TournamentEngine.AdmitOutcome outcome = engine.admitGuest("g2", "Bob renamed");
        assertEquals(TournamentEngine.AdmitOutcome.IGNORED_DUPLICATE, outcome);
        assertEquals(1, engine.queueLength()); // still only queued once
        assertEquals("Bob renamed", engine.nicknameOf("g2"));
    }

    @Test
    public void duplicateNicknameSameIdKeyed_notCollidingWithDifferentIdSameNickname() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "SameName");
        engine.admitGuest("g2", "SameName");
        assertEquals(2, engine.totalParticipantCount() - 1); // host + 2 distinct guests despite identical nicknames
        assertTrue(engine.isKnownParticipant("g1"));
        assertTrue(engine.isKnownParticipant("g2"));
    }

    // ---------------------------------------------------------- stale callback guard

    @Test
    public void matchGeneration_incrementsOnEveryRotation_soStaleCallbacksCanBeDetected() {
        TournamentEngine engine = newEngineWithHostSeated(1);
        engine.admitGuest("g1", "Alice");
        int genBeforeMatch = engine.matchGeneration();

        int scheduledGeneration = engine.matchGeneration();
        // Simulate a disconnect racing in before the scheduled postDelayed callback fires.
        engine.disconnect("g1");

        assertTrue("generation must change after any rotation", engine.matchGeneration() > scheduledGeneration);
        assertTrue(engine.matchGeneration() > genBeforeMatch);
    }

    @Test
    public void continueSeriesWithSwappedSymbols_alsoIncrementsGeneration() {
        TournamentEngine engine = newEngineWithHostSeated(2);
        engine.admitGuest("g1", "Alice");
        int gen = engine.matchGeneration();
        char aSymbol = engine.seatASymbol();
        char bSymbol = engine.seatBSymbol();
        engine.recordGameResult(aSymbol); // 1-0, not decided yet
        engine.continueSeriesWithSwappedSymbols();
        assertTrue(engine.matchGeneration() > gen);
        assertEquals(bSymbol, engine.seatASymbol());
        assertEquals(aSymbol, engine.seatBSymbol());
    }

    // -------------------------------------------------------------------- leaderboard

    @Test
    public void leaderboard_sortedBySeriesWinsDesc_thenJoinOrder() {
        TournamentEngine engine = newEngineWithHostSeated(1);
        engine.admitGuest("g1", "Alice");
        engine.admitGuest("g2", "Bob");
        engine.admitGuest("g3", "Carol");

        // Host beats g1 (host now has 1 win), g2 promoted.
        engine.recordGameResult(engine.seatASymbol());
        engine.concludeSeriesAndRotate();
        // Host beats g2 (host now has 2 wins), g3 promoted.
        engine.recordGameResult(engine.seatASymbol());
        engine.concludeSeriesAndRotate();

        java.util.List<TournamentParticipant> board = engine.leaderboardSnapshot();
        assertEquals(TournamentEngine.HOST_ID, board.get(0).id());
        assertEquals(2, board.get(0).seriesWins());
    }

    // ---------------------------------------------------------------- full flow

    @Test
    public void fullSessionFlow_threeGuests_closureAfterSecond_championDeclaredEventually() {
        TournamentEngine engine = newEngineWithHostSeated(1);
        engine.admitGuest("g1", "Alice");
        engine.admitGuest("g2", "Bob");
        engine.closeEnrollment(); // enrollment closed while g2 is still queued; no new guest afterwards
        assertEquals(TournamentEngine.AdmitOutcome.REJECTED_CLOSED, engine.admitGuest("g3", "Carol"));

        // Host beats g1: g2 promoted.
        engine.recordGameResult(engine.seatASymbol());
        TournamentEngine.RotationOutcome rot1 = engine.concludeSeriesAndRotate();
        assertEquals("g2", rot1.promotedChallengerId);
        assertFalse(rot1.championDeclared);

        // Host beats g2: queue empty, table CLOSED -> champion declared.
        engine.recordGameResult(engine.seatASymbol());
        TournamentEngine.RotationOutcome rot2 = engine.concludeSeriesAndRotate();
        assertTrue(rot2.championDeclared);
        assertEquals(TournamentEngine.HOST_ID, rot2.championId);
        assertEquals(TableState.FINISHED, engine.tableState());
    }
}
