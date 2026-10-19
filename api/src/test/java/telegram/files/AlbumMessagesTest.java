package telegram.files;

import org.drinkless.tdlib.TdApi;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AlbumMessagesTest {

    private static final long STEP = AlbumMessages.MESSAGE_ID_STEP;

    @Test
    void neighborIdsCoverAFullAlbumOnBothSidesOfTheAnchor() {
        long anchor = 20 * STEP;
        long[] ids = AlbumMessages.neighborIds(anchor);
        assertEquals(19, ids.length);
        assertEquals(anchor - 9 * STEP, ids[0]);
        assertEquals(anchor, ids[9]);
        assertEquals(anchor + 9 * STEP, ids[ids.length - 1]);
    }

    @Test
    void neighborIdsSkipNonPositiveIds() {
        long[] ids = AlbumMessages.neighborIds(2 * STEP);
        assertTrue(ids[0] > 0);
        assertEquals(STEP, ids[0]);
    }

    @Test
    void historyWalkKeepsAlbumSiblingsAndStopsAtTheBoundary() {
        long albumId = 42;
        TdApi.Message anchor = message(10 * STEP, albumId, new TdApi.MessagePhoto());
        Map<Long, TdApi.Message> acc = AlbumMessages.newAlbumMap(anchor);

        TdApi.Message[] newer = new TdApi.Message[]{
                message(13 * STEP, 0, new TdApi.MessageText()),
                message(12 * STEP, albumId, new TdApi.MessageVideo()),
                message(11 * STEP, albumId, new TdApi.MessagePhoto()),
                anchor
        };
        AlbumMessages.Walk newerWalk = AlbumMessages.absorb(albumId, anchor.id, AlbumMessages.Direction.NEWER, newer, acc);
        assertTrue(newerWalk.done());
        assertEquals(12 * STEP, newerWalk.nextEdge());

        TdApi.Message[] older = new TdApi.Message[]{
                anchor,
                message(9 * STEP, albumId, new TdApi.MessageVideo()),
                message(8 * STEP, 7, new TdApi.MessagePhoto())
        };
        AlbumMessages.Walk olderWalk = AlbumMessages.absorb(albumId, anchor.id, AlbumMessages.Direction.OLDER, older, acc);
        assertTrue(olderWalk.done());

        long[] ids = ArraysOf(AlbumMessages.toOldestFirst(acc));
        assertArrayEquals(new long[]{9 * STEP, 10 * STEP, 11 * STEP, 12 * STEP}, ids);
    }

    @Test
    void shortAlbumPageIsNotFinishedSoTheWalkerContinues() {
        long albumId = 5;
        TdApi.Message anchor = message(10 * STEP, albumId, new TdApi.MessagePhoto());
        Map<Long, TdApi.Message> acc = AlbumMessages.newAlbumMap(anchor);
        TdApi.Message[] page = new TdApi.Message[]{
                message(11 * STEP, albumId, new TdApi.MessageVideo()),
                anchor
        };
        AlbumMessages.Walk walk = AlbumMessages.absorb(albumId, anchor.id, AlbumMessages.Direction.NEWER, page, acc);
        assertFalse(walk.done());
        assertEquals(11 * STEP, walk.nextEdge());
    }

    @Test
    void anchorIsKeptWhenSearchDoesNotReturnIt() {
        long albumId = 9;
        TdApi.Message anchor = message(10 * STEP, albumId, new TdApi.MessageVideo());
        Map<Long, TdApi.Message> acc = AlbumMessages.newAlbumMap(anchor);
        AlbumMessages.absorbKnown(albumId, new TdApi.Message[]{
                null,
                message(11 * STEP, albumId, new TdApi.MessagePhoto()),
                message(12 * STEP, 1, new TdApi.MessageDocument())
        }, acc);
        AlbumMessages.absorb(albumId, anchor.id, AlbumMessages.Direction.NEWER, new TdApi.Message[]{anchor}, acc);

        long[] ids = ArraysOf(AlbumMessages.toOldestFirst(acc));
        assertArrayEquals(new long[]{10 * STEP, 11 * STEP}, ids);
    }

    @Test
    void mergePageReplacesOneSearchHitWithEveryMatchingSibling() {
        long albumId = 3;
        TdApi.Message anchor = message(10 * STEP, albumId, new TdApi.MessageVideo());
        TdApi.Message photo = message(11 * STEP, albumId, new TdApi.MessagePhoto());
        TdApi.Message otherVideo = message(9 * STEP, albumId, new TdApi.MessageVideo());
        TdApi.Message standalone = message(20 * STEP, 0, new TdApi.MessageDocument());
        TdApi.Message[] page = new TdApi.Message[]{standalone, anchor, photo};

        Map<Long, TdApi.Message[]> albums = Map.of(albumId, new TdApi.Message[]{otherVideo, anchor, photo});
        TdApi.Message[] merged = AlbumMessages.mergePage(page, albums, new TdApi.SearchMessagesFilterVideo());

        assertArrayEquals(new long[]{standalone.id, otherVideo.id, anchor.id}, ArraysOf(merged));
    }

    @Test
    void mergePageKeepsPhotosAndVideosForTheMediaFilterAndDedupesAlbumHits() {
        long albumId = 3;
        TdApi.Message video = message(10 * STEP, albumId, new TdApi.MessageVideo());
        TdApi.Message photo = message(11 * STEP, albumId, new TdApi.MessagePhoto());
        TdApi.Message[] page = new TdApi.Message[]{video, photo};
        Map<Long, TdApi.Message[]> albums = Map.of(albumId, new TdApi.Message[]{video, photo});

        TdApi.Message[] merged = AlbumMessages.mergePage(page, albums, new TdApi.SearchMessagesFilterPhotoAndVideo());
        assertArrayEquals(new long[]{video.id, photo.id}, ArraysOf(merged));
    }

    private static long[] ArraysOf(TdApi.Message[] messages) {
        long[] ids = new long[messages.length];
        for (int i = 0; i < messages.length; i++) {
            ids[i] = messages[i].id;
        }
        return ids;
    }

    private static TdApi.Message message(long id, long albumId, TdApi.MessageContent content) {
        TdApi.Message message = new TdApi.Message();
        message.id = id;
        message.chatId = 100;
        message.mediaAlbumId = albumId;
        message.content = content;
        return message;
    }
}
