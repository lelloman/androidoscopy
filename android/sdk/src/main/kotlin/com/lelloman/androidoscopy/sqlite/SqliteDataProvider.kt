package com.lelloman.androidoscopy.sqlite

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.lelloman.androidoscopy.ActionResult
import com.lelloman.androidoscopy.data.DataProvider
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that exposes SQLite databases to the dashboard.
 * Supports browsing tables, viewing schema, running queries, and CRUD operations.
 *
 * @param context Application context for accessing databases
 * @param dbName Optional specific database to monitor. If null, monitors all.
 */
class SqliteDataProvider(
    private val context: Context,
    private val dbName: String? = null
) : DataProvider {

    override val key: String = if (dbName != null) "sqlite_$dbName" else "sqlite"
    override val interval: Duration = 5.seconds

    private var selectedDatabase: String? = dbName
    private var selectedTable: String? = null
    private var currentPage: Int = 0
    private val pageSize: Int = 50

    /**
     * Get action handlers for SQLite operations.
     */
    fun getActionHandlers(): Map<String, suspend (Map<String, Any>) -> ActionResult> = mapOf(
        "sqlite_query" to ::handleQuery,
        "sqlite_select_db" to ::handleSelectDatabase,
        "sqlite_select_table" to ::handleSelectTable,
        "sqlite_insert" to ::handleInsert,
        "sqlite_update" to ::handleUpdate,
        "sqlite_delete" to ::handleDelete,
        "sqlite_refresh" to ::handleRefresh,
        "sqlite_next_page" to ::handleNextPage,
        "sqlite_prev_page" to ::handlePrevPage
    )

    override suspend fun collect(): Map<String, Any> {
        val databases = getDatabases()
        val selectedDb = selectedDatabase ?: databases.firstOrNull()

        val tables = if (selectedDb != null) {
            getTables(selectedDb)
        } else {
            emptyList()
        }

        val selectedTbl = selectedTable ?: tables.firstOrNull()

        val schema = if (selectedDb != null && selectedTbl != null) {
            getTableSchema(selectedDb, selectedTbl)
        } else {
            emptyList()
        }

        val data = if (selectedDb != null && selectedTbl != null) {
            getTableData(selectedDb, selectedTbl, currentPage, pageSize)
        } else {
            TableData(emptyList(), 0)
        }

        return mapOf(
            "databases" to databases,
            "database_count" to databases.size,
            "selected_database" to (selectedDb ?: ""),
            "tables" to tables,
            "table_count" to tables.size,
            "selected_table" to (selectedTbl ?: ""),
            "schema" to schema,
            "column_count" to schema.size,
            "data" to data.rows,
            "row_count" to data.totalCount,
            "current_page" to currentPage,
            "page_size" to pageSize,
            "total_pages" to ((data.totalCount + pageSize - 1) / pageSize)
        )
    }

    private fun getDatabases(): List<String> {
        val dbDir = context.getDatabasePath("dummy").parentFile ?: return emptyList()
        if (!dbDir.exists()) return emptyList()

        return dbDir.listFiles()
            ?.filter { it.extension == "db" || !it.name.contains("-journal") && !it.name.contains("-shm") && !it.name.contains("-wal") }
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.filter { isValidDatabase(it) }
            ?: emptyList()
    }

    private fun isValidDatabase(name: String): Boolean {
        return try {
            val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
            db.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getTables(dbName: String): List<String> {
        return try {
            val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
            val tables = mutableListOf<String>()
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    tables.add(it.getString(0))
                }
            }
            db.close()
            tables
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getTableSchema(dbName: String, tableName: String): List<Map<String, Any>> {
        return try {
            val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
            val schema = mutableListOf<Map<String, Any>>()
            val cursor = db.rawQuery("PRAGMA table_info('$tableName')", null)
            cursor.use {
                while (it.moveToNext()) {
                    schema.add(mapOf(
                        "cid" to it.getInt(0),
                        "name" to it.getString(1),
                        "type" to it.getString(2),
                        "notnull" to (it.getInt(3) == 1),
                        "default_value" to (it.getString(4) ?: "NULL"),
                        "pk" to (it.getInt(5) == 1)
                    ))
                }
            }
            db.close()
            schema
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getTableData(dbName: String, tableName: String, page: Int, size: Int): TableData {
        return try {
            val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

            // Get total count
            val countCursor = db.rawQuery("SELECT COUNT(*) FROM '$tableName'", null)
            val totalCount = countCursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }

            // Get paginated data
            val offset = page * size
            val cursor = db.rawQuery("SELECT * FROM '$tableName' LIMIT $size OFFSET $offset", null)
            val rows = cursorToList(cursor)
            cursor.close()
            db.close()

            TableData(rows, totalCount)
        } catch (e: Exception) {
            TableData(emptyList(), 0)
        }
    }

    private fun cursorToList(cursor: Cursor): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        val columnNames = cursor.columnNames

        while (cursor.moveToNext()) {
            val row = mutableMapOf<String, Any?>()
            for (i in columnNames.indices) {
                row[columnNames[i]] = when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                    Cursor.FIELD_TYPE_BLOB -> "[BLOB]"
                    else -> cursor.getString(i)
                }
            }
            rows.add(row)
        }
        return rows
    }

    private suspend fun handleQuery(args: Map<String, Any>): ActionResult {
        val dbName = args["database"] as? String ?: selectedDatabase
            ?: return ActionResult.failure("No database selected")
        val query = args["query"] as? String
            ?: return ActionResult.failure("Missing query")

        return try {
            val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
            val isSelect = query.trim().uppercase().startsWith("SELECT")

            if (isSelect) {
                val cursor = db.rawQuery(query, null)
                val rows = cursorToList(cursor)
                cursor.close()
                db.close()
                ActionResult.success("Query returned ${rows.size} rows", mapOf(
                    "rows" to rows,
                    "row_count" to rows.size
                ))
            } else {
                db.execSQL(query)
                db.close()
                ActionResult.success("Query executed successfully")
            }
        } catch (e: Exception) {
            ActionResult.failure("Query error: ${e.message}")
        }
    }

    private suspend fun handleSelectDatabase(args: Map<String, Any>): ActionResult {
        val dbName = args["database"] as? String
            ?: return ActionResult.failure("Missing database")
        selectedDatabase = dbName
        selectedTable = null
        currentPage = 0
        return ActionResult.success("Selected database: $dbName", collect())
    }

    private suspend fun handleSelectTable(args: Map<String, Any>): ActionResult {
        val tableName = args["table"] as? String
            ?: return ActionResult.failure("Missing table")
        selectedTable = tableName
        currentPage = 0
        return ActionResult.success("Selected table: $tableName", collect())
    }

    private suspend fun handleInsert(args: Map<String, Any>): ActionResult {
        val dbName = args["database"] as? String ?: selectedDatabase
            ?: return ActionResult.failure("No database selected")
        val tableName = args["table"] as? String ?: selectedTable
            ?: return ActionResult.failure("No table selected")
        @Suppress("UNCHECKED_CAST")
        val values = args["values"] as? Map<String, Any>
            ?: return ActionResult.failure("Missing values")

        return try {
            val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
            val columns = values.keys.joinToString(", ")
            val placeholders = values.keys.joinToString(", ") { "?" }
            val sql = "INSERT INTO '$tableName' ($columns) VALUES ($placeholders)"
            db.execSQL(sql, values.values.toTypedArray())
            db.close()
            ActionResult.success("Row inserted", collect())
        } catch (e: Exception) {
            ActionResult.failure("Insert error: ${e.message}")
        }
    }

    private suspend fun handleUpdate(args: Map<String, Any>): ActionResult {
        val dbName = args["database"] as? String ?: selectedDatabase
            ?: return ActionResult.failure("No database selected")
        val tableName = args["table"] as? String ?: selectedTable
            ?: return ActionResult.failure("No table selected")
        @Suppress("UNCHECKED_CAST")
        val values = args["values"] as? Map<String, Any>
            ?: return ActionResult.failure("Missing values")
        val whereClause = args["where"] as? String
            ?: return ActionResult.failure("Missing where clause")

        return try {
            val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
            val setClause = values.keys.joinToString(", ") { "$it = ?" }
            val sql = "UPDATE '$tableName' SET $setClause WHERE $whereClause"
            db.execSQL(sql, values.values.toTypedArray())
            db.close()
            ActionResult.success("Row updated", collect())
        } catch (e: Exception) {
            ActionResult.failure("Update error: ${e.message}")
        }
    }

    private suspend fun handleDelete(args: Map<String, Any>): ActionResult {
        val dbName = args["database"] as? String ?: selectedDatabase
            ?: return ActionResult.failure("No database selected")
        val tableName = args["table"] as? String ?: selectedTable
            ?: return ActionResult.failure("No table selected")
        val whereClause = args["where"] as? String
            ?: return ActionResult.failure("Missing where clause")

        return try {
            val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
            val sql = "DELETE FROM '$tableName' WHERE $whereClause"
            db.execSQL(sql)
            db.close()
            ActionResult.success("Row deleted", collect())
        } catch (e: Exception) {
            ActionResult.failure("Delete error: ${e.message}")
        }
    }

    private suspend fun handleRefresh(args: Map<String, Any>): ActionResult {
        return ActionResult.success("Refreshed", collect())
    }

    private suspend fun handleNextPage(args: Map<String, Any>): ActionResult {
        currentPage++
        return ActionResult.success("Page ${currentPage + 1}", collect())
    }

    private suspend fun handlePrevPage(args: Map<String, Any>): ActionResult {
        if (currentPage > 0) currentPage--
        return ActionResult.success("Page ${currentPage + 1}", collect())
    }

    private data class TableData(
        val rows: List<Map<String, Any?>>,
        val totalCount: Int
    )
}
