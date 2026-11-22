package com.soulrend;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

/** The sidebar consumes a server-authorized board and strictly public standings. */
public class BingoBoardResponseTest {
    @Test public void readsVisibleProgressAndPublicStandings() {
        String json = "{\"captain\":\"Captain\",\"progress\":{\"visible\":2,\"completed\":1},"
            + "\"standings\":[{\"rank\":1,\"team\":\"Team A\",\"points\":7,\"completed\":3}],"
            + "\"tiles\":[{\"name\":\"Vorkath\",\"status\":\"approved\",\"credited\":true}]}";
        BingoApiClient.TeamBoard board = new Gson().fromJson(json, BingoApiClient.TeamBoard.class);
        assertEquals("Captain", board.captain);
        assertEquals(2, board.progress.visible);
        assertEquals(1, board.progress.completed);
        assertEquals(1, board.standings.size());
        assertEquals(7, board.standings.get(0).points);
        assertEquals("Team A", board.standings.get(0).team);
        assertEquals(1, board.tiles.size());
        assertEquals("Vorkath", board.tiles.get(0).name);
    }

    @Test public void olderBoardResponseRemainsCompatible() {
        BingoApiClient.TeamBoard board = new Gson().fromJson("{\"tiles\":[]}", BingoApiClient.TeamBoard.class);
        assertNull(board.progress);
        assertNull(board.standings);
        assertNotNull(board.tiles);
    }
}
