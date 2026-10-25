/**
 * 文件作用：集中定义 Room 迁移 SQL，便于在 JVM 单元测试中逐条执行验证。
 */
package com.banlimusic.app.core.database

object MusicMigrations {

    /** v4 → v5: one-row-per-favourite becomes track metadata + many-to-many memberships. */
    val v4To5: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS favorite_tracks (
            trackId TEXT NOT NULL PRIMARY KEY,
            trackJson TEXT NOT NULL,
            addedAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
        "INSERT INTO favorite_tracks(trackId, trackJson, addedAt, updatedAt) " +
            "SELECT trackId, trackJson, addedAt, addedAt FROM favorites",
        """
        CREATE TABLE IF NOT EXISTS favorite_memberships (
            trackId TEXT NOT NULL,
            folder TEXT NOT NULL,
            addedAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER,
            PRIMARY KEY(trackId, folder)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS index_favorite_memberships_folder " +
            "ON favorite_memberships(folder)",
        "INSERT INTO favorite_memberships(trackId, folder, addedAt, updatedAt, deletedAt) " +
            "SELECT trackId, category, addedAt, addedAt, NULL FROM favorites",
        "DROP TABLE IF EXISTS favorites",
        "ALTER TABLE favorite_categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
        "UPDATE favorite_categories SET updatedAt = createdAt",
        "ALTER TABLE favorite_categories ADD COLUMN deletedAt INTEGER",
    )
}
