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

public class DatabaseHelper extends SQLiteOpenHelper {

	private static final String DBNAME = "Activity";
	private static final int DBVERSION = 1;

	private static final String DBCREATE = "CREATE TABLE activity ("
			+ "ID INTEGER PRIMARY KEY AUTOINCREMENT"
			+ ", start INTEGER NOT NULL" + ", stop INTEGER"
			+ ", activity INTEGER NOT NULL" + ");" + "CREATE TABLE detail ("
			+ "ID INTEGER PRIMARY KEY AUTOINCREMENT"
			+ ", time INTEGER NOT NULL" + ", type INTEGER NOT NULL"
			+ ", data TEXT" + ");";

	public DatabaseHelper(Context context) {
		super(context, DBNAME, null, DBVERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(DBCREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	// Register activity
	public void registerActivity(long time, long activity) {
		long id = -1;
		ContentValues cv = new ContentValues();

		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = db.query("activity", new String[] { "ID",
					"activity" }, null, new String[] {}, null, null,
					"start DESC LIMIT 1");
			try {
				if (cursor.moveToFirst() && cursor.getLong(1) == activity) {
					id = cursor.getLong(0);
					cv.put("stop", time);
				} else {
					cv.put("start", time);
					cv.put("stop", time);
					cv.put("activity", activity);
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
	}
}