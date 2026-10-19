package telegram.files;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.drinkless.tdlib.TdApi;
import telegram.files.repository.FileRecord;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FileRecordRetriever {

    private static final Log log = LogFactory.get();

    private static final int ALBUM_FETCH_CONCURRENCY = 3;

    public static Future<JsonObject> getFiles(long chatId, Map<String, String> filter) {
        return DataVerticle.fileRepository.getFiles(chatId, filter)
                .compose(r -> getTdMessages(r.v1).map(r::concat))
                .compose(r -> getThumbnails(r.v1).map(r::concat))
                .map(r -> {
                    Map<String, TdApi.Message> messageMap = r.v4;
                    Map<String, FileRecord> thumbnailMap = r.v5;
                    List<JsonObject> fileRecords = r.v1.stream()
                            .map(fileRecord -> TelegramConverter.withSource(fileRecord.telegramId(),
                                    fileRecord,
                                    StrUtil.isBlank(fileRecord.thumbnailUniqueId()) ? null : thumbnailMap.get(fileRecord.thumbnailUniqueId()),
                                    messageMap.get(fileRecord.uniqueId())
                            ))
                            .filter(Objects::nonNull)
                            .toList();
                    return new JsonObject()
                            .put("files", fileRecords)
                            .put("nextFromMessageId", r.v2)
                            .put("count", r.v3)
                            .put("size", fileRecords.size());
                });
    }

    /**
     * Get messages from telegram
     *
     * @return Future of map of message uniqueId and message
     */
    public static Future<Map<String, TdApi.Message>> getTdMessages(List<FileRecord> fileRecords) {
        if (fileRecords == null || fileRecords.isEmpty()) {
            return Future.succeededFuture(Collections.emptyMap());
        }

        Map<Long, List<FileRecord>> groupingByTelegramIdMap = fileRecords.stream()
                .collect(Collectors.groupingBy(FileRecord::telegramId));

        return Future.all(groupingByTelegramIdMap
                .entrySet()
                .stream()
                .map(entry -> getTdMessages(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList())
        ).map(compositeFuture -> {
            Map<String, TdApi.Message> combinedMessageMap = new HashMap<>();
            for (int i = 0; i < compositeFuture.size(); i++) {
                Map<String, TdApi.Message> partialMessageMap = compositeFuture.resultAt(i);
                combinedMessageMap.putAll(partialMessageMap);
            }
            return combinedMessageMap;
        }).recover(throwable -> Future.failedFuture(new RuntimeException("Failed to get Telegram message", throwable)));
    }

    /**
     * Get messages from telegram
     *
     * @return Future of map of message uniqueId and message
     */
    public static Future<Map<String, TdApi.Message>> getTdMessages(long telegramId, List<FileRecord> fileRecords) {
        if (fileRecords == null || fileRecords.isEmpty()) {
            return Future.succeededFuture(Collections.emptyMap());
        }

        Optional<TelegramVerticle> telegramVerticleOptional = TelegramVerticles.get(telegramId);
        if (telegramVerticleOptional.isEmpty()) {
            return Future.failedFuture("Telegram verticle not found，unable to get the message. telegramId: " + telegramId);
        }

        Map<Long, List<FileRecord>> groupingByChatIdMap = fileRecords.stream()
                .collect(Collectors.groupingBy(FileRecord::chatId));

        return Future.all(groupingByChatIdMap
                .entrySet()
                .stream()
                .map(entry -> {
                    long chatId = entry.getKey();
                    List<FileRecord> records = entry.getValue();
                    long[] messageIds = records.stream()
                            .mapToLong(FileRecord::messageId)
                            .toArray();

                    return telegramVerticleOptional
                            .get()
                            .client
                            .execute(new TdApi.GetMessages(chatId, messageIds), true)
                            .map(m -> m == null ? Collections.emptyMap() : createMessageMap(chatId, m.messages, records));
                })
                .collect(Collectors.toList())
        ).map(compositeFuture -> {
            Map<String, TdApi.Message> combinedMessageMap = new HashMap<>();
            for (int i = 0; i < compositeFuture.size(); i++) {
                Map<String, TdApi.Message> partialMessageMap = compositeFuture.resultAt(i);
                if (partialMessageMap == null || partialMessageMap.isEmpty()) {
                    continue;
                }
                combinedMessageMap.putAll(partialMessageMap);
            }
            return combinedMessageMap;
        }).recover(throwable -> Future.failedFuture(new RuntimeException("Failed to get Telegram message", throwable)));
    }

    private static Map<String, TdApi.Message> createMessageMap(long chatId, TdApi.Message[] messages, List<FileRecord> records) {
        Map<String, TdApi.Message> messageMap = new HashMap<>();

        for (TdApi.Message message : messages) {
            if (message == null) {
                continue;
            }
            String uniqueId = TdApiHelp.getFileUniqueId(message);

            boolean matchesFileRecord = records.stream()
                    .anyMatch(record -> record.messageId() == message.id && record.chatId() == chatId);

            if (matchesFileRecord) {
                messageMap.put(uniqueId, message);
            }
        }

        return messageMap;
    }

    public static Future<Map<String, FileRecord>> getThumbnails(Collection<FileRecord> fileRecords) {
        if (fileRecords == null || fileRecords.isEmpty()) {
            return Future.succeededFuture(Collections.emptyMap());
        }

        List<String> thumbnailUniqueIds = fileRecords.stream()
                .map(FileRecord::thumbnailUniqueId)
                .filter(Objects::nonNull)
                .toList();

        return DataVerticle.fileRepository
                .getFilesByUniqueId(thumbnailUniqueIds);
    }

    /**
     * Load every message that belongs to the same media album as {@code message}.
     * A link or a search hit is only one member. Albums hold up to 10 consecutive messages,
     * which may be older or newer than the one we already have, so both directions are read
     * from chat history. {@code searchChatMessages} is not used: it depends on the local search
     * index and does not return the other members of an album.
     */
    public static Future<TdApi.Message[]> getAlbumMessages(long telegramId, TdApi.Message message) {
        if (telegramId == 0 || message == null) {
            return Future.failedFuture("Message is null, unable to get album messages.");
        }
        if (message.mediaAlbumId == 0) {
            return Future.succeededFuture(new TdApi.Message[]{message});
        }
        Optional<TelegramVerticle> telegramVerticleOptional = TelegramVerticles.get(telegramId);
        if (telegramVerticleOptional.isEmpty()) {
            return Future.failedFuture("Telegram verticle not found，unable to get the message. telegramId: " + telegramId);
        }
        return fetchAlbum(telegramVerticleOptional.get().client, message);
    }

    /**
     * Expand album hits inside a chat-file page. Messages that are not in an album are unchanged.
     * {@code filter} limits siblings to the same file type the user is browsing; the search hit
     * itself is always kept.
     */
    public static Future<TdApi.Message[]> expandAlbums(long telegramId,
                                                       TdApi.Message[] messages,
                                                       TdApi.SearchMessagesFilter filter) {
        if (messages == null || messages.length == 0) {
            return Future.succeededFuture(messages == null ? new TdApi.Message[0] : messages);
        }
        Map<Long, TdApi.Message> anchors = new LinkedHashMap<>();
        for (TdApi.Message message : messages) {
            if (AlbumMessages.isAlbum(message)) {
                anchors.putIfAbsent(message.mediaAlbumId, message);
            }
        }
        if (anchors.isEmpty()) {
            return Future.succeededFuture(messages);
        }
        List<TdApi.Message> anchorList = new ArrayList<>(anchors.values());
        Map<Long, TdApi.Message[]> albums = new ConcurrentHashMap<>();
        int workers = Math.min(ALBUM_FETCH_CONCURRENCY, anchorList.size());
        List<Future<?>> fetches = new ArrayList<>();
        AtomicInteger index = new AtomicInteger();
        for (int i = 0; i < workers; i++) {
            fetches.add(fetchNextAlbum(telegramId, anchorList, index, albums));
        }
        return Future.all(fetches)
                .map(ignored -> {
                    TdApi.Message[] merged = AlbumMessages.mergePage(messages, albums, filter);
                    if (merged.length != messages.length) {
                        log.debug("Expanded media albums: searchHits=%d files=%d albums=%d"
                                .formatted(messages.length, merged.length, anchors.size()));
                    }
                    return merged;
                })
                .recover(err -> {
                    log.warn("Failed to expand media albums, keeping the search page: %s".formatted(err.getMessage()));
                    return Future.succeededFuture(messages);
                });
    }

    private static Future<Void> fetchNextAlbum(long telegramId,
                                               List<TdApi.Message> anchors,
                                               AtomicInteger index,
                                               Map<Long, TdApi.Message[]> albums) {
        int current = index.getAndIncrement();
        if (current >= anchors.size()) {
            return Future.succeededFuture();
        }
        TdApi.Message anchor = anchors.get(current);
        return getAlbumMessages(telegramId, anchor)
                .recover(err -> {
                    log.debug("Album fetch failed for message %d: %s".formatted(anchor.id, err.getMessage()));
                    return Future.succeededFuture(new TdApi.Message[]{anchor});
                })
                .compose(album -> {
                    albums.put(anchor.mediaAlbumId, album);
                    return fetchNextAlbum(telegramId, anchors, index, albums);
                });
    }

    private static Future<TdApi.Message[]> fetchAlbum(TelegramClient client, TdApi.Message anchor) {
        Map<Long, TdApi.Message> collected = new ConcurrentHashMap<>();
        collected.put(anchor.id, anchor);
        long albumId = anchor.mediaAlbumId;

        Future<Void> byId = client.<TdApi.Messages>execute(new TdApi.GetMessages(anchor.chatId, AlbumMessages.neighborIds(anchor.id)))
                .compose(loaded -> {
                    AlbumMessages.absorbKnown(albumId, loaded == null ? null : loaded.messages, collected);
                    return Future.<Void>succeededFuture();
                })
                .recover(err -> {
                    log.debug("GetMessages album window failed for %d: %s".formatted(anchor.id, err.getMessage()));
                    return Future.<Void>succeededFuture();
                });
        Future<Void> newer = walkHistory(client, anchor.chatId, albumId, anchor.id, AlbumMessages.Direction.NEWER, collected, 0);
        Future<Void> older = walkHistory(client, anchor.chatId, albumId, anchor.id, AlbumMessages.Direction.OLDER, collected, 0);
        return Future.all(byId, newer, older)
                .map(ignored -> AlbumMessages.toOldestFirst(collected));
    }

    private static Future<Void> walkHistory(TelegramClient client,
                                            long chatId,
                                            long albumId,
                                            long edgeId,
                                            AlbumMessages.Direction direction,
                                            Map<Long, TdApi.Message> collected,
                                            int page) {
        if (page >= AlbumMessages.MAX_HISTORY_PAGES || collected.size() >= AlbumMessages.MAX_ALBUM_SIZE) {
            return Future.succeededFuture();
        }
        int offset = direction == AlbumMessages.Direction.NEWER ? -(AlbumMessages.MAX_ALBUM_SIZE - 1) : 0;
        int limit = AlbumMessages.MAX_ALBUM_SIZE;
        return client.execute(new TdApi.GetChatHistory(chatId, edgeId, offset, limit, false))
                .compose(history -> {
                    TdApi.Message[] messages = history == null ? null : history.messages;
                    AlbumMessages.Walk walk = AlbumMessages.absorb(albumId, edgeId, direction, messages, collected);
                    if (walk.done() || walk.nextEdge() == edgeId) {
                        return Future.<Void>succeededFuture();
                    }
                    return walkHistory(client, chatId, albumId, walk.nextEdge(), direction, collected, page + 1);
                })
                .recover(err -> {
                    log.debug("getChatHistory album walk failed from %d: %s".formatted(edgeId, err.getMessage()));
                    return Future.<Void>succeededFuture();
                });
    }
}
