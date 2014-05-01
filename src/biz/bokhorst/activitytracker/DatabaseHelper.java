package biz.bokhorst.activitytracker;

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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.google.android.gms.location.DetectedActivity;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.location.Location;
import android.os.Build;
import android.os.Parcel;
import android.util.Log;

// sqlite3 /data/data/biz.bokhorst.activitytracker/databases/activity

public class DatabaseHelper extends SQLiteOpenHelper {
	private static String TAG = "ATRACKER";

	private static final String DBNAME = "activity";
	private static final int DBVERSION = 1;

	private static final String DBCREATE_ACTIVITY = "CREATE TABLE activity ("
			+ "ID INTEGER PRIMARY KEY AUTOINCREMENT"
			+ ", start INTEGER NOT NULL" + ", stop INTEGER"
			+ ", activity INTEGER NOT NULL" + ");";

	private static final String DBCREATE_DATA = "CREATE TABLE data ("
			+ "ID INTEGER PRIMARY KEY AUTOINCREMENT"
			+ ", activity INTEGER NOT NULL" + ", time INTEGER NOT NULL"
			+ ", type INTEGER NOT NULL" + ", data BLOB" + ");";

	public DatabaseHelper(Context context) {
		super(context, DBNAME, null, DBVERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(DBCREATE_ACTIVITY);
		db.execSQL(DBCREATE_DATA);
		Log.w(TAG, "Database created");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	public boolean registerActivityRecord(ActivityRecord record) {
		long id = -1;

		SQLiteDatabase db = getWritableDatabase();
		try {
			db.beginTransaction();
			try {
				ContentValues cv = new ContentValues();

				Cursor cursor = db.query("activity", new String[] { "ID",
						"activity" }, null, new String[] {}, null, null,
						"start DESC LIMIT 1");
				try {
					if (cursor.moveToFirst()
							&& cursor.getLong(1) == record.activity) {
						id = cursor.getLong(0);
						cv.put("stop", record.start);
					}
				} finally {
					cursor.close();
				}

				if (id < 0) {
					cv.put("start", record.start);
					cv.put("stop", record.start);
					cv.put("activity", record.activity);
					db.insert("activity", null, cv);
				} else
					db.update("activity", cv, "ID=?",
							new String[] { Long.toString(id) });

				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
		} finally {
			db.close();
		}

		return (id == -1);
	}

	public long getLastActivityRecordId() {
		long id = -1;

		SQLiteDatabase db = getReadableDatabase();
		try {
			db.beginTransaction();
			try {
				Cursor cursor = db
						.query("activity", new String[] { "ID", }, null,
								new String[] {}, null, null,
								"start DESC LIMIT 1");
				try {
					if (cursor.moveToFirst())
						id = cursor.getLong(0);
				} finally {
					cursor.close();
				}

				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
		} finally {
			db.close();
		}

		return id;
	}

	public void registerActivityData(ActivityData data) {
		long id = getLastActivityRecordId();
		if (id < 0)
			Log.w(TAG, "No activity for data");
		else {
			SQLiteDatabase db = getWritableDatabase();
			try {
				db.beginTransaction();
				try {
					long actid = -1;
					ContentValues cv = new ContentValues();

					// Aggregate steps
					if (data.type == ActivityData.TYPE_STEPS) {
						Cursor cursor = db.query("data", new String[] { "ID",
								"time", "type", "data" }, "activity=?",
								new String[] { Long.toString(id) }, null, null,
								"time DESC LIMIT 1");
						try {
							if (cursor.moveToFirst())
								if (cursor.getInt(2) == ActivityData.TYPE_STEPS) {
									actid = cursor.getLong(0);
									ActivityData prev = ActivityData
											.FromBlob(cursor.getLong(1),
													cursor.getInt(2),
													cursor.getBlob(3));
									prev.steps += data.steps;
									cv.put("data", prev.getBlob());
								}
						} finally {
							cursor.close();
						}
					}

					if (actid < 0) {
						cv.put("activity", id);
						cv.put("time", data.time);
						cv.put("type", data.type);
						cv.put("data", data.getBlob());
						db.insert("data", null, cv);
					} else {
						db.update("data", cv, "ID=?",
								new String[] { Long.toString(actid) });
					}

					db.setTransactionSuccessful();
				} finally {
					db.endTransaction();
				}
			} finally {
				db.close();
			}
		}
	}

	public long getActivityCount() {
		long count = 0;
		SQLiteDatabase db = getReadableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM activity", null);
			try {
				if (cursor.moveToFirst())
					count = cursor.getLong(0);
			} finally {
				cursor.close();
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		return count;
	}

	public long getDetailCount(long id) {
		long count = 0;
		SQLiteDatabase db = getReadableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = db.rawQuery(
					"SELECT COUNT(*) FROM data WHERE activity=" + id, null);
			try {
				if (cursor.moveToFirst())
					count = cursor.getLong(0);
			} finally {
				cursor.close();
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		return count;
	}

	public ActivityRecord getActivityRecord(long id) {
		ActivityRecord result = null;
		SQLiteDatabase db = getReadableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = db.query("activity", new String[] { "start",
					"stop", "activity" }, "ID=?",
					new String[] { Long.toString(id) }, null, null, null);
			try {
				if (cursor.moveToFirst()) {
					result = new ActivityRecord();
					result.start = cursor.getLong(0);
					result.stop = cursor.getLong(1);
					result.activity = cursor.getInt(2);
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

	public ActivityData getActivityData(long id, int index) {
		ActivityData result = null;
		SQLiteDatabase db = getReadableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = db.query("data", new String[] { "time", "type",
					"data" }, "activity=?", new String[] { Long.toString(id) },
					null, null, "time");
			try {
				if (cursor.moveToFirst())
					while (--index > 0)
						cursor.moveToNext();
				if (!cursor.isAfterLast()) {
					result = ActivityData.FromBlob(cursor.getLong(0),
							cursor.getInt(1), cursor.getBlob(2));
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

	public List<ActivityData> getActivityData(long from, long to) {
		List<ActivityData> result = new ArrayList<ActivityData>();

		SQLiteDatabase db = getReadableDatabase();
		db.beginTransaction();
		try {
			Cursor cursor = db.query("data", new String[] { "time", "type",
					"data" }, "time>? AND time<?",
					new String[] { Long.toString(from), Long.toString(to) },
					null, null, "time");
			try {
			} finally {
				while (cursor.moveToNext())
					result.add(ActivityData.FromBlob(cursor.getLong(0),
							cursor.getInt(1), cursor.getBlob(2)));
				cursor.close();
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		return result;
	}

	public static class ActivityRecord {
		public long start;
		public long stop;
		public int activity;

		public ActivityRecord() {
		}

		public ActivityRecord(int act) {
			start = new Date().getTime();
			activity = act;
		}

		public ActivityRecord(long time, int act) {
			start = time;
			activity = act;
		}

		public String getName(Context context) {
			switch (activity) {
			case DetectedActivity.IN_VEHICLE:
				return context.getString(R.string.title_in_vehicle);
			case DetectedActivity.ON_BICYCLE:
				return context.getString(R.string.title_on_bicycle);
			case DetectedActivity.ON_FOOT:
				return context.getString(R.string.title_on_foot);
			case DetectedActivity.STILL:
				return context.getString(R.string.title_still);
			case DetectedActivity.UNKNOWN:
				return context.getString(R.string.title_unknown);
			case DetectedActivity.TILTING:
				return context.getString(R.string.title_tilting);
			}
			return String.format(Locale.getDefault(), "Activity %d", activity);
		}
	}

	public static class ActivityData {
		public static final int TYPE_BOOT = 0;
		public static final int TYPE_ACTIVITY = 1;
		public static final int TYPE_TRACKPOINT = 2;
		public static final int TYPE_WAYPOINT = 3;
		public static final int TYPE_STEPS = 4;

		public long time;
		public int type;
		public int activity;
		public int confidence;
		public Location location;
		public int steps;

		public ActivityData() {
		}

		public ActivityData(long atime, int atype) {
			time = atime;
			type = atype;
		}

		public ActivityData(long atime, int aactivity, int aconfidence) {
			time = atime;
			type = TYPE_ACTIVITY;
			activity = aactivity;
			confidence = aconfidence;
		}

		public ActivityData(int atype, Location alocation) {
			time = alocation.getTime();
			type = atype;
			location = alocation;
		}

		public ActivityData(int asteps) {
			time = new Date().getTime();
			type = TYPE_STEPS;
			steps = asteps;
		}

		public byte[] getBlob() {
			Parcel parcel = Parcel.obtain();
			parcel.writeInt(1); // version
			if (type == TYPE_TRACKPOINT || type == TYPE_WAYPOINT) {
				parcel.writeDouble(location.getLatitude());
				parcel.writeDouble(location.getLongitude());
				parcel.writeDouble(location.getAltitude());
				parcel.writeFloat(location.getSpeed());
				parcel.writeFloat(location.getBearing());
				parcel.writeFloat(location.getAccuracy());
			} else if (type == TYPE_STEPS) {
				parcel.writeInt(steps);
			} else if (type == TYPE_ACTIVITY) {
				parcel.writeInt(activity);
				parcel.writeInt(confidence);
			}
			byte[] result = parcel.marshall();
			parcel.recycle();
			return result;
		}

		@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
		public static ActivityData FromBlob(long atime, int atype, byte[] result) {
			ActivityData data = new ActivityData(atime, atype);

			Parcel parcel = Parcel.obtain();
			parcel.unmarshall(result, 0, result.length);
			parcel.setDataPosition(0);
			int version = parcel.readInt();
			if (version == 1) {
				if (atype == TYPE_TRACKPOINT || atype == TYPE_WAYPOINT) {
					data.location = new Location("fused");
					data.location.setTime(atime);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
						data.location.setElapsedRealtimeNanos(0);
					data.location.setLatitude(parcel.readDouble());
					data.location.setLongitude(parcel.readDouble());
					data.location.setAltitude(parcel.readDouble());
					data.location.setSpeed(parcel.readFloat());
					data.location.setBearing(parcel.readFloat());
					data.location.setAccuracy(parcel.readFloat());
				} else if (atype == TYPE_STEPS) {
					data.steps = parcel.readInt();
				} else if (atype == TYPE_ACTIVITY) {
					data.activity = parcel.readInt();
					data.confidence = parcel.readInt();
				}
			}
			parcel.recycle();

			return data;
		}

		public String getName(Context context) {
			if (type == TYPE_BOOT)
				return context.getString(R.string.title_boot);
			else if (type == TYPE_TRACKPOINT)
				return context.getString(R.string.title_trackpoint);
			else if (type == TYPE_WAYPOINT)
				return context.getString(R.string.title_waypoint);
			else if (type == TYPE_STEPS)
				return context.getString(R.string.title_steps);
			else if (type == TYPE_ACTIVITY) {
				return context.getString(R.string.title_activity);
			} else
				return String.format(Locale.getDefault(), "Type %d", type);
		}

		public String getData(Context context) {
			if (type == TYPE_BOOT)
				return "";
			else if (type == TYPE_TRACKPOINT)
				return location.toString();
			else if (type == TYPE_WAYPOINT)
				return location.toString();
			else if (type == TYPE_STEPS)
				return Integer.toString(steps);
			else if (type == TYPE_ACTIVITY) {
				String act;
				switch (activity) {
				case DetectedActivity.IN_VEHICLE:
					act = context.getString(R.string.title_in_vehicle);
					break;
				case DetectedActivity.ON_BICYCLE:
					act = context.getString(R.string.title_on_bicycle);
					break;
				case DetectedActivity.ON_FOOT:
					act = context.getString(R.string.title_on_foot);
					break;
				case DetectedActivity.STILL:
					act = context.getString(R.string.title_still);
					break;
				case DetectedActivity.UNKNOWN:
					act = context.getString(R.string.title_unknown);
					break;
				case DetectedActivity.TILTING:
					act = context.getString(R.string.title_tilting);
					break;
				default:
					act = String.format(Locale.getDefault(), "Activity %d",
							activity);
				}
				return String.format(Locale.getDefault(), "%s %d %%", act,
						confidence);
			} else
				return String.format(Locale.getDefault(), "Type %d", type);
		}
	}
}