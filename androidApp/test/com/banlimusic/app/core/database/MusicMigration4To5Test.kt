package com.banlimusic.app.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.sql.DriverManager

/**
 * Executes the real v4→v5 statements against an in-memory SQLite database so a
 * broken migration fails in CI instead of wiping a user's library at startup.
 */
class MusicMigration4To5Test {

    @Test
    fun migratesLegacyFavoritesIntoMemberships() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE TABLE favorites (" +
                        "trackId TEXT NOT NULL PRIMARY KEY, " +
                        "trackJson TEXT NOT NULL, " +
                        "addedAt INTEGER NOT NULL, " +
                        "category TEXT NOT NULL)",
                )
                statement.executeUpdate(
                    "CREATE TABLE favorite_categories (" +
                        "name TEXT NOT NULL PRIMARY KEY, " +
                        "createdAt INTEGER NOT NULL)",
                )
                statement.executeUpdate(
                    "INSERT INTO favorites VALUES ('t1', '{\"id\":\"t1\"}', 100, '通勤')",
                )
                statement.executeUpdate(
                    "INSERT INTO favorites VALUES ('t2', '{\"id\":\"t2\"}', 200, '默认收藏')",
                )
                statement.executeUpdate("INSERT INTO favorite_categories VALUES ('空收藏夹', 50)")
            }

            MusicMigrations.v4To5.forEach { sql ->
                connection.createStatement().use { statement -> statement.executeUpdate(sql) }
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM favorite_tracks").use { result ->
                    result.next()
                    assertEquals(2, result.getInt(1))
                }
                statement.executeQuery(
                    "SELECT trackJson, addedAt, updatedAt FROM favorite_tracks WHERE trackId = 't1'",
                ).use { result ->
                    result.next()
                    assertEquals("{\"id\":\"t1\"}", result.getString("trackJson"))
                    assertEquals(100L, result.getLong("addedAt"))
                    assertEquals(100L, result.getLong("updatedAt"))
                }
                statement.executeQuery(
                    "SELECT folder, addedAt, updatedAt, deletedAt " +
                        "FROM favorite_memberships WHERE trackId = 't1'",
                ).use { result ->
                    result.next()
                    assertEquals("通勤", result.getString("folder"))
                    assertEquals(100L, result.getLong("addedAt"))
                    assertEquals(100L, result.getLong("updatedAt"))
                    assertNull(result.getObject("deletedAt"))
                }
                statement.executeQuery(
                    "SELECT COUNT(*) FROM favorite_memberships",
                ).use { result ->
                    result.next()
                    assertEquals(2, result.getInt(1))
                }
                statement.executeQuery(
                    "SELECT createdAt, updatedAt, deletedAt " +
                        "FROM favorite_categories WHERE name = '空收藏夹'",
                ).use { result ->
                    result.next()
                    assertEquals(50L, result.getLong("createdAt"))
                    assertEquals(50L, result.getLong("updatedAt"))
                    assertNull(result.getObject("deletedAt"))
                }
                statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master " +
                        "WHERE type = 'table' AND name = 'favorites'",
                ).use { result ->
                    result.next()
                    assertEquals(0, result.getInt(1))
                }
                statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master " +
                        "WHERE type = 'index' AND name = 'index_favorite_memberships_folder'",
                ).use { result ->
                    result.next()
                    assertEquals(1, result.getInt(1))
                }
            }
        }
    }
}
