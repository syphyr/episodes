/*
 * Copyright (C) 2012-2014 Jamie Nicol <jamie@thenicols.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.redcoracle.episodes;

import android.Manifest;
import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.redcoracle.episodes.db.EpisodesTable;
import com.redcoracle.episodes.db.ShowsProvider;
import com.redcoracle.episodes.db.ShowsTable;
import com.redcoracle.episodes.services.AsyncTask;
import com.redcoracle.episodes.services.RefreshAllShowsTask;

import java.util.ArrayList;
import java.util.List;

public class ShowsListFragment
		extends ListFragment
		implements LoaderManager.LoaderCallbacks<Cursor>
{
	private static final int LOADER_ID_SHOWS = 0;
	private static final int LOADER_ID_EPISODES = 1;

	private static final String KEY_PREF_SHOWS_FILTER = "pref_shows_filter";
	private static final String KEY_PREF_CONFIRMED_BACKUP = "pref_confirmed_backup";

	private static final int SHOWS_FILTER_ALL = 0;
	private static final int SHOWS_FILTER_STARRED = 1;
	private static final int SHOWS_FILTER_UNCOMPLETED = 2;
	private static final int SHOWS_FILTER_ARCHIVED = 3;
	private static final int SHOWS_FILTER_UPCOMING = 4;

	private ShowsListAdapter listAdapter;
	private Cursor showsData;
	private Cursor episodesData;

	private ActivityResultLauncher<String> reqNotificationPermission;


	public interface OnShowSelectedListener {
		public void onShowSelected(int showId);
	}
	private OnShowSelectedListener onShowSelectedListener;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			onShowSelectedListener = (OnShowSelectedListener)activity;
		} catch (ClassCastException e) {
			final String message =
					String.format("%s must implement OnShowSelectedListener",
							activity.toString());
			throw new ClassCastException(message);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		reqNotificationPermission = registerForActivityResult(
			new ActivityResultContracts.RequestPermission(),
			granted -> {}
		);
	}

	public View onCreateView(LayoutInflater inflater,
							 ViewGroup container,
							 Bundle savedInstanceState) {
		return inflater.inflate(R.layout.shows_list_fragment, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		listAdapter = new ShowsListAdapter(getActivity(),
				null,
				null);
		setListAdapter(listAdapter);

		getLoaderManager().initLoader(LOADER_ID_SHOWS, null, this);
		getLoaderManager().initLoader(LOADER_ID_EPISODES, null, this);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.shows_list_fragment, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {

		// hide refresh all option if no shows exist
		final boolean showsExist = (showsData != null && showsData.moveToFirst());
		menu.findItem(R.id.menu_refresh_all_shows).setVisible(showsExist);

		/* set the currently selected filter's menu item as checked */
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		final int filter = prefs.getInt(KEY_PREF_SHOWS_FILTER, SHOWS_FILTER_ALL);

		switch (filter) {
			case SHOWS_FILTER_ALL:
				menu.findItem(R.id.menu_filter_all).setChecked(true);
				break;
			case SHOWS_FILTER_STARRED:
				menu.findItem(R.id.menu_filter_starred).setChecked(true);
				break;
			case SHOWS_FILTER_UNCOMPLETED:
				menu.findItem(R.id.menu_filter_uncompleted).setChecked(true);
				break;
			case SHOWS_FILTER_ARCHIVED:
				menu.findItem(R.id.menu_filter_archived).setChecked(true);
				break;
			case SHOWS_FILTER_UPCOMING:
				menu.findItem(R.id.menu_filter_upcoming).setChecked(true);
				break;
		}

		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_refresh_all_shows:
				refreshAllShows();
				return true;

			case R.id.menu_filter_all:
			case R.id.menu_filter_starred:
			case R.id.menu_filter_uncompleted:
			case R.id.menu_filter_archived:
			case R.id.menu_filter_upcoming:
				if (!item.isChecked()) {
					item.setChecked(true);
				}

				final SharedPreferences prefs =
						PreferenceManager.getDefaultSharedPreferences(getActivity());
				final SharedPreferences.Editor editor = prefs.edit();
				if (item.getItemId() == R.id.menu_filter_all) {
					editor.putInt(KEY_PREF_SHOWS_FILTER, SHOWS_FILTER_ALL);
				} else if (item.getItemId() == R.id.menu_filter_starred) {
					editor.putInt(KEY_PREF_SHOWS_FILTER, SHOWS_FILTER_STARRED);
				} else if (item.getItemId() == R.id.menu_filter_uncompleted) {
					editor.putInt(KEY_PREF_SHOWS_FILTER, SHOWS_FILTER_UNCOMPLETED);
				} else if (item.getItemId() == R.id.menu_filter_archived) {
					editor.putInt(KEY_PREF_SHOWS_FILTER, SHOWS_FILTER_ARCHIVED);
				} else if (item.getItemId() == R.id.menu_filter_upcoming) {
					editor.putInt(KEY_PREF_SHOWS_FILTER, SHOWS_FILTER_UPCOMING);
				}
				editor.apply();

				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		if (id == LOADER_ID_SHOWS) {
			final String[] projection = {
					ShowsTable.COLUMN_ID,
					ShowsTable.COLUMN_NAME,
					ShowsTable.COLUMN_STARRED,
					ShowsTable.COLUMN_ARCHIVED,
					ShowsTable.COLUMN_BANNER_PATH
			};
			return new CursorLoader(getActivity(),
					ShowsProvider.CONTENT_URI_SHOWS,
					projection,
					null,
					null,
					ShowsTable.COLUMN_STARRED + " DESC, " +
					ShowsTable.COLUMN_NAME + " COLLATE LOCALIZED ASC");

		} else if (id == LOADER_ID_EPISODES) {
			final String[] projection = {
					EpisodesTable.COLUMN_SHOW_ID,
					EpisodesTable.COLUMN_SEASON_NUMBER,
					EpisodesTable.COLUMN_FIRST_AIRED,
					EpisodesTable.COLUMN_WATCHED
			};
			final String selection =
					String.format("%s!=?", EpisodesTable.COLUMN_SEASON_NUMBER);
			final String[] selectionArgs = {
					"0"
			};
			return new CursorLoader(getActivity(),
					ShowsProvider.CONTENT_URI_EPISODES,
					projection,
					selection,
					selectionArgs,
					null);

		} else {
			throw new IllegalArgumentException("invalid loader id");
		}
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		switch (loader.getId()) {
			case LOADER_ID_SHOWS:
				showsData = data;
				listAdapter.swapShowsCursor(data);
				break;

			case LOADER_ID_EPISODES:
				episodesData = data;
				listAdapter.swapEpisodesCursor(data);
				break;
		}

		getActivity().invalidateOptionsMenu();
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		onLoadFinished(loader, null);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		onShowSelectedListener.onShowSelected((int)id);
	}

	private void checkNotificationPermission() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			return;
		}

		boolean granted = ContextCompat.checkSelfPermission(
			this.requireContext(),
			Manifest.permission.POST_NOTIFICATIONS
		) == PackageManager.PERMISSION_GRANTED;

		if (!granted) {
			reqNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
		}
	}

	private void refreshAllShows() {
		checkNotificationPermission();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.requireContext());
		boolean confirmed_backup = prefs.getBoolean(KEY_PREF_CONFIRMED_BACKUP, false);

		if (confirmed_backup) {
			new AsyncTask().executeAsync(new RefreshAllShowsTask());
		} else {
			new AlertDialog.Builder(this.requireContext())
			.setTitle(R.string.provider_change_warning_title)
			.setMessage(R.string.provider_change_warning_detail)
			.setPositiveButton(R.string.provider_change_warning_confirm, (dialogInterface, i) -> {
				prefs.edit().putBoolean(KEY_PREF_CONFIRMED_BACKUP, true).apply();
				new AsyncTask().executeAsync(new RefreshAllShowsTask());
			})
			.setNegativeButton(R.string.provider_change_warning_cancel, (dialogInterface, i) -> {})
			.show();
		}
	}

	private static class ShowsListAdapter
			extends BaseAdapter
			implements SharedPreferences.OnSharedPreferenceChangeListener
	{
		private Context context;
		private Cursor showsCursor;
		private int filter;
		private EpisodesCounter episodesCounter;

		// list of shows to be displayed with current filter. maps from
		// the show's position in the list to its position in the cursor.
		private List<Integer> filteredShows;

		public ShowsListAdapter(Context context,
								Cursor showsCursor,
								Cursor episodesCursor) {
			this.context = context;

			episodesCounter = new EpisodesCounter(EpisodesTable.COLUMN_SHOW_ID);
			episodesCounter.swapCursor(episodesCursor);

			final SharedPreferences prefs =
					PreferenceManager.getDefaultSharedPreferences(context);
			prefs.registerOnSharedPreferenceChangeListener(this);
			filter = prefs.getInt(KEY_PREF_SHOWS_FILTER, SHOWS_FILTER_ALL);

			filteredShows = new ArrayList<Integer>();

			swapShowsCursor(showsCursor);
		}

		public void swapShowsCursor(Cursor showsCursor) {
			this.showsCursor = showsCursor;

			updateFilter();
			notifyDataSetChanged();
		}

		public void swapEpisodesCursor(Cursor episodesCursor) {
			episodesCounter.swapCursor(episodesCursor);

			if (showsCursor != null) {
				updateFilter();
				notifyDataSetChanged();
			}
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
											  String key) {
			if (key.equals(KEY_PREF_SHOWS_FILTER)) {
				filter = sharedPreferences.getInt(KEY_PREF_SHOWS_FILTER,
						SHOWS_FILTER_ALL);

				if (showsCursor != null) {
					updateFilter();
					notifyDataSetChanged();
				}
			}
		}

		private void updateFilter() {
			filteredShows.clear();

			if (showsCursor == null || !showsCursor.moveToFirst()) {
				return;
			}

			do {
				switch (filter) {
					case SHOWS_FILTER_STARRED:
						final int starredColumnIndex =
								showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_STARRED);
						if (showsCursor.getInt(starredColumnIndex) > 0) {
							filteredShows.add(showsCursor.getPosition());
						}
						break;

					case SHOWS_FILTER_ARCHIVED:
						final int archivedColumnIndex = showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_ARCHIVED);
						if (showsCursor.getInt(archivedColumnIndex) > 0) {
							filteredShows.add(showsCursor.getPosition());
						}
						break;

					case SHOWS_FILTER_UNCOMPLETED:
						final int idColumnIndexUncompleted = showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_ID);
						final int idUncompleted = showsCursor.getInt(idColumnIndexUncompleted);

						if ((episodesCounter.getNumWatchedEpisodes(idUncompleted) < episodesCounter.getNumAiredEpisodes(idUncompleted)) &&
								showsCursor.getInt(showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_ARCHIVED)) == 0)
						{
							filteredShows.add(showsCursor.getPosition());
						}
						break;

					case SHOWS_FILTER_UPCOMING:
						final int idColumnIndexUpcoming = showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_ID); //change to meaningful variable
						final int idUpcoming = showsCursor.getInt(idColumnIndexUpcoming);

						if((episodesCounter.getNumUpcomingEpisodes(idUpcoming) > 0) && (episodesCounter.getNumWatchedEpisodes(idUpcoming) == episodesCounter.getNumAiredEpisodes(idUpcoming)) &&
								showsCursor.getInt(showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_ARCHIVED)) == 0)
						{
							filteredShows.add(showsCursor.getPosition());
						}
						break;

					default:
						final int columnIndex = showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_ARCHIVED);
						if (showsCursor.getInt(columnIndex) == 0) {
							filteredShows.add(showsCursor.getPosition());
						}
						break;
				}
			} while (showsCursor.moveToNext());
		}

		@Override
		public int getCount() {
			if (showsCursor == null) {
				return 0;
			} else {
				return filteredShows.size();
			}
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			showsCursor.moveToPosition(filteredShows.get(position));

			final int idColumnIndex =
					showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_ID);
			return showsCursor.getInt(idColumnIndex);
		}

		@Override
		public View getView(int position,
							View convertView,
							ViewGroup parent) {

			final LayoutInflater inflater = LayoutInflater.from(context);
			if(convertView == null) {
				convertView = inflater.inflate(R.layout.shows_list_item,
						parent,
						false);
			}

			showsCursor.moveToPosition(filteredShows.get(position));

			final int idColumnIndex = showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_ID);
			final int id = showsCursor.getInt(idColumnIndex);

			final ContentResolver contentResolver = context.getContentResolver();

			final TextView nameView = convertView.findViewById(R.id.show_name_view);
			final int nameColumnIndex = showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_NAME);
			final String name = showsCursor.getString(nameColumnIndex);
			nameView.setText(name);

			final ImageView bannerView = convertView.findViewById(R.id.banner_view);
			final int bannerPathColumnIndex = showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_BANNER_PATH);
			final String bannerPath = showsCursor.getString(bannerPathColumnIndex);

			bannerView.setImageResource(R.drawable.blank_show_banner);
			if (bannerPath != null && !bannerPath.equals("")) {
				final String bannerUrl = String.format("https://image.tmdb.org/t/p/w1280/%s", bannerPath);

				Glide.with(convertView)
						.load(bannerUrl)
						.diskCacheStrategy(DiskCacheStrategy.RESOURCE)
						.centerCrop()
						.placeholder(R.drawable.blank_show_banner)
						.into(bannerView);
			}

			final ToggleButton starredToggle = (ToggleButton)convertView.findViewById(R.id.show_starred_toggle);
			final int starredColumnIndex = showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_STARRED);
			final boolean starred = showsCursor.getInt(starredColumnIndex) > 0;

			starredToggle.setOnCheckedChangeListener(null);
			starredToggle.setChecked(starred);

			starredToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				public void onCheckedChanged(CompoundButton buttonView,
											 boolean isChecked) {
					final AsyncQueryHandler handler = new AsyncQueryHandler(contentResolver) {};
					final ContentValues showValues = new ContentValues();
					showValues.put(ShowsTable.COLUMN_STARRED, isChecked);

					final Uri showUri = Uri.withAppendedPath(ShowsProvider.CONTENT_URI_SHOWS, String.valueOf(id));
					handler.startUpdate(0,
							null,
							showUri,
							showValues,
							null,
							null);
				}
			});

			final ToggleButton archivedToggle = (ToggleButton)convertView.findViewById(R.id.show_archived_toggle);
			final int archivedColumnIndex = showsCursor.getColumnIndexOrThrow(ShowsTable.COLUMN_ARCHIVED);
			final boolean archived = showsCursor.getInt(archivedColumnIndex) > 0;

			archivedToggle.setOnCheckedChangeListener(null);
			archivedToggle.setChecked(archived);

			archivedToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					final AsyncQueryHandler handler = new AsyncQueryHandler(contentResolver) {};
					final ContentValues showValues = new ContentValues();
					showValues.put(ShowsTable.COLUMN_ARCHIVED, isChecked);
					final Uri showUri = Uri.withAppendedPath(ShowsProvider.CONTENT_URI_SHOWS, String.valueOf(id));
					handler.startUpdate(0, null, showUri, showValues, null, null);
				}
			});

			final int numAired = episodesCounter.getNumAiredEpisodes(id);
			final int numWatched = episodesCounter.getNumWatchedEpisodes(id);
			final int numUpcoming = episodesCounter.getNumUpcomingEpisodes(id);

			final ProgressBar progressBar =
					(ProgressBar)convertView.findViewById(R.id.show_progress_bar);
			progressBar.setMax(numAired);
			progressBar.setProgress(numWatched);

			final TextView watchedCountView =
					(TextView)convertView.findViewById(R.id.watched_count_view);
			String watchedCountText = context.getString(R.string.watched_count,
					numWatched,
					numAired);
			if (numUpcoming != 0) {
				watchedCountText += " " +
						context.getString(R.string.upcoming_count,
								numUpcoming);
			}
			watchedCountView.setText(watchedCountText);

			return convertView;
		}
	}
}
