package de.anjakammer.bassa.SQLiteSync;


import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;



public class SQLiteSyncHelper {

    private boolean isMaster;
    private String dbID;
    private SQLiteDatabase db;
    private static final String LOG_TAG = SQLiteSyncHelper.class.getSimpleName();
    private static final String TABLE_SETTINGS = "SETTINGS";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_KEY = "key";
    private static final String COLUMN_VALUE = "value";
    private static final String SETTINGS_CREATE =
            "CREATE TABLE " + TABLE_SETTINGS +
                    "(" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_KEY + " TEXT NOT NULL, " +
                    COLUMN_VALUE + " TEXT NOT NULL ;";
    public static final String SETTINGS_DROP = "DROP TABLE IF EXISTS " + TABLE_SETTINGS;
    private static final String KEY_IS_MASTER = "isMaster";
    private static final String KEY_DB_ID = "DB_ID";
    private static final String KEY_TABLES = "tables";
    private static final String KEY_LASTSYNCTIME = "lastSyncTime";
    private static final String COLUMNE_IS_DELETED = "isDeleted";
    private static final String COLUMNE_TIMESTAMP = "timestamp";

    public SQLiteSyncHelper(SQLiteDatabase db, boolean isMaster, String dbID){
        this.db = db;
        if(!isTableExisting(TABLE_SETTINGS)){
            prepareSyncableDB(isMaster, dbID);
            return;
        }

        if(isDbMaster() != isMaster) {
            setMaster(isMaster);
        }

        if(!getDbId().equals(dbID)) {
            setDbID(dbID);
        }
    }

    private void prepareSyncableDB(boolean isMaster, String dbID){
        try {
            this.db.execSQL(SETTINGS_CREATE);
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "creating failed for table: "+ TABLE_SETTINGS + e.getMessage());
        }

        // Insert isMaster Key-Value pair
        int intValueIsMaster = (isMaster) ? 1 : 0;
        ContentValues isMasterKeyValue = new ContentValues();
        isMasterKeyValue.put(COLUMN_KEY, KEY_IS_MASTER);
        isMasterKeyValue.put(COLUMN_VALUE, intValueIsMaster);
        this.db.insert(TABLE_SETTINGS, null, isMasterKeyValue);

        // Insert dbID Key-Value pair
        ContentValues dbIDKeyValue = new ContentValues();
        isMasterKeyValue.put(COLUMN_KEY, KEY_DB_ID);
        isMasterKeyValue.put(COLUMN_VALUE, dbID);
        this.db.insert(TABLE_SETTINGS, null, dbIDKeyValue);
    }

    public void tearDownSyncableDB(){
        db.execSQL(SETTINGS_DROP);
    }

    public JSONObject getDelta(JSONObject peer) {

        // TODO test-data from peer delta
        String lastSyncTime = "1462109540";
        peer = new JSONObject();
        try {
            peer.put(KEY_LASTSYNCTIME,lastSyncTime);
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "JSONObject error for writing test-peer JSON: " + e.getMessage());
        }
        // TODO test-data from peer delta

        JSONObject delta = prepareDeltaObject(lastSyncTime, getDbId());

        List<String> tables = getSyncableTableNames();
        for (String table: tables) {
            Cursor cursor = this.db.query(
                    table, new String[] {COLUMNE_TIMESTAMP},COLUMNE_TIMESTAMP +" >= '?'",
                    new String[] {lastSyncTime}
                    ,null, null, null
            );
            // TODO fill delta JSONObject
            cursor.close();
        }
        return delta;

    }

    private JSONObject prepareDeltaObject(String lastSyncTime, String dbId){
        JSONObject delta = new JSONObject();
        try {
            delta.put(KEY_DB_ID, dbId);
            delta.put(KEY_IS_MASTER,String.valueOf(this.isMaster));
            delta.put(KEY_LASTSYNCTIME,lastSyncTime);
            delta.put(KEY_TABLES,new JSONArray());
        } catch (JSONException ex) {
            ex.printStackTrace();
            Log.e(LOG_TAG, "JSONObject error while preparing delta: " + ex.getMessage());
        }
        return delta;
    }

    private String getDbId(){
        Cursor cursor = this.db.query(
                TABLE_SETTINGS,new String[] {"value"},"key = '?'",new String[] {KEY_DB_ID}
                ,null, null, null
        );
        cursor.moveToFirst();
        int valueIndex = cursor.getColumnIndex("value");
        String DbId = cursor.getString(valueIndex);
        cursor.close();
        return DbId;
    }

    public List<String> getSyncableTableNames(){
        List<String> result = new ArrayList<>();
        Cursor cursor = this.db.rawQuery("SELECT DISTINCT tbl_name FROM sqlite_master", null);
        // TODO nur tabellen in die Liste schmeißen, die auch is_deleted und timestamp haben
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            result.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        result.remove(TABLE_SETTINGS);
        return result;
    }

    public void makeTableSyncable(String table){
        addIsDeletedColumn(table);
        addTimestampColumn(table);
    }

    private void addIsDeletedColumn(String table){
        Cursor cursor = this.db.rawQuery("SELECT "+ COLUMNE_IS_DELETED +" FROM " + table , null);
        cursor.moveToFirst();
        if(cursor.getColumnCount() < 1){
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + COLUMNE_IS_DELETED + " INTEGER");
        }
        cursor.close();
    }

    private void addTimestampColumn(String table){
        Cursor cursor = this.db.rawQuery("SELECT "+ COLUMNE_TIMESTAMP +" FROM " + table , null);
        cursor.moveToFirst();
        if(cursor.getColumnCount() < 1){
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + COLUMNE_TIMESTAMP + " TEXT");
        }
        cursor.close();
    }

    public boolean isTableExisting(String table){
        Cursor cursor = this.db.rawQuery("SELECT DISTINCT tbl_name FROM sqlite_master WHERE tbl_name = '" + table + "'", null);
        if(cursor!=null) {
            if(cursor.getCount()>0) {
                cursor.close();
                return true;
            }
            cursor.close();
        }
        return false;
    }

    public boolean isDbMaster(){
        Cursor cursor = this.db.query(
                TABLE_SETTINGS,new String[] {"value"},"key = '?'",new String[] {KEY_IS_MASTER}
                ,null, null, null
        );
        cursor.moveToFirst();
        int valueIndex = cursor.getColumnIndex("value");
        boolean isMaster = cursor.getInt(valueIndex)== 1;
        cursor.close();
        return isMaster;
    }

    public void setMaster(boolean isMaster) {
        this.isMaster = isMaster;
        int intValueIsMaster = (this.isMaster) ? 1 : 0;

        ContentValues values = new ContentValues();
        values.put(COLUMN_VALUE, intValueIsMaster);

        this.db.update(TABLE_SETTINGS,
                values,
                COLUMN_KEY + "=" + KEY_IS_MASTER,
                null);
    }

    public void setDbID(String dbID) {
        this.dbID = dbID;

        ContentValues values = new ContentValues();
        values.put(COLUMN_VALUE, this.dbID);

        this.db.update(TABLE_SETTINGS,
                values,
                COLUMN_KEY + "=" + KEY_DB_ID,
                null);
    }

}