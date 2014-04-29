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

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

public class ActivityMain extends Activity {
	private static ExecutorService mExecutor = Executors.newFixedThreadPool(
			Runtime.getRuntime().availableProcessors(),
			new PriorityThreadFactory());

	private static class PriorityThreadFactory implements ThreadFactory {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings)
			return true;
		return super.onOptionsItemSelected(item);
	}

	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_activity_main,
					container, false);

			return rootView;
		}

		@Override
		public void onViewCreated(View view, Bundle savedInstanceState) {
			ExpandableListView lvActivity = (ExpandableListView) getActivity()
					.findViewById(R.id.elvActivity);
			lvActivity.setAdapter(new ActivityAdapter(R.layout.activity_root));

			super.onViewCreated(view, savedInstanceState);
		}

		private class ActivityAdapter extends BaseExpandableListAdapter {
			private int count = 0;
			private Map<Integer, Integer> mapCount = new HashMap<Integer, Integer>();
			private DatabaseHelper mDatabaseHelper = null;
			private LayoutInflater mInflater = (LayoutInflater) getActivity()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			public ActivityAdapter(int resource) {
				mDatabaseHelper = new DatabaseHelper(getActivity());
			}

			@Override
			public Object getGroup(int groupPosition) {
				return groupPosition + 1;
			}

			@Override
			public int getGroupCount() {
				if (count == 0)
					count = mDatabaseHelper.getActivityCount();
				return count;
			}

			@Override
			public long getGroupId(int groupPosition) {
				return groupPosition * 1000;
			}

			private class GroupViewHolder {
				private View row;
				private int position;
				public TextView tvStart;
				public TextView tvStop;
				public TextView tvActivity;
				public TextView tvConfidence;

				public GroupViewHolder(View theRow, int thePosition) {
					row = theRow;
					position = thePosition;
					tvStart = (TextView) row.findViewById(R.id.tvStart);
					tvStop = (TextView) row.findViewById(R.id.tvStop);
					tvActivity = (TextView) row.findViewById(R.id.tvActivity);
					tvConfidence = (TextView) row
							.findViewById(R.id.tvConfidence);
				}
			}

			private class GroupHolderTask extends
					AsyncTask<Object, Object, Object> {
				private int position;
				private GroupViewHolder holder;
				private int id;
				private DatabaseHelper.Activity activity;

				public GroupHolderTask(int thePosition,
						GroupViewHolder theHolder, int theId) {
					position = thePosition;
					holder = theHolder;
					id = theId;
				}

				@Override
				protected Object doInBackground(Object... params) {
					activity = mDatabaseHelper.getActivity(id);
					return activity;
				}

				@Override
				protected void onPostExecute(Object result) {
					if (holder.position == position && result != null) {
						SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat(
								"HH:mm:ss", Locale.getDefault());

						// Set data
						holder.tvStart.setText(TIME_FORMATTER
								.format(activity.start));
						holder.tvStop.setText(TIME_FORMATTER
								.format(activity.stop));
						holder.tvActivity.setText(BackgroundService
								.getNameFromType(getActivity(),
										activity.activity));
						holder.tvConfidence.setText(String.format("%d %%",
								activity.confidence));
					}
				}
			}

			@Override
			public View getGroupView(int groupPosition, boolean isExpanded,
					View convertView, ViewGroup parent) {
				GroupViewHolder holder;
				if (convertView == null) {
					convertView = mInflater.inflate(R.layout.activity_root,
							null);
					holder = new GroupViewHolder(convertView, groupPosition);
					convertView.setTag(holder);
				} else {
					holder = (GroupViewHolder) convertView.getTag();
					holder.position = groupPosition;
				}

				// TODO: reset holder

				// Async update
				int id = (Integer) getGroup(groupPosition);
				new GroupHolderTask(groupPosition, holder, id)
						.executeOnExecutor(mExecutor, (Object) null);

				return convertView;
			}

			@Override
			public Object getChild(int groupPosition, int childPosition) {
				return childPosition + 1;
			}

			@Override
			public long getChildId(int groupPosition, int childPosition) {
				return groupPosition * 1000000 + childPosition;
			}

			@Override
			public int getChildrenCount(int groupPosition) {
				int id = (Integer) getGroup(groupPosition);
				if (!mapCount.containsKey(id))
					mapCount.put(id, mDatabaseHelper.getDetailCount(id));
				return mapCount.get(id);
			}

			@Override
			public boolean isChildSelectable(int groupPosition,
					int childPosition) {
				return false;
			}

			private class ChildViewHolder {
				private View row;
				private int groupPosition;
				private int childPosition;
				public TextView tvTime;
				public TextView tvType;
				public TextView tvData;

				private ChildViewHolder(View theRow, int gPosition,
						int cPosition) {
					row = theRow;
					groupPosition = gPosition;
					childPosition = cPosition;
					tvTime = (TextView) row.findViewById(R.id.tvTime);
					tvType = (TextView) row.findViewById(R.id.tvType);
					tvData = (TextView) row.findViewById(R.id.tvData);
				}
			}

			private class ChildHolderTask extends
					AsyncTask<Object, Object, Object> {
				private int groupPosition;
				private int childPosition;
				private ChildViewHolder holder;
				private DatabaseHelper.Detail detail;

				public ChildHolderTask(int gPosition, int cPosition,
						ChildViewHolder theHolder) {
					groupPosition = gPosition;
					childPosition = cPosition;
					holder = theHolder;
				}

				@Override
				protected Object doInBackground(Object... params) {
					int id = (Integer) getGroup(groupPosition);
					int childId = (Integer) getChild(groupPosition,
							childPosition);
					detail = mDatabaseHelper.getDetail(id, childId);
					return detail;
				}

				@Override
				protected void onPostExecute(Object result) {
					if (holder.groupPosition == groupPosition
							&& holder.childPosition == childPosition
							&& result != null) {
						SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat(
								"HH:mm:ss", Locale.getDefault());

						// Set data
						holder.tvTime.setText(TIME_FORMATTER
								.format(detail.time));
						holder.tvType.setText(DatabaseHelper
								.getNameForType(detail.type));
						if (detail.type == DatabaseHelper.TYPE_STEPS) {
							detail.data.setDataPosition(0);
							detail.data.readInt(); // Version
							holder.tvData.setText(Integer.toString(detail.data
									.readInt()));
						} else
							holder.tvData.setText("");
					}
				}
			}

			@Override
			public View getChildView(int groupPosition, int childPosition,
					boolean isLastChild, View convertView, ViewGroup parent) {
				ChildViewHolder holder;
				if (convertView == null) {
					convertView = mInflater.inflate(R.layout.activity_data,
							null);
					holder = new ChildViewHolder(convertView, groupPosition,
							childPosition);
					convertView.setTag(holder);
				} else {
					holder = (ChildViewHolder) convertView.getTag();
					holder.groupPosition = groupPosition;
					holder.childPosition = childPosition;
				}

				// TODO: reset holder

				// Async update
				new ChildHolderTask(groupPosition, childPosition, holder)
						.executeOnExecutor(mExecutor, (Object) null);

				return convertView;
			}

			@Override
			public boolean hasStableIds() {
				return true;
			}
		}
	}
}