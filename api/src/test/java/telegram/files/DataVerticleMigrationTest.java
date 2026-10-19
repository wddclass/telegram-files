package telegram.files;


import cn.hutool.core.collection.IterUtil;
import cn.hutool.core.lang.Version;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import telegram.files.repository.SettingKey;
import telegram.files.repository.SettingRecord;

import java.util.Set;
import java.util.stream.Collectors;

@ExtendWith(VertxExtension.class)
public class DataVerticleMigrationTest {

    private static final Log log = LogFactory.get();

    @BeforeAll
    static void setUp() {
        DataVerticleTest.printDBInfo();
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        DataVerticleTest.clear(vertx)
                .onComplete(v -> {
                    if (v.failed()) {
                        testContext.failNow(v.cause());
                    } else {
                        testContext.completeNow();
                    }
                });
    }

    @Test
    @DisplayName("Test new database initialization")
    void testNewDatabaseInitialization(Vertx vertx, VertxTestContext testContext) {
        initializeEmptyDatabase(vertx, testContext)
                .compose(v -> DataVerticle.settingRepository.getByKey(SettingKey.version))
                .onComplete(testContext.succeeding(version -> testContext.verify(() -> {
                    Assertions.assertEquals(new Version(Start.VERSION), version);
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test legacy database migration (pre-0.1.7, no version)")
    void testLegacyDatabaseMigration(Vertx vertx, VertxTestContext testContext) {
        initializeLegacyDatabase(vertx)
                .compose(v -> vertx.deployVerticle(new DataVerticle()))
                .compose(v -> DataVerticle.pool.getConnection())
                .compose(conn -> conn.query(getColumnsQuery()).execute()
                        .compose(result -> {
                            testContext.verify(() -> {
                                // Verify whether there are new columns
                                Set<String> columnNames = IterUtil.toList(result).stream()
                                        .map(row -> row.getString("name"))
                                        .collect(Collectors.toSet());
                                Assertions.assertTrue(columnNames.contains("start_date"));
                                Assertions.assertTrue(columnNames.contains("completion_date"));
                                Assertions.assertTrue(columnNames.contains("rclone_status"));
                                Assertions.assertTrue(columnNames.contains("rclone_error"));
                            });
                            return conn.close();
                        }))
                .compose(v -> DataVerticle.settingRepository.getByKey(SettingKey.version))
                .onComplete(testContext.succeeding(version -> testContext.verify(() -> {
                    Assertions.assertEquals(new Version(Start.VERSION), version);
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test migration from 0.1.6 to current version")
    void testOldVersionMigration(Vertx vertx, VertxTestContext testContext) {
        initializeOldVersionDatabase(vertx)
                .compose(v -> vertx.deployVerticle(new DataVerticle()))
                .compose(v -> DataVerticle.pool.getConnection())
                .compose(conn -> conn.query(getColumnsQuery()).execute()
                        .compose(result -> {
                            testContext.verify(() -> {
                                // Verify whether there are new columns
                                Set<String> columnNames = IterUtil.toList(result).stream()
                                        .map(row -> row.getString("name"))
                                        .collect(Collectors.toSet());
                                Assertions.assertTrue(columnNames.contains("start_date"));
                                Assertions.assertTrue(columnNames.contains("completion_date"));
                                Assertions.assertTrue(columnNames.contains("rclone_status"));
                                Assertions.assertTrue(columnNames.contains("rclone_error"));
                            });
                            return conn.close();
                        }))
                .compose(v -> DataVerticle.settingRepository.getByKey(SettingKey.version))
                .onComplete(testContext.succeeding(version -> testContext.verify(() -> {
                    Assertions.assertEquals(new Version(Start.VERSION), version);
                    testContext.completeNow();
                })));
    }

    private Future<Void> initializeEmptyDatabase(Vertx vertx, VertxTestContext testContext) {
        return vertx.deployVerticle(new DataVerticle())
                .compose(id -> {
                    // Verify that the database has been fully initialized
                    return DataVerticle.pool.getConnection()
                            .compose(conn -> conn.query(getTablesQuery()).execute()
                                    .compose(result -> {
                                        testContext.verify(() -> {
                                            Assertions.assertEquals(DataVerticle.definitions.size(), result.size());
                                        });
                                        return conn.close();
                                    }));
                })
                .mapEmpty();
    }

    private Future<Void> initializeLegacyDatabase(Vertx vertx) {
        // Create an old version database without a version field
        return Future.succeededFuture()
                .compose(v -> createTempSqlClient(vertx))
                .compose(sqlClient -> sqlClient.query("""
                                CREATE TABLE file_record (
                                    id INTEGER PRIMARY KEY,
                                    file_id TEXT NOT NULL,
                                    file_name TEXT NOT NULL
                                )
                                """).execute()
                        .compose(v2 -> sqlClient.query("""
                                INSERT INTO file_record (id, file_id, file_name) 
                                VALUES (1, '123', 'test.txt')
                                """).execute())
                        .eventually(() -> sqlClient.close())
                )
                .mapEmpty();
    }

    private Future<Void> initializeOldVersionDatabase(Vertx vertx) {
        // Create a database with a version field but a lower version
        return Future.succeededFuture()
                .compose(v -> createTempSqlClient(vertx))
                .compose(sqlClient -> sqlClient.query("""
                                CREATE TABLE setting_record (
                                    %s VARCHAR(255) PRIMARY KEY,
                                    value TEXT NOT NULL
                                )
                                """.formatted(SettingRecord.KEY_FIELD)).execute()
                        .compose(v2 -> sqlClient.query("""
                                INSERT INTO setting_record (%s, value)
                                VALUES ('version', '0.1.6')
                                """.formatted(SettingRecord.KEY_FIELD)).execute())
                        .compose(v3 -> sqlClient.query("""
                                CREATE TABLE file_record (
                                    id INTEGER PRIMARY KEY,
                                    file_id TEXT NOT NULL,
                                    file_name TEXT NOT NULL
                                )
                                """).execute())
                        .eventually(() -> sqlClient.close())
                )
                .mapEmpty();
    }

    private Future<SqlClient> createTempSqlClient(Vertx vertx) {
        if (Config.isSqlite()) {
            return Future.succeededFuture(JDBCPool.pool(vertx,
                    new JDBCConnectOptions()
                            .setJdbcUrl("jdbc:sqlite:%s".formatted(DataVerticle.getDataPath())),
                    new PoolOptions().setMaxSize(1).setName("temp-pool")
            ));
        } else if (Config.isPostgres() || Config.isMysql()) {
            SqlClient sqlClient = DataVerticle.createSqlClient(vertx, DataVerticle.createDefaultOptions());
            String createDatabaseQuery;
            if (Config.isPostgres()) {
                createDatabaseQuery = """
                        CREATE DATABASE "%s";
                        """.formatted(Config.DB_NAME);
            } else {
                createDatabaseQuery = """
                        CREATE DATABASE `%s` collate utf8mb4_bin;
                        """.formatted(Config.DB_NAME);
            }

            return sqlClient.query(createDatabaseQuery)
                    .execute()
                    .onSuccess(r -> log.info("Database created"))
                    .onFailure(err -> log.error("Failed to create database: %s".formatted(err.getMessage())))
                    .map(DataVerticle.createSqlClient(vertx, DataVerticle.getSqlConnectOptions()));
        } else {
            throw new VertxException("Unsupported database type");
        }
    }

    private String getColumnsQuery() {
        String getColumnsQuery;
        if (Config.isPostgres()) {
            getColumnsQuery = """
                    SELECT column_name as name FROM information_schema.columns
                    WHERE table_name = 'file_record'
                    """;
        } else if (Config.isMysql()) {
            getColumnsQuery = """
                    SELECT column_name as name FROM information_schema.columns
                    WHERE table_name = 'file_record'
                    """;
        } else {
            getColumnsQuery = """
                    PRAGMA table_info(file_record)
                    """;
        }
        return getColumnsQuery;
    }

    private String getTablesQuery() {
        String getTablesQuery;
        if (Config.isPostgres()) {
            getTablesQuery = """
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name IN ('setting_record', 'telegram_record', 'file_record', 'statistic_record')
                    """;
        } else if (Config.isMysql()) {
            getTablesQuery = """
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = DATABASE() AND table_name IN ('setting_record', 'telegram_record', 'file_record', 'statistic_record')
                    """;
        } else {
            getTablesQuery = """
                    SELECT name FROM sqlite_master
                    WHERE type='table' AND name IN ('setting_record', 'telegram_record', 'file_record', 'statistic_record')
                    """;
        }
        return getTablesQuery;
    }
}
