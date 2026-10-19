package telegram.files;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Telegram media albums are not a single message. They are 2-10 consecutive messages that share
 * {@code mediaAlbumId}. TDLib search (and a message link) returns only one of them; the rest have
 * to be loaded from chat history around that message.
 *
 * <p>Consecutive server message ids differ by {@code 2^20}.
 */
public final class AlbumMessages {

    public static final int MAX_ALBUM_SIZE = 10;

    public static final long MESSAGE_ID_STEP = 1L << 20;

    /** History pages to walk in each direction. An album cannot be larger than {@link #MAX_ALBUM_SIZE}. */
    public static final int MAX_HISTORY_PAGES = 4;

    public enum Direction {
        NEWER,
        OLDER
    }

    public record Walk(boolean done, long nextEdge) {
    }

    private AlbumMessages() {
    }

    public static boolean isAlbum(TdApi.Message message) {
        return message != null && message.mediaAlbumId != 0;
    }

    /**
     * Ids of the anchor and up to 9 neighbors on each side. Deleted holes inside an album still
     * fall inside this window because an album is at most 10 consecutive messages.
     */
    public static long[] neighborIds(long anchorId) {
        List<Long> ids = new ArrayList<>(MAX_ALBUM_SIZE * 2 - 1);
        for (int delta = -(MAX_ALBUM_SIZE - 1); delta <= MAX_ALBUM_SIZE - 1; delta++) {
            long id = anchorId + delta * MESSAGE_ID_STEP;
            if (id > 0) {
                ids.add(id);
            }
        }
        return ids.stream().mapToLong(Long::longValue).toArray();
    }

    public static void absorbKnown(long albumId, TdApi.Message[] messages, Map<Long, TdApi.Message> acc) {
        if (messages == null || albumId == 0) {
            return;
        }
        for (TdApi.Message message : messages) {
            if (message != null && message.mediaAlbumId == albumId) {
                acc.putIfAbsent(message.id, message);
            }
        }
    }

    /**
     * Fold one {@code getChatHistory} page into {@code acc}. History is contiguous and newest-first,
     * so a message that is not in the album means that side of the album has ended.
     */
    public static Walk absorb(long albumId,
                              long edgeId,
                              Direction direction,
                              TdApi.Message[] page,
                              Map<Long, TdApi.Message> acc) {
        boolean added = false;
        boolean crossed = false;
        long nextEdge = edgeId;
        if (page != null) {
            for (TdApi.Message message : page) {
                if (message == null) {
                    continue;
                }
                boolean onSide = direction == Direction.NEWER ? message.id > edgeId : message.id < edgeId;
                if (!onSide) {
                    continue;
                }
                if (message.mediaAlbumId == albumId) {
                    if (acc.putIfAbsent(message.id, message) == null) {
                        added = true;
                    }
                    nextEdge = direction == Direction.NEWER
                            ? Math.max(nextEdge, message.id)
                            : Math.min(nextEdge, message.id);
                } else {
                    crossed = true;
                }
            }
        }
        boolean done = !added || crossed || acc.size() >= MAX_ALBUM_SIZE;
        return new Walk(done, nextEdge);
    }

    public static TdApi.Message[] toOldestFirst(Map<Long, TdApi.Message> messages) {
        return messages.values().stream()
                .sorted(Comparator.comparingLong(message -> message.id))
                .toArray(TdApi.Message[]::new);
    }

    /**
     * Replace each album hit in a search page with every sibling that matches {@code filter}.
     * The anchor itself is always kept. Non-album messages stay in their original order.
     */
    public static TdApi.Message[] mergePage(TdApi.Message[] page,
                                             Map<Long, TdApi.Message[]> albumsById,
                                             TdApi.SearchMessagesFilter filter) {
        if (page == null || page.length == 0) {
            return page == null ? new TdApi.Message[0] : page;
        }
        List<TdApi.Message> merged = new ArrayList<>();
        Set<Long> seenAlbums = new java.util.HashSet<>();
        Set<Long> seenMessages = new java.util.HashSet<>();
        for (TdApi.Message message : page) {
            if (message == null) {
                continue;
            }
            if (message.mediaAlbumId == 0) {
                if (seenMessages.add(message.id)) {
                    merged.add(message);
                }
                continue;
            }
            if (!seenAlbums.add(message.mediaAlbumId)) {
                continue;
            }
            TdApi.Message[] album = albumsById == null ? null : albumsById.get(message.mediaAlbumId);
            if (album == null || album.length == 0) {
                album = new TdApi.Message[]{message};
            }
            Arrays.stream(album)
                    .filter(member -> member != null)
                    .sorted(Comparator.comparingLong(member -> member.id))
                    .filter(member -> member.id == message.id || matchesFilter(member, filter))
                    .filter(member -> seenMessages.add(member.id))
                    .forEach(merged::add);
        }
        return merged.toArray(TdApi.Message[]::new);
    }

    public static boolean matchesFilter(TdApi.Message message, TdApi.SearchMessagesFilter filter) {
        if (message == null || message.content == null) {
            return false;
        }
        int content = message.content.getConstructor();
        if (filter == null) {
            return isFileContent(content);
        }
        int kind = filter.getConstructor();
        if (kind == TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR) {
            return content == TdApi.MessagePhoto.CONSTRUCTOR
                    || content == TdApi.MessageVideo.CONSTRUCTOR
                    || content == TdApi.MessagePaidMedia.CONSTRUCTOR;
        }
        if (kind == TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR) {
            return content == TdApi.MessagePhoto.CONSTRUCTOR || content == TdApi.MessagePaidMedia.CONSTRUCTOR;
        }
        if (kind == TdApi.SearchMessagesFilterVideo.CONSTRUCTOR) {
            return content == TdApi.MessageVideo.CONSTRUCTOR || content == TdApi.MessagePaidMedia.CONSTRUCTOR;
        }
        if (kind == TdApi.SearchMessagesFilterAudio.CONSTRUCTOR) {
            return content == TdApi.MessageAudio.CONSTRUCTOR;
        }
        if (kind == TdApi.SearchMessagesFilterDocument.CONSTRUCTOR) {
            return content == TdApi.MessageDocument.CONSTRUCTOR;
        }
        return isFileContent(content);
    }

    private static boolean isFileContent(int content) {
        return content == TdApi.MessagePhoto.CONSTRUCTOR
                || content == TdApi.MessageVideo.CONSTRUCTOR
                || content == TdApi.MessageAudio.CONSTRUCTOR
                || content == TdApi.MessageDocument.CONSTRUCTOR
                || content == TdApi.MessagePaidMedia.CONSTRUCTOR;
    }

    /** Preserve insertion order while allowing concurrent album fetches to add siblings. */
    public static Map<Long, TdApi.Message> newAlbumMap(TdApi.Message anchor) {
        Map<Long, TdApi.Message> messages = new LinkedHashMap<>();
        if (anchor != null) {
            messages.put(anchor.id, anchor);
        }
        return messages;
    }
}
