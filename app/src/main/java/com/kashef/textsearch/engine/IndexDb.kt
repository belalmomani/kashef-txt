package com.kashef.textsearch.engine

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class FileMeta(val size: Long, val modified: Long)

data class IndexedRoot(val uri: String, val label: String, val indexedAt: Long, val files: Int, val bytes: Long)

class PendingEntry(val docId: String, val size: Long, val modified: Long, val bloom: TrigramBloom)

/**
 * The optional local index, stored in the app's private database. A folder is indexed ONLY after the user
 * turns indexing on for it. The index holds per-file Bloom filters of hashed trigrams (no readable text).
 */
class IndexDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "kashef_index.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE roots(uri TEXT PRIMARY KEY, label TEXT NOT NULL, indexed_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL(
            "CREATE TABLE files(doc_id TEXT PRIMARY KEY, root TEXT NOT NULL, size INTEGER NOT NULL, " +
                "modified INTEGER NOT NULL, bits INTEGER NOT NULL, bloom BLOB NOT NULL)",
        )
        db.execSQL("CREATE INDEX files_root ON files(root)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Version 2 builds the index from folded text; old entries are discarded and rebuilt on demand.
            db.execSQL("DELETE FROM files")
            db.execSQL("UPDATE roots SET indexed_at = 0")
        }
    }

    fun isEnabled(root: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM roots WHERE uri = ?", arrayOf(root)).use { it.moveToFirst() }

    fun enable(root: String, label: String) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO roots(uri, label) VALUES(?, ?)", arrayOf(root, label))
    }

    /** Turns indexing off for a folder and deletes everything stored for it. */
    fun disable(root: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM files WHERE root = ?", arrayOf(root))
            db.execSQL("DELETE FROM roots WHERE uri = ?", arrayOf(root))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM files")
            db.execSQL("DELETE FROM roots")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        try { db.execSQL("VACUUM") } catch (e: Exception) { /* space is reclaimed later */ }
    }

    fun roots(): List<IndexedRoot> {
        val out = ArrayList<IndexedRoot>()
        readableDatabase.rawQuery(
            "SELECT r.uri, r.label, r.indexed_at, " +
                "(SELECT COUNT(*) FROM files f WHERE f.root = r.uri), " +
                "(SELECT COALESCE(SUM(LENGTH(f.bloom)), 0) FROM files f WHERE f.root = r.uri) FROM roots r",
            null,
        ).use { c ->
            while (c.moveToNext()) out.add(IndexedRoot(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3), c.getLong(4)))
        }
        return out
    }

    fun loadMeta(root: String): HashMap<String, FileMeta> {
        val map = HashMap<String, FileMeta>()
        readableDatabase.rawQuery("SELECT doc_id, size, modified FROM files WHERE root = ?", arrayOf(root)).use { c ->
            while (c.moveToNext()) map[c.getString(0)] = FileMeta(c.getLong(1), c.getLong(2))
        }
        return map
    }

    fun bloom(docId: String): TrigramBloom? =
        readableDatabase.rawQuery("SELECT bits, bloom FROM files WHERE doc_id = ?", arrayOf(docId)).use { c ->
            if (c.moveToFirst()) TrigramBloom.fromBytes(c.getInt(0), c.getBlob(1)) else null
        }

    fun upsert(root: String, rows: List<PendingEntry>) {
        if (rows.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in rows) {
                val v = ContentValues().apply {
                    put("doc_id", r.docId); put("root", root); put("size", r.size); put("modified", r.modified)
                    put("bits", r.bloom.bits); put("bloom", r.bloom.data)
                }
                db.insertWithOnConflict("files", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.execSQL("UPDATE roots SET indexed_at = ? WHERE uri = ?", arrayOf(System.currentTimeMillis(), root))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Removes index rows of files that no longer exist (call only after a complete, error-free walk). */
    fun prune(root: String, seen: Set<String>) {
        val stale = ArrayList<String>()
        readableDatabase.rawQuery("SELECT doc_id FROM files WHERE root = ?", arrayOf(root)).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                if (id !in seen) stale.add(id)
            }
        }
        if (stale.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in stale) db.execSQL("DELETE FROM files WHERE doc_id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

/** The index of ONE folder during ONE pass: reads what is stored and queues new or refreshed entries. */
class IndexSession(private val db: IndexDb, val root: String) {
    private val meta = db.loadMeta(root)
    private val pending = ArrayList<PendingEntry>()

    fun lookup(docId: String): FileMeta? = meta[docId]

    fun bloomFor(docId: String): TrigramBloom? = db.bloom(docId)

    fun add(docId: String, size: Long, modified: Long, bloom: TrigramBloom) {
        pending.add(PendingEntry(docId, size, modified, bloom))
        if (pending.size >= 100) flush()
    }

    fun flush() {
        if (pending.isEmpty()) return
        db.upsert(root, pending)
        pending.clear()
    }

    fun prune(seen: Set<String>) = db.prune(root, seen)
}
