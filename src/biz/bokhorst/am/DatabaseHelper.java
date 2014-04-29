package biz.bokhorst.am;

/*
 Copyright 2014 Marcel Bokhorst
 All Rights Reserved

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.os.Parcel;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {

	private static final String DBNAME = "activity";
	private static final int DBVERSION = 1;

	private static final String DBCREATE_ACTIVITY = "CREATE TABLE activity ("
			+ "ID INTEGER PRIMARY KEY AUTOINCREMENT"
			+ ", start INTEGER NOT NULL" + ", stop INTEGER"
			+ ", activity INTEGER NOT NULL" + ", confidence INTEGER NOT NULL"
			+ ");";

	private static final String DBCREATE_DETAIL = "CREATE TABLE detail ("
			+ "ID INTEGER PRIMARY KEY AUTOINCREMENT"
			+ ", activity INTEGER NOT NULL" + ", time INTEGER NOT NULL"
			+ ", type INTEGER NOT NULL" + ", data BLOB" + ");";

	public static final long TYPE_LOCATION = 1;
	public static final long TYPE_STEPS = 2;

	public DatabaseHelper(Context context) {
		super(context, DBNAME, null, DBVERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(DBCREATE_ACTIVITY);
		db.execSQL(DBCREATE_DETAIL);
		Log.w("AM", "Database created");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	public boolean registerActivity(long time, int activity, int confidence) {
		long id = -1;
		ContentValues cv = new ContentValues();

		SQLiteDatabase db = getWritableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = db.query("activity", new String[] { "ID",
					"activity", "confidence" }, null, new String[] {}, null,
					null, "start DESC LIMIT 1");
			try {
				if (cursor.moveToFirst() && cursor.getLong(1) == activity) {
					id = cursor.getLong(0);
					cv.put("stop", time);
					cv.put("confidence", Math.max(confidence, cursor.getInt(2)));
				} else {
					cv.put("start", time);
					cv.put("stop", time);
					cv.put("activity", activity);
					cv.put("confidence", confidence);
				}
			} finally {
				cursor.close();
			}

			if (id == -1)
				db.insert("activity", null, cv);
			else
				db.update("activity", cv, "ID=?",
						new String[] { Long.toString(id) });

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		return (id == -1);
	}

	public int getActivityCount() {
		int count = 0;
		SQLiteDatabase db = getReadableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM activity", null);
			try {
				if (cursor.moveToFirst())
					count = cursor.getInt(0);
			} finally {
				cursor.close();
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		return count;
	}

	public Activity getActivity(int id) {
		Activity result = null;
		SQLiteDatabase db = getReadableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = db.query("activity", new String[] { "start",
					"stop", "activity", "confidence" }, "ID=?",
					new String[] { Integer.toString(id) }, null, null, null);
			try {
				if (cursor.moveToFirst()) {
					result = new Activity();
					result.start = cursor.getLong(0);
					result.stop = cursor.getLong(1);
					result.activity = cursor.getInt(2);
					result.confidence = cursor.getInt(3);
				}
			} finally {
				cursor.close();
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		return result;
	}

	public class Activity {
		public long start;
		public long stop;
		public int activity;
		public int confidence;
	}

	public boolean registerDetail(long time, long type, Parcel data) {
		boolean result = false;
		SQLiteDatabase db = getWritableDatabase();
		db.beginTransaction();
		try {
			long id = -1;
			Cursor cursor = db.query("activity", new String[] { "ID", }, null,
					new String[] {}, null, null, "start DESC LIMIT 1");
			try {
				if (cursor.moveToFirst())
					id = cursor.getLong(0);
			} finally {
				cursor.close();
			}

			if (id >= 0) {
				ContentValues cv = new ContentValues();
				cv.put("activity", id);
				cv.put("time", time);
				cv.put("type", type);
				cv.put("data", data.marshall());
				db.insert("detail", null, cv);
				result = true;
			} else
				Log.w("AM", "No activity for detail");

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		return result;
	}
}