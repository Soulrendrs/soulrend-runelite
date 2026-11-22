package com.soulrend;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class BoardTileTest {
    @Test public void hiddenTileIgnoresMetadata() {
        BingoApiClient.BoardTile tile = new com.google.gson.Gson().fromJson("{\"status\":\"hidden\",\"name\":\"Secret\"}", BingoApiClient.BoardTile.class);
        assertEquals("Hidden tile", tile.label());
    }
}
